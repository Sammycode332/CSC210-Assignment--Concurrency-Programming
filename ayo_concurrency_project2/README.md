# Ayo — Parallel Game AI (Assignment 3)

A playable **Ayòayò** (Ayo) board game with a parallel alpha-beta search engine and an
animated Swing board. The concurrency angle is the point: the game tree is explored on a
thread pool, and the design leans on immutability so that parallel search needs no locks —
a concrete application of ideas from *Java Concurrency in Practice* (Goetz).

Requires a **JDK 16 or newer** (the code uses `record`).

## Layout

```
ayo_concurrency_project2/
  src/
    ayo/model/Board.java              immutable Ayo position + rules (multi-lap relay sowing)
    ayo/ai/Evaluator.java             evaluation interface
    ayo/ai/HeuristicEvaluator.java    weighted-sum static evaluation
    ayo/ai/AlphaBetaSearch.java       negamax + alpha-beta + iterative deepening
    ayo/ai/ParallelRootSearch.java    parallel root search (young-brothers-wait)
    ayo/app/Main.java                 entry point; builds the Swing UI
    ayo/ui/BoardView.java             board rendering, pit hit-testing, sowing animation
    ayo/ui/GameController.java        game coordination; runs the AI off the EDT
  test/
    BoardSmokeTest.java  SearchSmokeTest.java  ParallelSmokeTest.java  UiSmokeTest.java
```

The entry point is `ayo.app.Main`.

## Build and run

Run these from **inside** the `ayo_concurrency_project2` folder:

```bash
javac -d out -sourcepath src src/ayo/app/Main.java
java  -cp out ayo.app.Main
```

The first line compiles `Main` and, via `-sourcepath src`, pulls in every class it depends
on into `out/`. The window opens with a **New game** button, a **thinking-time** selector
(0.3s / 0.8s / 2s), and an **Animate moves** toggle. You are SOUTH; click one of the
highlighted pits to move.

## Tests

The smoke tests live in the default package and reference the `ayo.*` classes, so compile
them against the built classes, then run each:

```bash
javac -d out -sourcepath src src/ayo/app/Main.java
javac -d out -cp out test/*.java
java -cp out BoardSmokeTest
java -cp out SearchSmokeTest
java -cp out ParallelSmokeTest
java -cp out UiSmokeTest
```

`UiSmokeTest` runs headless and renders a couple of board images to disk to prove the view
paints without a display. (Note: its output path is currently hard-coded to
`/mnt/user-data/outputs`; on Windows that resolves to `C:\mnt\user-data\outputs`. Harmless,
but change the `outDir` line in the test if you want the images somewhere specific.)

## How the concurrency fits together

- **Immutable board = lock-free search.** `Board` is immutable: `play(pit)` returns a new
  position instead of mutating the current one. That is what lets many search threads
  explore different lines at the same time without any synchronization — the core
  "publish an immutable object and share it freely" principle (JCiP ch. 3).
- **Task/executor framework.** `ParallelRootSearch` expresses each root move as a task on a
  thread pool sized to `Runtime.getRuntime().availableProcessors()`, rather than managing
  raw threads (JCiP ch. 6).
- **Young-brothers-wait.** The first root move is searched alone to establish a good
  alpha bound; the siblings are then searched in parallel, sharing a single
  `AtomicInteger` alpha floor so improvements found by one thread prune the others.
- **Responsive UI.** `GameController` runs the search off the Event Dispatch Thread so the
  board stays responsive while the AI thinks; the sowing animation is driven by a Swing
  `Timer` on the EDT (JCiP ch. 9).

## Rules note

This implements the "true" multi-lap Ayòayò: a lap that ends in an occupied pit relays
(lifts that pit and sows again). Relay sowing can cycle forever on some positions, so a
turn is abandoned after a lap cap (chosen by sampling many games), ending with no capture
and the seeds left where they lie.
