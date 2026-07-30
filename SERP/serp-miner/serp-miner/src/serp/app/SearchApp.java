package serp.app;

import serp.viz.SearchFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** GUI entry point: launches the search-engine window on the Event Dispatch Thread. */
public final class SearchApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // fall back to default look and feel
            }
            new SearchFrame().setVisible(true);
        });
    }

    private SearchApp() {}
}
