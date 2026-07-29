package serp.mine;

import java.util.List;

/**
 * Immutable configuration for one mining run.
 *
 * @param queries              the set of related queries fanned out in parallel
 *                             (Stage A). Using several queries — rather than one —
 *                             both enriches the corpus and creates genuine
 *                             independent I/O tasks to parallelise.
 * @param maxResultsPerQuery   cap on results requested per query
 * @param poolSize             worker threads in the shared {@code ExecutorService}
 * @param maxConcurrentRequests permits on the host {@code Semaphore}: how many
 *                             search calls may hit the backend at once (politeness /
 *                             bounded resource, JCiP 5.5.3)
 * @param searchTimeoutSeconds per-result wait bound for Stage A completions
 * @param extractTimeoutSeconds per-result wait bound for Stage B completions
 */
public record MiningConfig(
        List<String> queries,
        int maxResultsPerQuery,
        int poolSize,
        int maxConcurrentRequests,
        long searchTimeoutSeconds,
        long extractTimeoutSeconds) {

    public MiningConfig {
        queries = List.copyOf(queries);
        if (queries.isEmpty()) {
            throw new IllegalArgumentException("at least one query is required");
        }
        poolSize = Math.max(1, poolSize);
        maxConcurrentRequests = Math.max(1, maxConcurrentRequests);
        maxResultsPerQuery = Math.max(1, maxResultsPerQuery);
    }

    /** The default related-query set for crime-reporting systems. */
    public static final List<String> DEFAULT_QUERIES = List.of(
            "crime reporting system",
            "crime reporting mobile app",
            "crowdsourced crime reporting",
            "anonymous incident reporting",
            "citizen crime reporting platform",
            "emergency reporting application",
            "crime hotspot prediction",
            "crime mapping system");

    /**
     * A reasonable default config given the running machine.
     * Pool size follows the JCiP 8.2 formula for an I/O-bound workload:
     * {@code N_threads = N_cpu * U_cpu * (1 + W/C)}. With a high wait/compute
     * ratio (network dominates), we want many more threads than cores — but never
     * more than we have independent tasks (queries), which would just waste threads.
     */
    public static MiningConfig defaults() {
        int cpus = Runtime.getRuntime().availableProcessors();
        int ioBoundPool = suggestedIoPoolSize(cpus, /*targetUtil*/ 0.9, /*waitOverCompute*/ 8.0);
        int pool = Math.min(ioBoundPool, DEFAULT_QUERIES.size());
        return new MiningConfig(DEFAULT_QUERIES, 20, pool, /*maxConcurrent*/ 4, 30, 15);
    }

    /** JCiP 8.2 I/O pool-sizing formula, exposed so it can be justified/benchmarked. */
    public static int suggestedIoPoolSize(int cpus, double targetUtilisation, double waitOverCompute) {
        double n = cpus * targetUtilisation * (1.0 + waitOverCompute);
        return Math.max(1, (int) Math.round(n));
    }

    public MiningConfig withPoolSize(int newPoolSize) {
        return new MiningConfig(queries, maxResultsPerQuery, newPoolSize,
                maxConcurrentRequests, searchTimeoutSeconds, extractTimeoutSeconds);
    }

    public MiningConfig withQueries(List<String> newQueries) {
        return new MiningConfig(newQueries, maxResultsPerQuery, poolSize,
                maxConcurrentRequests, searchTimeoutSeconds, extractTimeoutSeconds);
    }
}
