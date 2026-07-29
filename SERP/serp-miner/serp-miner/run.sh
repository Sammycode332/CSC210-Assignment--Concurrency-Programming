#!/usr/bin/env bash
# Build and run the SERP Feature Miner. Requires a JDK 17+ (uses records).
set -e
cd "$(dirname "$0")"

mkdir -p out
echo ">> compiling sources..."
find src  -name '*.java' > .sources.txt
find test -name '*.java' > .tests.txt
javac -d out @.sources.txt
javac -cp out -d out @.tests.txt
echo ">> done."

cmd="${1:-run}"
shift || true

case "$cmd" in
  run)    java -cp out serp.app.Main "$@" ;;                    # offline demo (add --online for arXiv)
  online) java -cp out serp.app.Main --online "$@" ;;           # live arXiv
  bench)  java -cp out serp.bench.Benchmark ;;                   # pool-size sweep
  test)   for t in ModelSmokeTest ExtractionSmokeTest SearchSmokeTest ParallelSmokeTest; do
            java -cp out serp.test.$t
          done ;;
  *)      echo "usage: ./run.sh [run|online|bench|test] [args...]" ;;
esac
