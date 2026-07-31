package ayo.ui;

import ayo.ai.AlphaBetaSearch;
import ayo.ai.ParallelRootSearch;
import ayo.model.Board;

import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Runs the game: keeps the position, takes the human's clicks, and hands the
 * computer's turns to a background worker.
 *
 * <h2>The threading contract</h2>
 * <ul>
 *   <li>{@link #board} and every other field here are <b>confined to the event
 *       dispatch thread</b>. There is no synchronization in this class because
 *       nothing outside the EDT ever reads or writes them.</li>
 *   <li>The search runs inside {@link SwingWorker#doInBackground()}, so the
 *       interface keeps repainting while the computer thinks.</li>
 *   <li>The worker is handed an immutable {@link Board} and returns a single
 *       {@code int}. That is the entire interface between the two threads, and
 *       it is why no data can be corrupted by the hand-off.</li>
 *   <li>{@link SwingWorker#done()} runs back on the EDT, which is where the
 *       move is applied and the view updated.</li>
 * </ul>
 *
 * <h2>Stale results</h2>
 * If a new game starts while the computer is thinking, the in-flight worker is
 * cancelled — which interrupts the search, which unwinds it — but a worker that
 * has already finished may still have a {@code done()} queued on the EDT. The
 * generation counter causes such a result to be discarded rather than applied
 * to a position it was never computed for.
 */
public final class GameController implements BoardView.PitListener {

    /** Callbacks, always delivered on the EDT. */
    public interface GameListener {
        void stateChanged(Board board, boolean thinking);

        void gameOver(Board board, int winner);
    }

    private final BoardView view;
    private final ParallelRootSearch ai;
    private final int humanSide;

    private int maxDepth = 20;
    private long budgetMillis = 800;

    // Animation. A move plays back through a Swing Timer, which fires on the EDT
    // so each frame is a plain repaint with no cross-thread coordination. The
    // whole move is bounded to about MAX_MOVE_MILLIS: short moves show every
    // step, long relays skip through several steps per frame.
    private boolean animate = true;
    private static final int MAX_FRAMES = 36;
    private static final int MAX_MOVE_MILLIS = 2000;
    private static final int MIN_FRAME_MILLIS = 200;
    private static final int MAX_FRAME_MILLIS = 400;

    private Board board = Board.initial();
    private int lastMove = -1;
    private int generation = 0;
    private SwingWorker<Integer, Void> pending;
    private Timer animator;
    private GameListener listener;

    public GameController(BoardView view, ParallelRootSearch ai, int humanSide) {
        this.view = view;
        this.ai = ai;
        this.humanSide = humanSide;
        view.setHumanSide(humanSide);
        view.setPitListener(this);
    }

    public void setListener(GameListener listener) {
        this.listener = listener;
    }

    public void setThinkingTime(long millis) {
        this.budgetMillis = millis;
    }

    public void setMaxDepth(int depth) {
        this.maxDepth = depth;
    }

    /** Turns move animation on or off. Off applies moves instantly. */
    public void setAnimated(boolean animate) {
        this.animate = animate;
    }

    public boolean isAnimating() {
        return animator != null && animator.isRunning();
    }

    public Board board() {
        return board;
    }

    // ------------------------------------------------------------- game flow

    /** Starts, or restarts, from the opening position. Call on the EDT. */
    public void newGame() {
        generation++;                       // anything still in flight is now stale
        if (pending != null) {
            pending.cancel(true);           // interrupts the search, which unwinds
            pending = null;
        }
        stopAnimation();
        board = Board.initial();
        lastMove = -1;
        view.setThinking(false);
        refresh();
        if (board.sideToMove() != humanSide) startComputerTurn();
    }

    @Override
    public void pitClicked(int pit) {
        if (pending != null || isAnimating() || board.isGameOver()) return;
        if (board.sideToMove() != humanSide || !board.legalMoves().contains(pit)) return;
        applyMove(pit);
    }

    /**
     * Plays {@code move}. When animation is on this hands off to
     * {@link #animateMove}, which drives the play-back and calls
     * {@link #settleMove} at the end; when it is off the move settles at once.
     */
    private void applyMove(int move) {
        if (animate) {
            animateMove(move);
        } else {
            board = board.play(move);
            lastMove = move;
            settleMove();
        }
    }

    /**
     * Plays the move back one step at a time on a Swing {@link Timer}. The final
     * board is computed up front so the settled state is authoritative; the
     * steps are only a visualisation of how it was reached. The timer fires on
     * the EDT, so a frame is just a repaint and needs no locking.
     */
    private void animateMove(int move) {
        final List<Board.Step> steps = board.trace(move);
        final Board resolved = board.play(move);
        final int gen = generation;

        // Bound the whole move: pick a stride so at most MAX_FRAMES frames show,
        // then a per-frame delay that fills at most MAX_MOVE_MILLIS.
        int stride = Math.max(1, (int) Math.ceil((double) steps.size() / MAX_FRAMES));
        int frames = (int) Math.ceil((double) steps.size() / stride);
        int delay = Math.max(MIN_FRAME_MILLIS, Math.min(MAX_FRAME_MILLIS, MAX_MOVE_MILLIS / frames));

        view.setStatus(board.sideToMove() == humanSide ? "Sowing your move\u2026" : "Sowing the computer's move\u2026");
        view.setFrame(steps.get(0));

        final int[] index = {0};
        animator = new Timer(delay, null);
        animator.addActionListener(e -> {
            if (gen != generation) {            // a new game superseded this move
                animator.stop();
                return;
            }
            index[0] += stride;
            if (index[0] >= steps.size() - 1) {
                view.setFrame(steps.get(steps.size() - 1));   // always show the last step
                animator.stop();
                animator = null;
                board = resolved;
                lastMove = move;
                settleMove();
            } else {
                view.setFrame(steps.get(index[0]));
            }
        });
        animator.setInitialDelay(delay);
        animator.start();
        notifyState(false);
    }

    /** Applies the consequences of the move now that the board has settled. */
    private void settleMove() {
        refresh();
        if (board.isGameOver()) {
            announceResult();
        } else if (board.sideToMove() != humanSide) {
            startComputerTurn();
        }
    }

    private void stopAnimation() {
        if (animator != null) {
            animator.stop();
            animator = null;
        }
        view.clearAnimation();
    }

    /**
     * Hands the position to a background worker. The snapshot and generation are
     * captured now, on the EDT, so the worker never reads a field that the EDT
     * might change underneath it.
     */
    private void startComputerTurn() {
        final Board snapshot = board;
        final int gen = generation;

        SwingWorker<Integer, Void> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() {
                AlphaBetaSearch.SearchResult result = ai.search(snapshot, maxDepth, budgetMillis);
                return result.bestMove();
            }

            @Override
            protected void done() {
                if (gen != generation) return;      // a new game superseded this
                pending = null;
                view.setThinking(false);
                if (isCancelled()) {
                    refresh();
                    return;
                }
                try {
                    applyMove(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();   // never swallow it
                } catch (ExecutionException e) {
                    view.setStatus("The computer failed to move: " + e.getCause());
                }
            }
        };

        pending = worker;
        view.setThinking(true);
        notifyState(true);
        worker.execute();
    }

    /** Abandons the current search, if any, without ending the game. */
    public void stopThinking() {
        if (pending != null) {
            pending.cancel(true);
            pending = null;
            view.setThinking(false);
            refresh();
        }
    }

    /** Releases the search pool. Call when the window closes. */
    public void shutdown() {
        generation++;
        stopAnimation();
        if (pending != null) pending.cancel(true);
        ai.close();
    }

    // ----------------------------------------------------------------- output

    private void refresh() {
        boolean humanToPlay = !board.isGameOver() && board.sideToMove() == humanSide
                && pending == null && !isAnimating();
        List<Integer> selectable = humanToPlay ? board.legalMoves() : List.of();
        view.setPosition(board, selectable, lastMove);

        if (!board.isGameOver()) {
            if (humanToPlay) {
                int all = 0;
                int from = Board.firstPitOf(humanSide);
                for (int i = from; i < from + Board.PITS_PER_SIDE; i++) if (board.seeds(i) > 0) all++;
                view.setStatus(selectable.size() < all
                        ? "Your turn \u2014 you must feed your opponent, so your choices are limited."
                        : "Your turn \u2014 pick one of the highlighted pits.");
            } else {
                view.setStatus("");
            }
        }
        notifyState(pending != null);
    }

    private void announceResult() {
        int winner = board.winner();
        int mine = board.finalScore(humanSide);
        int theirs = board.finalScore(Board.opponentOf(humanSide));
        view.setStatus(winner == -1
                ? "Drawn, " + mine + " each."
                : (winner == humanSide ? "You win " : "The computer wins ") + Math.max(mine, theirs)
                  + "\u2013" + Math.min(mine, theirs) + ".");
        if (listener != null) listener.gameOver(board, winner);
    }

    private void notifyState(boolean thinking) {
        if (listener != null) listener.stateChanged(board, thinking);
    }
}