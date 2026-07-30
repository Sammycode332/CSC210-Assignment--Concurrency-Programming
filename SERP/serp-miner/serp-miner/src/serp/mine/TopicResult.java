package serp.mine;

import serp.model.Paper;

import java.util.List;

/**
 * Immutable result for one topic: the ranked top papers (the "top 10 papers" page) and
 * the feature ranking (the visualisation page), plus metadata and timings.
 *
 * <p>Being immutable, it is published straight to the Swing EDT for rendering — the UI
 * only ever reads a finished snapshot, never shared mutable state (JCiP Ch.9).
 *
 * @param topic         the query the user entered
 * @param sourcesLabel  which backends were queried (e.g. "multi[arxiv+openalex]")
 * @param topPapers     merged, de-duplicated, ranked papers (already capped to top-N)
 * @param ranked        features in descending order of how many of those papers have them
 * @param poolSize      worker threads used
 * @param searchMillis  wall-clock of the parallel multi-source search
 * @param extractMillis wall-clock of the parallel feature extraction
 * @param totalMillis   wall-clock for the whole topic
 * @param error         null on success, else a short description of what went wrong
 */
public record TopicResult(
        String topic,
        String sourcesLabel,
        List<Paper> topPapers,
        List<FeatureCount> ranked,
        int poolSize,
        long searchMillis,
        long extractMillis,
        long totalMillis,
        String error) {

    public TopicResult {
        topPapers = List.copyOf(topPapers);
        ranked = List.copyOf(ranked);
    }

    public boolean ok() {
        return error == null;
    }

    public long maxCount() {
        long max = 0;
        for (FeatureCount fc : ranked) {
            max = Math.max(max, fc.systemCount());
        }
        return max;
    }

    public static TopicResult failed(String topic, String error) {
        return new TopicResult(topic, "", List.of(), List.of(), 0, 0, 0, 0, error);
    }
}
