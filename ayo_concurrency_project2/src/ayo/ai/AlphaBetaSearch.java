package ayo.ai;

import ayo.model.Board;

import java.util.ArrayList;
import java.util.List;

/**
 * Depth-limited negamax search with alpha-beta pruning, iterative deepening and
 * a time budget.
 *
 * <p>Negamax rather than separate maximising and minimising cases, which is
 * legitimate here for two reasons specific to this rule set: the evaluator is
 * antisymmetric, and Ayoayo has strict turn alternation with no free moves. In
 * a variant that granted an extra turn the single-case formulation would be
 * wrong.
 *
 * <p><b>Thread confinement.</b> An instance carries mutable search state — the
 * node counter and the deadline — and is therefore <em>not</em> thread safe.
 * Give every worker thread its own instance. The {@link Board} objects flowing
 * through the search are immutable and may be shared freely; this class is the
 * only piece with any mutable state, and confining it to one thread is what
 * keeps the eventual parallel search lock free.
 *
 * <p><b>Cancellation.</b> The search is abandoned when the budget expires or
 * the calling thread is interrupted, and the best move from the last
 * <em>completed</em> iteration is returned. Interruption is not swallowed: the
 * thread's interrupt status is preserved for the caller.
 */
public final class AlphaBetaSearch {

    /** Any win outranks any positional score; the largest material margin is 4,800. */
    public static final int WIN_BONUS = 1_000_000;
    public static final int INFINITY = 2_000_000;

    /** Below this depth the subtree is too small to repay the cost of sorting. */
    private static final int ORDER_MIN_DEPTH = 2;

    /** How often to test the deadline and the interrupt flag. */
    private static final int ABORT_CHECK_MASK = 1023;

    /** The outcome of a search. */
    public record SearchResult(int bestMove, int score, int depth, long nodes, long millis, boolean complete) {

        public long nodesPerSecond() {
            return millis <= 0 ? nodes * 1000 : nodes * 1000 / millis;
        }
    }

    /**
     * Thrown to unwind the recursion when the budget expires or the thread is
     * interrupted; carries no stack trace. Package-private so that
     * {@link ParallelRootSearch} can recognise an abandoned worker.
     */
    static final class Aborted extends RuntimeException {
        Aborted() {
            super(null, null, false, false);
        }
    }

    private final Evaluator evaluator;

    private long nodes;
    private long deadlineNanos;

    public AlphaBetaSearch(Evaluator evaluator) {
        this.evaluator = evaluator;
    }

    public long nodesSearched() {
        return nodes;
    }

    // ------------------------------------------------------------------ entry

    /** Searches to a fixed depth with no time limit. */
    public SearchResult search(Board board, int maxDepth) {
        return search(board, maxDepth, 0L);
    }

    /**
     * Iterative deepening: searches depth 1, then 2, and so on, keeping the best
     * move from the last completed iteration. The shallow passes cost almost
     * nothing next to the deepest one and pay for themselves by supplying the
     * move ordering that makes the deepest one prune well.
     *
     * @param budgetMillis wall-clock budget, or zero or less for no limit
     */
    public SearchResult search(Board board, int maxDepth, long budgetMillis) {
        nodes = 0;
        long start = System.nanoTime();
        deadlineNanos = budgetMillis > 0 ? start + budgetMillis * 1_000_000L : Long.MAX_VALUE;

        List<Integer> moves = board.legalMoves();
        if (moves.isEmpty()) {
            throw new IllegalStateException("no legal moves: the game is over");
        }

        int bestMove = moves.get(0);
        int bestScore = 0;
        int reached = 0;
        boolean complete = true;

        for (int depth = 1; depth <= maxDepth; depth++) {
            try {
                RootScore root = searchRoot(board, depth, bestMove);
                bestMove = root.move;
                bestScore = root.score;
                reached = depth;
                // A forced result will not change with more depth.
                if (Math.abs(bestScore) >= WIN_BONUS) break;
            } catch (Aborted aborted) {
                complete = false;
                break;
            }
        }

        long millis = (System.nanoTime() - start) / 1_000_000L;
        return new SearchResult(bestMove, bestScore, reached, nodes, millis, complete);
    }

    private record RootScore(int move, int score) {}

    /**
     * The root is handled separately because it must remember which move
     * produced the best score, and because ties are broken deterministically by
     * pit index so that repeated runs — including parallel ones — agree.
     */
    private RootScore searchRoot(Board board, int depth, int previousBest) {
        List<Integer> moves = orderedMoves(board, previousBest);

        int alpha = -INFINITY;
        int bestMove = moves.get(0);
        int bestScore = -INFINITY;

        for (int move : moves) {
            int score = -negamax(board.play(move), depth - 1, -INFINITY, -alpha);
            if (score > bestScore || (score == bestScore && move < bestMove)) {
                bestScore = score;
                bestMove = move;
            }
            if (bestScore > alpha) alpha = bestScore;
        }
        return new RootScore(bestMove, bestScore);
    }

