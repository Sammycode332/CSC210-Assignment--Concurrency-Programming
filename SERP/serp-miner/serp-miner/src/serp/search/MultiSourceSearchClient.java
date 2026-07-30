package serp.search;

import serp.model.Paper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * A {@link SearchClient} that queries several backends <b>concurrently</b> for the same
 * topic and merges their results into one ranked SERP.
 *
 * <p>This is the per-topic search fan-out. It is invoked from a coordinator thread (a
 * {@code SwingWorker} background thread, or a dedicated coordinator task) and submits
 * one task per source to a <em>shared</em> {@link ExecutorService}, collecting the
 * results in completion order via an {@link ExecutorCompletionService}. Because only
 * the coordinator thread blocks (never a pool thread waiting on the same pool), there
 * is no risk of the thread-starvation deadlock JCiP 8.1.1 warns about.
 *
 * <p>A shared {@link Semaphore} bounds how many outbound HTTP requests are in flight at
 * once across <em>all</em> sources and topics, keeping us a polite client (JCiP 5.5.3).
 *
 * <p>Merge policy: results are de-duplicated across sources by normalised title; a paper
 * found by more sources, or ranked higher within a source, sorts higher — so agreement
 * across databases is rewarded. The kept copy is the one with the richest abstract.
 */
public final class MultiSourceSearchClient implements SearchClient {

    private final List<SearchClient> sources;
    private final ExecutorService pool;      // shared, not owned here
    private final Semaphore requestGate;     // shared politeness limit
    private final long perSourceTimeoutSeconds;

    public MultiSourceSearchClient(List<SearchClient> sources, ExecutorService pool,
                                   Semaphore requestGate, long perSourceTimeoutSeconds) {
        this.sources = List.copyOf(sources);
        this.pool = pool;
        this.requestGate = requestGate;
        this.perSourceTimeoutSeconds = perSourceTimeoutSeconds;
    }

    @Override
    public List<Paper> search(String query, int maxResults) {
        CompletionService<List<Paper>> ecs = new ExecutorCompletionService<>(pool);
        for (SearchClient source : sources) {
            ecs.submit(() -> {
                requestGate.acquire();
                try {
                    return source.search(query, maxResults);
                } finally {
                    requestGate.release();
                }
            });
        }

        Map<String, Agg> merged = new LinkedHashMap<>();
        for (int done = 0; done < sources.size(); done++) {
            Future<List<Paper>> f;
            try {
                f = ecs.poll(perSourceTimeoutSeconds, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (f == null) {
                break; // a source is too slow; proceed with what we have
            }
            try {
                List<Paper> batch = f.get();
                for (int rank = 0; rank < batch.size(); rank++) {
                    mergeOne(merged, batch.get(rank), rank);
                }
            } catch (ExecutionException e) {
                // One source failed (rate limit, network, parse) — isolate and continue.
                System.err.println("[multi] source failed: " + rootMessage(e));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        List<Agg> aggs = new ArrayList<>(merged.values());
        aggs.sort(Comparator
                .comparingInt((Agg a) -> a.sourceCount).reversed()   // agreement first
                .thenComparingInt(a -> a.bestRank)                    // then per-source rank
                .thenComparing(a -> -a.paper.year()));                // then newer
        List<Paper> out = new ArrayList<>(Math.min(maxResults, aggs.size()));
        for (int k = 0; k < aggs.size() && k < maxResults; k++) {
            out.add(aggs.get(k).paper);
        }
        return out;
    }

    private static void mergeOne(Map<String, Agg> merged, Paper p, int rank) {
        String key = normalise(p.title());
        if (key.isEmpty()) {
            return;
        }
        Agg agg = merged.get(key);
        if (agg == null) {
            merged.put(key, new Agg(p, rank));
        } else {
            agg.sourceCount++;
            agg.bestRank = Math.min(agg.bestRank, rank);
            // Keep whichever copy carries the longer abstract (better for extraction).
            if (p.summary().length() > agg.paper.summary().length()) {
                agg.paper = p;
            }
        }
    }

    /** Lower-case, strip non-alphanumerics, collapse spaces — a cross-source title key. */
    private static String normalise(String title) {
        return title.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t.getCause() != null ? t.getCause() : t;
        return c.getClass().getSimpleName() + ": " + c.getMessage();
    }

    @Override
    public String name() {
        StringBuilder b = new StringBuilder("multi[");
        for (int i = 0; i < sources.size(); i++) {
            if (i > 0) {
                b.append('+');
            }
            b.append(sources.get(i).name());
        }
        return b.append(']').toString();
    }

    /** Mutable per-title aggregate used only within a single {@link #search} call. */
    private static final class Agg {
        Paper paper;
        int sourceCount = 1;
        int bestRank;

        Agg(Paper paper, int rank) {
            this.paper = paper;
            this.bestRank = rank;
        }
    }
}
