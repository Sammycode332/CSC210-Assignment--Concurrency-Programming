package serp.bench;

import serp.extract.FeatureExtractor;
import serp.mine.FeatureMiner;
import serp.mine.MiningConfig;
import serp.mine.MiningResult;
import serp.search.OfflineSearchClient;

import java.util.List;

/**
 * Empirically justifies pool sizing (the way the Ayo lap-cap of 512 was justified by
 * sampling). Runs the offline pipeline — which simulates network latency — at a
 * range of pool sizes and prints wall-clock time and speedup, demonstrating the
 * I/O-bound scaling curve JCiP 8.2 predicts: time falls roughly as the pool grows
 * toward the number of independent queries, then flattens (more threads than tasks
 * buys nothing).
 *
 * <pre>java -cp out serp.bench.Benchmark</pre>
 */
public final class Benchmark {

    public static void main(String[] args) {
        // Fixed, non-random latency window so the numbers are comparable run-to-run.
        OfflineSearchClient client = new OfflineSearchClient(200, 200);
        FeatureExtractor extractor = new FeatureExtractor();
        MiningConfig base = MiningConfig.defaults();

        int queries = base.queries().size();
        int cpus = Runtime.getRuntime().availableProcessors();
        System.out.printf("cpus=%d  queries=%d  simulated per-query latency=200ms%n%n", cpus, queries);
        System.out.printf("JCiP 8.2 suggested I/O pool (U=0.9, W/C=8): %d threads%n",
                MiningConfig.suggestedIoPoolSize(cpus, 0.9, 8.0));
        System.out.println();
        System.out.printf("%-8s %-12s %-10s %-10s%n", "pool", "total(ms)", "speedup", "papers");
        System.out.println("--------------------------------------------------");

        List<Integer> poolSizes = List.of(1, 2, 4, 8, 16, 32);
        long baseline = -1;
        for (int pool : poolSizes) {
            // Lift the host semaphore to >= pool so that the THREAD POOL is the only
            // thing bounding concurrency here (in normal runs the semaphore stays low
            // to be polite to arXiv, and it — not the pool — can be the binding limit).
            MiningConfig cfg = new MiningConfig(base.queries(), base.maxResultsPerQuery(),
                    pool, Math.max(pool, base.maxConcurrentRequests()),
                    base.searchTimeoutSeconds(), base.extractTimeoutSeconds());
            FeatureMiner miner = new FeatureMiner(client, extractor, cfg);

            // one warm-up + one measured run to reduce first-call noise
            miner.mine();
            long t0 = System.nanoTime();
            MiningResult r = miner.mine();
            long ms = (System.nanoTime() - t0) / 1_000_000L;

            if (baseline < 0) {
                baseline = ms;
            }
            System.out.printf("%-8d %-12d %-10s %-10d%n",
                    pool, ms, String.format("%.2fx", baseline / (double) ms), r.papersFound());
        }
        System.out.println("--------------------------------------------------");
        System.out.println("Expect speedup to climb until pool ~= #queries, then flatten.");
    }

    private Benchmark() {}
}
