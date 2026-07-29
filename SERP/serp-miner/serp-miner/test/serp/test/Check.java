package serp.test;

/** Minimal assertion helpers so the smoke tests stay dependency-free. */
final class Check {

    private static int passed = 0;

    private Check() {}

    static void that(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        passed++;
        System.out.println("  ok - " + message);
    }

    static void equal(Object actual, Object expected, String message) {
        that(java.util.Objects.equals(actual, expected),
                message + " (expected=" + expected + ", actual=" + actual + ")");
    }

    static void done(String suite) {
        System.out.println(suite + ": all " + passed + " checks passed.\n");
        passed = 0;
    }
}
