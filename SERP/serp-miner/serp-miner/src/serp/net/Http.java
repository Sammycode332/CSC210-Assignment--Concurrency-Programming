package serp.net;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin wrapper over the JDK {@link HttpClient} for simple GET requests. Shared by the
 * JSON-based search clients so timeout / User-Agent / status-check policy lives in one
 * place. The single {@link HttpClient} is thread-safe and reused across all requests
 * (its own connection pool is what actually lets many concurrent source calls fly).
 */
public final class Http {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final String UA =
            "SERP-FeatureMiner/2.0 (student concurrency assignment; mailto:student@example.edu)";

    private Http() {}

    /** Blocking GET returning the body as a String; throws on non-2xx. */
    public static String getString(String url, Duration timeout) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .timeout(timeout)
                .GET()
                .build();
        HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + res.statusCode() + " from " + host(url));
        }
        return res.body();
    }

    private static String host(String url) {
        try {
            return URI.create(url).getHost();
        } catch (RuntimeException e) {
            return url;
        }
    }
}
