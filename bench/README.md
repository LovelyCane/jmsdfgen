# Benchmark & output-equivalence suite

Compares the Java `jmsdfgen` port (current git branch) against the C++ `msdfgen`
reference (single-threaded, no Skia) on a fixed corpus of glyphs.

## Files

- `src/lovely/cane/jmsdfgen/BenchComprehensive.java` — Java benchmark. Loads each
  glyph via FreeType, normalizes, colors, autoframes, then runs
  `generateMSDF` (pure distance field, error correction disabled) for a warmup
  phase followed by N measured reps, reporting avg/best per glyph.
- `analyze.py` — runs the C++ benchmark (median of N runs per glyph), verifies
  C++ vs Java CLI output is byte-identical (`binfloat` + md5), and prints the
  comparison table.
- `run-bench.sh` — orchestrator: builds the current branch's shadow jar, compiles
  the benchmark, runs it, then calls `analyze.py`.

## Usage

```bash
bench/run-bench.sh                    # defaults: 512px, warmup 8, reps 16, C++ runs 5
bench/run-bench.sh --size 256 --reps 32
bench/run-bench.sh --cpp /path/to/msdfgen
bench/run-bench.sh --skip-cpp         # Java-only
```

The C++ binary defaults to `$MSDFGEN_CPP` or
`/home/cane/Projects/C++/msdfgen/build/linux-rel/msdfgen`. Build it with Skia and
OpenMP disabled to match the Java port:

```bash
cmake -S . -B build/linux-rel -DCMAKE_BUILD_TYPE=Release \
      -DMSDFGEN_USE_SKIA=OFF -DMSDFGEN_USE_OPENMP=OFF
cmake --build build/linux-rel -j
```

## Notes

- The Java benchmark warms the JIT before measuring; results are steady-state
  `generateMSDF` time (the dominant phase). Use low system load for stable numbers.
- The C++ "CPU time" printed by `msdfgen` wraps only `generateMSDF`, matching the
  Java measurement semantics.
