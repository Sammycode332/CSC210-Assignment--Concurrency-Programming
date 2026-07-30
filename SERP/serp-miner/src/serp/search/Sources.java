package serp.search;

import java.util.List;

/** Convenience factory for the standard source sets. */
public final class Sources {

    private Sources() {}

    /** All four live academic backends. */
    public static List<SearchClient> online() {
        return List.of(
                new ArxivSearchClient(),
                new OpenAlexSearchClient(),
                new SemanticScholarSearchClient(),
                new CrossRefSearchClient());
    }

    /** The network-free corpus, as a single source (for demos / tests / benchmarks). */
    public static List<SearchClient> offline() {
        return List.of(new OfflineSearchClient());
    }

    public static List<SearchClient> offline(int minLatencyMillis, int maxLatencyMillis) {
        return List.of(new OfflineSearchClient(minLatencyMillis, maxLatencyMillis));
    }
}
