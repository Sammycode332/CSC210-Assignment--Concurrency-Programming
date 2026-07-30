package serp.mine;

import java.util.List;

/**
 * A short, human-readable synthesis of the relevant content found for a topic: a
 * one-line {@code overview} (paper count, year span, recurring features) plus a handful
 * of {@code keyPoints} — the most representative sentences pulled from the papers'
 * abstracts, each tagged with the rank of the paper it came from.
 *
 * <p>Immutable, so it rides inside {@link TopicResult} straight to the EDT for display.
 */
public record TopicSummary(String overview, List<KeyPoint> keyPoints) {

    public TopicSummary {
        keyPoints = List.copyOf(keyPoints);
    }

    /** One extracted sentence and the 1-based rank of its source paper. */
    public record KeyPoint(int paperRank, String text) {}

    public static TopicSummary empty() {
        return new TopicSummary("", List.of());
    }
}
