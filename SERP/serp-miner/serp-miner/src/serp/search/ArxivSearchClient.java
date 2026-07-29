package serp.search;

import serp.model.Paper;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A live {@link SearchClient} backed by the public arXiv Atom API.
 *
 * <p>Chosen as the concrete "search engine" because it is free, needs no API key,
 * returns genuinely ranked results with titles + abstracts, and is designed to be
 * queried programmatically — so we can honour the SERP framing without scraping
 * Google (which blocks bots and would make the exercise about dodging captchas
 * rather than about concurrency).
 *
 * <p>Deliberately dependency-free: it uses {@link HttpClient} (JDK&nbsp;11+) and the
 * built-in DOM parser, so the whole project compiles with plain {@code javac} and
 * runs with plain {@code java} — no Maven/Gradle, no external jars.
 *
 * <p>Each call performs one blocking HTTP request and is therefore I/O-bound; the
 * miner runs many such calls on a thread pool, and a {@link java.util.concurrent.Semaphore}
 * in the miner bounds how many hit arXiv at once so we stay a polite client.
 */
public final class ArxivSearchClient implements SearchClient {

    private static final String ENDPOINT = "http://export.arxiv.org/api/query";

    private final HttpClient http;
    private final Duration requestTimeout;

    public ArxivSearchClient() {
        this(Duration.ofSeconds(20));
    }

    public ArxivSearchClient(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public List<Paper> search(String query, int maxResults) throws Exception {
        String q = URLEncoder.encode("all:" + query, StandardCharsets.UTF_8);
        String url = ENDPOINT + "?search_query=" + q
                + "&start=0&max_results=" + Math.max(1, maxResults)
                + "&sortBy=relevance&sortOrder=descending";

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "SERP-FeatureMiner/1.0 (student concurrency assignment)")
                .timeout(requestTimeout)
                .GET()
                .build();

        HttpResponse<byte[]> response =
                http.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException(
                    "arXiv returned HTTP " + response.statusCode() + " for query: " + query);
        }
        return parse(response.body(), query);
    }

    /** Parse the Atom feed. Namespace-unaware DOM keeps tag lookups simple. */
    private List<Paper> parse(byte[] xml, String query) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        // Harden the parser against XXE — good practice even for a trusted feed.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml));

        NodeList entries = doc.getElementsByTagName("entry");
        List<Paper> papers = new ArrayList<>(entries.getLength());
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            String rawId = text(entry, "id");
            String title = collapse(text(entry, "title"));
            String summary = collapse(text(entry, "summary"));
            int year = parseYear(text(entry, "published"));
            if (!title.isEmpty()) {
                papers.add(new Paper(rawId.isEmpty() ? "arxiv:" + i : rawId,
                        title, summary, rawId, year, "arxiv"));
            }
        }
        return papers;
    }

    private static String text(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) {
            return "";
        }
        Node node = list.item(0);
        return node.getTextContent() == null ? "" : node.getTextContent().trim();
    }

    /** arXiv wraps titles/abstracts over several lines; collapse whitespace. */
    private static String collapse(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    private static int parseYear(String published) {
        if (published != null && published.length() >= 4) {
            try {
                return Integer.parseInt(published.substring(0, 4));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return 0;
    }

    @Override
    public String name() {
        return "arxiv";
    }
}
