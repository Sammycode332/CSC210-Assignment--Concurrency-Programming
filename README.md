# Fleet Vehicle Tracker — JCIP §4.2.2 / §4.3.1 Assignment

## 1. What this project demonstrates

This project simulates a real-world "fleet tracking" system — think delivery vans or taxis
sending GPS updates, and a dispatcher screen showing where they all are right now.

That system naturally splits into two kinds of threads sharing the same data:

- **Updater threads** — simulate vehicles sending new GPS coordinates (writes)
- **A view/render thread** (console output, or the GUI's redraw timer) — reads all vehicle
  positions to display them (reads)

Because both happen **at the same time**, on the **same shared data**, this is a textbook
thread-safety problem. The project implements **two different, both-correct** ways of solving
it, plus **one deliberately broken version**, so the difference between "looks fine" and
"is actually safe" is visible rather than just theoretical.

This directly follows Brian Goetz's *Java Concurrency in Practice*, §4.2.2 (Java Monitor
Pattern) and §4.3.1 (Delegating Thread Safety).

## 2. Files (package: `fleettracker`)

| File | Role |
|---|---|
| `MutablePoint.java` | Plain, **not** thread-safe (x, y) holder — JCIP Listing 4.5 |
| `MonitorVehicleTracker.java` | **Java Monitor Pattern** — one lock guards everything; never lets internal state escape; deep-copies on every read/write boundary — JCIP Listing 4.4 |
| `SafePoint.java` | An independently thread-safe (x, y) — its own lock guards `get()`/`set()` together, so x and y can never be read/written as a "torn" pair |
| `DelegatingVehicleTracker.java` | **Delegated thread safety** — built from `ConcurrentHashMap` + `SafePoint`, with no manual locking written by us at all — JCIP §4.3.1 |
| `UnsafeVehicleTracker.java` | Intentionally broken (plain `HashMap`, publishes live mutable state) — kept only for comparison |
| `Simulation.java` | Console-based demo: runs updater threads + a reader thread against all three trackers and reports whether torn reads/errors occurred |
| `FleetTrackerGUI.java` | Swing GUI demo: renders vehicles as moving dots on a panel, redrawn on a timer, while background threads update positions — a visual, presentable version of the same experiment |

## 3. The two safe designs, compared

### A. Java Monitor Pattern (`MonitorVehicleTracker`)
- **One intrinsic lock** (`synchronized` on `this`) guards the whole map and everything in it.
- The internal map and its `MutablePoint`s **never escape** the object — every getter returns a
  freshly deep-copied `Map`/`MutablePoint`.
- **Pro:** simple to reason about; caller gets a frozen, internally-consistent snapshot of the
  whole fleet at one instant.
- **Con:** copying is O(n) on every call — can matter if the fleet is huge; the snapshot goes
  stale the moment the caller holds onto it.

