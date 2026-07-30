@echo off
REM Build the Scholar Feature Miner on Windows. Requires JDK 17+ (records).
setlocal
cd /d "%~dp0"

if not exist out mkdir out

echo Compiling sources...
dir /s /b src\*.java > sources.txt
javac -d out @sources.txt || (echo COMPILE FAILED & del sources.txt 2>nul & exit /b 1)

echo Compiling tests...
dir /s /b test\*.java > tests.txt
javac -cp out -d out @tests.txt || (echo TEST COMPILE FAILED & del sources.txt tests.txt 2>nul & exit /b 1)

del sources.txt tests.txt 2>nul
echo.
echo Done. Compiled classes are in .\out
echo.
echo Run the GUI (search-engine window, two topics at once):
echo   java -cp out serp.app.SearchApp
echo.
echo Or the console version (no window needed):
echo   java -cp out serp.app.Main --offline "crime reporting system" "crime mapping"
echo   java -cp out serp.app.Main --online  "crime reporting system" "crime mapping"
echo.
echo Benchmark and tests:
echo   java -cp out serp.bench.Benchmark
echo   java -cp out serp.test.ParallelSmokeTest
