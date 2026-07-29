import ayo.ai.AlphaBetaSearch;
import ayo.ai.Evaluator;
import ayo.ai.HeuristicEvaluator;
import ayo.model.Board;

import java.util.List;
import java.util.Random;

public class SearchSmokeTest {

    static int checks = 0;
    static long minimaxNodes = 0;

    static void check(boolean condition, String what) {
        checks++;
        if (!condition) throw new AssertionError("FAILED: " + what);
    }

    /** Plain negamax with no pruning: the reference implementation. */
    static int minimax(Board b, int depth, Evaluator ev) {
        minimaxNodes++;
        List<Integer> moves = b.legalMoves();
        if (moves.isEmpty()) return AlphaBetaSearch.terminalScore(b, depth);
        if (depth == 0) return ev.evaluate(b, b.sideToMove());
        int best = Integer.MIN_VALUE;
        for (int m : moves) best = Math.max(best, -minimax(b.play(m), depth - 1, ev));
        return best;
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
        Random rng = new Random(1234L);

        // 1. Antisymmetry: the identity negamax depends on.
        for (int i = 0; i < 20000; i++) {
            Board b = randomPosition(rng, rng.nextInt(40));
            check(ev.evaluate(b, Board.SOUTH) == -ev.evaluate(b, Board.NORTH),
                    "evaluator is antisymmetric");
        }
        check(ev.evaluate(Board.initial(), Board.SOUTH) == 0, "the opening is dead level");
        System.out.println("Antisymmetry verified on 20,000 positions.");

        // 2. Pruning correctness: alpha-beta must agree with unpruned minimax.
        System.out.println("\nChecking alpha-beta against plain minimax:");
        long abTotal = 0, mmTotal = 0;
        int compared = 0;
        for (int i = 0; i < 60; i++) {
            Board b = randomPosition(rng, rng.nextInt(25));
            if (b.isGameOver()) continue;

            AlphaBetaSearch search = new AlphaBetaSearch(ev);
            AlphaBetaSearch.SearchResult r = search.search(b, 5);

            minimaxNodes = 0;
            int reference = minimax(b, r.depth(), ev);
            check(r.score() == reference,
                    "alpha-beta score " + r.score() + " equals minimax score " + reference);
            check(b.legalMoves().contains(r.bestMove()), "the chosen move is legal");

            abTotal += r.nodes();
            mmTotal += minimaxNodes;
            compared++;
        }
        System.out.printf("  %d positions agreed exactly at depth 5.%n", compared);
        System.out.printf("  minimax visited %,d nodes; alpha-beta %,d - a %.1fx reduction.%n",
                mmTotal, abTotal, (double) mmTotal / abTotal);

        // 3. Determinism: parallel runs later must be reproducible.
        Board fixed = randomPosition(new Random(99L), 12);
        int first = new AlphaBetaSearch(ev).search(fixed, 7).bestMove();
        for (int i = 0; i < 5; i++) {
            check(new AlphaBetaSearch(ev).search(fixed, 7).bestMove() == first,
                    "repeated searches pick the same move");
        }
        System.out.println("\nDeterminism verified over repeated searches.");

        // 4. The time budget is honoured and the partial result is usable.
        AlphaBetaSearch timed = new AlphaBetaSearch(ev);
        long t0 = System.currentTimeMillis();
        AlphaBetaSearch.SearchResult budgeted = timed.search(Board.initial(), 60, 200);
        long elapsed = System.currentTimeMillis() - t0;
        check(elapsed < 1500, "the search respected its 200ms budget (took " + elapsed + "ms)");
        check(Board.initial().legalMoves().contains(budgeted.bestMove()),
                "an aborted search still returns a legal move");
        System.out.printf("%nTime budget: asked for 200ms, took %dms, reached depth %d (%,d nodes, %,d nodes/sec).%n",
                elapsed, budgeted.depth(), budgeted.nodes(), budgeted.nodesPerSecond());

        // 5. Cancellation by interrupt, with the interrupt status preserved.
        final boolean[] flagSeen = {false};
        Thread worker = new Thread(() -> {
            new AlphaBetaSearch(ev).search(Board.initial(), 60);
            flagSeen[0] = Thread.currentThread().isInterrupted();
        });
        worker.start();
        Thread.sleep(150);
        worker.interrupt();
        worker.join(4000);
        check(!worker.isAlive(), "an interrupted search stops promptly");
        check(flagSeen[0], "the interrupt status is preserved for the caller");
        System.out.println("Cancellation by interrupt verified.");

        // 6. Strength: the search should crush a random player.
        System.out.println("\nPlaying 200 games against a random player at depth 6:");
        int aiWins = 0, draws = 0;
        Random gameRng = new Random(2026L);
        for (int g = 0; g < 200; g++) {
            int aiSide = g % 2;                     // alternate colours
            Board b = Board.initial();
            AlphaBetaSearch search = new AlphaBetaSearch(ev);
            while (!b.isGameOver()) {
                List<Integer> moves = b.legalMoves();
                int move = b.sideToMove() == aiSide
                        ? search.search(b, 6).bestMove()
                        : moves.get(gameRng.nextInt(moves.size()));
                b = b.play(move);
            }
            int w = b.winner();
            if (w == aiSide) aiWins++;
            else if (w == -1) draws++;
        }
        System.out.printf("  AI won %d, drew %d, lost %d.%n", aiWins, draws, 200 - aiWins - draws);
        check(aiWins >= 180, "the search beats random play convincingly");

        System.out.printf("%n%,d assertions passed.%n", checks);
    }
}
