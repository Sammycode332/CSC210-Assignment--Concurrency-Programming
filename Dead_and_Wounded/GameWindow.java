import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

public class GameWindow extends JFrame {

    JTextField guessField;
    JButton button;
    JLabel result;

    DefaultTableModel historyModel;
    JTable historyTable;

    GameEngine game;
    GameLogger logger;

    private static final Color HEADER_GREEN = new Color(76, 175, 80);
    private static final Color GRID_TEAL = new Color(178, 223, 219);

    public GameWindow() {

        game = new GameEngine();

        logger = new GameLogger("gameLog.txt");
        logger.start();

        setTitle("Dead and Wounded");
        setSize(520, 420);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        add(buildInputPanel(), BorderLayout.NORTH);
        add(buildHistoryPanel(), BorderLayout.CENTER);

        // On close, let the logger drain its queue and stop cleanly.
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                logger.shutdown();
            }
        });

        // Enter key in the field submits, same as clicking the button.
        guessField.addActionListener(e -> button.doClick());
        button.addActionListener(e -> submitGuess());

        setVisible(true);
    }

    private JPanel buildInputPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));

        guessField = new JTextField(10);
        button = new JButton("Guess");
        result = new JLabel("Enter Guess");

        panel.add(new JLabel("Guess:"));
        panel.add(guessField);
        panel.add(button);
        panel.add(result);

        return panel;
    }

    private JScrollPane buildHistoryPanel() {
        historyModel = new DefaultTableModel(new String[] {"Guess", "Result"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;   // history is read-only
            }
        };

        historyTable = new JTable(historyModel);
        historyTable.setRowHeight(26);
        historyTable.setShowGrid(true);
        historyTable.setGridColor(GRID_TEAL);
        historyTable.setFont(historyTable.getFont().deriveFont(14f));

        // Green header with white text, to echo the mockup, regardless of look-and-feel.
        JTableHeader header = historyTable.getTableHeader();
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            {
                setOpaque(true);
                setHorizontalAlignment(LEFT);
                setBackground(HEADER_GREEN);
                setForeground(Color.WHITE);
                setFont(getFont().deriveFont(Font.BOLD, 14f));
                setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            }
        });

        // A little breathing room inside the cells.
        DefaultTableCellRenderer cell = new DefaultTableCellRenderer();
        cell.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        historyTable.getColumnModel().getColumn(0).setCellRenderer(cell);
        historyTable.getColumnModel().getColumn(1).setCellRenderer(cell);

        JScrollPane scroll = new JScrollPane(historyTable);
        scroll.setBorder(BorderFactory.createTitledBorder("History"));
        return scroll;
    }

    // Runs on the Event Dispatch Thread, so touching Swing state here is safe.
    private void submitGuess() {

        String guess = guessField.getText().trim();

        // Reject anything that is not four different digits before scoring.
        if (!GameEngine.isValidGuess(guess)) {
            result.setText("Enter 4 different digits (0-9).");
            return;
        }

        String answer = game.checkGuess(guess);

        result.setText(answer);

        historyModel.insertw(0, new Object[] {guess, answer});   // newest on top

        logger.log(guess, answer);

        guessField.setText("");
        guessField.requestFocusInWindow();

        if (game.win(answer)) {
            JOptionPane.showMessageDialog(this, "YOU WIN!");
        }
    }

}