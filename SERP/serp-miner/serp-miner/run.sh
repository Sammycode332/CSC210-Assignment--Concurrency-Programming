#!/usr/bin/env bash
# Build and run the Scholar Feature Miner. Requires a JDK 17+ (uses records).
set -e
cd "$(dirname "$0")"

mkdir -p out
echo ">> compiling sources..."
find src  -name '*.java' > .sources.txt
find test -name '*.java' > .tests.txt
javac -d out @.sources.txt
javac -cp out -d out @.tests.txt
echo ">> done."

cmd="${1:-gui}"
shift || true

case "$cmd" in
  gui)     java -cp out serp.app.SearchApp ;;                    # the search-engine GUI (needs a display)
  run)     java -cp out serp.app.Main "$@" ;;                    # console, two topics, offline corpus
  online)  java -cp out serp.app.Main --online "$@" ;;           # console, two topics, live sources
  bench)   java -cp out serp.bench.Benchmark ;;                  # pool-size sweep (source fan-out)
  test)    for t in ModelSmokeTest ExtractionSmokeTest SearchSmokeTest \
                    JsonSmokeTest SourceMappingSmokeTest ParallelSmokeTest; do
             java -cp out serp.test.$t
           done ;;
  *)       echo "usage: ./run.sh [gui|run|online|bench|test] [\"topic 1\" \"topic 2\"]" ;;
esac
