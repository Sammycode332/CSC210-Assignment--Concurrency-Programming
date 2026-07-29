package ayo.ai;

import ayo.model.Board;

/**
 * The default Ayo evaluation function: a weighted sum of five positional terms,
 * each written as a difference between the two players so that the whole
 * function is antisymmetric by construction.
 *
 * <p>The design rule is that this function stays <em>cheap</em>. It is called
 * at every leaf of the search tree, hundreds of thousands of times per move, so
 * it measures static features only and never simulates what might happen next.
 * Working out whether a threat can actually be executed is the search's job,
 * and doing it here would multiply the cost at every leaf.
 *
 * <p>The terms:
 * <ol>
 *   <li><b>Material</b> — captured seeds are permanent and decide the game, so
 *       this dominates everything else put together.</li>
 *   <li><b>Presence</b> — seeds still in your row are ammunition, mobility and
 *       insurance against the sweep, but they are also what the opponent raids.
 *       A genuinely contested term; the modest default weight is a hypothesis
 *       to be tested by self-play, not a fact.</li>
 *   <li><b>Threats</b> — an empty pit facing a loaded enemy pit is a loaded
 *       gun. Reachability is deliberately not checked.</li>
 *   <li><b>Mobility</b> — approximated by the count of non-empty pits, which
 *       ignores the feeding restriction but costs nothing.</li>
 *   <li><b>Starvation</b> — an empty row is dangerous in proportion to the
 *       seeds still on the board, because a player who cannot move hands all of
 *       them to the opponent.</li>
 * </ol>
 *
 * <p>Instances are immutable and safe to share across search threads.
 */
public final class HeuristicEvaluator implements Evaluator {

    /**
     * Tunable coefficients. Tune by self-play: hold one vector fixed, vary
     * another, play a few hundred games, keep the winner. Those games are
     * independent of one another and so can run on the same thread pool the
     * parallel search uses.
     */
    public record Weights(int material, int presence, int threat, int mobility, int starvation) {

        public static final Weights DEFAULT = new Weights(100, 6, 3, 2, 10);
    }

    private final Weights weights;

    public HeuristicEvaluator() {
        this(Weights.DEFAULT);
    }

    public HeuristicEvaluator(Weights weights) {
        this.weights = weights;
    }

    public Weights weights() {
        return weights;
    }

    @Override
    public int evaluate(Board board, int player) {
        int opponent = Board.opponentOf(player);
        int score = 0;
        score += weights.material()   * (board.captured(player) - board.captured(opponent));
        score += weights.presence()   * (board.seedsOnSide(player) - board.seedsOnSide(opponent));
        score += weights.threat()     * (threatValue(board, player) - threatValue(board, opponent));
        score += weights.mobility()   * (movablePits(board, player) - movablePits(board, opponent));
        score -= weights.starvation() * (starvationRisk(board, player) - starvationRisk(board, opponent));
        return score;
    }

    /**
     * The seeds {@code player} would win if every one of their empty pits could
     * be landed in this turn: for each empty pit, the facing pit's contents plus
     * the capturing seed. An optimistic upper bound, and intentionally so.
     */
    static int threatValue(Board board, int player) {
        int value = 0;
        int from = Board.firstPitOf(player);
        for (int pit = from; pit < from + Board.PITS_PER_SIDE; pit++) {
            if (board.seeds(pit) != 0) continue;
            int across = board.seeds(Board.opposite(pit));
            if (across > 0) value += across + 1;
        }
        return value;
    }

    /** Non-empty pits in a player's row: a cheap stand-in for move count. */
    static int movablePits(Board board, int player) {
        int count = 0;
        int from = Board.firstPitOf(player);
        for (int pit = from; pit < from + Board.PITS_PER_SIDE; pit++) {
            if (board.seeds(pit) > 0) count++;
        }
        return count;
    }

    /** How much is at stake if this player is unable to move: everything. */
    static int starvationRisk(Board board, int player) {
        return board.seedsOnSide(player) == 0 ? board.seedsInPlay() : 0;
    }
}
