package ayo.ui;

import ayo.model.Board;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Paints a position and reports clicks on pits. Purely a view: it holds no game
 * rules, decides nothing, and never starts a search.
 *
 * <p><b>Threading.</b> Like every Swing component, this one is confined to the
 * event dispatch thread. Every mutator below must be called from the EDT, and
 * every one of them is called only by {@link GameController}, which itself runs
 * on the EDT. No background thread touches this class.
 */
public class BoardView extends JPanel {

    /** Notified on the EDT when the user clicks a pit. */
    public interface PitListener {
        void pitClicked(int pit);
    }

    private static final Color BACKDROP    = new Color(0xF5EFE6);
    private static final Color BOARD_WOOD  = new Color(0x8B5E3C);
    private static final Color BOARD_EDGE  = new Color(0x6B4426);
    private static final Color PIT_FILL    = new Color(0x5C3A20);
    private static final Color PIT_EMPTY   = new Color(0x744E2E);
    private static final Color SEED_TEXT   = new Color(0xFFF6EA);
    private static final Color LEGAL_RING  = new Color(0xE8A33D);
    private static final Color HOVER_RING  = new Color(0xFFD08A);
    private static final Color LAST_RING   = new Color(0x4FA3A5);
    private static final Color ACTIVE_RING = new Color(0xFFE39B);
    private static final Color CAPTURE_RING= new Color(0xD9524B);
    private static final Color HAND_FILL   = new Color(0xF2C063);
    private static final Color HAND_TEXT   = new Color(0x3A2C20);
    private static final Color INK         = new Color(0x3A2C20);
    private static final Color INK_SOFT    = new Color(0x7A6754);

    private static final int MARGIN = 24;
    private static final int HEADER = 64;
    private static final int FOOTER = 64;

    private Board board = Board.initial();
    private List<Integer> legal = List.of();
    private int humanSide = Board.SOUTH;
    private int lastMove = -1;
    private int hovered = -1;
    private boolean thinking = false;
    private String status = "";
    private PitListener listener;

    // Animation frame state. When animating, the view paints these arrays
    // instead of the board, because mid-move the seeds do not total 48 (some
    // are in the hand) and so cannot be held as a Board.
    private boolean animating = false;
    private int[] framePits;
    private int[] frameCaptured;
    private int frameHand = 0;
    private int frameActive = -1;
    private int frameOpposite = -1;
    private boolean frameCapture = false;

