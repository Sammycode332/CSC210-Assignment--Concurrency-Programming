package fleettracker.fleettracker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The shared campus layout: one road loop, the buildings and water around it, and
 * the world-to-panel transform. Both the map (which draws it) and the updater
 * threads (which move vehicles along it) read from here, so the cars sit exactly on
 * the drawn road.
 *
 * <p>Coordinates are a logical 0..1000 "world" space, transformed to panel pixels
 * with the same formula the GUI already uses for vehicles, so everything lines up.
 *
 * <p>This is a view/model helper only -- it holds no vehicle state and does no
 * locking, so it has no bearing on the thread-safety demo.
 */
public final class Campus {

    private Campus() {}

    public static final int WORLD = 1000;

    // ----- world -> panel transform (identical to the GUI's vehicle mapping) -----

    public static int px(double worldX, int panelW) {
        return (int) ((worldX / (double) WORLD) * (panelW - 40)) + 20;
    }

    public static int py(double worldY, int panelH) {
        return (int) ((worldY / (double) WORLD) * (panelH - 40)) + 20;
    }

    // --------------------------- the road loop ---------------------------

    /**
     * Anchor points of the loop, clockwise. No two consecutive anchors share an x
     * or a y, which matters for the unsafe demo: it guarantees consecutive road
     * points differ in both coordinates, so a torn (new-x, old-y) read always lands
     * off the road and gets caught.
     */
    private static final double[][] ANCHORS = {
            { 90, 250}, {260, 300}, {450, 330}, {640, 300}, {770, 380},
            {800, 540}, {690, 650}, {500, 700}, {300, 690}, {150, 560}, {100, 400},
    };

    private static final int[] ROUTE_X;
    private static final int[] ROUTE_Y;
    private static final Set<Long> ROUTE_SET = new HashSet<>();

    /** Landmarks each vehicle starts nearest, in VEHICLE_IDS order (TAXI-1, TAXI-2, TRUCK-1, POLICE-1). */
    private static final double[][] START_NEAR = {
            { 90, 250},   // TAXI-1  -> Main Gate
            {300, 690},   // TAXI-2  -> halls (bottom)
            {450, 330},   // TRUCK-1 -> faculties (top)
            {770, 380},   // POLICE-1-> library (right)
    };
    private static final int[] START_INDEX;

    static {
        List<int[]> pts = new ArrayList<>();
        double spacing = 8.0;
        int n = ANCHORS.length;
        int[] last = null;
        for (int a = 0; a < n; a++) {
            double[] p = ANCHORS[a];
            double[] q = ANCHORS[(a + 1) % n];
            double dx = q[0] - p[0], dy = q[1] - p[1];
            double len = Math.hypot(dx, dy);
            int steps = Math.max(2, (int) Math.round(len / spacing));
            int sx = dx >= 0 ? 1 : -1, sy = dy >= 0 ? 1 : -1;
            for (int s = 0; s < steps; s++) {   // include p, exclude q (q is next segment's p)
                double t = s / (double) steps;
                int cx = (int) Math.round(p[0] + dx * t);
                int cy = (int) Math.round(p[1] + dy * t);
                // Force a change in both coordinates from the previous point, so a torn
                // (new-x, old-y) read can never coincide with a real road point.
                if (last != null) {
                    if (cx == last[0]) cx += sx;
                    if (cy == last[1]) cy += sy;
                }
                int[] pt = {cx, cy};
                pts.add(pt);
                last = pt;
            }
        }
        ROUTE_X = new int[pts.size()];
        ROUTE_Y = new int[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            ROUTE_X[i] = pts.get(i)[0];
            ROUTE_Y[i] = pts.get(i)[1];
            ROUTE_SET.add(key(ROUTE_X[i], ROUTE_Y[i]));
        }

        START_INDEX = new int[START_NEAR.length];
        for (int i = 0; i < START_NEAR.length; i++) START_INDEX[i] = nearestIndex(START_NEAR[i][0], START_NEAR[i][1]);
    }

    private static long key(int x, int y) {
        return (long) x * 100_000L + y;
    }

    private static int nearestIndex(double wx, double wy) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < ROUTE_X.length; i++) {
            double d = Math.hypot(ROUTE_X[i] - wx, ROUTE_Y[i] - wy);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    public static int routeSize()          { return ROUTE_X.length; }
    public static int routeX(int i)         { return ROUTE_X[i]; }
    public static int routeY(int i)         { return ROUTE_Y[i]; }
    public static int next(int i)           { return (i + 1) % ROUTE_X.length; }

    public static int startIndex(int vehicle) { return START_INDEX[vehicle]; }
    public static int startX(int vehicle)     { return ROUTE_X[START_INDEX[vehicle]]; }
    public static int startY(int vehicle)     { return ROUTE_Y[START_INDEX[vehicle]]; }

    /** True when (x, y) is exactly a point on the road -- the invariant a valid vehicle position keeps. */
    public static boolean isOnRoute(int x, int y) {
        return ROUTE_SET.contains(key(x, y));
    }

    // --------------------------- static scenery ---------------------------

    /** A labelled building block in world coordinates: {x, y, w, h}. */
    public static final class Building {
        public final double x, y, w, h;
        public final String name;
        public Building(double x, double y, double w, double h, String name) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.name = name;
        }
    }

    public static final Building[] BUILDINGS = {
            new Building(300, 120, 150, 75, "Faculty of Engineering"),
            new Building(520, 140, 140, 75, "Faculty of Science"),
            new Building(690, 205, 120, 70, "Nnamdi Azikiwe Library"),
            new Building(395, 405, 120, 95, "Senate Building"),
            new Building(250, 450, 110, 70, "Jaja Hall"),
            new Building(400, 560, 110, 65, "Moremi Hall"),
            new Building(640, 495, 100, 60, "DLI"),
    };

    /** Sports field, world {x, y, w, h}. */
    public static final double[] FIELD = {40, 770, 200, 120};

    /** Lagoon outline in world coordinates (polygon). */
    public static final double[][] WATER = {
            {1000, 240}, {830, 430}, {890, 660}, {680, 760}, {560, 1000}, {1000, 1000},
    };
}