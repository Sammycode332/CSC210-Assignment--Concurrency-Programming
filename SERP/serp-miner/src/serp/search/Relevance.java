package serp.search;

import serp.model.Paper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scores how relevant a paper is to the user's query, so the engine can return the
 * <em>most relevant</em> papers rather than merely whatever each database happened to
 * return first. Deterministic and dependency-free.
 *
 * <p>The score rewards, in order of influence:
 * <ul>
 *   <li><b>Coverage</b> — how many distinct query terms appear at all (a paper that
 *       touches every query term beats one that touches only one);</li>
 *   <li><b>Title matches</b> — a term in the title counts ~3&times; a term in the body,
 *       with a square-root damping so ten repeats don't dominate;</li>
 *   <li><b>Exact phrase</b> — a bonus if the whole query appears verbatim (bigger in the
 *       title than the abstract).</li>
 * </ul>
 * Query terms that are generic stop-words are ignored so they don't dilute coverage.
 */
public final class Relevance {

    private static final Set<String> STOP = Set.of(
            "a", "an", "the", "of", "for", "and", "or", "to", "in", "on", "at", "with",
            "using", "use", "based", "via", "from", "by", "as", "is", "are", "be",
            "this", "that", "these", "those", "we", "our", "it", "its");

    private static final double TITLE_WEIGHT = 3.0;
    private static final double BODY_WEIGHT = 1.0;
    private static final double PHRASE_TITLE_BONUS = 4.0;
    private static final double PHRASE_BODY_BONUS = 1.5;

    private Relevance() {}

    public static double score(String query, Paper paper) {
        return score(query, paper.title(), paper.title() + " " + paper.summary());
    }

    public static double score(String query, String title, String text) {
        List<String> queryTerms = distinct(tokens(query));
        queryTerms.removeIf(STOP::contains);
        if (queryTerms.isEmpty()) {
            return 0.0;
        }

        Map<String, Integer> titleCounts = counts(tokens(title));
        Map<String, Integer> bodyCounts = counts(tokens(text));

        double s = 0.0;
        int covered = 0;
        for (String term : queryTerms) {
            int tt = titleCounts.getOrDefault(term, 0);
            int bt = bodyCounts.getOrDefault(term, 0);
            if (tt > 0 || bt > 0) {
                covered++;
            }
            s += TITLE_WEIGHT * Math.sqrt(tt) + BODY_WEIGHT * Math.sqrt(bt);
        }
        double coverage = covered / (double) queryTerms.size();
        s *= (0.4 + 0.6 * coverage); // strongly reward covering more of the query

        String q = normalise(query);
        if (!q.isEmpty()) {
            if (normalise(title).contains(q)) {
                s += PHRASE_TITLE_BONUS;
            } else if (normalise(text).contains(q)) {
                s += PHRASE_BODY_BONUS;
            }
        }
        return s;
    }

    // --- tokenisation helpers (shared, deterministic) ---

    public static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        for (String t : s.toLowerCase().split("[^a-z0-9]+")) {
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static List<String> distinct(List<String> tokens) {
        return new ArrayList<>(new LinkedHashSet<>(tokens));
    }

    private static Map<String, Integer> counts(List<String> tokens) {
        Map<String, Integer> m = new HashMap<>();
        for (String t : tokens) {
            m.merge(t, 1, Integer::sum);
        }
        return m;
    }

    private static String normalise(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }
}
