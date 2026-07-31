# JMH performance benchmarks — Design

_Date: 2026-07-28_

Measurement tooling for [the LinkedIn archive explorer](2026-07-22-linkedin-archive-explorer-design.md).
The project's technical constraints (pure-JDK runtime, zero build tool, hexagonal
architecture split into modules) are untouched — this document spells out exactly how
they stay that way.

## 1. Goal

Measure the tool's **whole pipeline** against a real archive: unzip, CSV parsing, HTML
extraction, search, rendering. Two uses:

- one end-to-end figure, comparable from one version of the code to the next;
- a stage-by-stage breakdown, to find out **where the time goes** before optimising
  anything.

Out of scope: micro-benchmarks of the search core (`SearchTerm.occurrencesIn`,
`CaseSensitivity.matchesAt`). They would be worth having — the scan is a naive O(n·m)
loop where `String.indexOf` is JIT-intrinsified — but that is a separate piece of work.

## 2. The decision everything else follows from — nothing in the repository

The whole harness lives in a **gitignored `benchmark/` directory**. Nothing is committed
except the `benchmark/` line in `.gitignore` and this document.

The immediate consequence: the hard constraints in `CLAUDE.md` remain **literally true**
and need no amendment.

| Constraint | Status |
|---|---|
| "Runtime is JDK-only", application imports only `java.*` | Intact — JMH never enters `dist/linkedin-explorer.jar` |
| "The only third-party jar is JUnit" | Intact — the JMH jars are not in the repository |
| "Zero build tool" | Intact — see §4, the launcher is a JEP 330 program |
| "Maven only as a one-off downloader" | An exception already granted, reused as-is |

The price: after a clone, the benchmarks do not exist. This document is the recipe for
rebuilding them. The source itself — `benchmark/bench` and `PipelineBenchmark.java` — is
not here: it lives in
[`../plans/2026-07-28-jmh-benchmarks.md`](../plans/2026-07-28-jmh-benchmarks.md), the
only versioned copy.

## 3. Layout

```
benchmark/                 (gitignored in its entirety)
  pom.xml                  a download manifest and nothing else — never a Maven build
  lib/                     the 4 jars (see §5)
  src/fr/craft/linkedinarchiveexplorer/cli/PipelineBenchmark.java
  out/                     compiled classes + the generated META-INF/BenchmarkList
  results/                 JMH's JSON output, timestamped
  bench                    JEP 330 program: compiles production, compiles the benchmarks, runs JMH
  bench.cmd                one-line Windows shim
```

The launcher lives **inside `benchmark/`**, not in `bin/`: a versioned `bin/bench` would
point at a directory that is absent on a fresh clone.

## 4. The `benchmark/bench` launcher

The same skeleton as [`bin/test`](../../../bin/test) — a JEP 330 program run through
`java --source 17`, using `ToolProvider.findFirst("javac")` for tool invocations, no
build tool anywhere. Three steps:

1. **Compile production as modules** into `out/`, with the exact command `bin/test` uses.
   The JPMS architecture check therefore comes for free: a forbidden dependency fails the
   campaign before it starts.

2. **Compile the benchmarks on the classpath**, with the annotation processor:

   ```
   javac -cp <JMH jars>:<out module directories>
         -processorpath <jmh-core>:<jmh-generator-annprocess>
         -d benchmark/out
         benchmark/src/**/*.java
   ```

   It is the annotation processor that generates the benchmark classes *and* the
   `META-INF/BenchmarkList` and `META-INF/CompilerHints` resources into `-d`.

3. **Run**:

   ```
   java -cp benchmark/out:<JMH jars>:<out module directories>
        org.openjdk.jmh.Main -rf json -rff benchmark/results/<timestamp>.json
   ```

   No uber-jar is needed: JMH forks its measurement JVMs by reusing `java.class.path`,
   and a classpath of directories suits it fine.

Every argument passed to `./benchmark/bench` is **forwarded verbatim to JMH**, after the
launcher's own defaults, so it can override them:

```bash
./benchmark/bench -f 1 -wi 1 -i 3     # quick round-trip
./benchmark/bench endToEnd            # a single benchmark (JMH's filter is a regex)
```

## 5. Dependencies and download

| jar | role |
|---|---|
| `org.openjdk.jmh:jmh-core:1.37` | the runtime |
| `org.openjdk.jmh:jmh-generator-annprocess:1.37` | the annotation processor |
| `net.sf.jopt-simple:jopt-simple` | transitive of `jmh-core` |
| `org.apache.commons:commons-math3` | transitive of `jmh-core` (statistics) |

Roughly 3 MB in total. The two transitives are not named explicitly: the `pom.xml`
declares only the two JMH artifacts, and the download resolves the rest.

```bash
mvn -f benchmark/pom.xml dependency:copy-dependencies -DoutputDirectory=lib
chmod +x benchmark/bench
```

The launcher must be executable: without the `chmod +x`, `./benchmark/bench` fails with
"Permission denied" after a rebuild.

A one-off operation. The `pom.xml` is **never** used to compile or run anything — it is a
download manifest, nothing more. `benchmark/bench` fails with an explicit message when
`benchmark/lib/` is empty, naming this command.

