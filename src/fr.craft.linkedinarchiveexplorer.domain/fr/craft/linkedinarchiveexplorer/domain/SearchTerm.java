package fr.craft.linkedinarchiveexplorer.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * The searched value together with its matching options. It knows how to find its own
 * non-overlapping occurrences in a text (Tell-Don't-Ask); the {@code caseSensitivity} and
 * {@code wordScope} options do the deciding, so no branch on them lives here.
 */
public record SearchTerm(String value, CaseSensitivity caseSensitivity, WordScope wordScope) {

  public SearchTerm {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("A search term must not be null or blank");
    }
    if (caseSensitivity == null || wordScope == null) {
      throw new IllegalArgumentException("A search term must have non-null options");
    }
  }

  /** A literal term: case- and accent-sensitive, matching anywhere (the default). */
  public static SearchTerm literal(String value) {
    return new SearchTerm(value, CaseSensitivity.SENSITIVE, WordScope.ANYWHERE);
  }

  /** Every non-overlapping occurrence of this term in {@code text}, in order. */
  public List<Match> occurrencesIn(String text) {
    List<Match> matches = new ArrayList<>();
    int length = value.length();
    for (int at = 0; at + length <= text.length(); at++) {
      int end = at + length;
      if (caseSensitivity.matchesAt(text, at, value) && wordScope.allows(text, at, end)) {
        matches.add(new Match(at, end, text.substring(at, end)));
        at = end - 1; // skip past the match so occurrences do not overlap
      }
    }
    return matches;
  }
}
