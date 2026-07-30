package serp.mine;

/**
 * Immutable configuration for a search/mine run.
 *
 * @param poolSize               worker threads in the shared {@code ExecutorService}
 *                               that runs source fetches and extraction
 * @param maxConcurrentRequests  permits on the shared host {@code Semaphore} — how many
 *                               outbound HTTP requests may be in flight at once, across
 *                               all sources and both topics (politeness, JCiP 5.5.3)
 * @param maxPapersPerTopic      how many merged papers to keep and rank per topic
 * @param sourceTimeoutSeconds   per-source completion wait bound (Stage A)
 * @param extractTimeoutSeconds  per-paper completion wait bound (Stage B)
 */
public record MiningConfig(
        int poolSize,
        int maxConcurrentRequests,
        int maxPapersPerTopic,
        long sourceTimeoutSeconds,
        long extractTimeoutSeconds) {

    public MiningConfig {
        poolSize = Math.max(1, poolSize);
        maxConcurrentRequests = Math.max(1, maxConcurrentRequests);
        maxPapersPerTopic = Math.max(1, maxPapersPerTopic);
    }

    /**
     * Sensible defaults given the number of sources being queried. Pool size follows the
     * JCiP 8.2 I/O formula {@code N = N_cpu * U * (1 + W/C)}, but is also floored at
     * {@code 2 * numSources} so that both topics can fan out across every source at once.
     */
    public static MiningConfig defaults(int numSources) {
        int cpus = Runtime.getRuntime().availableProcessors();
        int ioPool = suggestedIoPoolSize(cpus, 0.9, 8.0);
        int pool = Math.min(24, Math.max(ioPool, Math.max(8, numSources * 2)));
        return new MiningConfig(pool, /*maxConcurrent*/ 6, /*maxPapers*/ 10, 30, 15);
    }

    /** JCiP 8.2 I/O pool-sizing formula, exposed so it can be justified / benchmarked. */
    public static int suggestedIoPoolSize(int cpus, double targetUtilisation, double waitOverCompute) {
        double n = cpus * targetUtilisation * (1.0 + waitOverCompute);
        return Math.max(1, (int) Math.round(n));
    }

    public MiningConfig withPoolSize(int newPoolSize) {
        return new MiningConfig(newPoolSize, maxConcurrentRequests, maxPapersPerTopic,
                sourceTimeoutSeconds, extractTimeoutSeconds);
    }

    public MiningConfig withMaxConcurrentRequests(int permits) {
        return new MiningConfig(poolSize, permits, maxPapersPerTopic,
                sourceTimeoutSeconds, extractTimeoutSeconds);
    }
}
