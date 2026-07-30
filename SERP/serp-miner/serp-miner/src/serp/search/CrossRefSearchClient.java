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
 * {@link SearchClient} backed by CrossRef. Excellent DOI-anchored metadata coverage;
 * abstracts are present for some works only, and when present are JATS-XML fragments
 * (e.g. {@code <jats:p>...</jats:p>}), so {@link #stripTags} reduces them to plain text.
 * Title-only papers still contribute whatever features their title exposes.
 */
public final class CrossRefSearchClient implements SearchClient {

    private static final String ENDPOINT = "https://api.crossref.org/works";
    private final Duration timeout;

    public CrossRefSearchClient() {
        this(Duration.ofSeconds(20));
    }

    public CrossRefSearchClient(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public List<Paper> search(String query, int maxResults) throws Exception {
        String url = ENDPOINT
                + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&rows=" + Math.min(100, Math.max(1, maxResults))
                + "&select=title,abstract,DOI,URL,issued"
                + "&mailto=student@example.edu";
        return parse(Http.getString(url, timeout));
    }

    /** Map a CrossRef works response body to papers. Visible for testing. */
    public static List<Paper> parse(String body) {
        Map<String, Object> root = Json.obj(Json.parse(body));
        Map<String, Object> message = Json.obj(root.get("message"));
        List<Object> items = Json.arr(message.get("items"));
        List<Paper> papers = new ArrayList<>(items.size());
        for (Object o : items) {
            Map<String, Object> item = Json.obj(o);
            List<Object> titles = Json.arr(item.get("title"));
            if (titles.isEmpty() || !(titles.get(0) instanceof String title) || title.isBlank()) {
                continue;
            }
            String doi = Json.str(item, "DOI");
            String url = Json.str(item, "URL");
            if (url.isBlank() && !doi.isBlank()) {
                url = "https://doi.org/" + doi;
            }
            String abstractText = stripTags(Json.str(item, "abstract"));
            int year = extractYear(item);
            papers.add(new Paper(doi.isBlank() ? url : doi, title, abstractText, url, year, "crossref"));
        }
        return papers;
    }

    /** issued.date-parts[0][0] is the year. */
    private static int extractYear(Map<String, Object> item) {
        Map<String, Object> issued = Json.obj(item.get("issued"));
        List<Object> dateParts = Json.arr(issued.get("date-parts"));
        if (!dateParts.isEmpty()) {
            List<Object> first = Json.arr(dateParts.get(0));
            if (!first.isEmpty() && first.get(0) instanceof Double d) {
                return (int) Math.round(d);
            }
        }
        return 0;
    }

    /** Remove JATS/XML tags and collapse whitespace. */
    static String stripTags(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    @Override
    public String name() {
        return "crossref";
    }
}
