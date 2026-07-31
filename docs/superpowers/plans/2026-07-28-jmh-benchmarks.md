# JMH performance benchmarks — Implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Measure the tool's whole pipeline (unzip → CSV → HTML → search → render) against the real archive in `data/`, producing one end-to-end figure and a stage-by-stage breakdown.

**Architecture:** The entire harness lives in a **gitignored** `benchmark/` directory, with its own JEP 330 launcher modelled on `bin/test`. JMH is used as a `javac` annotation processor plus a `main()`, with no build tool involved. The benchmarks compile on the classpath (as the tests do), which is what grants access to the package-private `Main.run`.

**Tech Stack:** JDK 17+ (`javac`, `java`, `ToolProvider`), JMH 1.37, Maven as a one-off downloader only.

**Reference spec:** [`docs/superpowers/specs/2026-07-28-jmh-benchmarks-design.md`](../specs/2026-07-28-jmh-benchmarks-design.md)

## Global Constraints

- **JDK-only runtime**: nothing in this plan enters `dist/linkedin-explorer.jar`. The code under `src/` is **not modified**.
- **Zero build tool**: Maven is used exactly once, for `dependency:copy-dependencies`. Never to compile, never to run.
- **Java 17 is the floor**: the launcher is declared `java --source 17`, like `bin/test` and `bin/build`.
- **`benchmark/` is entirely gitignored**: one single commit in the whole plan (Task 1). Tasks 2 and 3 produce **nothing committable** — that is intended, do not force a `git add`.
- **Scripts run from the project root**: every path is relative to it, as in `bin/test`.
- **Explicit imports, no wildcards** — repository convention.
- **Text blocks only when they earn it**: multi-line literals, or literals holding characters that are awkward to escape (the `CLAUDE.md` rule).

## File Structure

| File | Responsibility | Versioned? |
|---|---|---|
| `.gitignore` | Adds the `benchmark/` line | ✅ yes |
| `benchmark/pom.xml` | Jar download manifest. Nothing else. | ❌ |
| `benchmark/bench` | JEP 330 launcher: compiles production, compiles the benchmarks, runs JMH | ❌ |
| `benchmark/bench.cmd` | One-line Windows shim | ❌ |
| `benchmark/src/fr/craft/linkedinarchiveexplorer/cli/PipelineBenchmark.java` | The 7 measurements | ❌ |
| `docs/superpowers/specs/2026-07-28-jmh-benchmarks-design.md` | Gains a §9 "reference results" in Task 4 | ✅ yes |

**On the duplication:** `benchmark/bench` copies five helpers from `bin/test` (`runTool`, `javaFiles`, `concat`, `deleteRecursively`, `moduleOutputClasspath`). This is deliberate: `bin/test` is versioned, `benchmark/bench` is not — factoring them out would make a committed file depend on one that is absent on a fresh clone.

---

### Task 1: Ignore `benchmark/` and download the JMH jars

**Files:**
- Modify: `.gitignore`
- Create: `benchmark/pom.xml`
- Create (downloaded): `benchmark/lib/*.jar`

**Interfaces:**
- Consumes: nothing.
- Produces: `benchmark/lib/` holding 4 jars, among them `jmh-core-1.37.jar` and `jmh-generator-annprocess-1.37.jar`. Task 2 builds its classpath by listing that directory.

- [ ] **Step 1: Add the line to `.gitignore`**

Append to `.gitignore`:

```gitignore

# Harnais de performance JMH : entièrement hors dépôt (jars, sources, résultats).
# La recette pour le remonter après un clone est dans
# docs/superpowers/specs/2026-07-28-jmh-benchmarks-design.md (le contexte) et
# docs/superpowers/plans/2026-07-28-jmh-benchmarks.md (la source, seule copie versionnée
# de bench et PipelineBenchmark.java).
benchmark/
```

- [ ] **Step 2: Check the ignore works before creating anything**

```bash
mkdir -p benchmark && touch benchmark/probe && git check-ignore -v benchmark/probe && rm benchmark/probe
```

Expected: a line reading `.gitignore:<number>:benchmark/	benchmark/probe`. If the command prints nothing, the ignore is not taking effect — fix that before going further.

- [ ] **Step 3: Commit (the only commit in this plan)**

```bash
git add .gitignore && git commit -m "chore: ignorer le harnais de performance benchmark/"
```

