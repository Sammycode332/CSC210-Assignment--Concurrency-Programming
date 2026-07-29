package fleettracker.fleettracker;

import java.util.HashMap;
import java.util.Map;



/**
 * JCIP Listing 4.4 — Java Monitor Pattern.
 *
 * Thread safety strategy:
 *  1. The single intrinsic lock (`this`) guards ALL access to the
 *     `locations` map and everything reachable from it.
 *  2. The internal map and its MutablePoint values are NEVER published.
 *     Every method that hands data to a caller returns a deep copy.
 *
 * Because of rule 2, MonitorVehicleTracker is thread-safe even though
 * MutablePoint itself is not.
 */
public class MonitorVehicleTracker {
    private final Map<String, MutablePoint> locations;

    public MonitorVehicleTracker(Map<String, MutablePoint> locations) {
        this.locations = deepCopy(locations);
    }

    /** Returns a consistent snapshot of all vehicle locations. */
    public synchronized Map<String, MutablePoint> getLocations() {
        return deepCopy(locations);
    }

    /** Returns a copy of one vehicle's location, or null if unknown. */
    public synchronized MutablePoint getLocation(String id) {
        MutablePoint loc = locations.get(id);
        return loc == null ? null : new MutablePoint(loc);
    }

    /** Called by updater threads (GPS feed / dispatcher UI). */
    public synchronized void setLocation(String id, int x, int y) {
        MutablePoint loc = locations.get(id);
        if (loc == null)
            throw new IllegalArgumentException("No such vehicle: " + id);
        loc.x = x;
        loc.y = y;
    }

    private static Map<String, MutablePoint> deepCopy(Map<String, MutablePoint> m) {
        Map<String, MutablePoint> result = new HashMap<>();
        for (String id : m.keySet())
            result.put(id, new MutablePoint(m.get(id)));
        return result;
    }
}
