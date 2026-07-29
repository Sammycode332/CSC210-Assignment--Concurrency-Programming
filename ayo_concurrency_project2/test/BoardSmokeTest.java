import ayo.model.Board;

import java.util.List;
import java.util.Random;

public class BoardSmokeTest {

    static int checks = 0;

    static void check(boolean condition, String what) {
        checks++;
        if (!condition) throw new AssertionError("FAILED: " + what);
    }

    static int totalSeeds(Board b) {
        return b.seedsInPlay() + b.captured(Board.SOUTH) + b.captured(Board.NORTH);
    }

    public static void main(String[] args) {
        Board start = Board.initial();
        check(start.seedsInPlay() == 48, "opening has 48 seeds in play");
        check(start.legalMoves().size() == 6, "opening has six legal moves");
        check(start.sideToMove() == Board.SOUTH, "SOUTH opens");
        check(Board.opposite(2) == 9 && Board.opposite(0) == 11, "opposite pairing");

        System.out.println("Opening position:");
        System.out.println(start);

        Board afterPit0 = start.play(0);
        System.out.println("\nAfter SOUTH plays pit 0:");
        System.out.println(afterPit0);
        System.out.println("SOUTH captured this turn: " + afterPit0.captured(Board.SOUTH));
        check(totalSeeds(afterPit0) == 48, "seeds conserved after one move");
        check(afterPit0.sideToMove() == Board.NORTH, "turn passes to NORTH");

        check(start.seeds(0) == 4, "the original board was not mutated");
        check(start.equals(Board.initial()), "the original board still equals a fresh opening");

        // Feeding rule: NORTH is starved, SOUTH must play a move that reaches them.
        int[] starved = new int[12];
        starved[0] = 8;   // sows through to pits 6, 7, 8 -> feeds NORTH
        starved[4] = 1;   // one seed into empty pit 5, turn ends -> cannot feed
        Board feeding = Board.of(starved, 20, 19, Board.SOUTH);
        List<Integer> forced = feeding.legalMoves();
        check(forced.contains(0), "the feeding move is legal");
        check(!forced.contains(4), "the non-feeding move is suppressed");
        check(forced.size() == 1, "only the feeding move remains");

        // Starvation with no feeding move available ends the game.
        int[] deadlock = new int[12];
        deadlock[4] = 1;  // reaches pit 5 only
        Board dead = Board.of(deadlock, 24, 23, Board.SOUTH);
        check(dead.isGameOver(), "no feeding move means the game is over");
        check(dead.finalScore(Board.SOUTH) == 25, "the mover sweeps their own row");
        check(dead.winner() == Board.SOUTH, "SOUTH wins 25-23");

        // A player with an empty row cannot move; the opponent sweeps.
        int[] empty = new int[12];
        empty[7] = 5;
        Board swept = Board.of(empty, 20, 23, Board.SOUTH);
        check(swept.isGameOver(), "an empty row means no legal moves");
        check(swept.finalScore(Board.NORTH) == 28, "NORTH sweeps the remaining five seeds");

        // Random playouts: the real test.
        Random rng = new Random(20260723L);
        int games = 20000, longest = 0, draws = 0, southWins = 0;
        long totalPlies = 0;
        for (int g = 0; g < games; g++) {
            Board b = Board.initial();
            int plies = 0;
            while (!b.isGameOver() && plies < 5000) {
                check(totalSeeds(b) == 48, "seeds conserved mid-game");
                List<Integer> moves = b.legalMoves();
                for (int m : moves) {
                    check(Board.owner(m) == b.sideToMove(), "legal moves stay on the mover's side");
                    check(b.seeds(m) > 0, "legal moves are never from an empty pit");
                }
                b = b.play(moves.get(rng.nextInt(moves.size())));
                plies++;
            }
            check(totalSeeds(b) == 48, "seeds conserved at the end");
            check(b.finalScore(Board.SOUTH) + b.finalScore(Board.NORTH) == 48, "final scores total 48");
            longest = Math.max(longest, plies);
            totalPlies += plies;
            int w = b.winner();
            if (w == -1) draws++; else if (w == Board.SOUTH) southWins++;
        }

        System.out.printf("%n%,d random games played, %,d assertions passed.%n", games, checks);
        System.out.printf("Average game length %.1f plies, longest %d.%n", (double) totalPlies / games, longest);
        System.out.printf("SOUTH won %.1f%%, draws %.1f%%.%n",
                100.0 * southWins / games, 100.0 * draws / games);
    }
}
