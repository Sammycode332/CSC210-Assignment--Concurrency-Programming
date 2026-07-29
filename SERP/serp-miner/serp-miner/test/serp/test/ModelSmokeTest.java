package serp.test;

import serp.model.Feature;
import serp.model.Paper;
import serp.model.PaperFeatures;

import java.util.EnumSet;
import java.util.Set;

/** Verifies the immutable domain model behaves as a safely-shareable value layer. */
public final class ModelSmokeTest {

    public static void main(String[] args) {
        System.out.println("ModelSmokeTest");

        Paper p = new Paper("id1", "Anonymous GPS Crime App", "gps anonymous report",
                "http://x", 2021, "offline");
        Check.equal(p.source(), "offline", "source preserved");
        Check.that(p.searchableText().equals("anonymous gps crime app gps anonymous report"),
                "searchableText lower-cases title + summary");

        // null summary/url tolerated and normalised
        Paper q = new Paper("id2", "T", null, null, 0, null);
        Check.equal(q.summary(), "", "null summary -> empty");
        Check.equal(q.source(), "unknown", "null source -> unknown");

        // PaperFeatures makes a defensive, immutable copy
        EnumSet<Feature> src = EnumSet.of(Feature.ANONYMOUS_REPORTING, Feature.GEOLOCATION);
        PaperFeatures pf = new PaperFeatures(p, src);
        src.add(Feature.MOBILE_APP); // mutate the ORIGINAL after construction
        Check.equal(pf.features().size(), 2, "PaperFeatures copied defensively (original mutation ignored)");
        Check.that(pf.has(Feature.GEOLOCATION), "has() works");

        boolean immutable = false;
        try {
            pf.features().add(Feature.MOBILE_APP);
        } catch (UnsupportedOperationException e) {
            immutable = true;
        }
        Check.that(immutable, "PaperFeatures.features() is unmodifiable");

        // The lexicon meets the assignment's "at least 10" bar.
        Check.that(Feature.values().length >= 10,
                "lexicon defines >= 10 distinctive features (has " + Feature.values().length + ")");
        for (Feature f : Feature.values()) {
            Set<String> kws = Set.copyOf(f.keywords());
            Check.that(!kws.isEmpty(), f.name() + " has keywords");
        }

        Check.done("ModelSmokeTest");
    }

    private ModelSmokeTest() {}
}