- [ ] **Step 4: Write the download manifest**

Create `benchmark/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
  MANIFESTE DE TÉLÉCHARGEMENT UNIQUEMENT.

  Ce POM n'est jamais utilisé pour compiler ni pour lancer quoi que ce soit — la
  contrainte « zéro build tool » du projet reste entière. Il ne sert qu'à résoudre les
  jars JMH et leurs transitives (jopt-simple, commons-math3) vers benchmark/lib/ :

    mvn -q -f benchmark/pom.xml dependency:copy-dependencies -DoutputDirectory=lib
-->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>fr.craft</groupId>
  <artifactId>linkedin-archive-explorer-benchmark-deps</artifactId>
  <version>0</version>
  <packaging>pom</packaging>

  <properties>
    <jmh.version>1.37</jmh.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.openjdk.jmh</groupId>
      <artifactId>jmh-core</artifactId>
      <version>${jmh.version}</version>
    </dependency>
    <dependency>
      <groupId>org.openjdk.jmh</groupId>
      <artifactId>jmh-generator-annprocess</artifactId>
      <version>${jmh.version}</version>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 5: Download the jars**

⚠️ **This step reaches the network and writes jars to disk. Get explicit approval before running it.**

```bash
mvn -q -f benchmark/pom.xml dependency:copy-dependencies -DoutputDirectory=lib
```

`-DoutputDirectory=lib` is relative to the POM's `basedir`, so the jars land in `benchmark/lib/`.

- [ ] **Step 6: Check the contents of `benchmark/lib/`**

```bash
ls -1 benchmark/lib/
```

Expected: exactly four jars —

```
commons-math3-3.6.1.jar
jmh-core-1.37.jar
jmh-generator-annprocess-1.37.jar
jopt-simple-5.0.4.jar
```

The two transitives' version numbers may differ depending on what JMH 1.37 resolves — that is not a problem, since the launcher lists the directory rather than naming files. **Four jars is the expected count**, however: fewer means `jmh-core` did not pull its transitives, and Task 2's compilation will fail.

- [ ] **Step 7: Check the repository stayed clean**

```bash
git status --short
```

Expected: **no output**. If `benchmark/` shows up, the Step 1 ignore did not take.

---

### Task 2: The `benchmark/bench` launcher and a first end-to-end benchmark

This task validates the entire toolchain — annotation processing, `META-INF/BenchmarkList` generation, the classpath, forking the measurement JVMs — against **one single** benchmark. It is the plan's risk point, so it is cleared first.

**Files:**
- Create: `benchmark/bench`
- Create: `benchmark/bench.cmd`
- Create: `benchmark/src/fr/craft/linkedinarchiveexplorer/cli/PipelineBenchmark.java`

**Interfaces:**
- Consumes: `benchmark/lib/*.jar` (Task 1).
- Produces:
  - `./benchmark/bench [JMH options…]` — compiles then runs, forwarding its arguments to JMH.
  - `benchmark/out/` — compiled classes + `META-INF/BenchmarkList`.
  - `benchmark/results/<yyyy-MM-dd-HHmmss>.json` — the JMH report.
  - The class `fr.craft.linkedinarchiveexplorer.cli.PipelineBenchmark`, which Task 3 extends.

- [ ] **Step 1: Choose the measured term**

The term must actually occur in the archive, otherwise `search` and `render` would measure nothing. Check:

```bash
./linkedin-archive-explorer --no-color craft | head -20
```

Expected: at least one result. If the output is empty, try another frequent word (`java`, `équipe`, `2024`…) and keep whichever produces results. **Carry the chosen word into the `TERM` constant in Step 3.**

- [ ] **Step 2: Write the launcher**

Create `benchmark/bench`:

```java
#!/usr/bin/env -S java --source 17

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Pure-JDK benchmark runner — a JEP 330 single-file source-code program (run as
 * {@code ./benchmark/bench}, no build tool involved). Compiles the modular production
 * tree (same command as {@code bin/test}, so the architecture is checked before any
 * measurement), then compiles the benchmarks on the classpath with the JMH annotation
 * processor and hands over to {@code org.openjdk.jmh.Main}. Run from the project root.
 *
 * <p>Every argument is forwarded to JMH: {@code ./benchmark/bench -f 1 -wi 1 -i 3} for a
 * quick round-trip, {@code ./benchmark/bench endToEnd} to run a single benchmark.
 */
