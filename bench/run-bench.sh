#!/usr/bin/env bash
# Full benchmark & output-equivalence suite for jmsdfgen vs C++ msdfgen.
#
# Builds the CURRENT git branch's cli shadow jar, then:
#   1. Runs the Java comprehensive benchmark (warmup + N measured reps per glyph).
#   2. Runs the C++ msdfgen benchmark (median of N runs per glyph) if a binary is available.
#   3. Verifies C++ vs Java CLI output is byte-identical (binfloat) for every glyph.
#   4. Prints a comparison table.
#
# Usage:
#   bench/run-bench.sh [options]
#
# Options (env vars also accepted):
#   --cpp <path>       C++ msdfgen binary (default: $MSDFGEN_CPP or the standard path)
#   --size <N>         output dimensions (default 512)
#   --warmup <N>       Java warmup reps per glyph (default 8)
#   --reps <N>         Java measured reps per glyph (default 16)
#   --cpp-runs <N>     C++ runs per glyph for median (default 5)
#   --skip-cpp         skip the C++ benchmark/equivalence steps
#   --outdir <dir>     artifact directory (default bench/build)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BENCH="$ROOT/bench"
OUTDIR="${OUTDIR:-$BENCH/build}"
SIZE="${SIZE:-512}"
WARMUP="${WARMUP:-8}"
REPS="${REPS:-16}"
CPP_RUNS="${CPP_RUNS:-5}"
CPP_BIN="${CPP_BIN:-${MSDFGEN_CPP:-/home/cane/Projects/C++/msdfgen/build/linux-rel/msdfgen}}"
CJK_FONT="${CJK_FONT:-/usr/share/fonts/noto-cjk/NotoSansCJK-Bold.ttc}"
WEST_FONT="${WEST_FONT:-/usr/share/fonts/TTF/DejaVuSans-Bold.ttf}"
# CJK: 好 永 我 國 龍 愛 龘 你 的 繁
CJK_GLYPHS=(22909 27704 25105 22283 40845 24859 40856 20320 30340 32321)
# Western: A R g W Q & @ 0 # B
WEST_GLYPHS=(65 82 103 87 81 38 64 48 35 66)

SKIP_CPP=0
EXTRA=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --cpp) CPP_BIN="$2"; shift 2;;
        --size) SIZE="$2"; shift 2;;
        --warmup) WARMUP="$2"; shift 2;;
        --reps) REPS="$2"; shift 2;;
        --cpp-runs) CPP_RUNS="$2"; shift 2;;
        --skip-cpp) SKIP_CPP=1; shift;;
        --outdir) OUTDIR="$2"; shift 2;;
        *) EXTRA+=("$1"); shift;;
    esac
done

mkdir -p "$OUTDIR/classes"

echo "== building cli shadow jar (current branch: $(git -C "$ROOT" branch --show-current)) =="
( cd "$ROOT" && ./gradlew :cli:shadowJar -q )
JAR="$ROOT/cli/build/libs/cli-0.0.1-all.jar"
if [[ ! -f "$JAR" ]]; then
    echo "error: no shadow jar at $JAR" >&2
    exit 1
fi

echo "== compiling benchmark sources =="
javac -cp "$JAR" -d "$OUTDIR/classes" "$BENCH"/src/lovely/cane/jmsdfgen/*.java

echo "== java benchmark (CJK) =="
java -Xmx8G -cp "$OUTDIR/classes:$JAR" lovely.cane.jmsdfgen.BenchComprehensive \
    "$CJK_FONT" "$WARMUP" "$REPS" "$SIZE" "${CJK_GLYPHS[@]}" 2>/dev/null | tee "$OUTDIR/java_cjk.txt"

echo "== java benchmark (Western) =="
java -Xmx8G -cp "$OUTDIR/classes:$JAR" lovely.cane.jmsdfgen.BenchComprehensive \
    "$WEST_FONT" "$WARMUP" "$REPS" "$SIZE" "${WEST_GLYPHS[@]}" 2>/dev/null | tee "$OUTDIR/java_west.txt"

ARGS=(--java-jar "$JAR" --cjk-font "$CJK_FONT" --west-font "$WEST_FONT"
      --size "$SIZE" --java-cjk "$OUTDIR/java_cjk.txt" --java-west "$OUTDIR/java_west.txt"
      --cjk-glyphs "${CJK_GLYPHS[@]}" --west-glyphs "${WEST_GLYPHS[@]}"
      --outdir "$OUTDIR" --runs "$CPP_RUNS")
if [[ "$SKIP_CPP" == "0" ]]; then
    if [[ -n "$CPP_BIN" && -x "$CPP_BIN" ]]; then
        ARGS+=(--cpp "$CPP_BIN")
    else
        echo "warning: C++ binary not found ($CPP_BIN); skipping C++ comparison" >&2
    fi
else
    ARGS+=(--skip-cpp --skip-equiv)
fi
python3 "$BENCH/analyze.py" "${ARGS[@]}" "${EXTRA[@]}"
