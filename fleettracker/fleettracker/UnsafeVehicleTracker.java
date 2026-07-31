package fleettracker.fleettracker;

import java.util.HashMap;
import java.util.Map;



/**
 * DELIBERATELY BROKEN — for demonstration only.
 *
 * Problems:
 *   1. Uses a plain HashMap (not thread-safe for concurrent structural
 *      modification / iteration).
 *   2. getLocations() publishes the live internal map, not a copy — a
 *      caller iterating it can collide with an updater thread mutating
 *      it, or the map's own writes can race and corrupt its internal
 *      linked structure.
 *   3. setLocation() mutates a shared MutablePoint's x and y as two
 *      separate, unsynchronized writes.
 */
public class UnsafeVehicleTracker {
    private final Map<String, MutablePoint> locations = new HashMap<>();

    public void addVehicle(String id, int x, int y) {
        MutablePoint p = new MutablePoint();
        p.x = x;
        p.y = y;
        locations.put(id, p);
    }

    public Map<String, MutablePoint> getLocations() {
        return locations; // BUG: publishes the live, mutable map
    }

    public void setLocation(String id, int x, int y) {
        MutablePoint loc = locations.get(id);
        loc.x = x; // BUG: two separate unsynchronized writes
        loc.y = y;
    }
}
