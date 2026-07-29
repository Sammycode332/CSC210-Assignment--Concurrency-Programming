package serp.model;

import java.util.Objects;

/**
 * One entry returned by the "search engine" (one row of the SERP).
 *
 * <p>Modelled as an immutable {@code record}, for the same reason {@code Board}
 * was immutable in the Ayo project: immutable, freely-shareable state is what
 * lets many worker threads read it concurrently with no locks and no risk of
 * interference (JCiP Ch.3 "Sharing Objects", Ch.16 "safe publication").
 * Each {@code Paper} we hand to the thread pool is effectively immutable, so it
 * is safe to publish to any number of threads.
 *
 * <p>We treat one paper as describing one "system" for the purpose of the
 * assignment's counting rule ("... in order of the number of systems having
 * the feature").
 *
 * @param id      stable de-duplication key (e.g. the arXiv id or DOI)
 * @param title   paper title
 * @param summary abstract / snippet text mined for features
 * @param url     link to the actual page (the SERP link)
 * @param year    publication year, or 0 if unknown
 * @param source  which backend produced this result ("arxiv", "offline", ...)
 */
public record Paper(String id, String title, String summary, String url,
                    int year, String source) {

    public Paper {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        summary = (summary == null) ? "" : summary;
        url = (url == null) ? "" : url;
        source = (source == null) ? "unknown" : source;
    }

    /** Title + abstract, lower-cased, the text the extractor scans. */
    public String searchableText() {
        return (title + " " + summary).toLowerCase();
    }
}
