package serp.test;

import serp.mine.FeatureCount;
import serp.mine.MiningConfig;
import serp.mine.SearchService;
import serp.mine.TopicResult;
import serp.extract.FeatureExtractor;
import serp.model.Feature;
import serp.model.Paper;
import serp.search.OfflineSearchClient;
import serp.search.SearchClient;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Concurrency-correctness test for the new engine: feature counts must be identical
 * regardless of work-pool size, and must match a single-threaded reference. Also checks
 * that mineBoth returns both topics. Uses several identical offline sources (0 latency)
 * to stress the source fan-out and the shared ConcurrentHashMap/LongAdder aggregation.
 */
public final class ParallelSmokeTest {

    public static void main(String[] args) {
        System.out.println("ParallelSmokeTest");
        String topic = "crime reporting";
        int maxPapers = 20;

        List<SearchClient> sources = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            sources.add(new OfflineSearchClient(0, 0));
        }

        Map<Feature, Long> reference = referenceCounts(topic, maxPapers);

        Map<Feature, Long> first = null;
        for (int pool : new int[]{1, 2, 4, 8}) {
            MiningConfig cfg = new MiningConfig(pool, Math.max(pool, 4), maxPapers, 30, 15);
            try (SearchService service = new SearchService(cfg, sources)) {
                TopicResult r = service.mineTopic(topic);
                Map<Feature, Long> got = toMap(r.ranked());
                Check.equal(got, reference, "pool=" + pool + " counts match single-threaded reference");

                long prev = Long.MAX_VALUE;
                for (FeatureCount fc : r.ranked()) {
                    Check.that(fc.systemCount() <= prev, "pool=" + pool + " ranking descending");
                    prev = fc.systemCount();
                }
                if (first == null) {
                    first = got;
                } else {
                    Check.equal(got, first, "pool=" + pool + " identical to first run");
                }
            }
        }

        // Two topics at once must both come back, labelled correctly.
        MiningConfig cfg = new MiningConfig(8, 6, maxPapers, 30, 15);
        try (SearchService service = new SearchService(cfg, sources)) {
            List<TopicResult> both = service.mineBoth("anonymous reporting", "crime mapping");
            Check.equal(both.size(), 2, "mineBoth returns two results");
            Check.equal(both.get(0).topic(), "anonymous reporting", "topic 1 label preserved");
            Check.equal(both.get(1).topic(), "crime mapping", "topic 2 label preserved");
            Check.that(both.get(0).ok() && both.get(1).ok(), "both topics succeeded");
            Check.that(!both.get(0).topPapers().isEmpty(), "topic 1 has papers");
        }

        Check.done("ParallelSmokeTest");
    }

    private static Map<Feature, Long> referenceCounts(String topic, int max) {
        FeatureExtractor ex = new FeatureExtractor();
        Map<Feature, Long> counts = new EnumMap<>(Feature.class);
        try {
            List<Paper> papers = new OfflineSearchClient(0, 0).search(topic, max);
            for (Paper p : papers) {
                for (Feature f : ex.extract(p)) {
                    counts.merge(f, 1L, Long::sum);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return counts;
    }

    private static Map<Feature, Long> toMap(List<FeatureCount> ranked) {
        Map<Feature, Long> m = new EnumMap<>(Feature.class);
        for (FeatureCount fc : ranked) {
            m.put(fc.feature(), fc.systemCount());
        }
        return m;
    }

    private ParallelSmokeTest() {}
}
