package serp.mine;

import serp.model.Paper;
import serp.search.Relevance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a {@link TopicSummary} from a topic's top papers by <b>extractive</b>
 * summarisation — no external NLP library, no network, fully deterministic.
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>Split every top paper's abstract into sentences and keep the substantial ones.</li>
 *   <li>Score each sentence by the corpus frequency of the (non-stopword) terms it
 *       contains — sentences built from words that recur across the papers are the ones
 *       that capture the shared, relevant content — with a bonus for query terms, and a
 *       length normalisation so long sentences don't win automatically.</li>
 *   <li>Greedily pick the highest-scoring sentences, skipping near-duplicates and
 *       capping how many come from any single paper, for a diverse digest.</li>
 * </ol>
 * The {@code overview} line reports paper count, year span and the top recurring
 * features, tying the prose back to the feature ranking the chart visualises.
 */
public final class Summarizer {

    private static final Set<String> STOP = Set.of(
            "a", "an", "the", "of", "for", "and", "or", "to", "in", "on", "at", "with",
            "using", "use", "used", "based", "via", "from", "by", "as", "is", "are", "be",
            "this", "that", "these", "those", "we", "our", "it", "its", "can", "such",
            "which", "also", "than", "then", "into", "over", "more", "most", "their",
            "they", "them", "have", "has", "was", "were", "but", "not", "all", "each",
            "paper", "papers", "study", "approach", "propose", "proposed", "present",
            "presents", "results", "method", "methods");

    private static final int MAX_SENTENCES = 4;
    private static final int MAX_PER_PAPER = 2;
    private static final int MIN_SENTENCE_CHARS = 40;
    private static final int MAX_SENTENCE_CHARS = 320;

    private Summarizer() {}

    public static TopicSummary summarize(String topic, List<Paper> topPapers, List<FeatureCount> ranked) {
        List<Candidate> candidates = collectSentences(topPapers);
        if (candidates.isEmpty()) {
            return new TopicSummary(overview(topPapers, ranked), List.of());
        }

        Map<String, Integer> corpusFreq = corpusFrequencies(candidates);
        Set<String> queryTerms = new HashSet<>(Relevance.tokens(topic));
        queryTerms.removeIf(STOP::contains);

        for (Candidate c : candidates) {
            // Bias toward sentences from the more relevant (higher-ranked) papers, without
            // ignoring content: rank 1 gets ~+50%, the last paper gets ~+0%.
            int n = topPapers.size();
            double rankWeight = 1.0 + 0.5 * (n - (c.paperRank - 1)) / (double) Math.max(1, n);
            c.score = scoreSentence(c, corpusFreq, queryTerms) * rankWeight;
        }
        candidates.sort((x, y) -> {
            int byScore = Double.compare(y.score, x.score);
            if (byScore != 0) {
                return byScore;
            }
            int byRank = Integer.compare(x.paperRank, y.paperRank);
            return byRank != 0 ? byRank : Integer.compare(x.position, y.position);
        });

        List<TopicSummary.KeyPoint> chosen = new ArrayList<>();
        List<Set<String>> chosenTokens = new ArrayList<>();
        Map<Integer, Integer> perPaper = new HashMap<>();
        for (Candidate c : candidates) {
            if (chosen.size() >= MAX_SENTENCES) {
                break;
            }
            if (perPaper.getOrDefault(c.paperRank, 0) >= MAX_PER_PAPER) {
                continue;
            }
            if (tooSimilar(c.tokenSet, chosenTokens)) {
                continue;
            }
            chosen.add(new TopicSummary.KeyPoint(c.paperRank, c.text));
            chosenTokens.add(c.tokenSet);
            perPaper.merge(c.paperRank, 1, Integer::sum);
        }
        return new TopicSummary(overview(topPapers, ranked), chosen);
    }

    private static String overview(List<Paper> papers, List<FeatureCount> ranked) {
        if (papers.isEmpty()) {
            return "No papers found.";
        }
        int minYear = Integer.MAX_VALUE;
        int maxYear = Integer.MIN_VALUE;
        for (Paper p : papers) {
            if (p.year() > 0) {
                minYear = Math.min(minYear, p.year());
                maxYear = Math.max(maxYear, p.year());
            }
        }
        StringBuilder b = new StringBuilder();
        b.append(papers.size()).append(papers.size() == 1 ? " paper" : " papers");
        if (minYear != Integer.MAX_VALUE) {
            b.append(minYear == maxYear ? " (" + minYear + ")" : " (" + minYear + "\u2013" + maxYear + ")");
        }
        b.append('.');

        List<String> top = new ArrayList<>();
        for (FeatureCount fc : ranked) {
            if (fc.systemCount() > 0 && top.size() < 3) {
                top.add(fc.feature().label() + " (" + fc.systemCount() + ")");
            }
        }
        if (!top.isEmpty()) {
            b.append(" Recurring capabilities: ").append(String.join(", ", top)).append('.');
        }
        return b.toString();
    }

    private static List<Candidate> collectSentences(List<Paper> papers) {
        List<Candidate> out = new ArrayList<>();
        for (int i = 0; i < papers.size(); i++) {
            String summary = papers.get(i).summary();
            if (summary == null || summary.isBlank()) {
                continue;
            }
            String[] sentences = summary.split("(?<=[.!?])\\s+");
            int pos = 0;
            for (String raw : sentences) {
                String s = raw.replaceAll("\\s+", " ").trim();
                if (s.length() >= MIN_SENTENCE_CHARS && s.length() <= MAX_SENTENCE_CHARS) {
                    out.add(new Candidate(i + 1, pos++, s));
                }
            }
        }
        return out;
    }

    private static Map<String, Integer> corpusFrequencies(List<Candidate> candidates) {
        Map<String, Integer> freq = new HashMap<>();
        for (Candidate c : candidates) {
            for (String t : c.tokenSet) {
                freq.merge(t, 1, Integer::sum);
            }
        }
        return freq;
    }

    private static double scoreSentence(Candidate c, Map<String, Integer> corpusFreq, Set<String> queryTerms) {
        double s = 0;
        int queryHits = 0;
        for (String t : c.tokenSet) {
            s += corpusFreq.getOrDefault(t, 0);
            if (queryTerms.contains(t)) {
                queryHits++;
            }
        }
        s = s / Math.sqrt(Math.max(1, c.tokenSet.size())); // length-normalise
        s += 2.0 * queryHits;                              // reward on-topic sentences
        return s;
    }

    private static boolean tooSimilar(Set<String> tokens, List<Set<String>> chosen) {
        for (Set<String> other : chosen) {
            if (jaccard(tokens, other) > 0.6) {
                return true;
            }
        }
        return false;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int inter = 0;
        for (String t : a) {
            if (b.contains(t)) {
                inter++;
            }
        }
        return inter / (double) (a.size() + b.size() - inter);
    }

    /** A candidate sentence with its provenance and content tokens. */
    private static final class Candidate {
        final int paperRank;
        final int position;
        final String text;
        final Set<String> tokenSet;
        double score;

        Candidate(int paperRank, int position, String text) {
            this.paperRank = paperRank;
            this.position = position;
            this.text = text;
            this.tokenSet = contentTokens(text);
        }

        private static Set<String> contentTokens(String text) {
            Set<String> set = new HashSet<>(Relevance.tokens(text));
            set.removeIf(STOP::contains);
            set.removeIf(t -> t.length() < 3);
            return set;
        }
    }
}
