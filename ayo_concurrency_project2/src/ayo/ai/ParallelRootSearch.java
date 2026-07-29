package ayo.ai;

import ayo.model.Board;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Root-split parallel search: the root's legal moves are farmed out to a pool
 * of workers, one task per move, and the best result wins.
 *
 * <h2>Why the root, and why it is awkward</h2>
 * Alpha-beta is inherently sequential. A move is refuted cheaply only because
 * earlier moves have already established a floor to compare it against. Hand
 * every root move to a different thread with a full window and each searches in
 * ignorance of the others, so almost nothing is pruned: the threads do far more
 * total work than one thread would, and the speedup is a fraction of the thread
 * count. Parallel search buys wall-clock time by wasting work, and the whole
 * design problem is wasting as little as possible.
 *
 * <h2>Young brothers wait</h2>
 * The mitigation implemented here: search the best-ordered move — the eldest
 * brother — sequentially first, so that a real alpha exists before anything is
 * parallelised. The remaining moves then run concurrently, each reading the
 * current alpha before it starts and raising it afterwards if it found
 * something better. The floor is shared through a single {@link AtomicInteger}
 * updated with a compare-and-swap loop, so the workers coordinate without ever
 * blocking one another.
 *
 * <h2>What is shared, and what is not</h2>
 * <ul>
 *   <li>{@link Board} positions are immutable and shared freely.</li>
 *   <li>Each worker owns an {@link AlphaBetaSearch}, held in a
 *       {@link ThreadLocal}, because that class carries mutable per-search
 *       state. This is thread confinement rather than locking.</li>
 *   <li>The alpha floor and the node counter are the only genuinely shared
 *       mutable state, and both are atomics rather than guarded fields.</li>
 * </ul>
 * There is not one lock in this class.
 *
 * <h2>Determinism</h2>
 * With alpha sharing on, which move is returned among several of exactly equal
 * value depends on thread timing, because a move searched against a higher
 * floor may report a bound instead of its true score. The value of the position
 * is unaffected. Construct with {@code shareAlpha = false} for a fully
 * reproducible search: every root move then gets a full window and an exact
 * score, so no task depends on any other and the outcome cannot vary between
 * runs. That mode is slower, and it is what the tests use.
 */
public final class ParallelRootSearch implements AutoCloseable {

    private final Evaluator evaluator;
    private final ExecutorService pool;
    private final boolean ownsPool;
    private final int threads;
    private final boolean shareAlpha;

    /** One searcher per worker thread: mutable state, confined, never shared. */
    private final ThreadLocal<AlphaBetaSearch> workerSearch;

    public ParallelRootSearch(Evaluator evaluator) {
        this(evaluator, Runtime.getRuntime().availableProcessors(), true);
    }

    public ParallelRootSearch(Evaluator evaluator, int threads, boolean shareAlpha) {
        this(evaluator, newPool(threads), threads, shareAlpha, true);
    }

    /** Uses a pool owned by the caller, who remains responsible for shutting it down. */
    public ParallelRootSearch(Evaluator evaluator, ExecutorService pool, int threads, boolean shareAlpha) {
        this(evaluator, pool, threads, shareAlpha, false);
    }

    private ParallelRootSearch(Evaluator evaluator, ExecutorService pool,
                               int threads, boolean shareAlpha, boolean ownsPool) {
        this.evaluator = evaluator;
        this.pool = pool;
        this.threads = threads;
        this.shareAlpha = shareAlpha;
        this.ownsPool = ownsPool;
        this.workerSearch = ThreadLocal.withInitial(() -> new AlphaBetaSearch(evaluator));
    }

