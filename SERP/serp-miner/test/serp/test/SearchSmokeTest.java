package serp.test;

import serp.model.Paper;
import serp.search.OfflineSearchClient;

import java.util.List;

/** Verifies the offline SERP source returns relevant, capped, well-formed results. */
public final class SearchSmokeTest {

    public static void main(String[] args) throws Exception {
        System.out.println("SearchSmokeTest");
        // zero latency so the test is fast
        OfflineSearchClient client = new OfflineSearchClient(0, 0);

        List<Paper> hits = client.search("crime reporting", 100);
        Check.that(!hits.isEmpty(), "query returns results");
        for (Paper p : hits) {
            Check.that(p.id() != null && !p.id().isBlank(), "result has an id");
            Check.that(!p.title().isBlank(), "result has a title");
        }

        List<Paper> capped = client.search("anonymous", 3);
        Check.that(capped.size() <= 3, "maxResults cap respected (<=3, got " + capped.size() + ")");

        // A nonsense query still returns a non-empty fallback so demos never break.
        List<Paper> fallback = client.search("zzzqqq-nonexistent-term", 5);
        Check.that(!fallback.isEmpty(), "nonsense query falls back to a non-empty page");

        Check.equal(client.name(), "offline", "client name");
        Check.done("SearchSmokeTest");
    }

    private SearchSmokeTest() {}
}
