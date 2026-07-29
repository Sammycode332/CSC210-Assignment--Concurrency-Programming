# SERP Feature Miner — Assignment 1, Part 1

A multithreaded Java program that queries a "search engine", mines the returned
crime-reporting papers for distinctive **system features**, and **categorises those
features by the number of systems having each one**, then visualises the ranking.

This is the deliverable for Assignment 1, Part 1:

> *"Design and implement a multithreaded program for returning ... the distinctive
> features of crime-reporting papers (at least 10) of SERP and categorise the
> features in order of the number of systems having the feature. Your implementation
> should also include visualization of your results."*

It has **zero external dependencies** — it compiles with plain `javac` and runs with
plain `java` on any **JDK 17+** (records are used for the immutable value layer).

---

## What it does (the pipeline)

1. **Search (Stage A, parallel I/O).** Fan out a set of related queries
   ("crime reporting system", "crowdsourced crime reporting", …) to a search backend,
   concurrently, and merge + de-duplicate the results into one SERP.
2. **Extract.** For each paper, detect which of **15 crime-reporting features** appear
   in its title + abstract, using a keyword/synonym lexicon with word-boundary matching.
3. **Aggregate + rank (Stage B, parallel CPU).** Count, across all papers, how many
   systems exhibit each feature, and sort the features in descending order of that count.
4. **Visualise.** Render the ranking as a console bar chart and a Swing bar chart.

One paper is counted as one "system" — the stated assumption behind the counting rule.

### The 15 features
Anonymous reporting · Geolocation/GPS · Real-time alerts · Media/evidence upload ·
Case/incident tracking · Encryption/security · Multilingual · Mobile app ·
Police/authority integration · Analytics/dashboard · Crowdsourcing · ML/prediction ·
Emergency/SOS · Verification/credibility · Chatbot/NLP.

---

## Build & run

```bash
./run.sh test      # compile + run the four smoke tests
./run.sh run       # offline demo (built-in corpus, always works) + Swing chart
./run.sh run --no-gui
./run.sh online    # live arXiv backend (needs internet)
./run.sh bench     # pool-size sweep, prints the speedup curve

# or manually:
javac -d out $(find src -name '*.java')
java  -cp out serp.app.Main --online "crime reporting system" "crime mapping"
```

**Why arXiv, not Google?** Google actively blocks bots and forbids scraping, so a
multithreaded scraper would spend its time fighting captchas instead of demonstrating
concurrency. The arXiv API is free, keyless, returns ranked title+abstract results, and
is meant to be queried programmatically — an honest SERP source. Swapping in
Semantic Scholar / OpenAlex / CrossRef only means writing another `SearchClient`.

**Offline by default.** `OfflineSearchClient` serves a small built-in corpus of realistic
crime-reporting abstracts with *simulated* latency, so the whole pipeline (and the
benchmarks) runs and is reproducible with no network. The live client exercises the exact
same downstream code.

---

## Architecture (layered, each layer swappable)

```
serp.model    Paper, Feature, PaperFeatures        immutable value layer (records/enum)
serp.search   SearchClient  ── ArxivSearchClient    the SERP source (task, not policy)
                            └─ OfflineSearchClient
serp.extract  FeatureExtractor                       lexicon matching (stateless/shared)
serp.mine     FeatureMiner, MiningConfig/Result      the concurrency engine  ← core
serp.viz      ConsoleReport, BarChartPanel, ChartWindow   visualisation
serp.app      Main                                   CLI wiring
serp.bench    Benchmark                               empirical pool-sizing
test/serp     4 smoke tests
```

The `SearchClient` interface is the seam that keeps the **task** (fetch a page of
results) separate from the **execution policy** (how many run at once) — the discipline
that lets the same engine drive a live web backend or an in-memory one unchanged.

---

## How it maps to *Java Concurrency in Practice* (Goetz)

| Design decision | JCiP concept |
|---|---|
| `Paper`, `PaperFeatures` are immutable records; one shared `FeatureExtractor` | Ch.3 immutability, 3.4/4.3.1 safe sharing, Ch.16 safe publication |
| Each query / each paper is a `Callable` submitted to one `ExecutorService` | Ch.6 task vs. execution policy |
| Both stages consume results via `ExecutorCompletionService` | 6.3.5 completion-order results |
| `Semaphore` bounds concurrent backend requests (politeness) | 5.5.3 bounded resource |
| Feature tally is a `ConcurrentHashMap<Feature, LongAdder>` updated by workers | Ch.5 concurrent collections; lock-free counting |
| Per-completion `poll(timeout)`; `Future.get` surfaces per-task failure | Ch.7 timed tasks, cancellation |
| `finally` shutdown with `awaitTermination` → `shutdownNow` fallback; interrupts restored | Ch.7 clean shutdown, 7.1.2 interruption policy |
| Pool size from the I/O formula `N = N_cpu · U · (1 + W/C)`, capped at #queries | 8.2 sizing thread pools |
| UI reads only a finished immutable `MiningResult` on the EDT | Ch.9 Swing single-thread rule |

**A subtle interaction worth noting for the write-up:** with the default polite
semaphore (4 permits) but 8 queries, the speedup flattens at 4× even with 8 threads —
because the *semaphore*, not the pool, becomes the binding constraint. The benchmark
lifts the semaphore to isolate pure pool scaling; the app keeps it low to stay polite.
That two-limits interplay (5.5.3 meeting 8.2) is a genuine concurrency lesson, not a bug.

---

## Empirical pool sizing

`./run.sh bench` sweeps pool sizes against the offline pipeline (200 ms simulated
per-query latency, 8 queries). Representative run:

```
pool   total(ms)   speedup
1      1626        1.00x
2       808        2.01x
4       410        3.97x
8       207        7.86x
16      211        7.71x
32      211        7.71x
```

The curve is textbook I/O-bound scaling: time falls almost linearly until the pool
reaches the number of independent tasks (8 queries), then flattens — more threads than
tasks buys nothing. This holds even on a 1-CPU machine because the work is *waiting on
I/O*, not computing, which is exactly why the JCiP formula recommends **many more
threads than cores** for this workload.

---

## Correctness of the concurrency

`ParallelSmokeTest` is the key check: it runs the miner at pool sizes 1, 2, 4, 8, 16 and
asserts the feature counts are **identical every time** and match a single-threaded
reference computed with no executor. If the shared-map aggregation had a lost-update or
visibility race, the counts would drift as threads were added; they don't.
