package serp.viz;

import serp.mine.FeatureCount;
import serp.mine.TopicResult;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/**
 * Dependency-free horizontal bar chart of one topic's features ranked by paper count.
 * Custom-painted (no JFreeChart). Reads only an immutable {@link TopicResult} and is
 * only touched on the EDT, so it needs no synchronisation (JCiP Ch.9).
 */
public final class BarChartPanel extends JPanel {

    private final TopicResult result;

    private static final Color BG = new Color(0xFFFFFF);
    private static final Color BAR = new Color(0x2E6E9E);
    private static final Color BAR_TOP = new Color(0x59A7D8);
    private static final Color AXIS = new Color(0xCCCCCC);
    private static final Color TEXT = new Color(0x222222);
    private static final Color SUBTLE = new Color(0x777777);

    public BarChartPanel(TopicResult result) {
        this.result = result;
        setBackground(BG);
        int rows = Math.max(1, result.ranked().size());
        setPreferredSize(new Dimension(820, 70 + rows * 28 + 20));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        g2.setColor(BG);
        g2.fillRect(0, 0, w, getHeight());

        g2.setColor(TEXT);
        g2.setFont(new Font("SansSerif", Font.BOLD, 15));
        g2.drawString("Features by number of papers \u2014 \"" + result.topic() + "\"", 20, 26);
        g2.setColor(SUBTLE);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.drawString(result.sourcesLabel() + "   \u00b7   " + result.topPapers().size()
                + " papers   \u00b7   " + result.totalMillis() + " ms", 20, 44);

        List<FeatureCount> ranked = result.ranked();
        if (ranked.isEmpty()) {
            g2.setColor(SUBTLE);
            g2.drawString("(no features detected)", 20, 70);
            g2.dispose();
            return;
        }

        int labelW = 220;
        int left = 20 + labelW;
        int right = w - 60;
        int top = 62;
        int rowH = 28;
        int barH = 16;
        long max = Math.max(1, result.maxCount());

        g2.setColor(AXIS);
        g2.drawLine(left, top - 6, left, top + ranked.size() * rowH);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        int y = top;
        for (FeatureCount fc : ranked) {
            int barW = Math.max(2, (int) Math.round((fc.systemCount() / (double) max) * (right - left)));
            g2.setColor(TEXT);
            String label = fc.feature().label();
            int lw = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, Math.max(12, left - 10 - lw), y + barH - 3);
            g2.setColor(BAR);
            g2.fillRoundRect(left, y, barW, barH, 6, 6);
            g2.setColor(BAR_TOP);
            g2.fillRoundRect(left, y, barW, 6, 6, 6);
            g2.setColor(TEXT);
            g2.drawString(String.valueOf(fc.systemCount()), left + barW + 8, y + barH - 3);
            y += rowH;
        }
        g2.dispose();
    }
}
