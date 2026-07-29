package ayo.app;

import ayo.ai.HeuristicEvaluator;
import ayo.ai.ParallelRootSearch;
import ayo.model.Board;
import ayo.ui.BoardView;
import ayo.ui.GameController;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Entry point. Builds the window on the event dispatch thread, as Swing
 * requires, and shuts the search pool down when the window closes.
 */
public final class Main {

    private record Level(String name, long millis) {
        @Override
        public String toString() {
            return name;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::build);
    }

    private static void build() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // The cross-platform look and feel is a perfectly good fallback.
        }

        BoardView view = new BoardView();
        ParallelRootSearch ai = new ParallelRootSearch(
                new HeuristicEvaluator(), Runtime.getRuntime().availableProcessors(), true);
        GameController controller = new GameController(view, ai, Board.SOUTH);

        JButton newGame = new JButton("New game");
        newGame.addActionListener(e -> controller.newGame());

        JComboBox<Level> level = new JComboBox<>(new Level[] {
                new Level("Quick (0.3s)", 300),
                new Level("Normal (0.8s)", 800),
                new Level("Slow (2s)", 2000),
        });
        level.setSelectedIndex(1);
        level.addActionListener(e -> {
            Level chosen = (Level) level.getSelectedItem();
            if (chosen != null) controller.setThinkingTime(chosen.millis());
        });

        javax.swing.JCheckBox animate = new javax.swing.JCheckBox("Animate moves", true);
        animate.addActionListener(e -> controller.setAnimated(animate.isSelected()));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        controls.add(newGame);
        controls.add(Box.createHorizontalStrut(12));
        controls.add(new JLabel("Thinking time:"));
        controls.add(level);
        controls.add(Box.createHorizontalStrut(12));
        controls.add(animate);

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        root.add(controls, BorderLayout.NORTH);
        root.add(view, BorderLayout.CENTER);

        JFrame frame = new JFrame("Ayo");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                controller.shutdown();     // stops the search, shuts the pool down
                frame.dispose();
            }
        });
        frame.setContentPane(root);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        controller.newGame();
    }
}