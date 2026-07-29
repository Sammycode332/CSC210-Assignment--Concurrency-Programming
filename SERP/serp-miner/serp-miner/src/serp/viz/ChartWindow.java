package serp.viz;

import serp.mine.MiningResult;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Hosts a {@link BarChartPanel} in a window. The window is always created and shown
 * on the Event Dispatch Thread via {@link SwingUtilities#invokeLater}, honouring the
 * Swing single-thread rule (JCiP Ch.9) — the same rule the Ayo UI followed.
 */
public final class ChartWindow {

    private ChartWindow() {}

    public static void show(MiningResult result) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("SERP Feature Miner \u2014 Crime-Reporting Systems");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.add(new JScrollPane(new BarChartPanel(result)));
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
