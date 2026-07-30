package serp.search;

import serp.json.Json;
import serp.model.Paper;
import serp.net.Http;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link SearchClient} backed by the Semantic Scholar Graph API. Abstracts are
 * returned inline, which is convenient for feature extraction.
 *
 * <p>Endpoint: {@code /graph/v1/paper/search?query=...&fields=title,abstract,year,url}.
 * The public API is rate-limited without a key; a 429 surfaces as an exception that the
 * multi-source fan-out isolates, so a throttled Semantic Scholar never sinks the run.
 */
public final class SemanticScholarSearchClient implements SearchClient {

    private static final String ENDPOINT =
            "https://api.semanticscholar.org/graph/v1/paper/search";
    private final Duration timeout;

    public SemanticScholarSearchClient() {
        this(Duration.ofSeconds(20));
    }

    public SemanticScholarSearchClient(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public List<Paper> search(String query, int maxResults) throws Exception {
        String url = ENDPOINT
                + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&limit=" + Math.min(100, Math.max(1, maxResults))
                + "&fields=title,abstract,year,url";
        return parse(Http.getString(url, timeout));
    }

    /** Map a Semantic Scholar search response body to papers. Visible for testing. */
    public static List<Paper> parse(String body) {
        Map<String, Object> root = Json.obj(Json.parse(body));
        List<Object> data = Json.arr(root.get("data"));
        List<Paper> papers = new ArrayList<>(data.size());
        for (Object o : data) {
            Map<String, Object> item = Json.obj(o);
            String id = Json.str(item, "paperId");
            String title = Json.str(item, "title");
            if (title.isBlank()) {
                continue;
            }
            String abstractText = Json.str(item, "abstract"); // "" when null
            String pageUrl = Json.str(item, "url");
            if (pageUrl.isBlank() && !id.isBlank()) {
                pageUrl = "https://www.semanticscholar.org/paper/" + id;
            }
            papers.add(new Paper(id.isBlank() ? pageUrl : id, title, abstractText,
                    pageUrl, Json.num(item, "year"), "semanticscholar"));
        }
        return papers;
    }

    @Override
    public String name() {
        return "semanticscholar";
    }
}
