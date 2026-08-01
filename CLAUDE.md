# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A tool to **explore a LinkedIn data export**. It runs a literal, grep-style search (case-
and accent-sensitive by default) over the user's **comments, posts and articles** and
shows the matches grouped by type, newest first, with clickable LinkedIn links and ~40
characters of context around each occurrence. Two grep-style options relax the match:
`-i`/`--ignore-case` (case-insensitive, accents still significant) and `-w`/`--word`
(whole word only). See
[docs/superpowers/specs/2026-07-24-search-options-design.md](docs/superpowers/specs/2026-07-24-search-options-design.md).

It has **two interchangeable UIs over the same core**: the terminal, and a local web page
served by the JDK's own HTTP server — which is what you get when no argument is given. See
[docs/superpowers/specs/2026-07-31-web-ui-design.md](docs/superpowers/specs/2026-07-31-web-ui-design.md).
It ships as **two bundles**: a `jlink` runtime image for whoever has no JDK, and a lite one
(jar + launchers) for whoever has one. See
[docs/superpowers/specs/2026-08-01-executable-binary-design.md](docs/superpowers/specs/2026-08-01-executable-binary-design.md).

Full design and the rationale behind every decision:
[docs/superpowers/specs/2026-07-22-linkedin-archive-explorer-design.md](docs/superpowers/specs/2026-07-22-linkedin-archive-explorer-design.md).
Read it before making structural changes.

## Hard constraints (do not break)

- **Runtime is JDK-only — no external artifact.** Application code uses only APIs shipped
  with the JDK: no third-party runtime dependency (no Jsoup, no CSV library, no web
  framework). The CSV and HTML parsing are hand-written. The only third-party jar is
  JUnit, and it is **test-scope only** (`lib/junit-platform-console-standalone-*.jar`).
  `java.*` (Java SE) is the rule; the **single exception** is `com.sun.net.httpserver`
  (module `jdk.httpserver`), used **only in the `web` module**. Rationale:
  [the web UI design, § 2](docs/superpowers/specs/2026-07-31-web-ui-design.md).
- **Zero build tool.** No Maven, no Gradle. Compile and package with the JDK's own
  `javac` / `jar` / `java`. (Maven may be used *only* as a one-off downloader for the
  JUnit jar into `lib/`.)
- **Java 17 or newer** — the code is verified to compile and test-pass on JDK 17.
  (`mise.toml` pins 26 for the dev environment.) Do **not** reach for `HttpHandlers` or
  `SimpleFileServer` (Java 18+): raising the floor was weighed and rejected, they buy
  three or four lines. See the web UI design, § 2.
- **Nothing from the archive reaches a page unescaped.** In the `web` module every value
  taken from the archive — and the search term — goes through `HtmlRenderer.escape`. An
  authored post may legitimately contain `<script>`; without this it would run in the
  user's own page. No JavaScript and no static files are served, by design.

## Commands

The dev scripts are **JEP 330 single-file programs** (run via a `java --source` shebang —
no build tool). Run them from the project root; each has a one-line `.cmd` shim for
Windows. `bin/build` and `bin/test` are pure JDK and depend on nothing; `bin/package`
publishes rather than develops, and deliberately shells out to the system `tar` (the JDK's
zip cannot carry the executable bit) — see
[the binary design, § 7](docs/superpowers/specs/2026-08-01-executable-binary-design.md).

- **Run all tests**: `./bin/test` — compiles the modular production tree (this is where
  the architecture is enforced), then compiles and runs the tests on the classpath with
  the JUnit standalone console. (Windows: `bin\test.cmd`.)
- **Compile production only** (fast architecture check):
  `javac --module-source-path src -d out $(find src -name '*.java')`
- **Run a single test class** (after `./bin/test` has compiled `out/` and `out-test/`):
  `java -jar lib/junit-platform-console-standalone-*.jar execute -cp "out-test:$(find out -mindepth 1 -maxdepth 1 -type d | tr '\n' ':')" --select-class fr.craft.linkedinarchiveexplorer.domain.SearchEngineTest`
