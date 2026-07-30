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
 * {@link SearchClient} backed by OpenAlex. Very reliable and keyless, with strong
 * social-science / applied coverage (good for crime-reporting work).
 *
 * <p>OpenAlex stores each abstract as an <em>inverted index</em> (word &rarr; list of
 * positions) rather than plain text, so {@link #reconstructAbstract} rebuilds the
 * running text by placing each word at its position(s). {@code mailto} is sent to use
 * the polite pool.
 */
public final class OpenAlexSearchClient implements SearchClient {

    private static final String ENDPOINT = "https://api.openalex.org/works";
    private final Duration timeout;

    public OpenAlexSearchClient() {
        this(Duration.ofSeconds(20));
    }

    public OpenAlexSearchClient(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public List<Paper> search(String query, int maxResults) throws Exception {
        String url = ENDPOINT
                + "?search=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&per_page=" + Math.min(200, Math.max(1, maxResults))
                + "&mailto=student@example.edu";
        return parse(Http.getString(url, timeout));
    }

    /** Map an OpenAlex works response body to papers. Visible for testing. */
    public static List<Paper> parse(String body) {
        Map<String, Object> root = Json.obj(Json.parse(body));
        List<Object> results = Json.arr(root.get("results"));
        List<Paper> papers = new ArrayList<>(results.size());
        for (Object o : results) {
            Map<String, Object> w = Json.obj(o);
            String title = Json.str(w, "title");
            if (title.isBlank()) {
                title = Json.str(w, "display_name");
            }
            if (title.isBlank()) {
                continue;
            }
            String id = Json.str(w, "id");
            String doi = Json.str(w, "doi");
            String url = !doi.isBlank() ? doi : id;
            String abstractText = reconstructAbstract(Json.obj(w.get("abstract_inverted_index")));
            papers.add(new Paper(id.isBlank() ? url : id, title, abstractText,
                    url, Json.num(w, "publication_year"), "openalex"));
        }
        return papers;
    }

    /** Rebuild plain-text abstract from OpenAlex's {word: [positions]} index. */
    static String reconstructAbstract(Map<String, Object> invertedIndex) {
        if (invertedIndex.isEmpty()) {
            return "";
        }
        int maxPos = -1;
        for (Object positions : invertedIndex.values()) {
            for (Object p : Json.arr(positions)) {
                if (p instanceof Double d) {
                    maxPos = Math.max(maxPos, (int) Math.round(d));
                }
            }
        }
        String[] slots = new String[maxPos + 1];
        for (Map.Entry<String, Object> e : invertedIndex.entrySet()) {
            for (Object p : Json.arr(e.getValue())) {
                if (p instanceof Double d) {
                    int pos = (int) Math.round(d);
                    if (pos >= 0 && pos < slots.length) {
                        slots[pos] = e.getKey();
                    }
                }
            }
        }
        StringBuilder b = new StringBuilder();
        for (String w : slots) {
            if (w != null) {
                if (b.length() > 0) {
                    b.append(' ');
                }
                b.append(w);
            }
        }
        return b.toString();
    }

    @Override
    public String name() {
        return "openalex";
    }
}
