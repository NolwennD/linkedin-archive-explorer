# LinkedIn Archive Explorer

A command-line tool to **explore a LinkedIn data export** and quickly find the
**comments, posts and articles** that mention a given topic — with a snippet of context
and a clickable link to the content on LinkedIn.

You can ask for your data on this [LinkedIn page](https://www.linkedin.com/mypreferences/d/download-my-data).
You will receive two archives, first one a few hours later, the second in a few days. **This tool uses the second**. 

Example: searching for `Date(0,0,0)` lists every piece of your content that mentions it,
grouped by type and sorted from newest to oldest.

## What it does

- **Literal** search (like `grep`), **case- and accent-sensitive**: `Date` ≠ `date`,
  `developpeur` ≠ `développeur`.
- Results **grouped by type** (articles, posts, comments) and **sorted by descending
  date**.
- For each match: a **clickable link** to LinkedIn (OSC 8 terminal hyperlinks) and the
  **list of excerpts** (~40 characters of context around each occurrence, term
  highlighted).
- Content that matches several times appears **only once**, with all of its excerpts.

## Which files it reads

The archive is the `.zip` provided by LinkedIn ("Get a copy of your data"). Three sources
are read as-is, without extracting anything to disk:

| Content | File in the archive | Columns / fields used |
|---|---|---|
| Comments | `Comments_*.csv` | `Date`, `Link`, `Message` |
| Posts / shares | `Shares_*.csv` | `Date`, `ShareLink`, `ShareCommentary` |
| Articles | `Articles/**/*.html` | title, URL (the `<h1>` link) and article text |

Articles exported by LinkedIn have no publication date: they are shown without a date, at
the end of their group.

## Prerequisites

**Java 17 or newer** (JDK). This is the only dependency: the program uses just the
standard library — no build tool and no external libraries.

The JDK version is pinned in `mise.toml`. If you use [mise](https://mise.jdx.dev/), install
it from the project directory with:

```sh
mise install
```

## Build

```sh
./bin/build          # Linux / macOS   (Windows: bin\build.cmd)
```

This produces a standalone jar: `dist/linkedin-explorer.jar`. You don't have to run this
by hand — the launcher below builds it on first use.

To open the project in **IntelliJ IDEA**, run `./ide/setup-intellij` once (or
`ide\setup-intellij.cmd`) to install the config; see [docs/intellij.md](docs/intellij.md).

## Usage

The easiest way is the **launcher** — a single-file Java program
([JEP 330](https://openjdk.org/jeps/330), run via a shebang, no build tool involved) that
builds the jar on first use:

```sh
./linkedin-archive-explorer [--archive <path>] [--color|--no-color] <term>
```

Run it from the project root: if `dist/linkedin-explorer.jar` is missing it is built
automatically, then run. The equivalent explicit command is:

```sh
java -jar dist/linkedin-explorer.jar [--archive <path>] [--color|--no-color] <term>
```

- `<term>`: the text to search for (quote it if it contains spaces or special characters,
  e.g. `"Date(0,0,0)"`).
- `--archive <path>`: the archive to explore. **When this flag is omitted**, the program
  uses the **most recent** `.zip` in the `data/` directory — recency is based on the date
  in the file name (`MM-DD-YYYY`, e.g. `Complete_LinkedInDataExport_07-21-2026.zip`),
  falling back to the last-modified time. So the usual workflow is: drop your export into
  `data/` and run the command with just a search term.
- `--color` / `--no-color`: force or disable colors and clickable links. **Enabled by
  default** in a terminal, and automatically disabled when the output is redirected (pipe,
  file). The `NO_COLOR` environment variable is also respected. Konsole tip: **Ctrl+click**
  to open a link.

### Example

```sh
./linkedin-archive-explorer --archive Complete_LinkedInDataExport.zip "Date(0,0,0)"
```

If the term is found nowhere, the program prints `No results for "…"`.

## Multiple archives

Each LinkedIn export is a full snapshot. If `data/` contains several `.zip` files, the
program automatically uses the **most recent** one (date read from the file name, otherwise
the last-modified time). Use `--archive` to target a specific one.
