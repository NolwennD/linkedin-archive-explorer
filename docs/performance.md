# Performance notes

No performance changes are made in the code. This just records what to measure if it ever
matters, and the one JVM flag that helps.

## Measure two different axes — they do NOT coincide

- **Allocation** (bytes/op): deterministic and machine-independent. Use JFR
  (`-XX:StartFlightRecording=...,settings=profile` then `jfr view allocation-by-site`) or
  `com.sun.management.ThreadMXBean.getThreadAllocatedBytes()` around the code. Good for
  comparing implementations and catching regressions.
- **CPU / time** (where the cycles actually go): JFR execution samples
  (`jfr view hot-methods`) or async-profiler. This governs wall-clock — and it is usually
  **not** where the allocations are, so profile it separately.

Both are JDK-only (JFR ships with the JDK). Never use one as a proxy for the other: an
allocation hotspot is often not a time hotspot, and vice versa.

## The one flag worth knowing

For a short-lived CLI (JVM relaunched per run, so mostly cold), **`-XX:TieredStopAtLevel=1`**
(C1 only) slightly improves startup: the C2 compiler never pays back in a process this
short, so stopping at C1 shaves a bit of time. Modest and zero-downside for a single-shot
run; the effect shrinks on newer JDKs whose default tiered startup is already well tuned.