- **Build**: `./bin/build` — modular compile (enforcement) then packages every
  layer into `dist/linkedin-explorer.jar`. Both compilations pin `--release 17`, so the jar
  runs on the documented floor whatever JDK built it. (Windows: `bin\build.cmd`.)
- **Package a release**: `./bin/package [version]` — builds, then produces the jlink runtime
  image and the lite bundle (jar + launchers), each with its `.sha256`, in `dist/`.
  Publishing is driven by pushing a `v*` tag; the procedure is [RELEASE.md](RELEASE.md).
  `jdk.zipfs` must be named explicitly in the jlink roots: it is found by `ServiceLoader`,
  so no module graph can deduce it, and without it the image starts and then fails on the
  first archive with `Provider not found`.
- **Run the CLI**: `./linkedin-archive-explorer [--archive <path>] [--color|--no-color] [-i|--ignore-case] [-w|--word] <term>`
  — builds the jar on first use; equivalently `java -jar dist/linkedin-explorer.jar …`.
  (default archive: most recent `.zip` in `data/`).
- **Run the web UI**: `./linkedin-archive-explorer [serve] [--archive <path>] [--port <n>] [--no-browser]`
  — serves the search page on `http://localhost:8080` (**loopback only**, never `0.0.0.0`:
  the archive is personal) and opens it in the browser. **With no argument at all the web UI
  is what you get**: `serve` only matters when you need `--port`. Ctrl-C to stop; no
  fallback if the port is taken. The browser is opened through the system's own
  `xdg-open`/`open`/`start`, never `Desktop.browse`, which would pull `java.desktop` into
  the image for +19 MB; failing to open one is never fatal.
- **Run performance benchmarks**: `./benchmark/bench` — the JMH harness is **not in the
  repository** (gitignored); rebuild it first per
  [docs/superpowers/specs/2026-07-28-jmh-benchmarks-design.md](docs/superpowers/specs/2026-07-28-jmh-benchmarks-design.md)
  and [docs/superpowers/plans/2026-07-28-jmh-benchmarks.md](docs/superpowers/plans/2026-07-28-jmh-benchmarks.md)
  (the latter holds the actual source).

## Architecture

Hexagonal (ports & adapters), split into **one Java module per layer**. Boundaries are
enforced at compile time by the **module system (JPMS)** — this replaces ArchUnit,
which needs a build tool. A forbidden dependency simply fails to compile, and the JPMS
forbids cycles.

```
fr.craft.linkedinarchiveexplorer.domain          requires nothing        (model + SearchEngine + ports)
fr.craft.linkedinarchiveexplorer.application      requires domain         (SearchContentsService)
fr.craft.linkedinarchiveexplorer.infrastructure   requires domain         (zip/CSV/HTML adapters)
fr.craft.linkedinarchiveexplorer.launcher         requires the three above (Explorer + ArchiveCatalog
                                                                          — composition root)
fr.craft.linkedinarchiveexplorer.cli              requires domain, application, launcher
                                                                          (Main + TerminalRenderer)
fr.craft.linkedinarchiveexplorer.web              requires domain, application, launcher
                                                  + jdk.httpserver        (WebMain + HtmlRenderer)
fr.craft.linkedinarchiveexplorer.app              requires cli, web        (App — the dispatch)
```

- `application` must **not** `requires infrastructure`; ports (interfaces in `domain`)
  are wired in **`launcher` only**, the single composition root. See
  [its design](docs/superpowers/specs/2026-07-31-composition-root-design.md).
- **No UI module may `requires infrastructure`.** `Explorer.open(…)` hands `cli` and `web`
  a wired `SearchContentsService` over an open archive — and `ArchiveCatalog` does the same
  for *several* archives, listing them and keeping one open, which is why the web UI can
  offer an archive selector without ever naming a file-system adapter
  ([its design](docs/superpowers/specs/2026-08-01-web-archive-selection-design.md)).
  Naming a concrete adapter from a UI module does not compile. A new adapter is therefore
  branched in one place, for both UIs at once.
