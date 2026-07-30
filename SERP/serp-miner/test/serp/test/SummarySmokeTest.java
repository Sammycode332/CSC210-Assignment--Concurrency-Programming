package serp.test;

import serp.mine.FeatureCount;
import serp.mine.Summarizer;
import serp.mine.TopicSummary;
import serp.model.Feature;
import serp.model.Paper;

import java.util.List;

/** Tests the extractive summary: overview facts + on-topic key-point selection. */
public final class SummarySmokeTest {

    public static void main(String[] args) {
        System.out.println("SummarySmokeTest");

        List<Paper> papers = List.of(
                new Paper("p1", "P1",
                        "Crowdsourced crime reporting lets citizens submit incident reports. "
                        + "The mobile app supports anonymous submission and geolocation tagging.",
                        "http://x/1", 2019, "offline"),
                new Paper("p2", "P2",
                        "A crowdsourced crime reporting platform aggregates citizen reports with geolocation. "
                        + "Weather was sunny during the pilot deployment in the city park.",
                        "http://x/2", 2021, "offline"),
                new Paper("p3", "P3",
                        "Citizens submit crime reports through a mobile application with anonymous options and geolocation.",
                        "http://x/3", 2020, "offline"));

        List<FeatureCount> ranked = List.of(
                new FeatureCount(Feature.CROWDSOURCING, 3),
                new FeatureCount(Feature.GEOLOCATION, 3));

        TopicSummary s = Summarizer.summarize("crime reporting", papers, ranked);

        Check.that(s.overview().contains("3 papers"), "overview reports paper count");
        Check.that(s.overview().contains("2019") && s.overview().contains("2021"),
                "overview reports year span");
        Check.that(s.overview().contains("Recurring capabilities"),
                "overview ties back to the feature ranking");
        Check.that(!s.keyPoints().isEmpty(), "produces key points");

        boolean weatherLeaked = false;
        for (TopicSummary.KeyPoint kp : s.keyPoints()) {
            Check.that(kp.paperRank() >= 1 && kp.paperRank() <= 3, "key point rank in range");
            if (kp.text().toLowerCase().contains("sunny")) {
                weatherLeaked = true;
            }
        }
        Check.that(!weatherLeaked, "off-topic (weather) sentence not selected over on-topic ones");

        Check.done("SummarySmokeTest");
    }

    private SummarySmokeTest() {}
}