### B. Delegated thread safety (`DelegatingVehicleTracker`)
- No locking code written by us at all. We delegate to two building blocks that are
  *already* thread-safe: `ConcurrentHashMap` (map-level operations) and `SafePoint`
  (so a single vehicle's x and y are always updated/read together).
- **Pro:** no copying, so reads are cheap and always "live" (up to date).
- **Con:** callers can see different vehicles at different points in time within a single scan
  (no whole-map snapshot consistency) — acceptable here because each vehicle's *own* (x, y) pair
  is still always internally consistent, and the assignment (like the real world) doesn't need a
  frozen instant of the *entire* fleet.

### C. `UnsafeVehicleTracker` — why it's broken
- Publishes the **live**, mutable `HashMap` reference to the view thread.
- `setLocation` writes `x` then `y` as two separate, unsynchronized statements.
- Under real contention this can produce **torn reads** (x and y from two different updates)
  or, if the map's structure ever changes concurrently, `ConcurrentModificationException` /
  silently corrupted `HashMap` internals.

> **Note for the report:** `Simulation.java` tries to *catch* a torn read live, by having every
> write set `x == y` and flagging any read where they differ. Whether it catches one on a given
> run is **timing-dependent** — that's the point. A race that doesn't show up in 5 test runs is
> not a race that's fixed; it's a bug waiting for the wrong moment (heavier load, a slower
> machine, a different JIT decision). This is exactly why JCIP insists on *designing in*
> thread safety rather than testing for its absence.

## 4. The GUI demo (`FleetTrackerGUI.java`)

The console simulation proves the point numerically; the GUI makes it visible.

- Each vehicle is drawn as a dot on a Swing panel.
- Background "updater" threads move each vehicle by a small random step at a regular interval
  (a gentle random walk, not a teleport) — this simulates a stream of GPS updates arriving
  continuously, like a real dispatch system.
- A separate Swing timer repaints the panel at a fixed interval, reading the current tracker
  state each time.
- The GUI can be pointed at any of the three trackers to make the safety difference visually
  obvious: the safe trackers always render smooth, consistent motion; the unsafe tracker can,
  under the right timing, show visibly glitchy or inconsistent positions.
- This turns the assignment into an actual working mini version of the "GUI vehicle tracker"
  scenario Goetz describes in the book, rather than just a console log.

## 5. How to build and run

Run these from inside the `fleettracker` folder.

**Compile (Windows PowerShell — list files explicitly, wildcards don't expand for `javac`):**
```powershell
javac -d out MutablePoint.java MonitorVehicleTracker.java SafePoint.java DelegatingVehicleTracker.java UnsafeVehicleTracker.java FleetTrackerGUI.java Simulation.java
```

**Run the console simulation:**
```powershell
java -cp out fleettracker.Simulation
```

**Run the GUI demo:**
```powershell
java -cp out fleettracker.FleetTrackerGUI
```

Run the console simulation several times — the two safe trackers always finish cleanly with no
exceptions and internally consistent (x, y) pairs, while the unsafe tracker's correctness is a
matter of luck.

## 6. Suggestions for presenting this to the lecturer

1. **Live demo, not just code.**
   - Run `FleetTrackerGUI.java` first — it's the most immediately understandable: "here's a
     shared map of vehicle positions being read and written by different threads at once."
   - Then run `Simulation.java` in the console 3–4 times. Point out that the safe versions are
     *always* correct, while the unsafe one's correctness depends on timing (rerun a few times —
     if a torn read isn't caught, say so honestly and explain *why that's still evidence of a
     design flaw*, not proof of safety).
2. **Explain WHY each is safe**, not just that it is:
   - Monitor pattern → single lock + no internal state ever published.
   - Delegating → composed from independently thread-safe pieces, with no invariant that spans
     across them.
3. **Trade-off table** (good as a slide):

   | | Monitor Pattern | Delegating |
   |---|---|---|
   | Snapshot consistency | Yes (frozen copy) | Per-vehicle only |
   | Copy cost per read | O(n) | O(1) |
   | Freshness of data | Stale after copy | Always live |
   | Code you must write/maintain | Locking logic | Almost none |

4. **Possible extensions** (good for extra marks, not required):
   - Replace the tight update loop with a `ScheduledExecutorService` firing simulated GPS
     updates every N ms, closer to a real system.
   - Add a JMH or simple stopwatch benchmark comparing throughput of Monitor vs Delegating under
     heavy load — this is literally the "could become a performance issue" caveat Goetz raises
     in the text (footnote 4).
   - Write JUnit tests using `CountDownLatch`/`CyclicBarrier` to deterministically force
     interleavings and reliably reproduce the unsafe tracker's bug, instead of relying on luck.

## 7. Key vocabulary to be ready to explain if asked

- **Race condition** — outcome depends on the relative timing of threads.
- **Intrinsic lock / monitor** — the lock associated with every Java object, acquired via `synchronized`.
- **Confinement / encapsulation** — never letting a reference to mutable internal state escape.
- **Delegation** — building a thread-safe class out of other thread-safe classes, when no
  invariant spans more than one of them.
- **Torn read/write** — reading or writing a multi-field value (like x, y) as two separate,
  non-atomic operations, so an observer can see an inconsistent in-between state.

