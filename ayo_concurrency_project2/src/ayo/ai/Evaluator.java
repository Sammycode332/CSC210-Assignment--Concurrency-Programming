package ayo.ai;

import ayo.model.Board;

/**
 * Scores a position from one player's point of view. Positive is good for
 * {@code player}, negative is good for the opponent.
 *
 * <p>Implementations must be <strong>antisymmetric</strong>:
 * {@code evaluate(b, SOUTH) == -evaluate(b, NORTH)} for every position
 * {@code b}. Negamax depends on this identity, and a violation shows up as a
 * search that plays well as one colour and badly as the other.
 *
 * <p>Implementations must also be <strong>stateless and thread safe</strong>.
 * A parallel search calls the same evaluator from many threads at once.
 */
@FunctionalInterface
public interface Evaluator {

    int evaluate(Board board, int player);
}
