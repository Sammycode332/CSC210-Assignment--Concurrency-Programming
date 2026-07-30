package serp.model;

import java.util.List;

/**
 * The catalogue of distinctive features a crime-reporting system may have.
 *
 * <p>The assignment asks for "at least 10" distinctive features; we define 15.
 * Each constant carries a human-readable label and the keyword/synonym set that
 * signals its presence in a paper's title or abstract. Keeping the lexicon here
 * (rather than scattered through the extractor) makes the feature definitions
 * the single source of truth and easy to defend / tweak.
 *
 * <p>The enum itself is immutable and its {@code keywords} list is unmodifiable,
 * so a single shared instance is safely publishable to every worker thread.
 */
public enum Feature {

    ANONYMOUS_REPORTING("Anonymous reporting",
            "anonymous", "anonymity", "confidential", "de-identified", "unidentified"),

    GEOLOCATION("Geolocation / GPS tagging",
            "geolocation", "geotag", "geo-tag", "gps", "location-based", "spatial", "geospatial", "map-based"),

    REALTIME_ALERTS("Real-time alerts / notifications",
            "real-time alert", "real-time notification", "push notification", "instant alert", "live alert", "realtime"),

    MEDIA_EVIDENCE("Media / evidence upload",
            "media upload", "photo upload", "image upload", "video upload", "evidence upload",
            "upload photo", "upload image", "upload video", "upload evidence",
            "photo evidence", "video evidence", "image evidence", "multimedia evidence",
            "attach evidence", "attach photo", "multimedia"),

    CASE_TRACKING("Case / incident tracking",
            "case tracking", "incident tracking", "track report", "report status", "case status", "follow-up", "case management"),

    ENCRYPTION_SECURITY("Encryption / data security",
            "encryption", "encrypted", "end-to-end", "cryptograph", "privacy-preserving", "secure channel", "data security"),

    MULTILINGUAL("Multilingual support",
            "multilingual", "multi-lingual", "multi-language", "language support", "translation", "cross-lingual"),

    MOBILE_APP("Mobile application",
            "mobile app", "mobile application", "android", "ios", "smartphone", "mobile-based", "mobile phone"),

    AUTHORITY_INTEGRATION("Police / authority integration",
            "police", "law enforcement", "dispatch", "emergency service", "authority integration", "911", "112", "999"),

    ANALYTICS_DASHBOARD("Analytics / dashboard",
            "dashboard", "analytics", "statistics", "data visualization", "reporting dashboard", "insight"),

    CROWDSOURCING("Crowdsourcing / citizen reporting",
            "crowdsourc", "crowd-sourc", "citizen report", "community report", "participatory", "citizen science", "volunteered geographic"),

    ML_PREDICTION("Machine learning / prediction",
            "machine learning", "deep learning", "neural network", "prediction", "predictive", "classification", "hotspot", "forecast"),

    EMERGENCY_SOS("Emergency / SOS / panic",
            "panic button", "sos", "distress signal", "emergency alert", "help button", "duress"),

    VERIFICATION("Verification / credibility",
            "verification", "verify report", "credibility", "fake report", "false report", "validation", "trust score", "reliability"),

    NLP_INTERFACE("Chatbot / NLP interface",
            "chatbot", "conversational agent", "natural language", "voice interface", "speech recognition", "dialogue system");

    private final String label;
    private final List<String> keywords;

    Feature(String label, String... keywords) {
        this.label = label;
        this.keywords = List.of(keywords); // unmodifiable, safely published
    }

    public String label() {
        return label;
    }

    /** Lower-cased trigger phrases; a paper "has" this feature if any appear. */
    public List<String> keywords() {
        return keywords;
    }
}
