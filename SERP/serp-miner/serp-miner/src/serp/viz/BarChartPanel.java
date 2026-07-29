package serp.viz;

import serp.mine.FeatureCount;
import serp.mine.MiningResult;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/**
 * A dependency-free horizontal bar chart of features ranked by system count.
 * Custom-painted (no JFreeChart) so the project stays jar-free.
 *
 * <p>Threading: this panel only ever reads an immutable {@link MiningResult}
 * snapshot and is only touched on the Swing Event Dispatch Thread, so it needs no
 * synchronisation of its own — the same EDT discipline used in the Ayo UI
 * (JCiP Ch.9: confine UI state to the EDT; hand it a finished immutable snapshot).
 */
public final class BarChartPanel extends JPanel {

    private final MiningResult result;

    private static final Color BG = new Color(0xFAFAFA);
    private static final Color BAR = new Color(0x2E6E9E);
    private static final Color BAR_TOP = new Color(0x59A7D8);
    private static final Color AXIS = new Color(0xBBBBBB);
    private static final Color TEXT = new Color(0x222222);

    public BarChartPanel(MiningResult result) {
        this.result = result;
        setBackground(BG);
        setPreferredSize(new Dimension(880, 40 + result.ranked().size() * 30 + 40));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        g2.setColor(BG);
        g2.fillRect(0, 0, w, h);

        // Title
        g2.setColor(TEXT);
        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.drawString("Crime-reporting features by number of systems ("
                + result.source() + ", " + result.papersFound() + " papers)", 20, 26);

        List<FeatureCount> ranked = result.ranked();
        if (ranked.isEmpty()) {
            g2.dispose();
            return;
        }

        int labelW = 230;
        int left = 20 + labelW;
        int right = w - 60;
        int top = 46;
        int rowH = 30;
        int barH = 18;
        long max = Math.max(1, result.maxCount());

        g2.setColor(AXIS);
        g2.drawLine(left, top - 6, left, top + ranked.size() * rowH);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        int y = top;
        for (FeatureCount fc : ranked) {
            int barW = (int) Math.round((fc.systemCount() / (double) max) * (right - left));

            // label (right-aligned to the axis)
            g2.setColor(TEXT);
            String label = fc.feature().label();
            int lw = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, Math.max(12, left - 10 - lw), y + barH - 4);

            // bar with a light top highlight
            g2.setColor(BAR);
            g2.fillRoundRect(left, y, Math.max(2, barW), barH, 6, 6);
            g2.setColor(BAR_TOP);
            g2.fillRoundRect(left, y, Math.max(2, barW), 6, 6, 6);

            // value
            g2.setColor(TEXT);
            g2.drawString(String.valueOf(fc.systemCount()), left + Math.max(2, barW) + 8, y + barH - 4);

            y += rowH;
        }
        g2.dispose();
    }
}
