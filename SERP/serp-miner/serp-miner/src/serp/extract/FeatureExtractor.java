package serp.extract;

import serp.model.Feature;
import serp.model.Paper;
import serp.model.PaperFeatures;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Detects which {@link Feature}s a {@link Paper} exhibits by matching the feature
 * lexicon against the paper's title + abstract.
 *
 * <p><b>Thread-safety:</b> this class is <em>immutable after construction</em> — the
 * compiled {@link Pattern}s are built once in the constructor and never mutated, and
 * {@link Pattern}/{@link java.util.regex.Matcher} are used in a strictly thread-local
 * way inside {@link #extract} (a fresh matcher per call). It is therefore safe to
 * share one extractor instance across every worker thread, which is exactly how the
 * miner uses it (JCiP 3.4 immutability, 4.3.1 sharing safely).
 *
 * <p>Matching uses {@code (?<![a-z0-9])keyword(?![a-z0-9])} boundaries so that short
 * tokens like "gps" or "sos" do not match as substrings inside unrelated words.
 */
public final class FeatureExtractor {

    // One compiled alternation Pattern per Feature: (kw1|kw2|...) with boundaries.
    private final Map<Feature, Pattern> patterns;

    public FeatureExtractor() {
        Map<Feature, Pattern> map = new EnumMap<>(Feature.class);
        for (Feature f : Feature.values()) {
            map.put(f, compile(f.keywords()));
        }
        this.patterns = map; // effectively immutable
    }

    private static Pattern compile(List<String> keywords) {
        StringBuilder alt = new StringBuilder();
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) {
                alt.append('|');
            }
            alt.append(Pattern.quote(keywords.get(i).toLowerCase()));
        }
        // Leading boundary keeps short tokens (gps, sos) from matching as substrings;
        // an optional inflectional suffix lets a stem also match its plural / -ing /
        // -ed form (upload -> uploads, report -> reporting) without loosening the tail
        // boundary enough to re-admit substring hits.
        String regex = "(?<![a-z0-9])(?:" + alt + ")(?:s|es|ing|ed)?(?![a-z0-9])";
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    /** All features present in the paper's searchable text. */
    public Set<Feature> extract(Paper paper) {
        String text = paper.searchableText();
        EnumSet<Feature> found = EnumSet.noneOf(Feature.class);
        for (Map.Entry<Feature, Pattern> e : patterns.entrySet()) {
            if (e.getValue().matcher(text).find()) {
                found.add(e.getKey());
            }
        }
        return found;
    }

    /** Convenience wrapper returning the immutable {@link PaperFeatures} value. */
    public PaperFeatures analyse(Paper paper) {
        return new PaperFeatures(paper, extract(paper));
    }
}
