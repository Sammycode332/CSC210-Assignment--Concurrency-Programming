# Scholar Feature Miner

A multithreaded Java program with a **search-engine-style GUI**. You type **two topics
at once**; the app searches several academic sources **concurrently**, mines the
returned papers for crime-reporting **system features**, ranks each feature by how many
papers exhibit it, and shows the results across **four pages**:

| Page | Content |
|------|---------|
| 1 · Papers (topic 1) | top-10 papers for topic 1 (clickable titles) |
| 2 · Papers (topic 2) | top-10 papers for topic 2 |
| 1 · Chart (topic 1)  | features ranked by number of papers, as a bar chart |
| 2 · Chart (topic 2)  | same visualisation for topic 2 |

The point of the assignment is the concurrency, so the two searches genuinely run at the
same time, and each search itself fans out across sources and papers.

No third-party libraries — just a JDK (17+). The included JSON parser means there is no
Gson/Jackson jar to put on the classpath.

## Run it

**Any OS with bash (macOS / Linux / Git Bash / WSL):**
```
./run.sh gui                       # the search-engine window
./run.sh online "crime reporting system" "crime mapping"   # console, live sources
./run.sh run    "crime reporting system" "crime mapping"   # console, offline corpus
./run.sh bench                     # pool-size sweep
./run.sh test                      # all smoke tests
```

**Windows (cmd):**
```
build.bat
java -cp out serp.app.SearchApp                                   REM the GUI
java -cp out serp.app.Main --online "crime reporting" "crime mapping"
```
The GUI needs a desktop display; `--online` needs internet access to reach the sources.

## Sources

`online` queries four keyless public APIs concurrently and merges them:

- **arXiv** (Atom XML)
- **OpenAlex** (JSON; abstracts stored as an inverted index, reconstructed on the fly)
- **Semantic Scholar** (JSON; rate-limited without a key — throttling is isolated)
- **CrossRef** (JSON; JATS-XML abstracts are tag-stripped)

Results are de-duplicated across sources by normalised title; a paper found by more
sources, or ranked higher within a source, sorts higher. `offline` uses a built-in
16-paper corpus so the whole pipeline runs and is testable with no network.

## Concurrency design (mapped to *Java Concurrency in Practice*)

Three levels of parallelism, one shared bounded work pool:

1. **Two topics at once.** In the GUI, each topic runs in its own `SwingWorker`; the
   console `mineBoth` runs each on its own thread in a size-2 coordinator pool.
2. **Sources per topic.** `MultiSourceSearchClient` submits one task per source and
   collects them with an `ExecutorCompletionService`, so slow sources don't hold up fast
   ones (Ch.6).
3. **Papers per topic.** Feature extraction is fanned out the same way, tallying into a
   `ConcurrentHashMap<Feature, LongAdder>` (Ch.5, 11).

Key JCiP points, applied concretely:

- **Immutable, safely-published state** (`Paper`, `Feature`, `TopicResult`) is read by
  many threads with no locks — the same principle that made `Board` lock-free in the Ayo
  project (Ch.3, 16).
- **Thread confinement / EDT discipline.** All Swing state is touched only on the Event
  Dispatch Thread; background work happens in `SwingWorker`, and finished immutable
  `TopicResult`s are published to the EDT for rendering (Ch.9).
- **No thread-starvation deadlock (8.1.1).** Only *coordinator* threads (SwingWorker
  background threads, or the size-2 coordinator pool) ever block waiting for results. The
  shared *work* pool runs only leaf tasks (one fetch, one extraction) that never wait on
  the pool — so tasks can't deadlock waiting for a thread the pool can't spare.
- **Bounded resources.** A shared `Semaphore` caps in-flight HTTP requests across all
  sources and both topics, keeping us a polite client (5.5.3). `poll` timeouts bound how
  long we wait on any one source or paper (Ch.7).
- **Failure isolation.** A failing source (rate limit, network) or a failing topic is
  caught and reported without sinking the rest of the run.
- **Pool sizing, justified empirically.** `serp.bench.Benchmark` sweeps the pool size
  against 8 simulated 200 ms sources; speedup climbs ~linearly until the pool reaches the
  number of sources, then flattens — the JCiP 8.2 I/O formula in action.

## Layout

```
src/serp/
  model/    Paper, Feature (15-feature crime-reporting lexicon), PaperFeatures  (immutable)
  json/     Json                      minimal dependency-free JSON parser
  net/      Http                      shared GET helper (timeouts, polite UA)
  search/   SearchClient + ArxivSearchClient, OpenAlexSearchClient,
            SemanticScholarSearchClient, CrossRefSearchClient, OfflineSearchClient,
            MultiSourceSearchClient (concurrent fan-out + merge), Sources
  extract/  FeatureExtractor          keyword/inflection matching with word boundaries
  mine/     SearchService (engine), TopicResult, FeatureCount, MiningConfig
  viz/      SearchFrame, ResultsView (4 tabs), PapersPanel, BarChartPanel, ConsoleReport
  app/      SearchApp (GUI entry), Main (console entry)
  bench/    Benchmark
test/serp/test/
  ModelSmokeTest, ExtractionSmokeTest, SearchSmokeTest,
  JsonSmokeTest, SourceMappingSmokeTest, ParallelSmokeTest      (175 checks)
```

## What was verified where

Compiles clean on JDK 21; all six smoke suites pass (175 checks); the console two-topic
pipeline and the benchmark run headless. The **GUI** and the **live APIs** were not
exercised in the build sandbox (no display; outbound network blocked) — run those on your
own machine. The source-mapping test covers each API's parsing against embedded sample
responses, so a well-formed response is proven to map correctly even though the live
endpoints weren't hit from here.

## Assumptions

- One paper = one "system" for the counting rule.
- Both topics are visualised with the same crime-reporting feature lexicon. The extractor
  is injected into the engine, so a different lexicon per topic (e.g. for a second
  assignment part) is a one-line change.
