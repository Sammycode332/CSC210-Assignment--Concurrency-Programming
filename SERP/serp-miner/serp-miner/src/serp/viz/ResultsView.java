package serp.viz;

import serp.mine.TopicResult;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;

/**
 * The 4-page result area: for each of the two topics, a "papers" page and a
 * "visualisation" page. Tabs are created immediately with a "Searching…" placeholder
 * and filled in as each topic's {@link TopicResult} arrives — so the two concurrent
 * searches surface independently, whichever finishes first.
 */
public final class ResultsView extends JTabbedPane {

    private final JPanel papers1 = holder();
    private final JPanel chart1 = holder();
    private final JPanel papers2 = holder();
    private final JPanel chart2 = holder();

    public ResultsView(String topic1, String topic2) {
        addTab("1 \u00b7 Papers: " + shorten(topic1), papers1);
        addTab("2 \u00b7 Papers: " + shorten(topic2), papers2);
        addTab("1 \u00b7 Chart: " + shorten(topic1), chart1);
        addTab("2 \u00b7 Chart: " + shorten(topic2), chart2);
        placeholder(papers1);
        placeholder(papers2);
        placeholder(chart1);
        placeholder(chart2);
    }

    /** Fill in one topic's two pages. {@code index} is 0 for topic 1, 1 for topic 2. */
    public void setResult(int index, TopicResult r) {
        JPanel papers = (index == 0) ? papers1 : papers2;
        JPanel chart = (index == 0) ? chart1 : chart2;
        swap(papers, new PapersPanel(r));
        swap(chart, new JScrollPane(new BarChartPanel(r)));
    }

    private static void swap(JPanel holder, java.awt.Component content) {
        holder.removeAll();
        holder.add(content, BorderLayout.CENTER);
        holder.revalidate();
        holder.repaint();
    }

    private static void placeholder(JPanel holder) {
        JLabel label = new JLabel("Searching\u2026", SwingConstants.CENTER);
        label.setForeground(new java.awt.Color(0x888888));
        holder.add(label, BorderLayout.CENTER);
    }

    private static JPanel holder() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        return p;
    }

    private static String shorten(String s) {
        if (s == null || s.isBlank()) {
            return "?";
        }
        String t = s.trim();
        return t.length() <= 18 ? t : t.substring(0, 17) + "\u2026";
    }
}
