package serp.mine;

import java.util.List;

/**
 * Immutable result of one mining run: the ranked features plus enough metadata to
 * report on and reproduce the run. Being immutable, it is trivially safe to hand to
 * the Swing EDT for visualisation (JCiP 9 — publish a finished, immutable snapshot
 * to the UI thread rather than sharing mutable state with it).
 *
 * @param source        backend label ("arxiv" / "offline")
 * @param queries       queries fanned out
 * @param papersFound   distinct papers analysed after de-duplication
 * @param ranked        features in descending order of system count
 * @param poolSize      worker threads used
 * @param searchMillis  wall-clock of Stage A (parallel search fan-out)
 * @param extractMillis wall-clock of Stage B (parallel extraction + aggregation)
 * @param totalMillis   wall-clock of the whole run
 */
public record MiningResult(
        String source,
        List<String> queries,
        int papersFound,
        List<FeatureCount> ranked,
        int poolSize,
        long searchMillis,
        long extractMillis,
        long totalMillis) {

    public MiningResult {
        queries = List.copyOf(queries);
        ranked = List.copyOf(ranked);
    }

    public long maxCount() {
        long max = 0;
        for (FeatureCount fc : ranked) {
            max = Math.max(max, fc.systemCount());
        }
        return max;
    }
}
