package fleettracker.fleettracker;

/**
 * JCIP Listing 4.5.
 *
 * NOT thread-safe on its own: x and y can be read/written independently by
 * different threads. It's only safe here because MonitorVehicleTracker
 * never lets a reference to a "live" MutablePoint escape — every point
 * handed to a caller is a defensive copy.
 */
public class MutablePoint {
    public int x, y;

    public MutablePoint() {
        x = 0;
        y = 0;
    }

    public MutablePoint(MutablePoint p) {
        this.x = p.x;
        this.y = p.y;
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
