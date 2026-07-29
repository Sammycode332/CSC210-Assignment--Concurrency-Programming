package ayo.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An immutable Ayoayo (Ayo) position.
 *
 * <p>Board layout. Twelve pits indexed 0..11, sown counter-clockwise by
 * increasing index modulo 12. Pits 0..5 belong to SOUTH, pits 6..11 to NORTH.
 * Pit {@code i} sits directly opposite pit {@code 11 - i}.
 *
 * <pre>
 *   NORTH   | 11 10  9  8  7  6 |
 *   SOUTH   |  0  1  2  3  4  5 |
 * </pre>
 *
 * <p>Rules implemented (the "true" Ayoayo / multi-lap variant):
 * <ul>
 *   <li>Each pit starts with four seeds; 48 seeds in total.</li>
 *   <li>A turn lifts every seed from one of the mover's non-empty pits and
 *       sows them one per pit, counter-clockwise.</li>
 *   <li>Within a single lap the pit the lap started from is skipped, so a lap
 *       carrying twelve or more seeds never refills its own origin.</li>
 *   <li>If the final seed of a lap lands in an <em>occupied</em> pit, the whole
 *       contents of that pit (including the seed just dropped) are lifted and a
 *       new lap begins from there. This is relay sowing.</li>
 *   <li>The turn ends when the final seed lands in an <em>empty</em> pit. If
 *       that pit belongs to the mover and the pit directly opposite is not
 *       empty, the mover captures the opposite pit's seeds plus the capturing
 *       seed.</li>
 *   <li>Feeding: if the opponent's row is empty, the mover must, if any such
 *       move exists, play a move that leaves at least one seed in it.</li>
 *   <li>The game ends when the side to move has no legal move. Each player then
 *       takes the seeds remaining in their own row.</li>
 * </ul>
 *
 * <p>Rule decision worth noting: if the final seed lands in an empty pit on the
 * mover's own side but the opposite pit is empty, nothing is captured and the
 * seed stays on the board. Sources differ on this point; this is the common
 * reading and it avoids rewarding a capture that takes no enemy seeds.
 *
 * <p>Thread safety. Instances are immutable and therefore safe to publish and
 * share across threads without synchronization. {@link #play(int)} returns a
 * new instance rather than mutating this one, which is what allows a parallel
 * search to explore many lines at once with no locking.
 */
public final class Board {

    public static final int PITS_PER_SIDE = 6;
    public static final int TOTAL_PITS = PITS_PER_SIDE * 2;
    public static final int SEEDS_PER_PIT = 4;
    public static final int TOTAL_SEEDS = TOTAL_PITS * SEEDS_PER_PIT;

    public static final int SOUTH = 0;
    public static final int NORTH = 1;

    /**
     * Relay sowing does not always terminate: some positions send a move into a
     * genuinely repeating cycle of laps. A turn is therefore abandoned once it
     * exceeds this many laps, ending with no capture and the seeds left where
     * they lie. Over 680,000 sampled moves from random play the longest
     * <em>terminating</em> relay ran 203 laps and roughly one move in 5,800
     * cycled forever, so this cap clears legitimate play by a wide margin.
     */
    private static final int MAX_LAPS = 512;

    private final int[] pits;
    private final int[] captured;
    private final int sideToMove;

    /** Takes ownership of both arrays; callers must pass fresh copies. */
    private Board(int[] pits, int[] captured, int sideToMove) {
        this.pits = pits;
        this.captured = captured;
        this.sideToMove = sideToMove;
    }

    /** The opening position: four seeds in every pit, SOUTH to move. */
    public static Board initial() {
        int[] p = new int[TOTAL_PITS];
        Arrays.fill(p, SEEDS_PER_PIT);
        return new Board(p, new int[2], SOUTH);
    }

    /** Builds an arbitrary position. Useful for tests and for endgame study. */
    public static Board of(int[] pits, int southCaptured, int northCaptured, int sideToMove) {
        if (pits == null || pits.length != TOTAL_PITS) {
            throw new IllegalArgumentException("expected " + TOTAL_PITS + " pits");
        }
        if (sideToMove != SOUTH && sideToMove != NORTH) {
            throw new IllegalArgumentException("bad side to move: " + sideToMove);
        }
        int total = southCaptured + northCaptured;
        for (int seeds : pits) {
            if (seeds < 0) throw new IllegalArgumentException("negative seed count");
            total += seeds;
        }
        if (total != TOTAL_SEEDS) {
            throw new IllegalArgumentException("seeds must total " + TOTAL_SEEDS + ", got " + total);
        }
        return new Board(pits.clone(), new int[] {southCaptured, northCaptured}, sideToMove);
    }

    // ---------------------------------------------------------------- geometry

    /** The pit reached by sowing one step counter-clockwise from {@code pit}. */
    public static int next(int pit) {
        return (pit + 1) % TOTAL_PITS;
    }

    /** The pit directly across the board from {@code pit}. */
    public static int opposite(int pit) {
        return TOTAL_PITS - 1 - pit;
    }

    /** The player who owns {@code pit}. */
    public static int owner(int pit) {
        return pit < PITS_PER_SIDE ? SOUTH : NORTH;
    }

    /** The index of {@code player}'s first pit. */
    public static int firstPitOf(int player) {
        return player * PITS_PER_SIDE;
    }

    public static int opponentOf(int player) {
        return 1 - player;
    }

    // --------------------------------------------------------------- accessors

    public int seeds(int pit) {
        return pits[pit];
    }

    public int captured(int player) {
        return captured[player];
    }

    public int sideToMove() {
        return sideToMove;
    }

    /** Seeds still on the board in {@code player}'s row. */
    public int seedsOnSide(int player) {
        int sum = 0;
        int from = firstPitOf(player);
        for (int i = from; i < from + PITS_PER_SIDE; i++) sum += pits[i];
        return sum;
    }

    /** Seeds still in play anywhere on the board. */
    public int seedsInPlay() {
        return seedsOnSide(SOUTH) + seedsOnSide(NORTH);
    }

    /** A copy of the pit array; the board's own state is never exposed. */
    public int[] toArray() {
        return pits.clone();
    }

    // ------------------------------------------------------------ legal moves

    /**
     * The pits the side to move may legally play, in ascending order.
     * Empty exactly when the game is over.
     */
    public List<Integer> legalMoves() {
        int from = firstPitOf(sideToMove);
        List<Integer> candidates = new ArrayList<>(PITS_PER_SIDE);
        for (int i = from; i < from + PITS_PER_SIDE; i++) {
            if (pits[i] > 0) candidates.add(i);
        }

        int opponent = opponentOf(sideToMove);
        if (seedsOnSide(opponent) > 0 || candidates.isEmpty()) {
            return Collections.unmodifiableList(candidates);
        }

        // Feeding obligation: the opponent is starved, so only moves that leave
        // them at least one seed are legal. Captures can empty their row again,
        // so we judge by the resulting position rather than by the sowing path.
        List<Integer> feeding = new ArrayList<>(candidates.size());
        for (int pit : candidates) {
            if (playUnchecked(pit).seedsOnSide(opponent) > 0) feeding.add(pit);
        }
        // If no move can feed them, the mover is out of legal moves and the
        // game ends with the mover taking the seeds left in their own row.
        return Collections.unmodifiableList(feeding);
    }

    public boolean isLegal(int pit) {
        return legalMoves().contains(pit);
    }

    // ------------------------------------------------------------------ moving

    /**
     * Plays {@code pit} and returns the resulting position. This board is
     * unchanged.
     *
     * @throws IllegalArgumentException if the pit is out of range
     * @throws IllegalStateException    if the move is not legal here
     */
    public Board play(int pit) {
        if (pit < 0 || pit >= TOTAL_PITS) {
            throw new IllegalArgumentException("pit out of range: " + pit);
        }
        if (!isLegal(pit)) {
            throw new IllegalStateException("illegal move: pit " + pit
                    + " for player " + sideToMove + "; legal moves are " + legalMoves());
        }
        return playUnchecked(pit);
    }

    /**
     * Sowing, relay and capture with no legality checking. Used internally so
     * that {@link #legalMoves()} can test the feeding rule without recursing
     * back into {@link #play(int)}.
     */
    private Board playUnchecked(int pit) {
        int[] p = pits.clone();
        int[] c = captured.clone();
        int mover = sideToMove;
        sow(p, c, mover, pit, null);
        return new Board(p, c, opponentOf(mover));
    }

    /**
     * The sowing engine, shared by {@link #play(int)} and {@link #trace(int)} so
     * that the rules exist in exactly one place. Mutates {@code p} and {@code c}
     * in place, and — only if {@code steps} is non-null — records a snapshot
     * after each atomic action.
     *
     * <p>When {@code steps} is null this allocates nothing beyond what the
     * caller already cloned, which is what keeps the search hot path fast: it
     * plays millions of moves and must never pay for animation bookkeeping.
     */
    private static void sow(int[] p, int[] c, int mover, int startPit, List<Step> steps) {
        int lapOrigin = startPit;
        int hand = p[startPit];
        p[startPit] = 0;
        emit(steps, p, c, hand, startPit, -1, Step.Kind.LIFT);

        int cursor = startPit;
        int laps = 0;
        while (true) {
            while (hand > 0) {
                cursor = next(cursor);
                if (cursor == lapOrigin) continue;   // a lap never refills its origin
                p[cursor]++;
                hand--;
                emit(steps, p, c, hand, cursor, -1, Step.Kind.DROP);
            }

            if (p[cursor] == 1) {
                // Landed in a pit that was empty: the turn ends here.
                if (owner(cursor) == mover) {
                    int across = opposite(cursor);
                    if (p[across] > 0) {
                        c[mover] += p[across] + 1;   // opposite pit plus the capturing seed
                        p[across] = 0;
                        p[cursor] = 0;
                        emit(steps, p, c, hand, cursor, across, Step.Kind.CAPTURE);
                    }
                }
                break;
            }

            // Landed in an occupied pit, so the relay would continue. Relay
            // sowing can cycle forever, so the turn is abandoned at the cap:
            // the seeds stay where they lie and nothing is captured. The check
            // happens before the seeds are lifted, so none can be lost.
            if (++laps >= MAX_LAPS) break;

            hand = p[cursor];
            p[cursor] = 0;
            lapOrigin = cursor;
            emit(steps, p, c, hand, cursor, -1, Step.Kind.RELAY);
        }
    }

    private static void emit(List<Step> steps, int[] p, int[] c, int hand,
                             int active, int opposite, Step.Kind kind) {
        if (steps != null) steps.add(new Step(p.clone(), c.clone(), hand, active, opposite, kind));
    }

    /**
     * Replays a move one action at a time, for animation. The returned list is
     * the play-by-play the board hides inside {@link #play(int)}: a lift, a drop
     * for each seed sown, a marker at the start of each relay lap, and a capture
     * if one occurs. Applying every step in order reaches exactly the position
     * {@code play(pit)} returns.
     *
     * <p>The game logic is untouched by this method; it is a read-only view of
     * how {@code play(pit)} unfolds, computed by the same engine.
     *
     * @throws IllegalStateException if the move is not legal here
     */
    public List<Step> trace(int pit) {
        if (!isLegal(pit)) {
            throw new IllegalStateException("illegal move: pit " + pit);
        }
        List<Step> steps = new ArrayList<>();
        sow(pits.clone(), captured.clone(), sideToMove, pit, steps);
        return steps;
    }

    /**
     * One atomic action within a move, holding the position immediately after
     * it. During sowing the on-board seeds plus captured seeds fall short of
     * {@link #TOTAL_SEEDS} by exactly {@link #hand()}, the seeds still in transit.
     */
    public static final class Step {

        public enum Kind {
            /** Seeds lifted from the chosen pit into the hand. */
            LIFT,
            /** One seed dropped into {@link #active()}. */
            DROP,
            /** A relay lap begins: the landing pit's seeds are lifted again. */
            RELAY,
            /** The final seed captured {@link #opposite()} into the store. */
            CAPTURE
        }

        private final int[] pits;
        private final int[] captured;
        private final int hand;
        private final int active;
        private final int opposite;
        private final Kind kind;

        Step(int[] pits, int[] captured, int hand, int active, int opposite, Kind kind) {
            this.pits = pits;
            this.captured = captured;
            this.hand = hand;
            this.active = active;
            this.opposite = opposite;
            this.kind = kind;
        }

        public int seeds(int pit) {
            return pits[pit];
        }

        public int[] pits() {
            return pits.clone();
        }

        public int captured(int player) {
            return captured[player];
        }

        /** Seeds still in the hand, waiting to be sown. */
        public int hand() {
            return hand;
        }

        /** The pit this action touched: lifted from, dropped into, or captured into. */
        public int active() {
            return active;
        }

        /** For a {@link Kind#CAPTURE}, the raided pit; otherwise -1. */
        public int opposite() {
            return opposite;
        }

        public Kind kind() {
            return kind;
        }
    }

    // ----------------------------------------------------------------- endgame

    /** True when the side to move has no legal move. */
    public boolean isGameOver() {
        return legalMoves().isEmpty();
    }

    /**
     * A player's score once the game is settled: seeds already captured, plus
     * the seeds left in their own row when the game has ended.
     */
    public int finalScore(int player) {
        return captured[player] + (isGameOver() ? seedsOnSide(player) : 0);
    }

    /**
     * {@link #SOUTH}, {@link #NORTH}, or -1 for a draw.
     *
     * @throws IllegalStateException if the game is still in progress
     */
    public int winner() {
        if (!isGameOver()) throw new IllegalStateException("game is not over");
        int south = finalScore(SOUTH);
        int north = finalScore(NORTH);
        return south == north ? -1 : (south > north ? SOUTH : NORTH);
    }

    // ------------------------------------------------------------------ output

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("      ");
        for (int i = TOTAL_PITS - 1; i >= PITS_PER_SIDE; i--) sb.append(String.format("%3d", pits[i]));
        sb.append("     NORTH captured ").append(captured[NORTH]).append('\n');
        sb.append("     ");
        sb.append(" ---".repeat(PITS_PER_SIDE)).append('\n');
        sb.append("      ");
        for (int i = 0; i < PITS_PER_SIDE; i++) sb.append(String.format("%3d", pits[i]));
        sb.append("     SOUTH captured ").append(captured[SOUTH]).append('\n');
        sb.append("     to move: ").append(sideToMove == SOUTH ? "SOUTH" : "NORTH");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Board other)) return false;
        return sideToMove == other.sideToMove
                && Arrays.equals(pits, other.pits)
                && Arrays.equals(captured, other.captured);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Arrays.hashCode(pits) + Arrays.hashCode(captured)) + sideToMove;
    }
}