#!/usr/bin/env python3
"""Aggregate jmsdfgen performance & output-equivalence comparison vs C++ msdfgen.

Usage:
  analyze.py --cpp <msdfgen-bin> --java-jar <cli.jar> \
             --cjk-font <font> --west-font <font> --size 512 \
             --java-cjk <java_cjk.txt> --java-west <java_west.txt> \
             --outdir <dir> --cjk-glyphs 22909 27704 ... --west-glyphs 65 82 ...
"""
import argparse
import hashlib
import json
import os
import re
import statistics
import subprocess
import sys

def parse_java(path):
    d = {}
    if not path or not os.path.exists(path):
        return d
    for line in open(path):
        m = re.match(r"U\+(\w+) edges=(\d+) avg=([\d.]+) best=([\d.]+)", line)
        if m:
            d[f"U+{m.group(1)}"] = (int(m.group(2)), float(m.group(3)), float(m.group(4)))
    return d

def run_cpp(cpp, font, u, size, out):
    r = subprocess.run([cpp, "msdf", "-font", font, str(u), "-o", out,
                        "-dimensions", str(size), str(size), "-autoframe",
                        "-pxrange", "2"], capture_output=True, text=True)
    m = re.search(r"CPU time: (\d+) us", r.stdout)
    return int(m.group(1)) / 1000.0 if m else None

def run_java_cli(jar, font, u, size, out):
    subprocess.run(["java", "-jar", jar, "-font", font, str(u), "-o", out,
                    "-dimensions", str(size), str(size), "-autoframe",
                    "-format", "binfloat"], capture_output=True)

def md5(p):
    return hashlib.md5(open(p, "rb").read()).hexdigest()

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cpp", default=os.environ.get("MSDFGEN_CPP", ""))
    ap.add_argument("--java-jar", required=True)
    ap.add_argument("--cjk-font", required=True)
    ap.add_argument("--west-font", required=True)
    ap.add_argument("--size", type=int, default=512)
    ap.add_argument("--cjk-glyphs", nargs="+", type=int, required=True)
    ap.add_argument("--west-glyphs", nargs="+", type=int, required=True)
    ap.add_argument("--java-cjk", default="")
    ap.add_argument("--java-west", default="")
    ap.add_argument("--outdir", default=".")
    ap.add_argument("--runs", type=int, default=5)
    ap.add_argument("--skip-cpp", action="store_true")
    ap.add_argument("--skip-equiv", action="store_true")
    args = ap.parse_args()

    os.makedirs(args.outdir, exist_ok=True)
    java_cjk = parse_java(args.java_cjk)
    java_west = parse_java(args.java_west)

    cpp_res = {}
    equiv = {}
    groups = [("CJK", args.cjk_font, args.cjk_glyphs, java_cjk),
              ("WEST", args.west_font, args.west_glyphs, java_west)]

    if args.cpp and not args.skip_cpp:
        if not os.path.exists(args.cpp):
            print(f"warning: C++ binary not found: {args.cpp}", file=sys.stderr)
            args.cpp = ""
    if args.cpp:
        for label, font, glyphs, _ in groups:
            for u in glyphs:
                times = [t for t in (run_cpp(args.cpp, font, u, args.size,
                                             os.path.join(args.outdir, "cpp.png")) for _ in range(args.runs)) if t]
                if times:
                    cpp_res[f"U+{u:04X}"] = (label, statistics.median(times), min(times))

    if args.cpp and not args.skip_equiv:
        print(f"== output equivalence (C++ vs Java CLI, byte-exact) ==")
        for label, font, glyphs, _ in groups:
            for u in glyphs:
                cpp_f = os.path.join(args.outdir, f"equiv_{label}_{u:04X}_cpp.binfloat")
                java_f = os.path.join(args.outdir, f"equiv_{label}_{u:04X}_java.binfloat")
                r = subprocess.run([args.cpp, "msdf", "-font", font, str(u), "-o", cpp_f,
                                    "-dimensions", str(args.size), str(args.size), "-autoframe",
                                    "-pxrange", "2", "-format", "binfloat"], capture_output=True)
                run_java_cli(args.java_jar, font, u, args.size, java_f)
                ok = os.path.exists(cpp_f) and os.path.exists(java_f) and md5(cpp_f) == md5(java_f)
                equiv[f"U+{u:04X}"] = ok
                print(f"  {label} U+{u:04X}: {'IDENTICAL' if ok else 'DIFFER'}")
        n = sum(1 for v in equiv.values() if v)
        print(f"  -> {n}/{len(equiv)} identical")
        for f in os.listdir(args.outdir):
            if f.startswith("equiv_"):
                os.remove(os.path.join(args.outdir, f))

    def cpp_time(key):
        return cpp_res.get(key, (None, float("nan"), float("nan")))[1]

    print(f"\n{'glyph':8} {'edges':>5} {'C++':>8} {'java':>8} {'java/C++':>8}")
    sums = {}
    for label, font, glyphs, jmap in groups:
        print(f"--- {label} ---")
        for u in glyphs:
            key = f"U+{u:04X}"
            j = jmap.get(key)
            edges = j[0] if j else 0
            jt = j[1] if j else float("nan")
            ct = cpp_time(key)
            ratio = jt / ct if ct == ct and jt == jt and ct > 0 else float("nan")
            sums.setdefault(label, [0.0, 0.0])
            if ct == ct: sums[label][0] += ct
            if jt == jt: sums[label][1] += jt
            print(f"{key:8} {edges:>5} {ct:>8.1f} {jt:>8.1f} {ratio:>7.2f}x")
        c, j = sums[label]
        print(f"{'SUM':8} {'':>5} {c:>8.1f} {j:>8.1f} {j/c:>7.2f}x" if c > 0 else "")

    tc = sum(v[0] for v in sums.values())
    tj = sum(v[1] for v in sums.values())
    if tc > 0:
        print(f"\nTOTAL: C++={tc:.0f}ms  Java={tj:.0f}ms  Java/C++={tj/tc:.2f}x")

if __name__ == "__main__":
    main()
