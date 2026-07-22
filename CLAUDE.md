# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A command-line tool to **explore a LinkedIn data export**. It runs a literal,
grep-style search (case- and accent-sensitive) over the user's **comments, posts and
articles** and prints the matches grouped by type, newest first, with clickable
LinkedIn links and ~40 characters of context around each occurrence.

Full design and the rationale behind every decision:
[docs/superpowers/specs/2026-07-22-linkedin-archive-explorer-design.md](docs/superpowers/specs/2026-07-22-linkedin-archive-explorer-design.md).
Read it before making structural changes.

## Hard constraints (do not break)

- **Runtime is JDK-only.** Application code imports **only `java.*`** — no third-party
  runtime dependency (no Jsoup, no CSV library). The CSV and HTML parsing are
  hand-written. The only third-party jar is JUnit, and it is **test-scope only**
  (`lib/junit-platform-console-standalone-*.jar`).
- **Zero build tool.** No Maven, no Gradle. Compile and package with the JDK's own
  `javac` / `jar` / `java`. (Maven may be used *only* as a one-off downloader for the
  JUnit jar into `lib/`.)
- **Java 17 or newer** — the code is verified to compile and test-pass on JDK 17.
  (`mise.toml` pins 26 for the dev environment.)

## Commands

The dev scripts are pure-JDK **JEP 330 single-file programs** (run via a `java --source`
shebang — no build tool). Run them from the project root; each has a one-line `.cmd` shim
for Windows.

- **Run all tests**: `./bin/test` — compiles the modular production tree (this is where
  the architecture is enforced), then compiles and runs the tests on the classpath with
  the JUnit standalone console. (Windows: `bin\test.cmd`.)
- **Compile production only** (fast architecture check):
  `javac --module-source-path src -d out $(find src -name '*.java')`
- **Run a single test class** (after `./bin/test` has compiled `out/` and `out-test/`):
  `java -jar lib/junit-platform-console-standalone-*.jar execute -cp "out-test:$(find out -mindepth 1 -maxdepth 1 -type d | tr '\n' ':')" --select-class fr.craft.linkedinarchiveexplorer.domain.SearchEngineTest`
- **Build the CLI**: `./bin/build` — modular compile (enforcement) then packages every
  layer into `dist/linkedin-explorer.jar`. (Windows: `bin\build.cmd`.)
- **Run the CLI**: `./linkedin-archive-explorer [--archive <path>] [--color|--no-color] <term>`
  — builds the jar on first use; equivalently `java -jar dist/linkedin-explorer.jar …`.
  (default archive: most recent `.zip` in `data/`).

## Architecture

Hexagonal (ports & adapters), split into **one Java module per layer**. Boundaries are
enforced at compile time by the **module system (JPMS)** — this replaces ArchUnit,
which needs a build tool. A forbidden dependency simply fails to compile, and the JPMS
forbids cycles.

```
fr.craft.linkedinarchiveexplorer.domain          requires nothing        (model + SearchEngine + ports)
fr.craft.linkedinarchiveexplorer.application      requires domain         (SearchContentsService)
fr.craft.linkedinarchiveexplorer.infrastructure   requires domain         (zip/CSV/HTML adapters)
fr.craft.linkedinarchiveexplorer.cli              requires the three above (Main + TerminalRenderer)
```

- `application` must **not** `requires infrastructure`; ports (interfaces in `domain`)
  are wired only in `cli`, the composition root.
- **Compile as modules, run as a plain classpath jar**: `module-info.class` are ignored
  at runtime — the enforcement is purely a compile-time guarantee.

### Two extension seams (keep them)

1. **UI**: the search core (`domain` + `application`) is independent of the CLI, so a
   future web UI can reuse it unchanged.
2. **HTML extraction**: behind the `ArticleTextExtractor` port. JDK-only implementation
   now; a Jsoup-backed one can replace it without touching anything else.

## Layout

```
src/<module-name>/            one directory per module (--module-source-path), e.g.
  fr.craft.linkedinarchiveexplorer.domain/module-info.java + fr/craft/.../domain/*.java
test/                         tests (compiled & run on the classpath; fixtures inline)
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
  `test-resources/fixtures/` directory.
- Tests run on the classpath, so they can reach package-private members even though
  production compiles as modules.
