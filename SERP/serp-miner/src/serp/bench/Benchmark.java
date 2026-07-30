package serp.bench;

import serp.mine.MiningConfig;
import serp.mine.SearchService;
import serp.search.OfflineSearchClient;
import serp.search.SearchClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Empirically justifies pool sizing (as the Ayo lap-cap of 512 was justified by
 * sampling). To exercise the source-level fan-out, it stands up EIGHT offline sources,
 * each with a fixed simulated latency, and mines one topic across all of them at a range
 * of pool sizes. The wall-clock should fall as the pool grows toward the number of
 * sources, then flatten (JCiP 8.2) — and it does so even on a single CPU because the
 * work is I/O wait, not computation.
 *
 * <pre>java -cp out serp.bench.Benchmark</pre>
 */
public final class Benchmark {

    public static void main(String[] args) {
        int numSources = 8;
        List<SearchClient> sources = new ArrayList<>();
        for (int i = 0; i < numSources; i++) {
            sources.add(new OfflineSearchClient(200, 200)); // fixed 200ms "network" per source
        }

        int cpus = Runtime.getRuntime().availableProcessors();
        System.out.printf("cpus=%d  sources=%d  simulated per-source latency=200ms%n%n", cpus, numSources);
        System.out.printf("JCiP 8.2 suggested I/O pool (U=0.9, W/C=8): %d threads%n%n",
                MiningConfig.suggestedIoPoolSize(cpus, 0.9, 8.0));
        System.out.printf("%-8s %-12s %-10s %-10s%n", "pool", "total(ms)", "speedup", "papers");
        System.out.println("--------------------------------------------------");

        long baseline = -1;
        for (int pool : List.of(1, 2, 4, 8, 16)) {
            // permits lifted to >= pool so the THREAD POOL is the sole bound here.
            MiningConfig cfg = new MiningConfig(pool, Math.max(pool, 8), 16, 30, 15);
            try (SearchService service = new SearchService(cfg, sources)) {
                service.mineTopic("crime reporting");                 // warm-up
                long t0 = System.nanoTime();
                var r = service.mineTopic("crime reporting");         // measured
                long ms = (System.nanoTime() - t0) / 1_000_000L;
                if (baseline < 0) {
                    baseline = ms;
                }
                System.out.printf("%-8d %-12d %-10s %-10d%n",
                        pool, ms, String.format("%.2fx", baseline / (double) ms), r.topPapers().size());
            }
        }
        System.out.println("--------------------------------------------------");
        System.out.println("Expect speedup to climb until pool ~= #sources, then flatten.");
    }

    private Benchmark() {}
}
