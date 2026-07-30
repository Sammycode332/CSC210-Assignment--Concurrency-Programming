package serp.mine;

import serp.extract.FeatureExtractor;
import serp.model.Feature;
import serp.model.Paper;
import serp.model.PaperFeatures;
import serp.search.MultiSourceSearchClient;
import serp.search.SearchClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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
 * The concurrency engine behind the search UI. Turns one or two topics into ranked
 * papers + ranked features, exploiting parallelism at three levels.
 *
 * <h2>Three levels of parallelism</h2>
 * <ol>
 *   <li><b>Topics.</b> {@link #mineBoth} runs the two topics at the same time, each on
 *       its own <em>coordinator</em> thread.</li>
 *   <li><b>Sources.</b> Within a topic, {@link MultiSourceSearchClient} queries arXiv,
 *       OpenAlex, Semantic Scholar and CrossRef concurrently and merges them.</li>
 *   <li><b>Papers.</b> Feature extraction over the merged papers is fanned out too.</li>
 * </ol>
 *
 * <h2>Thread roles (why there is no deadlock)</h2>
 * All blocking waits happen on <em>coordinator</em> threads — either the two threads in
 * {@code coordinatorPool}, or a {@code SwingWorker} background thread when the GUI calls
 * {@link #mineTopic} directly. The <em>work pool</em> only ever runs leaf tasks (one
 * HTTP fetch, one extraction) that never wait on the pool themselves. Separating the
 * threads that block from the threads that do the work is what prevents the
 * thread-starvation deadlock of JCiP 8.1.1, even though everything shares one work pool.
 *
 * <p>A shared {@link Semaphore} bounds total in-flight HTTP requests (5.5.3); per-source
 * and per-paper waits are bounded by {@code poll} timeouts (Ch.7); failures in one
 * source or one topic are isolated so the rest still complete.
 */
public final class SearchService implements AutoCloseable {

    private final MiningConfig config;
    private final List<SearchClient> sources;
    private final FeatureExtractor extractor = new FeatureExtractor();

    private final ExecutorService workPool;         // leaf tasks: fetch + extract
    private final ExecutorService coordinatorPool;  // one thread per concurrent topic
    private final Semaphore requestGate;            // shared politeness limit

    public SearchService(MiningConfig config, List<SearchClient> sources) {
        this.config = config;
        this.sources = List.copyOf(sources);
        this.workPool = Executors.newFixedThreadPool(config.poolSize(), named("work"));
        this.coordinatorPool = Executors.newFixedThreadPool(2, named("coordinator"));
        this.requestGate = new Semaphore(config.maxConcurrentRequests());
    }

    /** Run both topics concurrently; returns results in [topic1, topic2] order. */
    public List<TopicResult> mineBoth(String topic1, String topic2) {
        Callable<TopicResult> a = () -> mineTopic(topic1);
        Callable<TopicResult> b = () -> mineTopic(topic2);
        Future<TopicResult> fa = coordinatorPool.submit(a);
        Future<TopicResult> fb = coordinatorPool.submit(b);
        return List.of(await(fa, topic1), await(fb, topic2));
    }

    private TopicResult await(Future<TopicResult> f, String topic) {
        try {
            return f.get();
        } catch (ExecutionException e) {
            return TopicResult.failed(topic, rootMessage(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TopicResult.failed(topic, "interrupted");
        }
    }

    /**
     * Mine a single topic. Runs on the CALLING thread (a coordinator), submitting leaf
     * tasks to the shared work pool. Safe to call directly from a SwingWorker background
     * thread — that thread simply plays the coordinator role.
     */
    public TopicResult mineTopic(String topic) {
        long t0 = System.nanoTime();

        MultiSourceSearchClient search = new MultiSourceSearchClient(
                sources, workPool, requestGate, config.sourceTimeoutSeconds());
        List<Paper> papers = search.search(topic, config.maxPapersPerTopic());  // Stage A
        long t1 = System.nanoTime();

        Map<Feature, LongAdder> tally = extractAll(papers);                      // Stage B
        long t2 = System.nanoTime();

        List<FeatureCount> ranked = rank(tally);
        TopicSummary summary = Summarizer.summarize(topic, papers, ranked);
        return new TopicResult(topic, search.name(), papers, ranked, summary, config.poolSize(),
                millis(t0, t1), millis(t1, t2), millis(t0, t2), null);
    }

    // ---- Stage B: parallel feature extraction + thread-safe aggregation ---------

    private Map<Feature, LongAdder> extractAll(List<Paper> papers) {
        ConcurrentHashMap<Feature, LongAdder> tally = new ConcurrentHashMap<>();
        CompletionService<PaperFeatures> ecs = new ExecutorCompletionService<>(workPool);
        for (Paper paper : papers) {
            ecs.submit(() -> {
                PaperFeatures pf = extractor.analyse(paper);
                for (Feature feature : pf.features()) {
                    tally.computeIfAbsent(feature, k -> new LongAdder()).increment();
                }
                return pf;
            });
        }
        for (int i = 0; i < papers.size(); i++) {
            Future<PaperFeatures> f;
            try {
                f = ecs.poll(config.extractTimeoutSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (f == null) {
                break;
            }
            try {
                f.get();
            } catch (ExecutionException e) {
                System.err.println("[extract] paper failed: " + rootMessage(e));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return tally;
    }

    private List<FeatureCount> rank(Map<Feature, LongAdder> tally) {
        List<FeatureCount> counts = new ArrayList<>();
        for (Map.Entry<Feature, LongAdder> e : tally.entrySet()) {
            counts.add(new FeatureCount(e.getKey(), e.getValue().sum()));
        }
        counts.sort(Comparator.comparingLong(FeatureCount::systemCount).reversed()
                .thenComparing(fc -> fc.feature().label()));
        return counts;
    }

    @Override
    public void close() {
        shutdown(coordinatorPool);
        shutdown(workPool);
    }

    private static void shutdown(ExecutorService pool) {
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

    private static java.util.concurrent.ThreadFactory named(String prefix) {
        LongAdder seq = new LongAdder();
        return r -> {
            seq.increment();
            Thread t = new Thread(r, "serp-" + prefix + "-" + seq.sum());
            t.setDaemon(true);
            return t;
        };
    }

    private static long millis(long from, long to) {
        return (to - from) / 1_000_000L;
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t.getCause() != null ? t.getCause() : t;
        return c.getClass().getSimpleName() + ": " + c.getMessage();
    }
}