public final class Bench {

  private static final Path ROOT = Path.of("benchmark");
  private static final Path LIB = ROOT.resolve("lib");
  private static final Path SRC = ROOT.resolve("src");
  private static final Path OUT = ROOT.resolve("out");
  private static final Path RESULTS = ROOT.resolve("results");

  public static void main(String[] args) throws IOException, InterruptedException {
    String jmh = jmhJars();

    deleteRecursively(OUT);
    deleteRecursively(Path.of("out"));
    Files.createDirectories(OUT);
    Files.createDirectories(RESULTS);

    // 1. Production compile as modules — identical to bin/test, so a forbidden
    //    inter-module dependency fails here rather than mid-campaign.
    runTool("javac", concat(List.of("--module-source-path", "src", "-d", "out"), javaFiles("src")));

    String moduleCp = moduleOutputClasspath();

    // 2. Benchmarks compile on the classpath (so they can reach package-private members,
    //    like the tests do). The annotation processor generates the benchmark classes and
    //    the META-INF/BenchmarkList resource into -d.
    runTool("javac", concat(
        List.of(
            "-cp", jmh + File.pathSeparator + moduleCp,
            "-processorpath", jmh,
            "-d", OUT.toString()),
        javaFiles(SRC.toString())));

    // 3. Hand over to JMH. It forks its own measurement JVMs, rebuilding their classpath
    //    from java.class.path — a classpath of directories is enough, no uber-jar needed.
    List<String> command = new ArrayList<>(List.of(
        "java",
        "-cp", OUT + File.pathSeparator + jmh + File.pathSeparator + moduleCp,
        "org.openjdk.jmh.Main"));
    // Two independent guards: supplying one of these must not silently drop the other.
    // (JMH's own defaults would otherwise write jmh-result.csv at the project root,
    // which is not gitignored.)
    List<String> forwarded = List.of(args);
    if (!forwarded.contains("-rf")) {
      command.addAll(List.of("-rf", "json"));
    }
    if (!forwarded.contains("-rff")) {
      command.addAll(List.of("-rff", RESULTS.resolve(timestamp() + ".json").toString()));
    }
    command.addAll(forwarded);

    System.exit(new ProcessBuilder(command).inheritIO().start().waitFor());
  }

  /** Every jar in benchmark/lib, joined — the JMH runtime, its processor and transitives. */
  private static String jmhJars() throws IOException {
    List<String> jars = List.of();
    if (Files.isDirectory(LIB)) {
      try (Stream<Path> paths = Files.list(LIB)) {
        jars = paths.map(Path::toString).filter(p -> p.endsWith(".jar")).sorted().toList();
      }
    }
    if (jars.isEmpty()) {
      System.err.println("""
          No JMH jar found in benchmark/lib/. Download them once with:
            mvn -q -f benchmark/pom.xml dependency:copy-dependencies -DoutputDirectory=lib""");
      System.exit(1);
    }
    return String.join(File.pathSeparator, jars);
  }

  private static String timestamp() {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss").format(LocalDateTime.now());
  }

  /** Colon/semicolon-joined list of every compiled module output directory under out/. */
  private static String moduleOutputClasspath() throws IOException {
    try (Stream<Path> dirs = Files.list(Path.of("out"))) {
      return dirs.filter(Files::isDirectory).map(Path::toString)
          .collect(Collectors.joining(File.pathSeparator));
    }
  }

  private static void runTool(String name, List<String> arguments) {
    ToolProvider tool = ToolProvider.findFirst(name)
        .orElseThrow(() -> new IllegalStateException("JDK tool not found: " + name));
    int status = tool.run(System.out, System.err, arguments.toArray(String[]::new));
    if (status != 0) {
      System.err.println(name + " failed (exit " + status + ")");
      System.exit(status);
    }
  }

  private static List<String> javaFiles(String dir) throws IOException {
    try (Stream<Path> paths = Files.walk(Path.of(dir))) {
      return paths.filter(p -> p.toString().endsWith(".java")).map(Path::toString).sorted().toList();
    }
  }

  private static List<String> concat(List<String> head, List<String> tail) {
    List<String> all = new ArrayList<>(head);
    all.addAll(tail);
    return all;
  }

