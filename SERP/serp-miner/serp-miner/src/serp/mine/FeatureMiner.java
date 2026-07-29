package serp.mine;

import serp.extract.FeatureExtractor;
import serp.model.Feature;
import serp.model.Paper;
import serp.model.PaperFeatures;
import serp.search.SearchClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * The concurrency engine. Turns a set of queries into a ranked table of
 * crime-reporting features by system count, using two parallel fan-out stages over
 * a single shared thread pool.
 *
 * <h2>How this maps to Java Concurrency in Practice</h2>
 * <ul>
 *   <li><b>Task vs. execution policy (Ch.6).</b> Searching a query and extracting a
 *       paper are expressed as {@code Callable} tasks submitted to an
 *       {@link ExecutorService}; the miner never touches raw {@code Thread}s.</li>
 *   <li><b>ExecutorCompletionService (6.3.5).</b> Both stages consume results in
 *       completion order, so a slow query/paper never blocks a fast one.</li>
 *   <li><b>Bounded resource via Semaphore (5.5.3).</b> Stage A acquires a permit
 *       before each backend call, capping concurrent requests so we stay a polite
 *       client of arXiv.</li>
 *   <li><b>Thread-safe aggregation (Ch.5, Java 8 counters).</b> Stage B workers
 *       tally features into a {@link ConcurrentHashMap} of {@link LongAdder}s —
 *       lock-free, contention-friendly counting. The result is provably independent
 *       of pool size (see {@code ParallelSmokeTest}).</li>
 *   <li><b>Cancellation, timeouts &amp; clean shutdown (Ch.7).</b> Each completion is
 *       awaited with a bounded {@code poll}; the pool is shut down in a
 *       {@code finally} block with an {@code awaitTermination}/{@code shutdownNow}
 *       fallback; {@code InterruptedException} restores the interrupt flag.</li>
 *   <li><b>Safe publication (Ch.3, Ch.16).</b> {@link Paper}/{@link PaperFeatures}
 *       are immutable, so passing them between threads needs no extra sync.</li>
 * </ul>
 */
public final class FeatureMiner {

    private final SearchClient client;
    private final FeatureExtractor extractor;
    private final MiningConfig config;

    public FeatureMiner(SearchClient client, FeatureExtractor extractor, MiningConfig config) {
        this.client = client;
        this.extractor = extractor;
        this.config = config;
    }

    public MiningResult mine() {
        ExecutorService pool = Executors.newFixedThreadPool(config.poolSize());
        Semaphore hostGate = new Semaphore(config.maxConcurrentRequests());
        try {
            long t0 = System.nanoTime();
            List<Paper> papers = fanOutSearch(pool, hostGate);   // Stage A: parallel I/O
            long t1 = System.nanoTime();
            Map<Feature, LongAdder> tally = fanOutExtract(pool, papers); // Stage B: parallel CPU
            long t2 = System.nanoTime();

            List<FeatureCount> ranked = rank(tally);
            return new MiningResult(
                    client.name(), config.queries(), papers.size(), ranked, config.poolSize(),
                    millis(t0, t1), millis(t1, t2), millis(t0, t2));
        } finally {
            shutdown(pool);
        }
    }

    // ---- Stage A: fan out the queries, collect + de-duplicate the SERP ----------

    private List<Paper> fanOutSearch(ExecutorService pool, Semaphore hostGate) {
        CompletionService<List<Paper>> ecs = new ExecutorCompletionService<>(pool);
        for (String query : config.queries()) {
            ecs.submit(() -> {
                hostGate.acquire();                 // bounded concurrent requests
                try {
                    return client.search(query, config.maxResultsPerQuery());
                } finally {
                    hostGate.release();
                }
            });
        }

        // De-duplicate by paper id, preserving first-seen (relevance) order.
        Map<String, Paper> byId = new LinkedHashMap<>();
        int expected = config.queries().size();
        for (int i = 0; i < expected; i++) {
            Future<List<Paper>> f = awaitNext(ecs, config.searchTimeoutSeconds());
            if (f == null) {
                break; // overall timeout waiting for the next completion
            }
            try {
                for (Paper p : f.get()) {           // already completed -> no blocking
                    byId.putIfAbsent(p.id(), p);
                }
            } catch (ExecutionException e) {
                // Isolate a single failed query; the run continues on the rest.
                System.err.println("[search] a query failed: " + rootMessage(e));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return new ArrayList<>(byId.values());
    }

    // ---- Stage B: fan out extraction, aggregate feature counts concurrently -----

    private Map<Feature, LongAdder> fanOutExtract(ExecutorService pool, List<Paper> papers) {
        // Shared, thread-safe tally updated directly by the workers.
        ConcurrentHashMap<Feature, LongAdder> tally = new ConcurrentHashMap<>();
        CompletionService<PaperFeatures> ecs = new ExecutorCompletionService<>(pool);

        for (Paper paper : papers) {
            ecs.submit(() -> {
                PaperFeatures pf = extractor.analyse(paper);
                for (Feature feature : pf.features()) {
                    // computeIfAbsent + LongAdder.increment are both thread-safe;
                    // this is lock-free counting under contention (JCiP Java 8 update).
                    tally.computeIfAbsent(feature, k -> new LongAdder()).increment();
                }
                return pf;
            });
        }

        for (int i = 0; i < papers.size(); i++) {
            Future<PaperFeatures> f = awaitNext(ecs, config.extractTimeoutSeconds());
            if (f == null) {
                break;
            }
            try {
                f.get(); // surface any task exception; counting already happened in-task
            } catch (ExecutionException e) {
                System.err.println("[extract] a paper failed: " + rootMessage(e));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return tally;
    }

    // ---- ranking + plumbing -----------------------------------------------------

    private List<FeatureCount> rank(Map<Feature, LongAdder> tally) {
        List<FeatureCount> counts = new ArrayList<>();
        for (Map.Entry<Feature, LongAdder> e : tally.entrySet()) {
            counts.add(new FeatureCount(e.getKey(), e.getValue().sum()));
        }
        // Descending by count; ties broken by label for a stable, deterministic order.
        counts.sort(Comparator.comparingLong(FeatureCount::systemCount).reversed()
                .thenComparing(fc -> fc.feature().label()));
        return counts;
    }

    /** Bounded wait for the next completed task; null on timeout. */
    private <T> Future<T> awaitNext(CompletionService<T> ecs, long timeoutSeconds) {
        try {
            return ecs.poll(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void shutdown(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                pool.awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static long millis(long fromNanos, long toNanos) {
        return (toNanos - fromNanos) / 1_000_000L;
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t.getCause() != null ? t.getCause() : t;
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
