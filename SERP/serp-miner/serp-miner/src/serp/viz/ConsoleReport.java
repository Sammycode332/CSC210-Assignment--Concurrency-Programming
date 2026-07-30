package serp.viz;

import serp.mine.FeatureCount;
import serp.mine.TopicResult;
import serp.model.Paper;

import java.util.List;

/**
 * Headless renderer for {@link TopicResult}s: the top-papers list and the feature
 * ranking (with an inline ASCII bar chart). Works with no display, which is what makes
 * the pipeline testable and benchmarkable from a terminal.
 */
public final class ConsoleReport {

    private ConsoleReport() {}

    /** Print both topics: papers page then visualisation page, for each. */
    public static void printAll(List<TopicResult> results) {
        for (TopicResult r : results) {
            printPapers(r);
            printFeatures(r);
        }
    }

    public static void printPapers(TopicResult r) {
        System.out.println();
        System.out.println("================================================================");
        System.out.printf(" TOP %d PAPERS  -  topic: \"%s\"%n", r.topPapers().size(), r.topic());
        System.out.println("================================================================");
        if (!r.ok()) {
            System.out.println(" (failed: " + r.error() + ")");
            return;
        }
        System.out.printf(" sources=%s%n", r.sourcesLabel());
        int n = 1;
        for (Paper p : r.topPapers()) {
            System.out.printf(" %2d. %s%n", n++, p.title());
            System.out.printf("     %s%s%n", p.source(), p.year() > 0 ? ", " + p.year() : "");
            if (!p.url().isBlank()) {
                System.out.printf("     %s%n", p.url());
            }
        }
    }

    public static void printFeatures(TopicResult r) {
        System.out.println();
        System.out.println("----------------------------------------------------------------");
        System.out.printf(" FEATURES BY NUMBER OF PAPERS  -  topic: \"%s\"%n", r.topic());
        System.out.println("----------------------------------------------------------------");
        if (!r.ok()) {
            System.out.println(" (failed: " + r.error() + ")");
            return;
        }
        System.out.printf(" papers=%d  pool=%d  timing: search=%dms extract=%dms total=%dms%n",
                r.topPapers().size(), r.poolSize(), r.searchMillis(), r.extractMillis(), r.totalMillis());
        long max = Math.max(1, r.maxCount());
        int rank = 1;
        for (FeatureCount fc : r.ranked()) {
            int barLen = (int) Math.round((fc.systemCount() / (double) max) * 26);
            System.out.printf(" %2d. %-34s %3d  %s%n",
                    rank++, fc.feature().label(), fc.systemCount(), "#".repeat(Math.max(0, barLen)));
        }
    }
}
