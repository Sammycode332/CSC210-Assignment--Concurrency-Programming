package serp.app;

import serp.extract.FeatureExtractor;
import serp.mine.FeatureMiner;
import serp.mine.MiningConfig;
import serp.mine.MiningResult;
import serp.search.ArxivSearchClient;
import serp.search.OfflineSearchClient;
import serp.search.SearchClient;
import serp.viz.ChartWindow;
import serp.viz.ConsoleReport;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point. Wires a {@link SearchClient} (live arXiv or offline corpus) to the
 * {@link FeatureMiner} and renders the ranked features to the console and, when a
 * display is available, to a Swing bar chart.
 *
 * <pre>
 *   java -cp out serp.app.Main [options] [query ...]
 *
 *   --online            use the live arXiv backend (needs internet)
 *   --offline           use the built-in corpus (default; always works)
 *   --pool N            worker threads (default: JCiP I/O formula, capped at #queries)
 *   --max N             max results requested per query (default 20)
 *   --no-gui            skip the Swing chart (console only)
 *   query ...           one or more queries; overrides the default crime-reporting set
 * </pre>
 */
public final class Main {

    public static void main(String[] args) {
        boolean online = false;
        boolean noGui = false;
        Integer pool = null;
        int max = 20;
        List<String> queries = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--online" -> online = true;
                case "--offline" -> online = false;
                case "--no-gui" -> noGui = true;
                case "--pool" -> pool = Integer.parseInt(args[++i]);
                case "--max" -> max = Integer.parseInt(args[++i]);
                default -> queries.add(args[i]);
            }
        }

        SearchClient client = online ? new ArxivSearchClient() : new OfflineSearchClient();

        MiningConfig config = MiningConfig.defaults();
        if (!queries.isEmpty()) {
            config = config.withQueries(queries);
        }
        if (pool != null) {
            config = config.withPoolSize(pool);
        }
        config = new MiningConfig(config.queries(), max, config.poolSize(),
                config.maxConcurrentRequests(), config.searchTimeoutSeconds(),
                config.extractTimeoutSeconds());

        System.out.printf("Mining features via '%s' backend, %d queries, pool=%d ...%n",
                client.name(), config.queries().size(), config.poolSize());

        FeatureMiner miner = new FeatureMiner(client, new FeatureExtractor(), config);
        MiningResult result = miner.mine();

        ConsoleReport.print(result);

        if (!noGui && !GraphicsEnvironment.isHeadless()) {
            ChartWindow.show(result);
        } else if (!noGui) {
            System.out.println("(no display detected \u2014 skipping the Swing chart; console report above)");
        }
    }

    private Main() {}
}
