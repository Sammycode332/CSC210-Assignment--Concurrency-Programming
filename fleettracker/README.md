# Fleet Vehicle Tracker (Assignment 2)

A Swing demonstration of the *Tracking Fleet Vehicles* example from **Java Concurrency in
Practice §4.2–4.3**. Several background "updater" threads continuously move vehicles while
a Swing timer repaints — the classic multiple-writers / one-reader setup — shown three
ways side by side:

- **Monitor** (`MonitorVehicleTracker`) — one lock guards everything; callers only ever get
  deep copies. Safe.
- **Delegating** (`DelegatingVehicleTracker`) — no manual locking; thread safety delegated
  to a `ConcurrentHashMap` of already-safe `SafePoint`s. Safe.
- **Unsafe** (`UnsafeVehicleTracker`) — plain `HashMap`, live map exposed, non-atomic x/y
  writes. Broken, and the UI flags every torn read it catches.

The vehicles drive a route around a **University of Lagos** backdrop drawn from live
OpenStreetMap tiles, so it reads as a fleet moving around campus.

## Layout and package

Every class is in the package **`fleettracker.fleettracker`**, so the files live two levels
deep:

```
fleettracker/                      <- assignment folder (first package segment)
  fleettracker/                    <- package folder (second segment)
    MutablePoint.java
    SafePoint.java
    MonitorVehicleTracker.java
    DelegatingVehicleTracker.java
    UnsafeVehicleTracker.java
    Campus.java                    road route + start points + world/panel transform
    OsmMap.java                    live OpenStreetMap tile backdrop (local unilag.png fallback)
    FleetTrackerGUI.java           the GUI (entry point)
    Simulation.java                console version (entry point)
    README.md                      (this file)
```

Because the package is `fleettracker.fleettracker`, Java needs the compile/run directory to
be the folder that **contains the outer `fleettracker` folder** — in this repository that is
the repository root, one level above this folder.

## Build and run

From the folder that contains the outer `fleettracker/` directory (the repo root here):

```bash
javac -encoding UTF-8 -d out fleettracker/fleettracker/*.java
java  -cp out fleettracker.fleettracker.FleetTrackerGUI    # the GUI + campus map
java  -cp out fleettracker.fleettracker.Simulation         # the console demo
```

- `-encoding UTF-8` is needed because a few files contain `§` and `—`; it avoids an
  "unmappable character" error on machines whose default charset isn't UTF-8.
- Run by the **full class name** (`fleettracker.fleettracker.FleetTrackerGUI`), not
  `java FleetTrackerGUI`.

In the GUI, pick **Monitor / Delegating / Unsafe** at the top. The two safe modes always
move correctly; in **Unsafe** the status bar's "off-road (torn) reads" counter climbs and
cars flash red as the painter catches half-finished writes. Run the console `Simulation`
several times to see the same contrast in text.

### The map

`OsmMap` fetches OpenStreetMap tiles for the campus in the background (cached, with a valid
User-Agent and attribution) — so it needs an internet connection at runtime. For a fully
offline map, export a campus image from openstreetmap.org, save it as **`unilag.png`** in
the folder you run `java` from, and `OsmMap` uses that instead of the network. With neither,
it shows a plain backdrop and the demo still runs.

## How the concurrency fits together (JCiP)

- **Thread confinement** — all Swing state is touched only on the Event Dispatch Thread; the
  Swing timer is the single, consistent reader (ch. 3, 9).
- **Monitor pattern** — `MonitorVehicleTracker` guards state with one intrinsic lock and
  hands out deep copies so nothing escapes (Listing 4.4).
- **Delegation** — `DelegatingVehicleTracker` composes already-thread-safe parts and adds no
  locking of its own (§4.3).
- **Publication hazard** — `UnsafeVehicleTracker` deliberately publishes mutable state and
  writes `x`/`y` non-atomically; a legitimate position always sits on the road, so a torn
  (new-x, old-y) read lands off the road and is flagged.

## Files

| File | Role |
|------|------|
| `MutablePoint.java` | Plain mutable `(x, y)` holder. |
| `SafePoint.java` | Thread-safe point; reads/writes `x` and `y` together under a lock. |
| `MonitorVehicleTracker.java` | Safe tracker — single lock, deep copies. |
| `DelegatingVehicleTracker.java` | Safe tracker — delegates to `ConcurrentHashMap` + `SafePoint`. |
| `UnsafeVehicleTracker.java` | Deliberately broken tracker (the teaching foil). |
| `Campus.java` | Shared road loop, per-vehicle start points, world↔panel transform, on-road test. |
| `OsmMap.java` | Live OSM tile backdrop with a local-`unilag.png` fallback. |
| `FleetTrackerGUI.java` | The GUI; mode switch, painting, torn-read counter. Entry point. |
| `Simulation.java` | Console version of the same three-tracker comparison. Entry point. |

## Note on the folder nesting

The `fleettracker.fleettracker` package (and the resulting double folder) is a bit unusual —
the tidier arrangement would be a single `fleettracker` package in one folder, letting you
compile and run from inside `fleettracker/`. That would mean changing the `package` line in
each file to `package fleettracker;` and moving the files up one level. The current layout
works as documented above; the collapse is optional cleanup.
