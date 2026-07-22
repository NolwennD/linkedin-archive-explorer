#!/usr/bin/env python3
"""Manual mutation-testing harness (not part of the build).

For each mutation it applies one exact, unique text replacement in a production file,
runs ./test.sh, then restores the original from memory (even on failure) -- so nothing
is ever left mutated on disk. A mutation is KILLED if the suite goes red, SURVIVED if it
stays green (a test gap, unless the mutation is behaviourally equivalent).

Usage (from the project root):
    python3 tools/mutation_testing.py

Edit the MUTATIONS list to target other code. Each entry is
(file, exact_original_fragment, mutant_fragment, label); the original must occur exactly
once in the file (non-unique fragments are reported as SKIP).
"""
import pathlib
import subprocess
import sys

D = "src/fr.craft.linkedinarchiveexplorer.domain/fr/craft/linkedinarchiveexplorer/domain/"
A = "src/fr.craft.linkedinarchiveexplorer.application/fr/craft/linkedinarchiveexplorer/application/"
I = "src/fr.craft.linkedinarchiveexplorer.infrastructure/fr/craft/linkedinarchiveexplorer/infrastructure/"
C = "src/fr.craft.linkedinarchiveexplorer.cli/fr/craft/linkedinarchiveexplorer/cli/"

# (file, original, mutant, label)
MUTATIONS = [
    (D + "SearchEngine.java", "matchStart - CONTEXT", "matchStart + CONTEXT", "SE window-start sign"),
    (D + "SearchEngine.java", "matchEnd + CONTEXT", "matchEnd - CONTEXT", "SE window-end sign"),
    (D + "SearchEngine.java", "windowStart > 0 ? ELLIPSIS", "windowStart >= 0 ? ELLIPSIS", "SE prefix-ellipsis boundary"),
    (D + "SearchEngine.java", "windowEnd < text.length() ? ELLIPSIS", "windowEnd <= text.length() ? ELLIPSIS", "SE suffix-ellipsis boundary"),
    (D + "SearchEngine.java", "at + term.length()", "at + 1", "SE indexOf step (overlap)"),
    (D + "SearchEngine.java", "snippetMatchStart + termLength", "snippetMatchStart + termLength + 1", "SE matchEnd off-by-one"),
    (D + "SearchEngine.java", r"c == '\n' || c == '\r' || c == '\t'", r"c == '\n' || c == '\t'", "SE flatten drop CR"),
    (D + "Excerpt.java", "matchEnd > snippet.length()", "matchEnd >= snippet.length()", "Excerpt end boundary"),
    (D + "SearchTerm.java", "value == null || value.isBlank()", "value == null && value.isBlank()", "SearchTerm blank-check ||->&&"),
    (A + "SearchContentsService.java", "rightDate.get().compareTo(leftDate.get())", "leftDate.get().compareTo(rightDate.get())", "SCS sort reversed"),
    (A + "SearchContentsService.java", "return -1;", "return 1;", "SCS undated-order"),
    (A + "SearchContentsService.java", "hit.content().type() == type", "hit.content().type() != type", "SCS type filter ==->!="),
    (I + "Csv.java", "else if (c == ',')", "else if (c == ';')", "Csv field separator"),
    (I + "Csv.java", "inQuotes = false;", "inQuotes = true;", "Csv closing quote"),
    (I + "Csv.java", "content.charAt(i + 1) == '\"'", "content.charAt(i + 1) != '\"'", "Csv escaped-quote detect"),
    (I + "Csv.java", r"content.charAt(i + 1) == '\n'", r"content.charAt(i + 1) != '\n'", "Csv CRLF detect"),
    (C + "TerminalRenderer.java", "substring(0, excerpt.matchStart())", "substring(0, excerpt.matchEnd())", "TR highlight start->end"),
    (C + "TerminalRenderer.java", 'case COMMENT -> "COMMENTAIRES";', 'case COMMENT -> "COMMENTAIRE";', "TR heading label"),
    (C + "TerminalRenderer.java", '"[" + day + "] "', '"" + day + " "', "TR date bracket"),
    (C + "Main.java", "!noColor && (forceColor || defaultStyled)", "!noColor && (forceColor && defaultStyled)", "Main styled ||->&&"),
    (C + "Main.java", "!noColor && (forceColor || defaultStyled)", "noColor && (forceColor || defaultStyled)", "Main styled drop-!noColor"),
    (C + "Main.java", "term == null || term.isBlank()", "term == null && term.isBlank()", "Main term-check ||->&&"),
    (C + "Main.java", 'if (arg.startsWith("--") || term != null)', 'if (arg.startsWith("--") && term != null)', "Main extra-arg guard"),
    (C + "Main.java", "i + 1 >= args.length", "i + 1 > args.length", "Main --archive bound"),
]


def run_tests():
    r = subprocess.run(["./test.sh"], capture_output=True, text=True)
    out = r.stdout + r.stderr
    if r.returncode == 0:
        return "SURVIVED"
    if "error:" in out and "tests successful" not in out:
        return "killed(compile)"
    return "KILLED"


def main():
    base = subprocess.run(["./test.sh"], capture_output=True, text=True)
    if base.returncode != 0:
        print("Baseline is not green — aborting.")
        sys.exit(1)
    print("Baseline green.\n")

    survivors = []
    for path, orig, mut, label in MUTATIONS:
        p = pathlib.Path(path)
        text = p.read_text()
        n = text.count(orig)
        if n != 1:
            print(f"  SKIP        | {label}  ({n} matches for fragment)")
            continue
        p.write_text(text.replace(orig, mut, 1))
        try:
            result = run_tests()
        finally:
            p.write_text(text)
        marker = "  <== SURVIVOR" if result == "SURVIVED" else ""
        print(f"  {result:14}| {label}{marker}")
        if result == "SURVIVED":
            survivors.append((path, orig, mut, label))

    print(f"\n{len(MUTATIONS)} mutations, {len(survivors)} survivor(s).")
    for path, orig, mut, label in survivors:
        print(f"  SURVIVOR: {label}\n      {path}\n      {orig!r} -> {mut!r}")


if __name__ == "__main__":
    main()
