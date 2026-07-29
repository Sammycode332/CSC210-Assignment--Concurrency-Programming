import javax.swing.SwingUtilities;

public class Main {

    public static void main(String[] args) {

        // Start the GUI on the Swing Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            new GameWindow();
        });

    }
}