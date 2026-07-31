package fleettracker.fleettracker;


import javax.swing.*;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A Swing UI that visually demonstrates the exact MVC scenario JCIP
 * describes in §4.2.2 / §4.3.1, side by side, for all three trackers:
 *
 *   - Several "updater" threads (background workers) continuously write
 *     new vehicle positions, simulating GPS pings, driving each vehicle
 *     along the campus road loop (see Campus).
 *   - A Swing Timer repaints the panel roughly 30 times/sec — this plays
 *     the role of the "view thread" that reads positions to render them.
 *
 * Three selectable modes:
 *   MONITOR    -> MonitorVehicleTracker   (JCIP Listing 4.4 — single lock,
 *                 defensive deep copies, no internal state ever escapes)
 *   DELEGATING -> DelegatingVehicleTracker (JCIP §4.3.1 — no manual locking,
 *                 built from ConcurrentHashMap + SafePoint, both already
 *                 thread-safe on their own)
 *   UNSAFE     -> UnsafeVehicleTracker    (deliberately broken — plain
 *                 HashMap, live map exposed, torn x/y writes)
 *
 * In both safe modes, motion is always correct no matter the load. In
 * UNSAFE mode, watch the on-screen race counter — every "torn read" (an
 * inconsistent x,y pair briefly visible mid-update) is caught and flagged
 * live: a valid vehicle position always sits on the road, so a torn
 * (new-x, old-y) pair lands off the road and is drawn red.
 */
public class FleetTrackerGUI extends JFrame {

    private enum Mode { MONITOR, DELEGATING, UNSAFE }

    private static final String[] VEHICLE_IDS = {"TAXI-1", "TAXI-2", "TRUCK-1", "POLICE-1"};
    private static final Color[] COLORS = {
            new Color(0xE63946), new Color(0x2A9D8F), new Color(0xF4A261), new Color(0x264653)
    };
    private static final int WORLD_SIZE = 1000; // logical coordinate space

    private volatile Mode mode = Mode.MONITOR;
    private volatile int raceCount = 0;

    private MonitorVehicleTracker monitorTracker;
    private DelegatingVehicleTracker delegatingTracker;
    private UnsafeVehicleTracker unsafeTracker;

    private ExecutorService updaterPool;
    private final TrackPanel panel = new TrackPanel();
    private final JLabel statusLabel = new JLabel();
    private final JRadioButton monitorBtn = new JRadioButton("Monitor pattern (safe)", true);
    private final JRadioButton delegatingBtn = new JRadioButton("Delegating (safe)");
    private final JRadioButton unsafeBtn = new JRadioButton("Unsafe (broken)");

