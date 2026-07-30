package serp.test;

import serp.search.Relevance;

/** Unit tests for the query-relevance scorer that drives the "most relevant" ranking. */
public final class RelevanceSmokeTest {

    public static void main(String[] args) {
        System.out.println("RelevanceSmokeTest");
        String q = "crime reporting";

        // Title match beats an off-topic paper.
        double onTopic = Relevance.score(q, "Crime Reporting Mobile System",
                "Crime Reporting Mobile System a system for citizens to report crime");
        double offTopic = Relevance.score(q, "Weather Forecasting with Satellites",
                "Weather Forecasting with Satellites predicting rain and sunshine");
        Check.that(onTopic > offTopic, "on-topic paper outranks off-topic paper");
        Check.that(offTopic == 0.0, "paper with no query terms scores zero");

        // Covering more query terms beats covering fewer.
        double both = Relevance.score(q, "Crime reporting tool", "Crime reporting tool");
        double one = Relevance.score(q, "Crime analytics", "Crime analytics dashboard");
        Check.that(both > one, "covering both query terms beats covering one");

        // Exact phrase in the title beats the same terms scattered.
        double phrase = Relevance.score(q, "Crime reporting", "Crime reporting");
        double scattered = Relevance.score(q, "Reporting incidents of crime",
                "Reporting incidents of crime");
        Check.that(phrase > scattered, "exact-phrase title gets a bonus over scattered terms");

        // Title weighted more heavily than body.
        double inTitle = Relevance.score(q, "Crime reporting", "unrelated text here");
        double inBody = Relevance.score(q, "unrelated title", "this discusses crime reporting");
        Check.that(inTitle > inBody, "match in title beats match in body");

        // Empty / stopword-only query is safe.
        Check.that(Relevance.score("the of and", "anything", "anything") == 0.0,
                "stopword-only query scores zero");

        Check.done("RelevanceSmokeTest");
    }

    private RelevanceSmokeTest() {}
}
