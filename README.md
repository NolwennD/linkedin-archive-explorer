# LinkedIn Archive Explorer

A tool to **explore a LinkedIn data export** and quickly find the **comments, posts and
articles** that mention a given topic — with a snippet of context and a clickable link to
the content on LinkedIn.

It comes with **two interfaces over the same search engine**: a command line, and a
[local web page](#web-interface) if you would rather search from a browser.

You can ask for your data on this [LinkedIn page](https://www.linkedin.com/mypreferences/d/download-my-data).
You will receive two archives, first one a few hours later, the second in a few days. **This tool uses the second**. 

Example: searching for `Date(0,0,0)` lists every piece of your content that mentions it,
grouped by type and sorted from newest to oldest.

## What it does

- **Literal** search (like `grep`), **case- and accent-sensitive** by default: `Date` ≠
  `date`, `developpeur` ≠ `développeur`. Two `grep`-style options relax it: `-i` ignores
  case, `-w` matches whole words only. Accents always stay significant.
- Results **grouped by type** (articles, posts, comments) and **sorted by descending
  date**.
- For each match: a **clickable link** to LinkedIn (OSC 8 hyperlinks in the terminal, plain
  links on the web page) and the **list of excerpts** (~40 characters of context around
  each occurrence, term highlighted).
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
./linkedin-archive-explorer [--archive <path>] [--color|--no-color] [-i] [-w] <term>
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
- `-i` / `--ignore-case`: ignore case, so `date` also finds `Date` and `DATE`. **Accents
  remain significant**: `developpe` still does not find `développe`.
- `-w` / `--word`: match **whole words only**, so `dev` no longer matches inside
  `developpeur`. The short flags combine: `-iw` (or `-wi`).
- `--color` / `--no-color`: force or disable colors and clickable links. **Enabled by
  default** in a terminal, and automatically disabled when the output is redirected (pipe,
  file). The `NO_COLOR` environment variable is also respected. Konsole tip: **Ctrl+click**
  to open a link.

### Example

```sh
./linkedin-archive-explorer --archive Complete_LinkedInDataExport.zip "Date(0,0,0)"
```

If the term is found nowhere, the program prints `No results for "…"`.

## Web interface

Same search, same results, in a browser:

```sh
./linkedin-archive-explorer serve
```

Then open **<http://localhost:8080>**. Press Ctrl-C to stop the server.

```sh
./linkedin-archive-explorer serve [--archive <path>] [--port <n>]
```

- `--archive <path>`: same meaning as for the command line, with one addition — the file
  is what the page's archive field starts on, and it is suggested there. Omitted, the most
  recent `.zip` in `data/` is used; if `data/` is empty the server still starts and the
  page asks you for a path.
- `--port <n>`: the port to listen on, **8080** by default. If it is already taken the
  server says so and stops; it never silently picks another one, so the address you were
  given is always the address that works.

### What the page gives you

- A **search field** and the two option checkboxes (*Ignore case*, *Whole word*), matching
  the `-i` and `-w` flags.
- An **archive field**: a path you can type or paste, with the `.zip` files of `data/`
  offered as suggestions (most recent first). It starts filled with the most recent one, it
  sits in the search form — so you switch archive by running a search — and the choice is
  **remembered** in a cookie, so the next start-up opens the archive you last used. A path
  that does not open comes back with the reason, the path kept so you can fix it.
- Results **grouped by type**, each group **collapsible** — handy when a common term
  returns hundreds of comments and buries everything else. The count stays visible on the
  collapsed group.
- The search lives **entirely in the URL** (`/?q=café&i=on&archive=export.zip`), so a
  search is bookmarkable and shareable, and the browser's back button becomes your search
  history. An `archive=` in the URL always wins over the remembered one.

### Privacy

The server listens on **`127.0.0.1` only** — never on your network interface. Your export
is personal data, so it stays on your machine, and nobody else on the network can reach it.
Note that the archive field takes any path, so the server can open any file you can read —
the same reach `--archive` already has, on a loopback server meant for one person.
There is **no JavaScript** and no file serving: the page is plain HTML built server-side,
and everything coming out of the archive is escaped, so a post of yours containing markup
is shown as text rather than executed.

## Multiple archives

Each LinkedIn export is a full snapshot. If `data/` contains several `.zip` files, the
program automatically uses the **most recent** one (date read from the file name, otherwise
the last-modified time). Use `--archive` to target a specific one.

In the web UI you do not have to choose up front: the page suggests them all and lets you
switch between searches — or point at an archive kept anywhere else (see above).
