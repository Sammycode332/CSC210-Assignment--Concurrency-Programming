package serp.test;

import serp.extract.FeatureExtractor;
import serp.model.Feature;
import serp.model.Paper;

import java.util.Set;

/** Verifies feature extraction detects the right features and avoids false positives. */
public final class ExtractionSmokeTest {

    public static void main(String[] args) {
        System.out.println("ExtractionSmokeTest");
        FeatureExtractor ex = new FeatureExtractor();

        Paper a = paper("Anonymous mobile app with GPS geolocation and end-to-end encryption; "
                + "users upload photo evidence and receive push notifications.");
        Set<Feature> fa = ex.extract(a);
        Check.that(fa.contains(Feature.ANONYMOUS_REPORTING), "detects anonymous reporting");
        Check.that(fa.contains(Feature.GEOLOCATION), "detects geolocation (gps/geolocation)");
        Check.that(fa.contains(Feature.ENCRYPTION_SECURITY), "detects encryption");
        Check.that(fa.contains(Feature.MEDIA_EVIDENCE), "detects media/evidence upload");
        Check.that(fa.contains(Feature.MOBILE_APP), "detects mobile app");

        // Boundary safety: 'sos' must NOT match inside 'sostenuto'.
        Paper b = paper("A sostenuto analysis of musical tempo.");
        Check.that(!ex.extract(b).contains(Feature.EMERGENCY_SOS),
                "word boundaries prevent 'sos' matching inside 'sostenuto'");

        // But a real standalone SOS is detected.
        Paper c = paper("A panic button sends an SOS distress signal.");
        Check.that(ex.extract(c).contains(Feature.EMERGENCY_SOS), "detects standalone SOS / panic");

        // A clearly unrelated paper yields no crime-reporting features.
        Paper d = paper("Photosynthesis rates in tropical ferns under shade.");
        Check.that(ex.extract(d).isEmpty(), "unrelated paper -> no features");

        Check.done("ExtractionSmokeTest");
    }

    private static Paper paper(String summary) {
        return new Paper("t", "Test", summary, "http://x", 2020, "test");
    }

    private ExtractionSmokeTest() {}
}