  private static void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(root)) {
      paths.sorted(Comparator.reverseOrder()).forEach(p -> {
        try {
          Files.delete(p);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    }
  }
}
```

Make it executable:

```bash
chmod +x benchmark/bench
```

- [ ] **Step 3: Write the Windows shim**

Create `benchmark/bench.cmd` (one line, CRLF endings per `.gitattributes` — the file is not versioned, but staying consistent costs nothing):

```bat
@cd /d "%~dp0.." && java --source 17 benchmark\bench %*
```

- [ ] **Step 4: Write the end-to-end benchmark, on its own**

Create `benchmark/src/fr/craft/linkedinarchiveexplorer/cli/PipelineBenchmark.java`. The `cli` package is forced: `Main.run` is package-private, and compiling on the classpath grants the same access the tests have.

**Replace the value of `TERM` with the word chosen in Step 1.**

```java
package fr.craft.linkedinarchiveexplorer.cli;

import fr.craft.linkedinarchiveexplorer.infrastructure.ArchiveLocator;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Macro benchmarks of the whole pipeline, measured on the real archive in data/.
 * Lives in the cli package because Main.run is package-private.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(2)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 5)
public class PipelineBenchmark {

  private static final String TERM = "craft";
  private static final Path ARCHIVE_DIR = Path.of("data");

  private Path archive;
  private PrintStream discard;

  @Setup(Level.Trial)
  public void setUp() {
    archive = ArchiveLocator.mostRecent(ARCHIVE_DIR).orElseThrow(() ->
        new IllegalStateException("No .zip archive in " + ARCHIVE_DIR.toAbsolutePath()));
    discard = new PrintStream(OutputStream.nullOutputStream());
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    discard.close();
  }

  /** The user-visible run: open, load everything, search, render. Output goes nowhere. */
  @Benchmark
  public int endToEnd() {
    return new Main().run(
        new String[] {"--archive", archive.toString(), "--no-color", TERM},
        discard, discard, false);
  }
}
```

- [ ] **Step 5: Check the error message when the jars are missing**

```bash
mv benchmark/lib benchmark/lib.off && ./benchmark/bench; mv benchmark/lib.off benchmark/lib
```

Expected: on stderr, `No JMH jar found in benchmark/lib/.` followed by the `mvn` command, and exit code 1. The launcher must **not** blow up with a `NoSuchFileException`.

- [ ] **Step 6: Run the benchmark in fast mode**

```bash
./benchmark/bench -f 1 -wi 1 -i 2 -w 2 -r 2 endToEnd
```

(`-w`/`-r` = the duration of one warmup / measurement iteration, here 2 s, for a round-trip of roughly 10 s.)

Expected, in this order:
1. both compilations pass without error;
2. JMH prints `# JMH version: 1.37` and `# Benchmark: fr.craft.linkedinarchiveexplorer.cli.PipelineBenchmark.endToEnd`;
3. a final table of the form

```
Benchmark                    Mode  Cnt   Score   Error  Units
PipelineBenchmark.endToEnd   avgt    2  xx,xxx          ms/op
```

**If `Unable to find the resource: /META-INF/BenchmarkList` appears**: the annotation processor did not run — check that `-processorpath` really points at the jars and that `jmh-generator-annprocess` is present in `benchmark/lib/`.

**If JMH refuses to start on the JDK 26** pinned in `mise.toml`: rerun on the JDK 17 floor. `JAVA_HOME` **has no effect here** — the `#!/usr/bin/env -S java --source 17` shebang resolves `java` from `PATH`, and so does the launcher's `ProcessBuilder("java", …)`; you must therefore prefix `PATH` rather than set `JAVA_HOME`:

```bash
mise install java@17   # if not already installed — absent by default on this machine
PATH="$(mise where java@17)/bin:$PATH" ./benchmark/bench -f 1 -wi 1 -i 2 endToEnd
```

and record the fallback for Task 4.

- [ ] **Step 7: Check the JSON report was written**

```bash
ls -1 benchmark/results/
```

Expected: a non-empty `<yyyy-MM-dd-HHmmss>.json` file.

- [ ] **Step 8: Check the repository stayed clean**

```bash
git status --short
```

Expected: **no output**. No commit for this task — everything is gitignored, which is the intended behaviour.

---

### Task 3: The six stage-level benchmarks

