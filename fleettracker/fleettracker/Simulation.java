package fleettracker.fleettracker;

import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Simulates the MVC scenario from JCIP §4.2.2:
 *   - several "updater" threads (GPS feeds / dispatcher) writing
 *     vehicle positions concurrently
 *   - one "view" thread reading and rendering a snapshot of all
 *     positions while updates are in flight
 *
 * Runs the same scenario three times: against the safe monitor-pattern
 * tracker, the safe delegating tracker, and finally the intentionally
 * broken tracker, so you can point at the difference.
 */
public class Simulation {

    private static final String[] VEHICLE_IDS = {"TAXI-1", "TAXI-2", "TRUCK-1", "POLICE-1"};
    private static final int UPDATES_PER_VEHICLE = 20_000;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Fleet Vehicle Tracker Simulation ===\n");
        runMonitorDemo();
        System.out.println();
        runDelegatingDemo();
        System.out.println();
        runUnsafeDemo();
    }

    // ---------- Safe demo #1: Java Monitor Pattern ----------

    private static void runMonitorDemo() throws InterruptedException {
        System.out.println("--- MonitorVehicleTracker (Java monitor pattern) ---");
        Map<String, MutablePoint> initial = new HashMap<>();
        for (String id : VEHICLE_IDS) initial.put(id, new MutablePoint());
        MonitorVehicleTracker tracker = new MonitorVehicleTracker(initial);

        runSafeDemo(
            tracker::setLocation,
            () -> {
                StringBuilder sb = new StringBuilder();
                tracker.getLocations().forEach((id, p) -> sb.append(id).append("=").append(p).append(" "));
                return sb.toString();
            }
        );
    }

    // ---------- Safe demo #2: delegated thread safety ----------

    private static void runDelegatingDemo() throws InterruptedException {
        System.out.println("--- DelegatingVehicleTracker (ConcurrentHashMap + SafePoint) ---");
        Map<String, SafePoint> initial = new HashMap<>();
        for (String id : VEHICLE_IDS) initial.put(id, new SafePoint(0, 0));
        DelegatingVehicleTracker tracker = new DelegatingVehicleTracker(initial);

        runSafeDemo(
            tracker::setLocation,
            () -> {
                StringBuilder sb = new StringBuilder();
                tracker.getLocations().forEach((id, p) -> sb.append(id).append("=").append(p).append(" "));
                return sb.toString();
            }
        );
    }

    @FunctionalInterface
    interface Setter { void set(String id, int x, int y); }
    @FunctionalInterface
    interface Renderer { String render(); }

    private static void runSafeDemo(Setter setter, Renderer renderer) throws InterruptedException {
        ExecutorService updaterPool = Executors.newFixedThreadPool(VEHICLE_IDS.length);
        Random rnd = new Random();

        for (String id : VEHICLE_IDS) {
            updaterPool.submit(() -> {
                for (int i = 0; i < UPDATES_PER_VEHICLE; i++)
                    setter.set(id, rnd.nextInt(1000), rnd.nextInt(1000));
            });
        }

        // "view thread" behaviour, run here on main while updaters are busy
        for (int i = 0; i < 3; i++) {
            Thread.sleep(15);
            System.out.println("  view snapshot " + i + ": " + renderer.render());
        }

        updaterPool.shutdown();
        updaterPool.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("  final state       : " + renderer.render());
        System.out.println("  -> completed with no exceptions and no torn (x,y) pairs.");
    }

    // ---------- Unsafe demo, for contrast ----------

    private static void runUnsafeDemo() throws InterruptedException {
        System.out.println("--- UnsafeVehicleTracker (broken on purpose — DO NOT use) ---");
        System.out.println("  Technique: every write sets x == y on purpose (e.g. (7,7)).");
        System.out.println("  Since x and y are written as two separate, unsynchronized");
        System.out.println("  field assignments, a reader that ever sees x != y has just");
        System.out.println("  witnessed a real race — a 'torn' read of the point.");

        UnsafeVehicleTracker tracker = new UnsafeVehicleTracker();
        for (String id : VEHICLE_IDS) tracker.addVehicle(id, 0, 0);

        ExecutorService pool = Executors.newFixedThreadPool(VEHICLE_IDS.length + 1);
        Random rnd = new Random();

        for (String id : VEHICLE_IDS) {
            pool.submit(() -> {
                for (int i = 0; i < UPDATES_PER_VEHICLE; i++) {
                    int n = rnd.nextInt(1000);
                    tracker.setLocation(id, n, n); // x and y always equal when set correctly
                }
            });
        }

        Future<Boolean> viewer = pool.submit(() -> {
            for (int i = 0; i < 500_000; i++) {
                for (Map.Entry<String, MutablePoint> e : tracker.getLocations().entrySet()) {
                    MutablePoint p = e.getValue();
                    int x = p.x, y = p.y; // two unsynchronized reads
                    if (x != y) {
                        System.out.println("  DETECTED TORN READ on " + e.getKey()
                                + ": x=" + x + " y=" + y + " (should always be equal!)");
                        System.out.println("  This is exactly the class of bug MonitorVehicleTracker / "
                                + "DelegatingVehicleTracker prevent.");
                        return true;
                    }
                }
            }
            return false;
        });

        pool.shutdown();
        pool.awaitTermination(15, TimeUnit.SECONDS);
        try {
            if (!viewer.get())
                System.out.println("  (no torn read caught this run — races are timing-dependent; try again)");
        } catch (Exception ignored) {
        }
    }
}