    public FleetTrackerGUI() {
        super("Fleet Vehicle Tracker — Thread Safety Demo (JCIP 4.2/4.3)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ButtonGroup group = new ButtonGroup();
        group.add(monitorBtn);
        group.add(delegatingBtn);
        group.add(unsafeBtn);
        monitorBtn.addActionListener(e -> switchMode(Mode.MONITOR));
        delegatingBtn.addActionListener(e -> switchMode(Mode.DELEGATING));
        unsafeBtn.addActionListener(e -> switchMode(Mode.UNSAFE));
        controls.add(new JLabel("Tracker:"));
        controls.add(monitorBtn);
        controls.add(delegatingBtn);
        controls.add(unsafeBtn);
        add(controls, BorderLayout.NORTH);
        add(panel, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        setSize(820, 680);
        setLocationRelativeTo(null);

        startMode(Mode.MONITOR);

        // The "view thread": a Swing Timer callback runs on the Event
        // Dispatch Thread and simply triggers a repaint. Swing itself
        // guarantees paintComponent() runs on the EDT, so this is our
        // single, consistent "reader" of vehicle state.
        Timer viewTimer = new Timer(33, e -> panel.repaint());
        viewTimer.start();

        updateStatusLabel();
    }

    private void switchMode(Mode newMode) {
        if (newMode == mode) return;
        stopUpdaters();
        mode = newMode;
        startMode(newMode);
        updateStatusLabel();
    }

    private void updateStatusLabel() {
        String text;
        switch (mode) {
            case MONITOR:
                text = "  MonitorVehicleTracker — one lock guards everything; every read/write "
                        + "goes through synchronized methods; callers only ever get deep copies.";
                break;
            case DELEGATING:
                text = "  DelegatingVehicleTracker — no manual locking; built from ConcurrentHashMap "
                        + "+ SafePoint, both already thread-safe on their own.";
                break;
            default:
                text = "  UnsafeVehicleTracker — plain HashMap, live map exposed, unsynchronized x/y "
                        + "writes.  Off-road (torn) reads detected: " + raceCount;
        }
        statusLabel.setText(text);
    }

    // ---------- Mode setup ----------

    private void startMode(Mode m) {
        raceCount = 0;
        switch (m) {
            case MONITOR -> startMonitorMode();
            case DELEGATING -> startDelegatingMode();
            case UNSAFE -> startUnsafeMode();
        }
    }

    // How often each vehicle advances to the next point on the road. Slower =
    // calmer, easier to watch and narrate; still plenty fast for the demo.
    private static final long UPDATE_INTERVAL_MS = 200;

    private void startMonitorMode() {
        Map<String, MutablePoint> initial = new HashMap<>();
        for (int v = 0; v < VEHICLE_IDS.length; v++) {
            MutablePoint p = new MutablePoint();
            p.x = Campus.startX(v);
            p.y = Campus.startY(v);
            initial.put(VEHICLE_IDS[v], p);
        }
        monitorTracker = new MonitorVehicleTracker(initial);

        updaterPool = Executors.newFixedThreadPool(VEHICLE_IDS.length);
        for (int v = 0; v < VEHICLE_IDS.length; v++) {
            String id = VEHICLE_IDS[v];
            int[] idx = { Campus.startIndex(v) };
            updaterPool.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    idx[0] = Campus.next(idx[0]);
                    monitorTracker.setLocation(id, Campus.routeX(idx[0]), Campus.routeY(idx[0]));
                    sleepQuietly(UPDATE_INTERVAL_MS);
                }
            });
        }
    }

    private void startDelegatingMode() {
        Map<String, SafePoint> initial = new HashMap<>();
        for (int v = 0; v < VEHICLE_IDS.length; v++)
            initial.put(VEHICLE_IDS[v], new SafePoint(Campus.startX(v), Campus.startY(v)));
        delegatingTracker = new DelegatingVehicleTracker(initial);

        updaterPool = Executors.newFixedThreadPool(VEHICLE_IDS.length);
        for (int v = 0; v < VEHICLE_IDS.length; v++) {
            String id = VEHICLE_IDS[v];
            int[] idx = { Campus.startIndex(v) };
            updaterPool.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    idx[0] = Campus.next(idx[0]);
                    delegatingTracker.setLocation(id, Campus.routeX(idx[0]), Campus.routeY(idx[0]));
                    sleepQuietly(UPDATE_INTERVAL_MS);
                }
            });
        }
    }

    private void startUnsafeMode() {
        unsafeTracker = new UnsafeVehicleTracker();
        for (int v = 0; v < VEHICLE_IDS.length; v++)
            unsafeTracker.addVehicle(VEHICLE_IDS[v], Campus.startX(v), Campus.startY(v));

        updaterPool = Executors.newFixedThreadPool(VEHICLE_IDS.length);
        for (int v = 0; v < VEHICLE_IDS.length; v++) {
            String id = VEHICLE_IDS[v];
            int[] idx = { Campus.startIndex(v) };
            updaterPool.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    idx[0] = Campus.next(idx[0]);
                    // The tracker writes x then y non-atomically, so a reader can still
                    // catch a torn (new-x, old-y) pair — which now lands OFF the road.
                    unsafeTracker.setLocation(id, Campus.routeX(idx[0]), Campus.routeY(idx[0]));
                    sleepQuietly(UPDATE_INTERVAL_MS);
                }
            });
        }
    }

    private void stopUpdaters() {
        if (updaterPool != null) updaterPool.shutdownNow();
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------- The "view": paints whichever tracker is currently active ----------

    private class TrackPanel extends JPanel {
        private final OsmMap map = new OsmMap(this::repaint);
        TrackPanel() {
            setBackground(Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();

            map.paint(g2, w, h);   // live OpenStreetMap backdrop, drawn before the vehicles

            switch (mode) {
                case MONITOR -> paintMonitor(g2, w, h);
                case DELEGATING -> paintDelegating(g2, w, h);
                case UNSAFE -> paintUnsafe(g2, w, h);
            }
            updateStatusLabel();
        }

        private void paintMonitor(Graphics2D g2, int w, int h) {
            Map<String, MutablePoint> snapshot = monitorTracker.getLocations();
            int i = 0;
            for (String id : VEHICLE_IDS) {
                MutablePoint p = snapshot.get(id);
                if (p != null) drawVehicle(g2, id, p.x, p.y, w, h, COLORS[i % COLORS.length], false);
                i++;
            }
        }

        private void paintDelegating(Graphics2D g2, int w, int h) {
            Map<String, SafePoint> live = delegatingTracker.getLocations();
            int i = 0;
            for (String id : VEHICLE_IDS) {
                SafePoint p = live.get(id);
                if (p != null) {
                    int[] xy = p.get(); // SafePoint guards x,y together — always a valid pair
                    drawVehicle(g2, id, xy[0], xy[1], w, h, COLORS[i % COLORS.length], false);
                }
                i++;
            }
        }

        private void paintUnsafe(Graphics2D g2, int w, int h) {
            // Reading the LIVE map directly, unsynchronized, on purpose —
            // this is exactly the bug UnsafeVehicleTracker has.
            Map<String, MutablePoint> live = unsafeTracker.getLocations();
            int i = 0;
            for (String id : VEHICLE_IDS) {
                MutablePoint p = live.get(id);
                if (p != null) {
                    int x = p.x, y = p.y; // two separate, unsynchronized reads
                    boolean torn = !Campus.isOnRoute(x, y); // a valid position is always on the road
                    if (torn) raceCount++;
                    drawVehicle(g2, id, x, y, w, h, COLORS[i % COLORS.length], torn);
                }
                i++;
            }
        }

        private void drawVehicle(Graphics2D g2, String id, int worldX, int worldY,
                                  int panelW, int panelH, Color color, boolean torn) {
            int px = (int) ((worldX / (double) WORLD_SIZE) * (panelW - 40)) + 20;
            int py = (int) ((worldY / (double) WORLD_SIZE) * (panelH - 40)) + 20;

            g2.setColor(torn ? Color.RED : color);
            int r = torn ? 14 : 10;
            g2.fillOval(px - r, py - r, r * 2, r * 2);

            if (torn) {
                g2.setStroke(new BasicStroke(2));
                g2.setColor(Color.RED);
                g2.drawOval(px - r - 4, py - r - 4, (r + 4) * 2, (r + 4) * 2);
            }

            g2.setColor(Color.BLACK);
            g2.drawString(id + (torn ? "  <-- TORN READ!" : ""), px + r + 4, py + 4);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FleetTrackerGUI().setVisible(true));
    }
}