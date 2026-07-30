package serp.search;

import serp.model.Paper;

import java.util.List;

/**
 * A small, self-contained corpus of realistic (synthetic) abstracts describing
 * crime-reporting systems. Embedding it in code means the whole pipeline —
 * search fan-out, extraction, aggregation, ranking, visualisation — runs and is
 * fully testable with <b>no network access</b>, which also makes the concurrency
 * benchmarks reproducible. The live {@link ArxivSearchClient} exercises the exact
 * same downstream code against real search results.
 *
 * <p>Each abstract deliberately mixes several crime-reporting features so the
 * ranking produced by the miner is non-trivial and meaningful.
 */
final class OfflineCorpus {

    private OfflineCorpus() {}

    static List<Paper> all() {
        return CORPUS;
    }

    private static Paper p(String id, String title, String summary) {
        return new Paper(id, title, summary, "https://example.org/paper/" + id, 2020, "offline");
    }

    private static final List<Paper> CORPUS = List.of(
        p("c01",
          "A Mobile Crowdsourced Crime Reporting Application with Anonymous Submission",
          "We present a mobile application for Android and iOS that lets citizens submit crime reports " +
          "anonymously. Reports are geo-tagged using GPS and can include photo and video evidence uploads. " +
          "A community dashboard visualises reporting hotspots and statistics for law enforcement."),

        p("c02",
          "Secure Real-Time Incident Reporting with End-to-End Encryption",
          "This paper describes a secure incident reporting platform providing end-to-end encryption of " +
          "citizen reports. The system issues real-time push notifications to nearby users and dispatches " +
          "alerts to the police. A panic button enables emergency SOS submission with location sharing."),

        p("c03",
          "Predicting Crime Hotspots from Citizen Reports using Deep Learning",
          "We apply deep learning and neural network classification to volunteered geographic crime reports " +
          "to forecast crime hotspots. An analytics dashboard presents predictive statistics. Reports are " +
          "geolocation-tagged and crowdsourced from a mobile app."),

        p("c04",
          "A Multilingual Chatbot for Community Crime Reporting",
          "Our conversational agent uses natural language understanding to collect crime reports through a " +
          "chatbot. Multilingual support and translation broaden access. The mobile application performs " +
          "case tracking so users can follow the status of a report and receive notifications."),

        p("c05",
          "Verifying Crowdsourced Crime Reports to Reduce False Reports",
          "We propose a verification pipeline that assigns a trust score to crowdsourced reports to detect " +
          "fake reports. Machine learning classification flags unreliable submissions. Verified, geo-tagged " +
          "reports feed a law-enforcement analytics dashboard."),

        p("c06",
          "Anonymous SafeWalk: An Emergency SOS and Distress Alert System",
          "SafeWalk is a smartphone panic button application that sends a distress signal with GPS location " +
          "to emergency services and trusted contacts. Reports may be filed anonymously, and encrypted " +
          "location streaming supports real-time alerts during an incident."),

        p("c07",
          "Geospatial Analytics Dashboard for Urban Crime Incident Tracking",
          "This work builds a geospatial analytics dashboard for tracking reported incidents across a city. " +
          "Case tracking follows each report from submission to resolution, integrating with police dispatch " +
          "systems. Statistics and data visualisation reveal spatial patterns."),

        p("c08",
          "A Participatory Mobile Platform for Anonymous Harassment Reporting",
          "We describe a participatory, crowdsourced mobile platform for anonymous harassment reporting on " +
          "campus. Reports support media evidence upload and are encrypted. A dashboard aggregates statistics " +
          "for administrators, and multilingual support serves international students."),

        p("c09",
          "Real-Time Emergency Dispatch Integration for Citizen Reports",
          "Our system routes citizen crime reports directly to police dispatch in real time. Push " +
          "notifications confirm receipt, and case tracking exposes report status to the reporter. The mobile " +
          "app tags each report with GPS geolocation."),

        p("c10",
          "Trust and Credibility Scoring in Crowdsourced Incident Reporting",
          "We study credibility and verification of crowdsourced incident reports, computing a reliability " +
          "score to filter false reports. Machine learning models rank report trustworthiness. Results are " +
          "shown on an analytics dashboard."),

        p("c11",
          "An Encrypted Whistleblower Platform for Anonymous Corruption Reporting",
          "This platform enables anonymous, confidential whistleblower submissions with strong encryption and " +
          "a secure channel. Multilingual support and evidence upload are provided. Case tracking lets " +
          "investigators manage each confidential report."),

        p("c12",
          "Deep Learning for Multilingual Classification of Emergency Calls",
          "We train neural network models for multilingual classification of emergency call transcripts, " +
          "integrating with 911 dispatch. Speech recognition and natural language processing route distress " +
          "reports, and a dashboard visualises call statistics."),

        p("c13",
          "A GPS-Tagged Photo Reporting App for Neighbourhood Watch",
          "Residents use a mobile app to submit GPS-tagged photo and video evidence of suspicious activity. " +
          "The community dashboard maps reports, sends push notifications to neighbours, and supports " +
          "anonymous submission."),

        p("c14",
          "Privacy-Preserving Location Sharing for Real-Time Crime Alerts",
          "We design a privacy-preserving, encrypted location-sharing scheme for real-time crime alerts. Users " +
          "receive push notifications about nearby incidents. An SOS panic button escalates to emergency " +
          "services with the user's geolocation."),

        p("c15",
          "Citizen-Sourced Crime Mapping with Predictive Hotspot Analytics",
          "Combining crowdsourced, geolocation-tagged reports with predictive machine learning, this system " +
          "produces crime hotspot forecasts. An analytics dashboard visualises statistics, and the mobile app " +
          "offers case tracking and notifications."),

        p("c16",
          "A Conversational SMS Assistant for Low-Bandwidth Crime Reporting",
          "For low-connectivity regions we build a chatbot SMS assistant using natural language dialogue and " +
          "multilingual support. Reports can be anonymous, are forwarded to local police, and receive a case " +
          "tracking reference for status follow-up.")
    );
}
