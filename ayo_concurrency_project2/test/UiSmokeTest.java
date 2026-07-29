import ayo.ai.HeuristicEvaluator;
import ayo.ai.ParallelRootSearch;
import ayo.model.Board;
import ayo.ui.BoardView;
import ayo.ui.GameController;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class UiSmokeTest {

    static int checks = 0;

    static void check(boolean condition, String what) {
        checks++;
        if (!condition) throw new AssertionError("FAILED: " + what);
    }

    static BufferedImage render(BoardView view, int w, int h) throws Exception {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            view.setSize(w, h);
            Graphics2D g = image.createGraphics();
            view.paint(g);
            g.dispose();
        });
        return image;
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        File outDir = new File("/mnt/user-data/outputs");
        outDir.mkdirs();

        // 1. The view paints without a display.
        BoardView view = new BoardView();
        SwingUtilities.invokeAndWait(() ->
                view.setPosition(Board.initial(), Board.initial().legalMoves(), -1));
        SwingUtilities.invokeAndWait(() -> view.setStatus("Your turn \u2014 pick one of the highlighted pits."));
        BufferedImage opening = render(view, 760, 480);
        ImageIO.write(opening, "png", new File(outDir, "ayo-ui-opening.png"));
        check(opening.getWidth() == 760, "the opening rendered");

        // 2. A mid-game position, with the computer thinking.
        Random rng = new Random(31L);
        Board mid = Board.initial();
        for (int i = 0; i < 9 && !mid.isGameOver(); i++) {
            List<Integer> moves = mid.legalMoves();
            mid = mid.play(moves.get(rng.nextInt(moves.size())));
        }
        final Board shown = mid;
        SwingUtilities.invokeAndWait(() -> {
            view.setPosition(shown, List.of(), 4);
            view.setThinking(true);
        });
        BufferedImage midImage = render(view, 760, 480);
        ImageIO.write(midImage, "png", new File(outDir, "ayo-ui-thinking.png"));
        check(midImage.getWidth() == 760, "the mid-game position rendered");
        System.out.println("Rendered two board images headlessly.");

        // 3. Hit testing maps points back to pits.
        SwingUtilities.invokeAndWait(() -> {
            view.setSize(760, 480);
            for (int pit = 0; pit < Board.TOTAL_PITS; pit++) {
                check(view.pitAt(centre(view, pit)) == pit, "pit " + pit + " hit-tests to itself");
            }
        });
        System.out.println("Hit testing verified for all twelve pits.");

        // 4. The event dispatch thread stays responsive while the search runs.
        BoardView live = new BoardView();
        ParallelRootSearch ai = new ParallelRootSearch(
                new HeuristicEvaluator(), Runtime.getRuntime().availableProcessors(), true);
        GameController controller = new GameController(live, ai, Board.SOUTH);
        controller.setThinkingTime(1500);

        CountDownLatch computerMoved = new CountDownLatch(1);
        AtomicBoolean sawThinking = new AtomicBoolean(false);
        controller.setListener(new GameController.GameListener() {
            @Override
            public void stateChanged(Board board, boolean thinking) {
                if (thinking) sawThinking.set(true);
                else if (sawThinking.get() && board.sideToMove() == Board.SOUTH) computerMoved.countDown();
            }

            @Override
            public void gameOver(Board board, int winner) {
                computerMoved.countDown();
            }
        });

        final long[] last = {0};
        final long[] maxGap = {0};
        final int[] ticks = {0};
        Timer probe = new Timer(10, e -> {
            long now = System.nanoTime();
            if (last[0] != 0) maxGap[0] = Math.max(maxGap[0], (now - last[0]) / 1_000_000L);
            last[0] = now;
            ticks[0]++;
        });

        SwingUtilities.invokeAndWait(() -> {
            controller.newGame();
            probe.start();
            controller.pitClicked(0);       // the human moves; the computer starts thinking
        });

        long t0 = System.currentTimeMillis();
        boolean finished = computerMoved.await(20, TimeUnit.SECONDS);
        long searchMs = System.currentTimeMillis() - t0;
        SwingUtilities.invokeAndWait(probe::stop);

        check(finished, "the computer replied");
        check(sawThinking.get(), "the thinking state was published to the interface");
        check(ticks[0] > 10, "the EDT kept processing events during the search (ticks: " + ticks[0] + ")");
        check(maxGap[0] < searchMs / 2, "the EDT was never blocked for the length of the search");
        System.out.printf("%nSearch took %dms. The EDT ticked %d times, worst pause %dms.%n",
                searchMs, ticks[0], maxGap[0]);

        // 5. A whole game through the controller, human moves chosen at random.
        CountDownLatch over = new CountDownLatch(1);
        controller.setListener(new GameController.GameListener() {
            @Override
            public void stateChanged(Board board, boolean thinking) {
                if (thinking || board.isGameOver()) return;
                if (board.sideToMove() == Board.SOUTH) {
                    List<Integer> moves = board.legalMoves();
                    SwingUtilities.invokeLater(() ->
                            controller.pitClicked(moves.get(rng.nextInt(moves.size()))));
                }
            }

            @Override
            public void gameOver(Board board, int winner) {
                over.countDown();
            }
        });
        controller.setThinkingTime(60);
        SwingUtilities.invokeAndWait(controller::newGame);
        SwingUtilities.invokeLater(() -> controller.pitClicked(controller.board().legalMoves().get(0)));

        check(over.await(90, TimeUnit.SECONDS), "a full game completed through the controller");
        SwingUtilities.invokeAndWait(() -> {
            Board finalBoard = controller.board();
            check(finalBoard.isGameOver(), "the game really ended");
            check(finalBoard.finalScore(0) + finalBoard.finalScore(1) == 48, "seeds conserved through the UI");
            System.out.printf("Full game finished %d\u2013%d.%n",
                    finalBoard.finalScore(Board.SOUTH), finalBoard.finalScore(Board.NORTH));
        });

        // 6. Shutdown stops the pool.
        SwingUtilities.invokeAndWait(controller::shutdown);
        Thread.sleep(300);
        long alive = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith("ayo-search-") && t.isAlive())
                .count();
        check(alive == 0, "shutdown released the search threads (alive: " + alive + ")");
        System.out.println("Shutdown verified.");

        System.out.printf("%n%,d assertions passed.%n", checks);
        System.exit(0);
    }

    /** Recomputes a pit centre the same way the view does, for hit testing. */
    static java.awt.Point centre(BoardView view, int pit) {
        int margin = 24, header = 64, footer = 64;
        int boardHeight = view.getHeight() - header - footer;
        int cellWidth = (view.getWidth() - 2 * margin) / Board.PITS_PER_SIDE;
        int column = Board.owner(pit) == Board.SOUTH
                ? pit
                : Board.PITS_PER_SIDE - 1 - (pit - Board.PITS_PER_SIDE);
        int x = margin + cellWidth * column + cellWidth / 2;
        int y = header + (Board.owner(pit) == Board.SOUTH ? 3 * boardHeight / 4 : boardHeight / 4);
        return new java.awt.Point(x, y);
    }
}
