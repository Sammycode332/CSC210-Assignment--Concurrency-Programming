package fleettracker.fleettracker;

/**
 * JCIP §4.3.1 — a mutable, thread-safe Point, guarded by its own lock.
 * get()/set() are atomic w.r.t. each other, so x and y are always read
 * or written together — no torn reads.
 */
public class SafePoint {
    private int x, y;

    public SafePoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public SafePoint(SafePoint p) {
        this(p.get());
    }

    private SafePoint(int[] a) {
        this(a[0], a[1]);
    }

    public synchronized int[] get() {
        return new int[]{x, y};
    }

    public synchronized void set(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        int[] xy = get();
        return "(" + xy[0] + "," + xy[1] + ")";
    }
}
