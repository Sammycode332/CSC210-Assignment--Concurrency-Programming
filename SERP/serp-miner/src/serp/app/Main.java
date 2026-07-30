package serp.app;

import serp.mine.MiningConfig;
import serp.mine.SearchService;
import serp.mine.TopicResult;
import serp.search.SearchClient;
import serp.search.Sources;
import serp.viz.ConsoleReport;

import java.util.List;

/**
 * Console entry point (no display needed). Mines two topics concurrently and prints the
 * four "pages" as text: top papers + feature ranking for each topic. The GUI equivalent
 * is {@link SearchApp}.
 *
 * <pre>
 *   java -cp out serp.app.Main [--online|--offline] [--max N] ["topic 1"] ["topic 2"]
 * </pre>
 */
public final class Main {

    public static void main(String[] args) {
        boolean online = false;
        int max = 10;
        String[] topics = {"crime reporting system", "crowdsourced crime reporting app"};
        int t = 0;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--online" -> online = true;
                case "--offline" -> online = false;
                case "--max" -> max = Integer.parseInt(args[++i]);
                default -> {
                    if (t < topics.length) {
                        topics[t++] = args[i];
                    }
                }
            }
        }

        List<SearchClient> sources = online ? Sources.online() : Sources.offline();
        MiningConfig config = new MiningConfig(
                MiningConfig.defaults(sources.size()).poolSize(), 6, max, 30, 15);

        System.out.printf("Searching two topics concurrently via %s (pool=%d)...%n",
                online ? "live sources" : "offline corpus", config.poolSize());

        try (SearchService service = new SearchService(config, sources)) {
            List<TopicResult> results = service.mineBoth(topics[0], topics[1]);
            ConsoleReport.printAll(results);
        }
    }

    private Main() {}
}