    // ----------------------------------------------------------------- search

    /**
     * Returns the value of {@code board} to the side to move.
     *
     * <p>The window inverts on the way down — {@code -negamax(child, -beta,
     * -alpha)} — because a score that is good for the opponent is bad for us by
     * exactly the same amount. When {@code alpha >= beta} the remaining moves
     * cannot affect the result: the opponent already has a reply that holds this
     * line below something we can reach elsewhere, so the branch is refuted.
     */
    private int negamax(Board board, int depth, int alpha, int beta) {
        nodes++;
        if ((nodes & ABORT_CHECK_MASK) == 0) checkAbort();

        List<Integer> moves = board.legalMoves();
        if (moves.isEmpty()) return terminalScore(board, depth);
        if (depth == 0) return evaluator.evaluate(board, board.sideToMove());

        int best = -INFINITY;
        for (int move : ordered(board, moves, depth, -1)) {
            int score = -negamax(board.play(move), depth - 1, -beta, -alpha);
            if (score > best) best = score;
            if (best > alpha) alpha = best;
            if (alpha >= beta) break;          // beta cutoff
        }
        return best;
    }

    /**
     * The value of a finished game to the side to move, with the sweep already
     * applied by {@link Board#finalScore(int)}.
     *
     * <p>The remaining depth is folded in so that a win found sooner scores
     * higher than the same win found later. Without it an engine that is winning
     * will shuffle seeds indefinitely, because a win at depth two and a win at
     * depth eight look identical.
     */
    public static int terminalScore(Board board, int depth) {
        int mover = board.sideToMove();
        int margin = board.finalScore(mover) - board.finalScore(Board.opponentOf(mover));
        if (margin == 0) return 0;
        int magnitude = WIN_BONUS + 100 * depth + Math.abs(margin);
        return margin > 0 ? magnitude : -magnitude;
    }

    // --------------------------------------------------------------- ordering

    static List<Integer> orderedMoves(Board board, int first) {
        return ordered(board, board.legalMoves(), Integer.MAX_VALUE, first);
    }

    /**
     * Orders moves best-first, which is what makes alpha-beta worth having: with
     * good ordering the effective branching factor falls to roughly its own
     * square root.
     *
     * <p>Moves are ranked by the seeds they capture immediately, with an
     * optionally forced first move — at the root, the best move from the
     * previous iteration. Ranking requires playing each move, so shallow nodes
     * skip the sort rather than pay for it; the children generated here are
     * discarded and replayed by the caller, a deliberate trade of a few cheap
     * plays for much better pruning.
     */
    static List<Integer> ordered(Board board, List<Integer> moves, int depth, int first) {
        if (moves.size() <= 1) return moves;
        if (depth < ORDER_MIN_DEPTH && first < 0) return moves;

        int mover = board.sideToMove();
        int before = board.captured(mover);

        List<int[]> ranked = new ArrayList<>(moves.size());
        for (int move : moves) {
            int gain = move == first
                    ? Integer.MAX_VALUE
                    : board.play(move).captured(mover) - before;
            ranked.add(new int[] {move, gain});
        }
        // Descending by gain, then ascending by pit index to stay deterministic.
        ranked.sort((a, b) -> a[1] != b[1] ? Integer.compare(b[1], a[1]) : Integer.compare(a[0], b[0]));

        List<Integer> result = new ArrayList<>(ranked.size());
        for (int[] entry : ranked) result.add(entry[0]);
        return result;
    }

    /**
     * Searches a single child position within a caller-supplied window and
     * returns its score from the <em>parent's</em> point of view. This is the
     * hook {@link ParallelRootSearch} uses to farm root moves out to workers:
     * each worker owns its own instance, so the mutable state below stays
     * confined to one thread.
     *
     * <p>Passing {@code beta = INFINITY} makes the result exact whenever it
     * exceeds {@code alpha}; a result at or below {@code alpha} is only an upper
     * bound, which is all that is needed to discard a refuted move.
     *
     * @param depth remaining depth for the child
     * @throws Aborted if the deadline passes or the thread is interrupted
     */
    int searchChild(Board child, int depth, int alpha, int beta, long deadlineNanos) {
        this.deadlineNanos = deadlineNanos;
        return -negamax(child, depth, -beta, -alpha);
    }

    // ----------------------------------------------------------- cancellation

    private void checkAbort() {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadlineNanos) {
            throw new Aborted();
        }
    }
}