**Version worth re-checking**: 1.37 was the latest known release at the time of writing;
prefer a newer one if it exists. JMH is also sensitive to very recent JDKs — if the
campaign misbehaves on the JDK 26 pinned in `mise.toml`, the fallback is to run it on the
JDK 17 floor.

## 6. The benchmarks

A single class: `fr.craft.linkedinarchiveexplorer.cli.PipelineBenchmark`.

The `cli` package is **forced, not chosen**: `Main.run` is package-private. The benchmark
reaches it through the same mechanism as
[`MainAcceptanceTest`](../../../test/fr/craft/linkedinarchiveexplorer/cli/MainAcceptanceTest.java)
— compiled on the classpath rather than as a module, so the JPMS boundaries do not apply.
It is incidentally the composition root's package, which is the right place to measure the
whole from.

### State

`@State(Scope.Benchmark)`. In `@Setup(Level.Trial)`:

- the archive is resolved by `ArchiveLocator.mostRecent(Path.of("data"))` — a clean,
  explicit failure when `data/` is empty, rather than a silently meaningless measurement;
- the search term is **fixed** (no `@Param`), literal, with default options.

### The measurements

| benchmark | region measured | prepared in `@Setup` |
|---|---|---|
| `endToEnd` | `new Main().run(args, null stream, null stream, false)` | — |
| `openArchive` | `ZipArchive.open` then `close` | — |
| `loadComments` | `CommentsContentSource.load()` | zip opened |
| `loadShares` | `SharesContentSource.load()` | zip opened |
| `loadArticles` | `ArticlesContentSource.load()` | zip opened |
| `search` | `SearchEngine.search(term, contents)` | contents loaded |
| `render` | `TerminalRenderer(false).render(term, results)` | results computed |

`endToEnd` is given `--no-color` and writes to a null `PrintStream`: what is measured is
the computation, not the terminal.

Every method **returns** its value, so that JMH consumes it and the JIT cannot fold the
work away as dead code.

### Settings

`Mode.AverageTime`, `TimeUnit.MILLISECONDS`. The budget is annotated at class level:
`@Fork(2)`, `@Warmup(iterations = 3, time = 5)`, `@Measurement(iterations = 5, time = 5)`
— about 80 s per benchmark, a full campaign around 9 minutes, shortenable through the CLI
arguments of §4.

## 7. What the numbers say — and what they don't

**The disk cache is warm.** `endToEnd` reopens the zip and reloads everything on every
invocation, which is faithful to real behaviour — but once warmup ends, the OS is serving
those bytes from memory. The result therefore measures **CPU cost** (inflate, CSV parsing,
HTML extraction), not a cold first launch. That is what you want in order to compare two
versions of the code; it is not the latency felt on the very first
`./linkedin-archive-explorer`.

**The stages don't add up to the whole.** Shared allocation, GC, cache effects: the gap
between `endToEnd` and the sum of the other measurements is information, not a flaw in the
harness.

**The numbers are not portable.** They depend on the local archive, the machine and the
JDK. They exist to compare *before and after* on one workstation, not to establish an
absolute reference. That is why `benchmark/results/` is timestamped.

## 8. Deliberately left out

- **No regression threshold, no CI.** CI sees nothing of the `benchmark/` directory.
  Machine-dependent numbers have no place in a `pull_request`.
- **No `@Param`.** A fixed term is enough for the goal in §1. The matrix of
  frequent/rare term × `-i`/`-w` options is a trivial later addition.
- **No dedicated test archive.** Measurement runs against the real archive in `data/`,
  already gitignored. Consistent with the project's "inline fixtures" decision: no
  versioned data set.

## 9. Reference results

_Measured 2026-07-28. JDK 26.0.1 (Temurin-26.0.1+8), Linux 7.0.0-28-generic, 699,907-byte
archive._
_These numbers are not portable (§7): they are a before/after comparison point on this
workstation, not an absolute reference._

| Benchmark | Score (ms/op) | Error |
|---|---|---|
| `endToEnd` | 10.549 | ± 0.484 |
| `openArchive` | 0.015 | ± 0.001 |
| `loadComments` | 5.839 | ± 0.305 |
| `loadShares` | 2.180 | ± 0.142 |
| `loadArticles` | 0.171 | ± 0.006 |
| `search` | 1.900 | ± 0.076 |
| `render` | 0.043 | ± 0.003 |

**Reading:** `loadComments` dominates by a wide margin (5.839 ms/op, 55% of `endToEnd`),
well ahead of `loadShares` (2.180 ms/op, 21%). The six stages sum to 10.148 ms/op, close
to `endToEnd` at 10.549 ms/op; the 0.401 ms/op gap is information (shared allocation, GC)
rather than a flaw in the harness. `search`, although it works entirely in memory on
already-loaded content, is not negligible (1.900 ms/op, 18% of `endToEnd`) — but the three
loaders together account for 78%, so that is where the time actually goes. Part of that
0.401 ms/op gap is also known unmeasured work: `SearchResults.from`, the filter-and-sort
pass per `ContentType` that `Main.run` performs between search and rendering, has no
benchmark of its own.