**Files:**
- Modify: `benchmark/src/fr/craft/linkedinarchiveexplorer/cli/PipelineBenchmark.java` (wholesale replacement)

**Interfaces:**
- Consumes: the launcher and the class from Task 2.
- Produces: seven `@Benchmark` methods — `endToEnd`, `openArchive`, `loadComments`, `loadShares`, `loadArticles`, `search`, `render`.

- [ ] **Step 1: Replace `PipelineBenchmark.java` wholesale**

**Keep the `TERM` value chosen in Task 2 Step 1.**

```java
package fr.craft.linkedinarchiveexplorer.cli;

import fr.craft.linkedinarchiveexplorer.application.SearchResults;
import fr.craft.linkedinarchiveexplorer.domain.Content;
import fr.craft.linkedinarchiveexplorer.domain.ContentSource;
import fr.craft.linkedinarchiveexplorer.domain.SearchEngine;
import fr.craft.linkedinarchiveexplorer.domain.SearchHit;
import fr.craft.linkedinarchiveexplorer.domain.SearchTerm;
import fr.craft.linkedinarchiveexplorer.infrastructure.ArchiveLocator;
import fr.craft.linkedinarchiveexplorer.infrastructure.ArticlesContentSource;
import fr.craft.linkedinarchiveexplorer.infrastructure.CommentsContentSource;
import fr.craft.linkedinarchiveexplorer.infrastructure.JdkArticleTextExtractor;
import fr.craft.linkedinarchiveexplorer.infrastructure.SharesContentSource;
import fr.craft.linkedinarchiveexplorer.infrastructure.ZipArchive;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Macro benchmarks of the whole pipeline, measured on the real archive in data/:
 * one end-to-end figure plus a stage-by-stage breakdown. Lives in the cli package
 * because Main.run is package-private — the same classpath trick the tests use.
 *
 * <p>Every method returns its value so JMH consumes it and the JIT cannot fold the
 * work away as dead code.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(2)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 5)
public class PipelineBenchmark {

  private static final String TERM = "craft";
  private static final Path ARCHIVE_DIR = Path.of("data");

  private Path archive;
  private SearchTerm term;
  private PrintStream discard;

  private ZipArchive zip;
  private ContentSource comments;
  private ContentSource shares;
  private ContentSource articles;
  private List<Content> contents;
  private SearchResults results;

  /**
   * Prepares state for all seven benchmarks — the load, search and grouping done here is
   * unused by {@code openArchive} and {@code endToEnd}. Runs once per trial, outside any
   * measured region.
   */
  @Setup(Level.Trial)
  public void setUp() {
    archive = ArchiveLocator.mostRecent(ARCHIVE_DIR).orElseThrow(() ->
        new IllegalStateException("No .zip archive in " + ARCHIVE_DIR.toAbsolutePath()));
    term = SearchTerm.literal(TERM);
    discard = new PrintStream(OutputStream.nullOutputStream());

    zip = ZipArchive.open(archive);
    comments = new CommentsContentSource(zip);
    shares = new SharesContentSource(zip);
    articles = new ArticlesContentSource(zip, new JdkArticleTextExtractor());

    contents = List.of(comments, shares, articles).stream()
        .flatMap(source -> source.load().stream())
        .toList();
    // Fail loudly rather than measure emptiness.
    if (contents.isEmpty()) {
      throw new IllegalStateException("Archive loaded no content at all: " + archive);
    }

    results = SearchResults.from(new SearchEngine().search(term, contents));
    if (results.groups().isEmpty()) {
      throw new IllegalStateException(
          "Term '" + TERM + "' matches nothing — pick a term present in the archive");
    }
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    zip.close();
    discard.close();
  }

  /** The user-visible run: open, load everything, search, render. Output goes nowhere. */
  @Benchmark
  public int endToEnd() {
    return new Main().run(
        new String[] {"--archive", archive.toString(), "--no-color", TERM},
        discard, discard, false);
  }

  /** Opening alone: the zip central directory is read, no entry is inflated yet. */
  @Benchmark
  public ZipArchive openArchive() {
    try (ZipArchive opened = ZipArchive.open(archive)) {
      return opened; // returned closed, purely so JMH consumes the result
    }
  }

  @Benchmark
  public List<Content> loadComments() {
    return comments.load();
  }

  @Benchmark
  public List<Content> loadShares() {
    return shares.load();
  }

  /**
   * CSV plus HTML extraction through JdkArticleTextExtractor. Its rank among the load*
   * benchmarks tracks the archive's article count, not the per-article cost.
   */
  @Benchmark
  public List<Content> loadArticles() {
    return articles.load();
  }

  @Benchmark
  public List<SearchHit> search() {
    return new SearchEngine().search(term, contents);
  }

  @Benchmark
  public String render() {
    return new TerminalRenderer(false).render(term, results);
  }
}
```