    public BoardView() {
        setPreferredSize(new Dimension(760, 480));
        setBackground(BACKDROP);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int pit = pitAt(e.getPoint());
                int next = legal.contains(pit) ? pit : -1;
                if (next != hovered) {
                    hovered = next;
                    setCursor(java.awt.Cursor.getPredefinedCursor(
                            next >= 0 ? java.awt.Cursor.HAND_CURSOR : java.awt.Cursor.DEFAULT_CURSOR));
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = -1;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int pit = pitAt(e.getPoint());
                if (pit >= 0 && listener != null && legal.contains(pit)) {
                    listener.pitClicked(pit);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void setPitListener(PitListener listener) {
        this.listener = listener;
    }

    public void setHumanSide(int side) {
        this.humanSide = side;
        repaint();
    }

    /** Shows a position. {@code legalMoves} should be empty when it is not the human's turn. */
    public void setPosition(Board board, List<Integer> legalMoves, int lastMove) {
        this.board = board;
        this.legal = legalMoves;
        this.lastMove = lastMove;
        this.hovered = -1;
        this.animating = false;
        repaint();
    }

    public void setThinking(boolean thinking) {
        this.thinking = thinking;
        repaint();
    }

    public void setStatus(String status) {
        this.status = status;
        repaint();
    }

    /** Shows one step of a move in progress. Called on the EDT by the animation timer. */
    public void setFrame(Board.Step step) {
        this.animating = true;
        this.framePits = step.pits();
        this.frameCaptured = new int[] {step.captured(Board.SOUTH), step.captured(Board.NORTH)};
        this.frameHand = step.hand();
        this.frameActive = step.active();
        this.frameOpposite = step.opposite();
        this.frameCapture = step.kind() == Board.Step.Kind.CAPTURE;
        this.hovered = -1;
        repaint();
    }

    /** Leaves animation mode; the next {@link #setPosition} paints the settled board. */
    public void clearAnimation() {
        this.animating = false;
        this.framePits = null;
        this.frameCaptured = null;
        this.frameHand = 0;
        this.frameActive = -1;
        this.frameOpposite = -1;
        this.frameCapture = false;
        repaint();
    }

    public boolean isAnimating() {
        return animating;
    }

    // --------------------------------------------------------------- geometry

    private int columnOf(int pit) {
        // The human's row runs left to right; the far row is mirrored above it.
        return Board.owner(pit) == humanSide
                ? pit - Board.firstPitOf(humanSide)
                : Board.PITS_PER_SIDE - 1 - (pit - Board.firstPitOf(Board.opponentOf(humanSide)));
    }

    private int cellWidth() {
        return (getWidth() - 2 * MARGIN) / Board.PITS_PER_SIDE;
    }

    private int radius() {
        int boardHeight = getHeight() - HEADER - FOOTER;
        return Math.max(12, Math.min(cellWidth(), boardHeight / 2) / 2 - 8);
    }

    private Point centreOf(int pit) {
        int boardHeight = getHeight() - HEADER - FOOTER;
        int x = MARGIN + cellWidth() * columnOf(pit) + cellWidth() / 2;
        boolean near = Board.owner(pit) == humanSide;
        int y = HEADER + (near ? 3 * boardHeight / 4 : boardHeight / 4);
        return new Point(x, y);
    }

    /** The pit under a point, or -1. */
    public int pitAt(Point p) {
        int r = radius();
        for (int pit = 0; pit < Board.TOTAL_PITS; pit++) {
            Point c = centreOf(pit);
            if (p.distanceSq(c) <= (double) r * r) return pit;
        }
        return -1;
    }

    // --------------------------------------------------------------- painting

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int computer = Board.opponentOf(humanSide);
        int boardHeight = getHeight() - HEADER - FOOTER;

        // Board.
        g2.setColor(BOARD_WOOD);
        g2.fillRoundRect(MARGIN, HEADER, getWidth() - 2 * MARGIN, boardHeight, 28, 28);
        g2.setColor(BOARD_EDGE);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(MARGIN, HEADER, getWidth() - 2 * MARGIN, boardHeight, 28, 28);

        // Source of truth: frame arrays while a move animates, else the board.
        boolean anim = animating && framePits != null;

        // Pits.
        int r = radius();
        for (int pit = 0; pit < Board.TOTAL_PITS; pit++) {
            Point c = centreOf(pit);
            int seeds = anim ? framePits[pit] : board.seeds(pit);

            g2.setColor(seeds == 0 ? PIT_EMPTY : PIT_FILL);
            g2.fillOval(c.x - r, c.y - r, 2 * r, 2 * r);

            if (!anim && pit == lastMove) {
                g2.setColor(LAST_RING);
                g2.setStroke(new BasicStroke(3f));
                g2.drawOval(c.x - r - 4, c.y - r - 4, 2 * r + 8, 2 * r + 8);
            }
            if (anim && (pit == frameActive || (frameCapture && pit == frameOpposite))) {
                g2.setColor(frameCapture ? CAPTURE_RING : ACTIVE_RING);
                g2.setStroke(new BasicStroke(4f));
                g2.drawOval(c.x - r - 4, c.y - r - 4, 2 * r + 8, 2 * r + 8);
            }
            if (!anim && legal.contains(pit)) {
                g2.setColor(pit == hovered ? HOVER_RING : LEGAL_RING);
                g2.setStroke(new BasicStroke(pit == hovered ? 4f : 3f));
                g2.drawOval(c.x - r, c.y - r, 2 * r, 2 * r);
            }

            g2.setColor(SEED_TEXT);
            g2.setFont(getFont().deriveFont(Font.BOLD, Math.max(14f, r * 0.85f)));
            drawCentred(g2, String.valueOf(seeds), c.x, c.y);
        }

        // The travelling hand: seeds picked up and not yet sown.
        if (anim && frameHand > 0 && frameActive >= 0) {
            Point c = centreOf(frameActive);
            int hr = Math.max(12, r / 2);
            int hy = c.y - r - hr - 2;
            g2.setColor(HAND_FILL);
            g2.fillOval(c.x - hr, hy - hr, 2 * hr, 2 * hr);
            g2.setColor(BOARD_EDGE);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(c.x - hr, hy - hr, 2 * hr, 2 * hr);
            g2.setColor(HAND_TEXT);
            g2.setFont(getFont().deriveFont(Font.BOLD, Math.max(12f, hr * 0.9f)));
            drawCentred(g2, String.valueOf(frameHand), c.x, hy);
        }

        // Captured counts, above and below the board.
        int compCaptured = anim ? frameCaptured[computer] : board.captured(computer);
        int humanCaptured = anim ? frameCaptured[humanSide] : board.captured(humanSide);
        g2.setColor(INK);
        g2.setFont(getFont().deriveFont(Font.BOLD, 17f));
        g2.drawString("Computer", MARGIN + 4, HEADER - 34);
        g2.drawString("You", MARGIN + 4, HEADER + boardHeight + 30);

        g2.setFont(getFont().deriveFont(Font.BOLD, 26f));
        drawRight(g2, compCaptured + " captured", getWidth() - MARGIN - 4, HEADER - 30);
        drawRight(g2, humanCaptured + " captured", getWidth() - MARGIN - 4, HEADER + boardHeight + 34);

        // Status line.
        g2.setColor(thinking ? new Color(0xB4622A) : INK_SOFT);
        g2.setFont(getFont().deriveFont(thinking ? Font.BOLD : Font.PLAIN, 14f));
        String line = thinking ? "Computer is thinking\u2026" : status;
        if (line != null && !line.isEmpty()) {
            g2.drawString(line, MARGIN + 4, getHeight() - 16);
        }

        g2.dispose();
    }

    private void drawCentred(Graphics2D g2, String text, int cx, int cy) {
        var fm = g2.getFontMetrics();
        g2.drawString(text, cx - fm.stringWidth(text) / 2, cy + (fm.getAscent() - fm.getDescent()) / 2);
    }

    private void drawRight(Graphics2D g2, String text, int right, int baseline) {
        var fm = g2.getFontMetrics();
        g2.drawString(text, right - fm.stringWidth(text), baseline);
    }
}