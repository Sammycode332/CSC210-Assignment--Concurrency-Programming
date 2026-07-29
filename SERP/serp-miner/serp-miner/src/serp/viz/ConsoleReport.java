package serp.viz;

import serp.mine.FeatureCount;
import serp.mine.MiningResult;

/**
 * Renders a {@link MiningResult} as text: a ranked table with an inline ASCII bar
 * chart plus a run/timing summary. This is the headless counterpart of the Swing
 * chart — it works with no display, which is what makes the pipeline testable and
 * benchmarkable in a terminal / CI environment.
 */
public final class ConsoleReport {

    private ConsoleReport() {}

    public static void print(MiningResult r) {
        System.out.println();
        System.out.println("==================================================================");
        System.out.println(" CRIME-REPORTING SYSTEM FEATURES  (ranked by number of systems)");
        System.out.println("==================================================================");
        System.out.printf(" source=%s   papers=%d   pool=%d threads%n",
                r.source(), r.papersFound(), r.poolSize());
        System.out.printf(" timing: search=%dms  extract=%dms  total=%dms%n",
                r.searchMillis(), r.extractMillis(), r.totalMillis());
        System.out.println(" note: one paper is counted as one system.");
        System.out.println("------------------------------------------------------------------");

        long max = Math.max(1, r.maxCount());
        int rank = 1;
        for (FeatureCount fc : r.ranked()) {
            int barLen = (int) Math.round((fc.systemCount() / (double) max) * 28);
            String bar = "#".repeat(Math.max(0, barLen));
            System.out.printf(" %2d. %-34s %3d  %s%n",
                    rank++, truncate(fc.feature().label(), 34), fc.systemCount(), bar);
        }
        System.out.println("==================================================================");
    }

    private static String truncate(String s, int width) {
        return s.length() <= width ? s : s.substring(0, width - 1) + "\u2026";
    }
}