- [ ] **Step 2: Check all seven benchmarks are discovered**

```bash
./benchmark/bench -l
```

Expected: the seven names listed, all prefixed `fr.craft.linkedinarchiveexplorer.cli.PipelineBenchmark.` —
`endToEnd`, `loadArticles`, `loadComments`, `loadShares`, `openArchive`, `render`, `search`.

- [ ] **Step 3: Fast pass over all seven**

```bash
./benchmark/bench -f 1 -wi 1 -i 2 -w 2 -r 2
```

Expected: a seven-row final table, each row carrying a numeric `Score` and the unit `ms/op`. No row may show `NaN` or `≈ 10⁻⁶`.

Coherence checks — if one fails, the measurement is wrong and you must understand why before Task 4:
- `endToEnd` must be **the slowest** of them all;
- `openArchive` must be markedly faster than any `load*`;
- `search` and `render` work on data already in memory: they must come out far below the sum of the `load*` figures.

- [ ] **Step 4: Check the repository stayed clean**

```bash
git status --short
```

Expected: **no output**.

---

### Task 4: Reference campaign and recording the numbers

**Files:**
- Modify: `docs/superpowers/specs/2026-07-28-jmh-benchmarks-design.md` (adding a §9)

**Interfaces:**
- Consumes: the seven benchmarks from Task 3.
- Produces: a dated §9 "Reference results" in the spec — the only versioned trace of the measurements.

- [ ] **Step 1: Run the full campaign**

Roughly nine minutes. Close other demanding applications; plug the machine into mains power if it is a laptop (frequency scaling skews the measurements).

```bash
./benchmark/bench
```

Expected: the seven-row final table, this time with a populated `Error` column (`± x,xxx`).

- [ ] **Step 2: Collect the measurement context**

```bash
java -version 2>&1 | head -2 && uname -sr && ls -l data/*.zip
```

These three pieces of information are what make the numbers interpretable later.

- [ ] **Step 3: Add §9 to the spec**

Append to `docs/superpowers/specs/2026-07-28-jmh-benchmarks-design.md`, replacing every angle-bracketed value with what Step 1 measured and Step 2 collected:

```markdown
## 9. Reference results

_Measured <date>. JDK <version>, <OS>, <size> archive._
_These numbers are not portable (§7): they are a before/after comparison point on this
workstation, not an absolute reference._

| Benchmark | Score (ms/op) | Error |
|---|---|---|
| `endToEnd` | <score> | ± <error> |
| `openArchive` | <score> | ± <error> |
| `loadComments` | <score> | ± <error> |
| `loadShares` | <score> | ± <error> |
| `loadArticles` | <score> | ± <error> |
| `search` | <score> | ± <error> |
| `render` | <score> | ± <error> |

**Reading:** <one to three sentences — which stage dominates, and the gap between
endToEnd and the sum of the stages.>
```

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-07-28-jmh-benchmarks-design.md
git commit -m "docs: résultats de référence des benchmarks JMH"
```

- [ ] **Step 5: Final verification**

```bash
git status --short && ./bin/test 2>&1 | tail -5
```

Expected: `git status` silent, and the test suite still green — no change touched `src/` or `test/`.

---

## Watch-outs for whoever executes this

1. **Do not try to commit `benchmark/`.** Three of the four tasks produce no commit at all. That is the spec's central decision (§2), not an oversight.
2. **Do not add JUnit tests for the harness.** They would themselves be gitignored, and lost on a clone. Verification goes through execution instead, which is why the "Expected: …" steps are spelled out in detail.
3. **Do not touch `src/`.** If a benchmark fails to compile, the cause is in the harness, never in production code.
4. **The first run's numbers may surprise you.** The archive is 700 KB: `endToEnd` will likely land in the tens of milliseconds, not seconds. That is not a measurement error.