    private static ExecutorService newPool(int threads) {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "ayo-search-" + counter.incrementAndGet());
                t.setDaemon(true);      // never keeps the application alive
                return t;
            }
        };
        return Executors.newFixedThreadPool(threads, factory);
    }

    public int threads() {
        return threads;
    }

    // ------------------------------------------------------------------ entry

    public AlphaBetaSearch.SearchResult search(Board board, int maxDepth) {
        return search(board, maxDepth, 0L);
    }

    /**
     * Iteratively deepens, parallelising each iteration across the root moves
     * and keeping the best move from the last iteration that finished. An
     * iteration cut short by the deadline is discarded entirely, because a
     * partially searched depth can easily be worse than a fully searched
     * shallower one.
     */
    public AlphaBetaSearch.SearchResult search(Board board, int maxDepth, long budgetMillis) {
        long start = System.nanoTime();
        long deadline = budgetMillis > 0 ? start + budgetMillis * 1_000_000L : Long.MAX_VALUE;

        List<Integer> legal = board.legalMoves();
        if (legal.isEmpty()) {
            throw new IllegalStateException("no legal moves: the game is over");
        }

        AtomicLong nodes = new AtomicLong();
        int bestMove = legal.get(0);
        int bestScore = 0;
        int reached = 0;
        boolean complete = true;

        for (int depth = 1; depth <= maxDepth; depth++) {
            MoveScore best = searchOneDepth(board, depth, bestMove, deadline, nodes);
            if (best == null) {                 // abandoned: keep the previous depth
                complete = false;
                break;
            }
            bestMove = best.move();
            bestScore = best.score();
            reached = depth;
            if (Math.abs(bestScore) >= AlphaBetaSearch.WIN_BONUS) break;
        }

        long millis = (System.nanoTime() - start) / 1_000_000L;
        return new AlphaBetaSearch.SearchResult(bestMove, bestScore, reached, nodes.get(), millis, complete);
    }

    private record MoveScore(int move, int score) {}

    /** @return the best move at this depth, or null if the iteration was abandoned */
    private MoveScore searchOneDepth(Board board, int depth, int previousBest,
                                     long deadline, AtomicLong nodes) {

        List<Integer> moves = AlphaBetaSearch.orderedMoves(board, previousBest);
        AtomicInteger sharedAlpha = new AtomicInteger(-AlphaBetaSearch.INFINITY);

        int bestMove;
        int bestScore;

        // The eldest brother: searched on this thread, with a full window, so
        // that the younger ones start against a meaningful floor.
        try {
            bestMove = moves.get(0);
            bestScore = runOne(board, bestMove, depth,
                    -AlphaBetaSearch.INFINITY, deadline, nodes);
            if (shareAlpha) sharedAlpha.set(bestScore);
        } catch (AlphaBetaSearch.Aborted aborted) {
            return null;
        }

        if (moves.size() == 1) return new MoveScore(bestMove, bestScore);

        // The younger brothers, in parallel.
        List<Future<MoveScore>> futures = new ArrayList<>(moves.size() - 1);
        for (int i = 1; i < moves.size(); i++) {
            final int move = moves.get(i);
            Callable<MoveScore> task = () -> {
                int floor = shareAlpha ? sharedAlpha.get() : -AlphaBetaSearch.INFINITY;
                int score = runOne(board, move, depth, floor, deadline, nodes);
                if (shareAlpha) sharedAlpha.accumulateAndGet(score, Math::max);
                return new MoveScore(move, score);
            };
            futures.add(pool.submit(task));
        }

        boolean abandoned = false;
        for (Future<MoveScore> future : futures) {
            try {
                long remaining = deadline == Long.MAX_VALUE
                        ? Long.MAX_VALUE
                        : deadline - System.nanoTime();
                MoveScore result = remaining == Long.MAX_VALUE
                        ? future.get()
                        : future.get(Math.max(remaining, 0), TimeUnit.NANOSECONDS);

                if (result.score() > bestScore
                        || (result.score() == bestScore && result.move() < bestMove)) {
                    bestScore = result.score();
                    bestMove = result.move();
                }
            } catch (TimeoutException e) {
                abandoned = true;
                break;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof AlphaBetaSearch.Aborted) {
                    abandoned = true;
                    break;
                }
                throw new IllegalStateException("search task failed", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();   // never swallow an interrupt
                abandoned = true;
                break;
            }
        }

        if (abandoned) {
            // Stragglers are interrupted; the searches notice and unwind.
            for (Future<MoveScore> future : futures) future.cancel(true);
            return null;
        }
        return new MoveScore(bestMove, bestScore);
    }

    /** Searches one root move on the calling thread and accumulates its node count. */
    private int runOne(Board board, int move, int depth, int alpha, long deadline, AtomicLong nodes) {
        AlphaBetaSearch searcher = workerSearch.get();
        long before = searcher.nodesSearched();
        try {
            return searcher.searchChild(board.play(move), depth - 1,
                    alpha, AlphaBetaSearch.INFINITY, deadline);
        } finally {
            nodes.addAndGet(searcher.nodesSearched() - before);
        }
    }

    // --------------------------------------------------------------- shutdown

    /**
     * Shuts the pool down if this object created it. Pools passed in by the
     * caller are left alone. Worker threads are daemons, so a forgotten
     * shutdown will not hang the application, but calling this is still the
     * correct thing to do.
     */
    @Override
    public void close() {
        if (!ownsPool) return;
        pool.shutdownNow();
        try {
            if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                System.err.println("ayo: search pool did not shut down cleanly");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
