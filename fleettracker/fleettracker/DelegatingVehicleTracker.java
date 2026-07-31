package fleettracker.fleettracker;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JCIP §4.3.1 — thread safety by DELEGATION rather than manual locking.
 *
 * Instead of one big lock, we lean on two already-thread-safe building
 * blocks:
 *   - ConcurrentHashMap for the map itself (safe concurrent get/put)
 *   - SafePoint for the values (safe concurrent get/set of x,y together)
 *
 * No `synchronized` appears anywhere in this class. Because both parts
 * it's built from are independently thread-safe and have no invariants
 * that span the two, composing them is safe "for free". This also
 * avoids the copy-on-every-read cost of the monitor version, at the
 * price of getLocations() being a live (if unmodifiable) view rather
 * than a frozen snapshot.
 */
public class DelegatingVehicleTracker {
    private final ConcurrentHashMap<String, SafePoint> locations;
    private final Map<String, SafePoint> unmodifiableMap;

    public DelegatingVehicleTracker(Map<String, SafePoint> points) {
        locations = new ConcurrentHashMap<>(points);
        unmodifiableMap = Collections.unmodifiableMap(locations);
    }

    /** Live (read-only) view — reflects updates as they happen. */
    public Map<String, SafePoint> getLocations() {
        return unmodifiableMap;
    }

    public SafePoint getLocation(String id) {
        return locations.get(id);
    }

    public void setLocation(String id, int x, int y) {
        if (!locations.containsKey(id))
            throw new IllegalArgumentException("No such vehicle: " + id);
        locations.get(id).set(x, y);
    }
}

