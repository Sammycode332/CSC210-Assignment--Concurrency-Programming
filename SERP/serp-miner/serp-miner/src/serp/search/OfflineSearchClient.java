package serp.search;

import serp.model.Paper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A deterministic, network-free {@link SearchClient} backed by {@link OfflineCorpus}.
 *
 * <p>It filters the corpus by naive keyword relevance to the query and sleeps for a
 * configurable amount of time to <em>simulate network latency</em>. That simulated
 * blocking is what makes the fan-out parallelism visible and measurable in the
 * benchmark: with a per-query wait of ~200&nbsp;ms, running N queries on one thread
 * costs ~N&times;200&nbsp;ms, whereas a large enough pool collapses it toward a
 * single wait — exactly the I/O-bound speedup JCiP&nbsp;8.2 predicts.
 *
 * <p>The latency is clearly labelled as simulated; it is never presented as real
 * network time in reports.
 */
public final class OfflineSearchClient implements SearchClient {

    private final int minLatencyMillis;
    private final int maxLatencyMillis;

    public OfflineSearchClient() {
        this(150, 350);
    }

    public OfflineSearchClient(int minLatencyMillis, int maxLatencyMillis) {
        this.minLatencyMillis = Math.max(0, minLatencyMillis);
        this.maxLatencyMillis = Math.max(this.minLatencyMillis, maxLatencyMillis);
    }

    @Override
    public List<Paper> search(String query, int maxResults) throws InterruptedException {
        simulateLatency();

        String[] terms = query.toLowerCase().split("\\s+");
        List<Paper> matches = new ArrayList<>();
        for (Paper paper : OfflineCorpus.all()) {
            String text = paper.searchableText();
            boolean relevant = false;
            for (String term : terms) {
                if (!term.isBlank() && text.contains(term)) {
                    relevant = true;
                    break;
                }
            }
            if (relevant) {
                matches.add(paper);
            }
            if (matches.size() >= maxResults) {
                break;
            }
        }
        // Fall back to the whole corpus if the query matched nothing, so a demo
        // never comes back empty.
        return matches.isEmpty()
                ? new ArrayList<>(OfflineCorpus.all().subList(0, Math.min(maxResults, OfflineCorpus.all().size())))
                : matches;
    }

    private void simulateLatency() throws InterruptedException {
        int span = maxLatencyMillis - minLatencyMillis;
        int millis = minLatencyMillis + (span == 0 ? 0 : ThreadLocalRandom.current().nextInt(span + 1));
        if (millis > 0) {
            Thread.sleep(millis); // responds to interruption -> supports clean cancellation
        }
    }

    @Override
    public String name() {
        return "offline";
    }
}