- **`cli` and `web` are siblings — no edge between them.** Two UI adapters over the same
  core. The choice between them belongs to **`app`**, which requires both and is the jar's
  `Main-Class` and the image's single jlink root; `cli` and `web` export their entry point
  *to `app` only* (qualified export). Never make one UI module require the other — a third
  UI joins at `app`. See
  [the binary design](docs/superpowers/specs/2026-08-01-executable-binary-design.md).
  The `linkedin-archive-explorer` **launch script** (a JEP 330 program outside the module
  graph, not to be confused with the `launcher` module) no longer dispatches anything: it
  just runs the jar.
- **Compile as modules, run as a plain classpath jar**: `module-info.class` are ignored
  at runtime — the enforcement is purely a compile-time guarantee. `jdk.httpserver`
  resolves from the classpath without `--add-modules` (verified).

### Two extension seams (keep them)

1. **UI**: the search core (`domain` + `application`) is independent of any UI — which is
   why the `web` module could be added without touching `domain`, `application` or
   `infrastructure`. Keep it that way. A third UI needs only `Explorer.open(…)`.
2. **HTML extraction**: behind the `ArticleTextExtractor` port. JDK-only implementation
   now; a Jsoup-backed one can replace it without touching anything else.

## Layout

```
src/<module-name>/            one directory per module (--module-source-path), e.g.
  fr.craft.linkedinarchiveexplorer.domain/module-info.java + fr/craft/.../domain/*.java
test/                         tests (compiled & run on the classpath; fixtures inline)
test/data/corrupted.zip       the one versioned fixture (see Conventions)
tools/                        AnonymizeArchive.java (one-off fixture generator)
lib/                          JUnit standalone jar (test-scope)
data/                         the LinkedIn .zip archives (gitignored)
```

## Conventions

- **TDD** (red → green → refactor), following the neighbouring `chatbot` project's
  discipline: outside-in, a testing *diamond* (most confidence from sociable tests over
  real collaborators; the true I/O boundary faked).
- **No mock framework** (JDK-only): substitute collaborators with **hand-written fakes**
  on the domain ports.
- **Type-driven domain**: business concepts are `record`s that validate in their
  **compact constructor** (see `SearchTerm`, `Content`, `Excerpt`).
- **Organise tests, never a flat method list**: group related cases in non-static
  `@Nested` inner classes named for the facet under test (see `SearchEngineTest`:
  `Matching`, `Deduplication`, `ContextExcerpts`); when cases differ only by input, use
  one `@ParameterizedTest` (`@CsvSource`/`@ValueSource`/`@MethodSource`) with a `name =`
  template. `junit-platform-console-standalone` bundles `junit-jupiter-params`.
- **Text blocks, but only when they earn it**: use `"""… """` for a literal *only* if
  it spans more than one line **or** contains characters that are troublesome in a plain
  literal — needing escaping (`"`, `\`) or otherwise problematic (regex
  metacharacters, HTML `<`, …). Keep plain one-line strings otherwise.
- **Fixtures are inline** (decision, kept): build test data in the test itself — text
  blocks for CSV/HTML, small helpers that zip on the fly. Do **not** add a versioned
  `test-resources/fixtures/` directory. **One exception, `test/data/corrupted.zip`**: a
  real zip with its END header truncated away, used by `ZipArchiveTest`,
  `MainAcceptanceTest` and `WebAcceptanceTest` to check that a damaged archive yields the
  JDK's diagnosis rather than a stack trace. No zip writer produces a broken zip, so this
  one cannot be built inline. Tests reach it by the project-root-relative path — keep
  `./bin/test` (and any IDE run configuration) running from the project root.
- Tests run on the classpath, so they can reach package-private members even though
  production compiles as modules.
