#!/usr/bin/env python3
"""Manual mutation-testing harness (not part of the build).

For each mutation it applies one exact, unique text replacement in a production file,
runs ./bin/test, then restores the original from memory (even on failure) -- so nothing
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
    # SearchTerm.occurrencesIn — the matching loop and validation
    (D + "SearchTerm.java", "at + length <= text.length()", "at + length < text.length()", "ST loop end-bound (drop last position)"),
    (D + "SearchTerm.java", "at = end - 1;", "at = end;", "ST non-overlap step (skip a char)"),
    (D + "SearchTerm.java", "matchesAt(text, at, value) && wordScope.allows(text, at, end)", "matchesAt(text, at, value) || wordScope.allows(text, at, end)", "ST match/scope &&->||"),
    (D + "SearchTerm.java", "value == null || value.isBlank()", "value == null && value.isBlank()", "ST blank-check ||->&&"),

    # CaseSensitivity — the two comparison modes
    (D + "CaseSensitivity.java", "regionMatches(false, at, needle, 0, needle.length())", "regionMatches(true, at, needle, 0, needle.length())", "CS SENSITIVE becomes insensitive"),
    (D + "CaseSensitivity.java", "regionMatches(true, at, needle, 0, needle.length())", "regionMatches(false, at, needle, 0, needle.length())", "CS INSENSITIVE becomes sensitive"),

    # WordScope — the whole-word boundary rule
    (D + "WordScope.java", "freeAt(text, start - 1) && freeAt(text, end)", "freeAt(text, start - 1) || freeAt(text, end)", "WS boundary &&->||"),
    (D + "WordScope.java", "Character.isLetterOrDigit(c) || c == '_'", "Character.isLetterOrDigit(c)", "WS drop underscore as word char"),
    (D + "WordScope.java", "index < 0 || index >= text.length()", "index < 0 && index >= text.length()", "WS freeAt edge ||->&&"),

    # Match — the located-occurrence invariants
    (D + "Match.java", "value.length() != end - start", "value.length() == end - start", "Match length-invariant negated"),
    (D + "Match.java", "start < 0 || end < start", "start < 0 && end < start", "Match span-check ||->&&"),

    # Body — context window + whitespace flattening
    (D + "Body.java", "matchStart - CONTEXT", "matchStart + CONTEXT", "Body window-start sign"),
    (D + "Body.java", "matchEnd + CONTEXT", "matchEnd - CONTEXT", "Body window-end sign"),
    (D + "Body.java", "windowStart > 0 ? ELLIPSIS", "windowStart >= 0 ? ELLIPSIS", "Body prefix-ellipsis boundary"),
    (D + "Body.java", "windowEnd < value.length() ? ELLIPSIS", "windowEnd <= value.length() ? ELLIPSIS", "Body suffix-ellipsis boundary"),
    (D + "Body.java", "c == '\\n' || c == '\\r' || c == '\\t'", "c == '\\n' || c == '\\t'", "Body flatten drop CR"),

    # SearchResults — grouping/sorting
    (A + "SearchResults.java", "hit.type() == type", "hit.type() != type", "SR type filter ==->!="),
    (A + "SearchResults.java", "Comparator.<LocalDate>reverseOrder()", "Comparator.<LocalDate>naturalOrder()", "SR date order reversed"),

    # Csv — hand-written parser
    (I + "Csv.java", "else if (c == ',')", "else if (c == ';')", "Csv field separator"),
    (I + "Csv.java", "\n          inQuotes = false;", "\n          inQuotes = true;", "Csv closing quote"),
    (I + "Csv.java", "content.charAt(i + 1) == '\"'", "content.charAt(i + 1) != '\"'", "Csv escaped-quote detect"),

    # Main — argument parsing, including the new flags and bundles
    (C + "Main.java", "i + 1 >= args.length", "i + 1 > args.length", "Main --archive bound"),
    (C + "Main.java", "term == null || term.isBlank()", "term == null && term.isBlank()", "Main term-check ||->&&"),
    (C + "Main.java", "!noColor && (forceColor || defaultStyled)", "!noColor && (forceColor && defaultStyled)", "Main styled ||->&&"),
    (C + "Main.java", "arg.charAt(i) != 'i' && arg.charAt(i) != 'w'", "arg.charAt(i) != 'i' || arg.charAt(i) != 'w'", "Main bundle letter-set &&->||"),
    (C + "Main.java", "arg.charAt(1) == '-'", "arg.charAt(1) != '-'", "Main bundle double-dash guard"),

    # TerminalRenderer — headings and date bracket
    (C + "TerminalRenderer.java", 'case COMMENT -> "COMMENTS";', 'case COMMENT -> "COMMENT";', "TR heading label"),
    (C + "TerminalRenderer.java", '"[" + day + "] "', '"" + day + " "', "TR date bracket"),
]


def run_tests():
    r = subprocess.run(["./bin/test"], capture_output=True, text=True)
    out = r.stdout + r.stderr
    if r.returncode == 0:
        return "SURVIVED"
    if "error:" in out and "tests successful" not in out:
        return "killed(compile)"
    return "KILLED"


def main():
    base = subprocess.run(["./bin/test"], capture_output=True, text=True)
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
