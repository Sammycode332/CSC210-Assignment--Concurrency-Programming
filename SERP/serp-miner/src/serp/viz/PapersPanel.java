package serp.viz;

import serp.mine.TopicResult;
import serp.mine.TopicSummary;
import serp.model.Paper;

import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.net.URI;

/**
 * The "top N papers" page for one topic, rendered like a search-engine results list:
 * a numbered entry per paper with a clickable title (opens the paper in the browser),
 * a source/year line, and an abstract snippet. Uses an HTML {@link JEditorPane} purely
 * for layout/wrapping; all data comes from an immutable {@link TopicResult}.
 */
public final class PapersPanel extends JPanel {

    public PapersPanel(TopicResult r) {
        super(new BorderLayout());
        JEditorPane pane = new JEditorPane("text/html", buildHtml(r));
        pane.setEditable(false);
        pane.setOpaque(true);
        pane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                openInBrowser(e.getURL() != null ? e.getURL().toString() : e.getDescription());
            }
        });
        pane.setCaretPosition(0);
        add(new JScrollPane(pane), BorderLayout.CENTER);
    }

    private static String buildHtml(TopicResult r) {
        StringBuilder b = new StringBuilder();
        b.append("<html><body style='font-family:sans-serif; margin:14px;'>");
        b.append("<div style='color:#666; font-size:11px; margin-bottom:10px;'>")
                .append("Top ").append(r.topPapers().size()).append(" papers for <b>")
                .append(esc(r.topic())).append("</b> &middot; ")
                .append(esc(r.sourcesLabel())).append(" &middot; ranked by relevance</div>");

        if (r.ok()) {
            appendSummary(b, r);
        }

        if (!r.ok()) {
            b.append("<div style='color:#b00;'>Search failed: ").append(esc(r.error())).append("</div>");
        } else if (r.topPapers().isEmpty()) {
            b.append("<div style='color:#666;'>No papers found.</div>");
        } else {
            int n = 1;
            for (Paper p : r.topPapers()) {
                String href = p.url().isBlank() ? "#" : esc(p.url());
                b.append("<div style='margin-bottom:16px;'>");
                b.append("<div style='font-size:15px;'>")
                        .append(n++).append(". <a href='").append(href).append("'>")
                        .append(esc(p.title())).append("</a></div>");
                b.append("<div style='color:#1a7f37; font-size:11px;'>")
                        .append(esc(p.source()));
                if (p.year() > 0) {
                    b.append(" &middot; ").append(p.year());
                }
                b.append("</div>");
                String snippet = snippet(p.summary());
                if (!snippet.isEmpty()) {
                    b.append("<div style='color:#333; font-size:12px;'>")
                            .append(esc(snippet)).append("</div>");
                }
                b.append("</div>");
            }
        }
        return b.append("</body></html>").toString();
    }

    private static void appendSummary(StringBuilder b, TopicResult r) {
        TopicSummary s = r.summary();
        b.append("<div style='background:#f4f7fa; border:1px solid #dde5ec; "
                + "padding:10px 12px; margin-bottom:16px;'>");
        b.append("<div style='font-size:12px; font-weight:bold; color:#2E6E9E; "
                + "margin-bottom:4px;'>Summary of relevant content</div>");
        if (!s.overview().isBlank()) {
            b.append("<div style='color:#333; font-size:12px; margin-bottom:6px;'>")
                    .append(esc(s.overview())).append("</div>");
        }
        if (!s.keyPoints().isEmpty()) {
            b.append("<ul style='margin:0 0 0 16px; padding:0; color:#333; font-size:12px;'>");
            for (TopicSummary.KeyPoint kp : s.keyPoints()) {
                b.append("<li style='margin-bottom:3px;'>")
                        .append("<span style='color:#1a7f37;'>[").append(kp.paperRank()).append("]</span> ")
                        .append(esc(kp.text())).append("</li>");
            }
            b.append("</ul>");
        }
        b.append("</div>");
    }

    private static String snippet(String s) {
        if (s == null) {
            return "";
        }
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() <= 260 ? one : one.substring(0, 259) + "\u2026";
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void openInBrowser(String url) {
        if (url == null || url.isBlank() || url.equals("#")) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() ->
                    System.err.println("Could not open browser for " + url + ": " + ex.getMessage()));
        }
    }
}
