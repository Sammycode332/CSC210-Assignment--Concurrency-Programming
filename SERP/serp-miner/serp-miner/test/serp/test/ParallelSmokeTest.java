package serp.test;

import serp.extract.FeatureExtractor;
import serp.mine.FeatureCount;
import serp.mine.FeatureMiner;
import serp.mine.MiningConfig;
import serp.mine.MiningResult;
import serp.model.Feature;
import serp.model.Paper;
import serp.search.OfflineSearchClient;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The central concurrency-correctness test: the ranked feature counts must be
 * <b>identical regardless of pool size</b>, and must match a single-threaded
 * reference computed with no executor at all. If the shared {@code ConcurrentHashMap}
 * + {@code LongAdder} aggregation had a race (lost update, visibility bug), the
 * counts would drift as threads were added — this pins that down.
 */
public final class ParallelSmokeTest {

    public static void main(String[] args) {
        System.out.println("ParallelSmokeTest");

        OfflineSearchClient client = new OfflineSearchClient(0, 0); // no latency; stress the CPU path
        FeatureExtractor extractor = new FeatureExtractor();
        MiningConfig base = MiningConfig.defaults();

        // 1) Single-threaded reference: extract every distinct paper once, by hand.
        Map<Feature, Long> reference = referenceCounts(client, extractor, base);

        // 2) Run the miner at several pool sizes; every ranking must match the reference.
        Map<Feature, Long> firstRun = null;
        for (int pool : new int[]{1, 2, 4, 8, 16}) {
            FeatureMiner miner = new FeatureMiner(client, extractor, base.withPoolSize(pool));
            MiningResult r = miner.mine();
            Map<Feature, Long> got = toMap(r.ranked());

            Check.equal(got, reference, "pool=" + pool + " counts match single-threaded reference");

            // ranking is sorted strictly non-increasing by count
            long prev = Long.MAX_VALUE;
            for (FeatureCount fc : r.ranked()) {
                Check.that(fc.systemCount() <= prev, "pool=" + pool + " ranking is descending");
                prev = fc.systemCount();
            }
            if (firstRun == null) {
                firstRun = got;
            } else {
                Check.equal(got, firstRun, "pool=" + pool + " identical to first run");
            }
        }

        Check.done("ParallelSmokeTest");
    }

    /** De-duplicate exactly as the miner does, then count features single-threaded. */
    private static Map<Feature, Long> referenceCounts(OfflineSearchClient client,
                                                       FeatureExtractor extractor,
                                                       MiningConfig cfg) {
        Map<String, Paper> byId = new java.util.LinkedHashMap<>();
        try {
            for (String q : cfg.queries()) {
                for (Paper p : client.search(q, cfg.maxResultsPerQuery())) {
                    byId.putIfAbsent(p.id(), p);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Map<Feature, Long> counts = new EnumMap<>(Feature.class);
        for (Paper p : byId.values()) {
            for (Feature f : extractor.extract(p)) {
                counts.merge(f, 1L, Long::sum);
            }
        }
        return counts;
    }

    private static Map<Feature, Long> toMap(List<FeatureCount> ranked) {
        Map<Feature, Long> m = new EnumMap<>(Feature.class);
        List<FeatureCount> copy = new ArrayList<>(ranked);
        for (FeatureCount fc : copy) {
            m.put(fc.feature(), fc.systemCount());
        }
        return m;
    }

    private ParallelSmokeTest() {}
}
