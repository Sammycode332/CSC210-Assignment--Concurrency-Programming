import ayo.ai.AlphaBetaSearch;
import ayo.ai.Evaluator;
import ayo.ai.HeuristicEvaluator;
import ayo.ai.ParallelRootSearch;
import ayo.model.Board;

import java.util.List;
import java.util.Random;

public class ParallelSmokeTest {

    static int checks = 0;

    static void check(boolean condition, String what) {
        checks++;
        if (!condition) throw new AssertionError("FAILED: " + what);
    }

    static Board randomPosition(Random rng, int plies) {
        Board b = Board.initial();
        for (int i = 0; i < plies && !b.isGameOver(); i++) {
            List<Integer> moves = b.legalMoves();
            b = b.play(moves.get(rng.nextInt(moves.size())));
        }
        return b;
    }

    public static void main(String[] args) throws Exception {
        Evaluator ev = new HeuristicEvaluator();
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("Cores available: " + cores);

        // 1. Exact mode must agree with the sequential search on the value.
        System.out.println("\nAgreement with the sequential search (exact mode, depth 7):");
        Random rng = new Random(4321L);
        int compared = 0, sameMove = 0;
        try (ParallelRootSearch exact = new ParallelRootSearch(ev, 4, false)) {
            for (int i = 0; i < 40; i++) {
                Board b = randomPosition(rng, rng.nextInt(25));
                if (b.isGameOver()) continue;

                AlphaBetaSearch.SearchResult seq = new AlphaBetaSearch(ev).search(b, 7);
                AlphaBetaSearch.SearchResult par = exact.search(b, 7);

                check(par.score() == seq.score(),
                        "parallel score " + par.score() + " equals sequential " + seq.score());
                check(b.legalMoves().contains(par.bestMove()), "the chosen move is legal");
                check(par.depth() == seq.depth(), "both reached the same depth");
                if (par.bestMove() == seq.bestMove()) sameMove++;
                compared++;
            }
        }
        System.out.printf("  %d positions: scores agreed everywhere, moves agreed %d times.%n",
                compared, sameMove);

        // 2. Exact mode is reproducible: no task depends on any other.
        Board fixed = randomPosition(new Random(77L), 14);
        try (ParallelRootSearch exact = new ParallelRootSearch(ev, 4, false)) {
            AlphaBetaSearch.SearchResult first = exact.search(fixed, 8);
            for (int i = 0; i < 8; i++) {
                AlphaBetaSearch.SearchResult again = exact.search(fixed, 8);
                check(again.bestMove() == first.bestMove(), "exact mode picks the same move every run");
                check(again.score() == first.score(), "exact mode returns the same score every run");
                check(again.nodes() == first.nodes(), "exact mode visits the same nodes every run");
            }
        }
        System.out.println("\nExact mode is bit-for-bit reproducible across runs.");

        // 3. Shared-alpha mode agrees on the value and prunes harder.
        System.out.println("\nShared alpha versus full windows (depth 9):");
        long sharedNodes = 0, exactNodes = 0;
        try (ParallelRootSearch shared = new ParallelRootSearch(ev, 4, true);
             ParallelRootSearch exact = new ParallelRootSearch(ev, 4, false)) {
            Random r2 = new Random(555L);
            for (int i = 0; i < 15; i++) {
                Board b = randomPosition(r2, r2.nextInt(20));
                if (b.isGameOver()) continue;
                AlphaBetaSearch.SearchResult s = shared.search(b, 9);
                AlphaBetaSearch.SearchResult x = exact.search(b, 9);
                check(s.score() == x.score(), "both parallel modes agree on the value");
                sharedNodes += s.nodes();
                exactNodes += x.nodes();
            }
        }
        System.out.printf("  full windows: %,d nodes; shared alpha: %,d nodes (%.1f%% saved).%n",
                exactNodes, sharedNodes, 100.0 * (exactNodes - sharedNodes) / exactNodes);

        // 4. The time budget is honoured.
        try (ParallelRootSearch p = new ParallelRootSearch(ev, cores, true)) {
            long t0 = System.currentTimeMillis();
            AlphaBetaSearch.SearchResult r = p.search(Board.initial(), 60, 300);
            long elapsed = System.currentTimeMillis() - t0;
            check(elapsed < 2000, "the 300ms budget was respected (took " + elapsed + "ms)");
            check(Board.initial().legalMoves().contains(r.bestMove()),
                    "an abandoned search still returns a legal move");
            System.out.printf("%nTime budget: asked 300ms, took %dms, reached depth %d.%n", elapsed, r.depth());
        }

        // 5. A full game, to prove nothing deadlocks over many searches.
        try (ParallelRootSearch p = new ParallelRootSearch(ev, cores, true)) {
            Board b = Board.initial();
            int plies = 0;
            while (!b.isGameOver() && plies < 300) {
                b = b.play(p.search(b, 8).bestMove());
                plies++;
            }
            check(b.isGameOver(), "the self-play game finished");
            check(b.finalScore(0) + b.finalScore(1) == 48, "seeds conserved through self-play");
            System.out.printf("Self-play game completed in %d plies, %d-%d.%n",
                    plies, b.finalScore(0), b.finalScore(1));
        }

        // 6. Shutdown really releases the threads.
        ParallelRootSearch closing = new ParallelRootSearch(ev, 4, true);
        closing.search(Board.initial(), 6);
        closing.close();
        Thread.sleep(300);
        long alive = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith("ayo-search-") && t.isAlive())
                .count();
        check(alive == 0, "close() shut every worker thread down (still alive: " + alive + ")");
        System.out.println("Pool shutdown verified.");

        // 7. Speedup, if the hardware allows it. Warm the JIT up first, or the
        //    run that happens to go first pays the compilation cost and the
        //    comparison is meaningless.
        System.out.println("\nBenchmark (depth 14 from the opening):");
        try (ParallelRootSearch warm = new ParallelRootSearch(ev, cores, true)) {
            for (int i = 0; i < 3; i++) {
                new AlphaBetaSearch(ev).search(Board.initial(), 12);
                warm.search(Board.initial(), 12);
            }
        }

        long seqMs = Long.MAX_VALUE;
        AlphaBetaSearch.SearchResult seq = null;
        for (int i = 0; i < 3; i++) {
            long t0 = System.nanoTime();
            seq = new AlphaBetaSearch(ev).search(Board.initial(), 14);
            seqMs = Math.min(seqMs, (System.nanoTime() - t0) / 1_000_000L);
        }
        System.out.printf("  sequential: %,dms, %,d nodes%n", seqMs, seq.nodes());

        try (ParallelRootSearch p = new ParallelRootSearch(ev, cores, true)) {
            long parMs = Long.MAX_VALUE;
            AlphaBetaSearch.SearchResult par = null;
            for (int i = 0; i < 3; i++) {
                long t0 = System.nanoTime();
                par = p.search(Board.initial(), 14);
                parMs = Math.min(parMs, (System.nanoTime() - t0) / 1_000_000L);
            }
            System.out.printf("  parallel (%d threads): %,dms, %,d nodes%n", cores, parMs, par.nodes());
            System.out.printf("  speedup %.2fx, work inflation %.2fx%n",
                    (double) seqMs / Math.max(parMs, 1), (double) par.nodes() / seq.nodes());
            check(par.score() == seq.score(), "the benchmark searches agree on the value");
        }

        System.out.printf("%n%,d assertions passed.%n", checks);
    }
}
