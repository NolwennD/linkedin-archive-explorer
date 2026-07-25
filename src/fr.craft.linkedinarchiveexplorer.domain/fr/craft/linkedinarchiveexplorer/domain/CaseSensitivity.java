package fr.craft.linkedinarchiveexplorer.domain;

/**
 * How a {@link SearchTerm} compares its characters against the text. Each value knows how
 * to test whether the term occurs at a given position (Tell-Don't-Ask), so the search
 * loop never branches on the option.
 */
public enum CaseSensitivity {

  /** {@code Date} matches {@code Date} but not {@code date}. */
  SENSITIVE {
    @Override
    boolean matchesAt(String text, int at, String needle) {
      return text.regionMatches(false, at, needle, 0, needle.length());
    }
  };

  /**
   * Whether {@code needle} occurs at index {@code at} of {@code text}. Built on
   * {@link String#regionMatches}, which compares character by character and never shifts
   * length — so a match always spans exactly {@code needle.length()} characters.
   */
  abstract boolean matchesAt(String text, int at, String needle);
}
