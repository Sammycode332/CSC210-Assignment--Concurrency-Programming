package serp.test;

import serp.model.Paper;
import serp.search.ArxivSearchClient;
import serp.search.CrossRefSearchClient;
import serp.search.OpenAlexSearchClient;
import serp.search.SemanticScholarSearchClient;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Verifies each source client maps a realistic (trimmed) API response to {@link Paper}s
 * correctly. This runs entirely offline against embedded sample payloads, so the parsing
 * / abstract-reconstruction / tag-stripping logic is covered even though the live
 * endpoints are unreachable from a sandbox. (It does NOT prove the endpoints are up —
 * only that a well-formed response is mapped correctly.)
 */
public final class SourceMappingSmokeTest {

    public static void main(String[] args) throws Exception {
        System.out.println("SourceMappingSmokeTest");
        semanticScholar();
        openAlex();
        crossRef();
        arxiv();
        Check.done("SourceMappingSmokeTest");
    }

    private static void semanticScholar() {
        String body = "{ \"total\": 2, \"data\": ["
                + "{ \"paperId\": \"abc123\", \"title\": \"Anonymous GPS Crime Reporting App\","
                + "  \"abstract\": \"A mobile app with gps and anonymous reporting.\","
                + "  \"year\": 2021, \"url\": \"http://example.org/abc123\" },"
                + "{ \"paperId\": \"zzz\", \"title\": \"\", \"abstract\": null } ] }";
        List<Paper> papers = SemanticScholarSearchClient.parse(body);
        Check.equal(papers.size(), 1, "S2: blank-title paper skipped");
        Paper p = papers.get(0);
        Check.equal(p.title(), "Anonymous GPS Crime Reporting App", "S2: title mapped");
        Check.equal(p.year(), 2021, "S2: year mapped");
        Check.equal(p.source(), "semanticscholar", "S2: source tag");
        Check.that(p.summary().contains("gps"), "S2: abstract mapped");
    }

    private static void openAlex() {
        // abstract_inverted_index: {word: [positions]} -> "Crowdsourced crime mapping system"
        String body = "{ \"results\": [ {"
                + " \"id\": \"https://openalex.org/W1\","
                + " \"title\": \"Crowdsourced Crime Mapping\","
                + " \"publication_year\": 2020,"
                + " \"doi\": \"https://doi.org/10.1/x\","
                + " \"abstract_inverted_index\": { \"Crowdsourced\": [0], \"crime\": [1],"
                + "   \"mapping\": [2], \"system\": [3] } } ] }";
        List<Paper> papers = OpenAlexSearchClient.parse(body);
        Check.equal(papers.size(), 1, "OpenAlex: one work mapped");
        Paper p = papers.get(0);
        Check.equal(p.title(), "Crowdsourced Crime Mapping", "OpenAlex: title mapped");
        Check.equal(p.year(), 2020, "OpenAlex: year mapped");
        Check.equal(p.summary(), "Crowdsourced crime mapping system",
                "OpenAlex: abstract reconstructed from inverted index in order");
        Check.equal(p.url(), "https://doi.org/10.1/x", "OpenAlex: doi used as url");
        Check.equal(p.source(), "openalex", "OpenAlex: source tag");
    }

    private static void crossRef() {
        String body = "{ \"message\": { \"items\": [ {"
                + " \"title\": [ \"Real-time Emergency Reporting\" ],"
                + " \"abstract\": \"<jats:p>Uses <jats:italic>push</jats:italic> notifications.</jats:p>\","
                + " \"DOI\": \"10.5/y\", \"URL\": \"http://doi.org/10.5/y\","
                + " \"issued\": { \"date-parts\": [ [ 2019, 5, 1 ] ] } } ] } }";
        List<Paper> papers = CrossRefSearchClient.parse(body);
        Check.equal(papers.size(), 1, "CrossRef: one item mapped");
        Paper p = papers.get(0);
        Check.equal(p.title(), "Real-time Emergency Reporting", "CrossRef: title[0] mapped");
        Check.equal(p.year(), 2019, "CrossRef: year from date-parts");
        Check.equal(p.summary(), "Uses push notifications.", "CrossRef: JATS tags stripped");
        Check.equal(p.source(), "crossref", "CrossRef: source tag");
    }

    private static void arxiv() throws Exception {
        String atom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\">"
                + "<entry>"
                + "<id>http://arxiv.org/abs/2101.00001v1</id>"
                + "<title>Deep Learning for Crime Hotspot Prediction</title>"
                + "<summary>We use a neural network and gps data for prediction.</summary>"
                + "<published>2021-01-01T00:00:00Z</published>"
                + "</entry>"
                + "</feed>";
        List<Paper> papers = ArxivSearchClient.parse(atom.getBytes(StandardCharsets.UTF_8));
        Check.equal(papers.size(), 1, "arXiv: one entry parsed");
        Paper p = papers.get(0);
        Check.equal(p.title(), "Deep Learning for Crime Hotspot Prediction", "arXiv: title mapped");
        Check.equal(p.source(), "arxiv", "arXiv: source tag");
        Check.that(p.summary().contains("neural network"), "arXiv: summary mapped");
    }

    private SourceMappingSmokeTest() {}
}
