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
| `linkedin-archive-explorer-<version>-linux-x64.tar.gz` | Linux, no JDK needed | ~8 MB |
| `linkedin-archive-explorer-<version>-macos-aarch64.tar.gz` | Apple Silicon, no JDK needed | ~8 MB |
| `linkedin-archive-explorer-<version>-windows-x64.zip` | Windows, no JDK needed | ~8 MB |
| `linkedin-archive-explorer-<version>-lite.tar.gz` / `.zip` | anyone with **Java 21+** | ~60 KB |
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

This matters more than it looks: an ahead-of-time build can link and start, and still be
missing something only reached at run time — a service provider, a reflective call. See
*When it fails*, below.

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

1. **Three runners in parallel** — `ubuntu-22.04`, `macos-latest`, `windows-latest` — each
   compiling the binary for its own platform with **GraalVM 25** `native-image`. (The
   *source* floor is Java 21, pinned by `--release` in `bin/build` and checked by the CI
   workflow. The two are different things.) Linux is pinned rather than `latest` because a
   native executable links against the build machine's glibc, and that becomes the floor
   every user has to clear; the workflow prints it.
2. Each runner runs the full test suite, then `bin/package`.
3. Each runner **smoke-tests the packaged binary**: it opens a deliberately damaged archive
   and expects a readable diagnosis, then starts the server and expects it to announce its
   port.
4. A final job collects the artifacts and publishes them with `gh release create
   --generate-notes`. The lite bundle is built on all three but only the Linux copy is
   published, since it is platform-independent.

One third-party action is used, `graalvm/setup-graalvm`, and no secret beyond the token
GitHub provides.

## When it fails

**The smoke test fails on the first archive.** Something reached only at run time was left
out of the closed world native-image computes. GraalVM resolves `ServiceLoader` providers at
build time from the JDK's own `provides` clauses, so `jdk.zipfs` comes along by itself — but
reflection does not. Declare what is missing in a reflection configuration.

**The binary dies with `Illegal instruction`.** The CPU is older than the baseline it was
compiled for. `bin/package` pins `-march=compatibility` to avoid exactly this; check that it
has not been changed.

**`GLIBC_2.xx not found`.** The user's distribution is older than the runner that built the
binary. The floor is printed by the workflow; lowering it means building in an older
container, or a static build against musl.

These failure modes are why the smoke step exists: nothing on the development classpath
reproduces them, because a full JVM provides everything and decides at run time.

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
