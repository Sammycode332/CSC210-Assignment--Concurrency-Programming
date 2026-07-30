package serp.search;

import serp.model.Paper;

import java.util.List;

/**
 * Abstracts "the search engine": given a query, return a ranked page of results
 * (our SERP). Keeping this an interface separates the <em>task</em> (fetch a page
 * of results) from the <em>execution policy</em> (how many run in parallel), which
 * is the core discipline of JCiP Ch.6 — and it lets us swap a live web backend
 * ({@link ArxivSearchClient}) for a deterministic in-memory one
 * ({@link OfflineSearchClient}) without touching the concurrency engine.
 */
public interface SearchClient {

    /**
     * Run one query and return up to {@code maxResults} ranked papers.
     * Implementations are expected to be blocking / I/O-bound; the miner runs
     * many of these calls concurrently on a thread pool.
     *
     * @throws Exception any backend failure (network, parse, ...); the miner
     *                   isolates per-query failures so one bad query cannot sink
     *                   the whole run.
     */
    List<Paper> search(String query, int maxResults) throws Exception;

    /** Short label for reports, e.g. "arxiv" or "offline". */
    String name();
}
