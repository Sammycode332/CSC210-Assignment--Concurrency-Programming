package serp.viz;

import serp.mine.MiningConfig;
import serp.mine.SearchService;
import serp.mine.TopicResult;
import serp.search.SearchClient;
import serp.search.Sources;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;

/**
 * The search-engine page. Two topic inputs are searched <b>at the same time</b>; each
 * topic's results populate its own pair of pages (papers + chart) in {@link ResultsView}
 * as soon as that topic finishes.
 *
 * <h2>Concurrency / EDT discipline (JCiP Ch.9)</h2>
 * The button click only kicks off work; the actual mining runs off the EDT in two
 * {@link SwingWorker}s (one per topic), which keeps the UI responsive. Each worker's
 * {@code doInBackground} plays the "coordinator" role for its topic — it calls
 * {@link SearchService#mineTopic} which fans the real work out onto the service's shared
 * bounded thread pool. Results (immutable {@link TopicResult}s) are published back to the
 * EDT in {@code done}. Two workers running together are the "two searches at once" demo.
 */
public final class SearchFrame extends JFrame {

    private final JTextField topic1 = new JTextField("crime reporting system", 26);
    private final JTextField topic2 = new JTextField("crowdsourced crime reporting app", 26);
    private final JComboBox<String> mode = new JComboBox<>(new String[]{
            "Online: arXiv + OpenAlex + Semantic Scholar + CrossRef",
            "Offline demo (built-in corpus, no network)"});
    private final JButton searchButton = new JButton("Search both");
    private final JPanel center = new JPanel(new BorderLayout());

    private SearchService service;   // current run's engine
    private int pending;             // topics still running (EDT-confined)

    public SearchFrame() {
        super("Scholar Feature Miner \u2014 crime-reporting systems");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        add(buildHeader(), BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        center.add(intro(), BorderLayout.CENTER);
        searchButton.addActionListener(e -> startSearch());
        setPreferredSize(new Dimension(940, 680));
        pack();
        setLocationRelativeTo(null);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                closeServiceAsync(service);
            }
        });
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
        header.setBackground(Color.WHITE);

        JLabel title = new JLabel("Scholar Feature Miner");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setAlignmentX(LEFT_ALIGNMENT);
        JLabel sub = new JLabel("Enter two topics \u2014 both are searched across academic sources at once.");
        sub.setForeground(new Color(0x666666));
        sub.setAlignmentX(LEFT_ALIGNMENT);

        JPanel inputs = new JPanel();
        inputs.setLayout(new BoxLayout(inputs, BoxLayout.X_AXIS));
        inputs.setAlignmentX(LEFT_ALIGNMENT);
        inputs.setBackground(Color.WHITE);
        inputs.add(new JLabel("Topic 1: "));
        inputs.add(topic1);
        inputs.add(Box.createHorizontalStrut(14));
        inputs.add(new JLabel("Topic 2: "));
        inputs.add(topic2);
        inputs.add(Box.createHorizontalStrut(14));
        inputs.add(searchButton);

        JPanel modeRow = new JPanel();
        modeRow.setLayout(new BoxLayout(modeRow, BoxLayout.X_AXIS));
        modeRow.setAlignmentX(LEFT_ALIGNMENT);
        modeRow.setBackground(Color.WHITE);
        modeRow.add(new JLabel("Sources: "));
        modeRow.add(mode);
        modeRow.add(Box.createHorizontalGlue());

        header.add(title);
        header.add(sub);
        header.add(Box.createVerticalStrut(10));
        header.add(inputs);
        header.add(Box.createVerticalStrut(6));
        header.add(modeRow);
        return header;
    }

    private JLabel intro() {
        JLabel l = new JLabel("<html><div style='text-align:center;color:#888;'>"
                + "Type two topics above and press <b>Search both</b>.<br>"
                + "You'll get four pages: top-10 papers and a feature chart for each topic."
                + "</div></html>", JLabel.CENTER);
        return l;
    }

    private void startSearch() {
        String t1 = topic1.getText().trim();
        String t2 = topic2.getText().trim();
        if (t1.isEmpty() || t2.isEmpty()) {
            return;
        }
        boolean offline = mode.getSelectedIndex() == 1;
        List<SearchClient> sources = offline ? Sources.offline() : Sources.online();

        // Tear down the previous run's engine (off the EDT) and start a fresh one.
        closeServiceAsync(service);
        service = new SearchService(MiningConfig.defaults(sources.size()), sources);

        ResultsView results = new ResultsView(t1, t2);
        center.removeAll();
        center.add(results, BorderLayout.CENTER);
        center.revalidate();
        center.repaint();

        searchButton.setEnabled(false);
        pending = 2;
        launchWorker(results, 0, t1);
        launchWorker(results, 1, t2);
    }

    /** One SwingWorker per topic; both run concurrently. */
    private void launchWorker(ResultsView results, int index, String topic) {
        SearchService engine = service; // capture the current engine
        new SwingWorker<TopicResult, Void>() {
            @Override protected TopicResult doInBackground() {
                return engine.mineTopic(topic);          // off the EDT (coordinator role)
            }
            @Override protected void done() {
                TopicResult r;
                try {
                    r = get();
                } catch (Exception ex) {
                    r = TopicResult.failed(topic, ex.getMessage());
                }
                results.setResult(index, r);             // back on the EDT
                if (--pending == 0) {
                    searchButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private static void closeServiceAsync(SearchService s) {
        if (s != null) {
            Thread t = new Thread(s::close, "serp-service-close");
            t.setDaemon(true);
            t.start();
        }
    }
}
