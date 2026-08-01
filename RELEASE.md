# Cutting a release

Publishing is driven by a **git tag**. Push a tag that starts with `v`, and
[`.github/workflows/release.yml`](.github/workflows/release.yml) builds every bundle, checks
them, and attaches them to a GitHub release. Nothing else triggers it — merging to `main`
does not.

The design behind all of this, and the reasoning for each choice, is in
[docs/superpowers/specs/2026-08-01-executable-binary-design.md](docs/superpowers/specs/2026-08-01-executable-binary-design.md).

## What a release contains

| File | For whom | Size |
|---|---|---|
| `linkedin-archive-explorer-<version>-linux-x64.tar.gz` | Linux, no JDK needed | ~22 MB |
| `linkedin-archive-explorer-<version>-macos-aarch64.tar.gz` | Apple Silicon, no JDK needed | ~22 MB |
| `linkedin-archive-explorer-<version>-windows-x64.zip` | Windows, no JDK needed | ~22 MB |
| `linkedin-archive-explorer-<version>-lite.tar.gz` / `.zip` | anyone with **Java 17+** | ~60 KB |
| `*.sha256` | one next to each archive | — |

The three fat bundles carry their own Java runtime (a `jlink` image). The lite one carries
only the jar and the same launchers, so **the command is identical whichever you install**.

## 1. Rehearse locally

Never tag something you have not packaged at least once. From the project root:

```sh
./bin/test && ./bin/package 1.2.3
```

`bin/package` writes into `dist/` (git-ignored) and prints one SHA-256 per archive. On Linux
and macOS it produces the `.tar.gz` bundles; on Windows, the `.zip` ones. It only ever builds
the image **for the machine it runs on** — `jlink` does not cross-compile — which is why CI
uses one runner per platform.

Then actually run what you built, from somewhere else, so the check is real:

```sh
cd /tmp && tar xzf ~/dev/projects/linkedin-archive-explorer/dist/linkedin-archive-explorer-1.2.3-linux-x64.tar.gz
./linkedin-archive-explorer-1.2.3/bin/linkedin-archive-explorer --archive <your-export.zip> java
./linkedin-archive-explorer-1.2.3/bin/linkedin-archive-explorer serve --no-browser --port 8099
```

This matters more than it looks: a runtime image can compile, link and start, and still be
missing a service provider that only shows up when an archive is opened. See *When it
fails*, below.

## 2. Tag and push

The tag is the version. The workflow strips the leading `v`, so `v1.2.3` produces
`linkedin-archive-explorer-1.2.3-…`.

```sh
git tag -a v1.2.3 -m "1.2.3"
git push origin v1.2.3
```

Then watch the run:

```sh
gh run watch
```

There is **no `--version` flag in the program**: the version exists only in the file names
and in the release page. Nothing in the source needs editing before tagging.

## 3. What the workflow does

1. **Three runners in parallel** — `ubuntu-latest`, `macos-latest`, `windows-latest` — each
   building the image for its own platform on **JDK 25**, which is the runtime that ends up
   embedded. (The *source* floor stays Java 17, pinned by `--release` in `bin/build` and
   checked by the CI workflow. The two are different things.)
2. Each runner runs the full test suite, then `bin/package`.
3. Each runner **smoke-tests the packaged binary**: it opens a deliberately damaged archive
   and expects a readable diagnosis, then starts the server and expects it to announce its
   port.
4. A final job collects the artifacts and publishes them with `gh release create
   --generate-notes`. The lite bundle is built on all three but only the Linux copy is
   published, since it is platform-independent.

No third-party action is used anywhere, and no secret beyond the token GitHub provides.

## When it fails

**The smoke test fails with `Provider not found`.** The runtime image is missing a service
provider. `jlink` computes its module closure from `requires`, and a provider found through
`ServiceLoader` is invisible to it — that is how `jdk.zipfs`, which `ZipArchive` needs to
open a `.zip`, was once left out. Name the module explicitly in the `--add-modules` list of
`bin/package`. The blanket alternative, `--bind-services`, drags in charsets, locales and
crypto besides.

This failure mode is why the smoke step exists: nothing on the development classpath
reproduces it, because a full JDK provides everything.

**The smoke test fails to find the launcher.** `jlink --launcher` produces a shell script on
Unix and a `.bat` on Windows; the step tries both. If the Windows layout ever changes, that
is the line to fix.

**`tar` behaves oddly on macOS.** It is `bsdtar`, not GNU tar. `bin/package` sets
`COPYFILE_DISABLE=1` so it does not slip `._*` extended-attribute files into the archive;
GNU-only options such as `--sort` or `--mtime` are deliberately unused.

**A run failed halfway.** Delete the tag locally and remotely, fix, and tag again:

```sh
git tag -d v1.2.3 && git push origin :refs/tags/v1.2.3
```

If the release was already created, delete it too (`gh release delete v1.2.3`), otherwise
the re-run will refuse to create it a second time.

## After publishing

Check the release page carries, for each archive, its `.sha256`. The verification command
worth putting in the notes is:

```sh
sha256sum -c linkedin-archive-explorer-1.2.3-linux-x64.tar.gz.sha256
```

A checksum proves the download is intact. It does **not** prove where the file came from —
it is not a signature, and the notes should not suggest it is. Signing options, and why none
is used yet, are in § 11 of the design.

Worth repeating in the notes for macOS users: the bundled Java runtime is unsigned, so an
archive downloaded through a browser is quarantined and Gatekeeper refuses it. The remedy is
in the README.
