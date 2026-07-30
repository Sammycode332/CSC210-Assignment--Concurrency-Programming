# Dead and Wounded

A Swing-based number-guessing game in Java. The computer picks a secret four-digit
code and you try to crack it. Each guess comes back scored as **Dead** and
**Wounded** hits — a variant of the classic *Bulls and Cows* / *Mastermind*.

The project doubles as a small demonstration of Java concurrency: the interface,
the game logic, and log persistence each run on their own thread, coordinated so
the UI never freezes.

## How to play

The engine generates a secret number of **four distinct digits** (0–9, no repeats).
Type a four-digit guess and press **Guess** (or hit Enter). Each guess is scored
digit by digit:

- **Dead** — a correct digit sitting in its correct position.
- **Wounded** — a correct digit that appears in the secret but in a different
  position.

For example, if the secret is `5 6 4 7` and you guess `5 7 4 6`, you get
**2 Dead** (the `5` and `4` are in place) and **2 Wounded** (the `7` and `6` are
right digits in the wrong spots).

Every scored guess is added to a **History** table so you can see your attempts so
far, newest at the top. You win when a guess scores **4 Dead** — every digit
correct and in place.

Guesses that aren't four different digits (too short, too long, a repeated digit,
or a non-digit) are rejected before scoring, with a prompt to try again.

## Threading model

The game runs several threads at once. Keeping them from stepping on each other is
the point of the design.

- **Event Dispatch Thread (EDT).** `Main` launches the window through
  `SwingUtilities.invokeLater`, so all UI construction and event handling happen on
  Swing's own thread rather than the `main` thread. Pressing **Guess** fires its
  action listener here, and every read or write of UI state — the result label, the
  history table — stays confined to this thread.
- **Game logic.** `GameEngine.checkGuess` is `synchronized`, so scoring a guess is
  a single atomic operation on the engine even if more than one caller reaches it.
- **Logger thread.** A single long-lived `GameLogger` runs for the life of the
  game. The EDT (the producer) drops each finished guess onto a `BlockingQueue` and
  returns immediately; the logger (the consumer) takes entries one at a time and
  writes them to `gameLog.txt`. The queue does the hand-off and the thread-safety,
  so neither side needs an explicit lock. Because the write happens off the EDT, a
  slow disk never stalls the interface.

Because scoring, logging, and rendering are separated across threads, a slow log
write never stalls the guess-and-respond loop.

### Concurrency concepts demonstrated

The design maps deliberately onto ideas from *Java Concurrency in Practice*:

- **Thread confinement** — all Swing state lives on, and is only touched from, the
  EDT (ch. 3).
- **Producer–consumer over a `BlockingQueue`** — the logger is fed by the UI thread
  through a queue rather than spawning a thread per guess (ch. 5).
- **Poison-pill shutdown** — on window close the UI queues a sentinel; because the
  queue is FIFO with a single producer, every real entry is written before the
  logger sees the pill and stops (ch. 7).
- **Intrinsic locking** — `checkGuess` is guarded by the engine's own monitor.

## Project structure

| File | Role |
|------|------|
| `Main.java` | Entry point; starts the UI on the Swing EDT. |
| `GameEngine.java` | Generates the secret number, validates guesses (`isValidGuess`), and scores them (Dead / Wounded); `checkGuess` is synchronized. |
| `GameWindow.java` | The `JFrame` UI — guess field, button, result label, and the scrolling history table — wiring guesses to the engine and logger. |
| `GameLogger.java` | A single background thread fed by a `BlockingQueue`; appends each guess and result to the log and shuts down cleanly via a poison pill. |
| `gameLog.txt` | Running log of guesses and their results. |

## Build and run

From inside the `Dead_and_Wounded` directory:

```bash
javac *.java
java Main
```

Requires a JDK (Java 8 or newer). A window titled **Dead and Wounded** opens; enter
a four-digit guess and press **Guess**. Watch the console for the interleaved
output of the game and logger threads as you play.

## Notes and ideas

- The history table shows the engine's raw result string (`Dead: 1 Wounded: 1`). A
  prettier display format can be added by having `GameEngine` expose the dead/wounded
  counts separately from the value that `win()` checks, so the wording can change
  without breaking win detection.
- `Executors.newSingleThreadExecutor()` is the library-provided equivalent of the
  logger's single-thread-plus-queue setup; the explicit queue here just makes the
  producer–consumer structure visible.
- Possible next steps: colour-coding history rows as `dead` climbs, a restart button
  for a new round, or persisting the history table between runs.
