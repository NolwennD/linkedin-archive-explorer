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
  },

  /** {@code date} matches {@code Date} and {@code DATE}; accents stay significant. */
  INSENSITIVE {
    @Override
    boolean matchesAt(String text, int at, String needle) {
      for (int offset = 0; offset < needle.length(); offset++) {
        if (folded(text.charAt(at + offset)) != folded(needle.charAt(offset))) {
          return false;
        }
      }
      return true;
    }
  };

  /** {@code ı} U+0131 — a letter of the Turkish alphabet, not an {@code i} short of a dot. */
  private static final char DOTLESS_SMALL_I = 'ı';

  /** {@code İ} U+0130 — likewise: the Turkish capital {@code i}, dot and all. */
  private static final char DOTTED_CAPITAL_I = 'İ';

  /**
   * Whether {@code needle} occurs at index {@code at} of {@code text}. Both values compare
   * character by character and never shift length — so a match always spans exactly
   * {@code needle.length()} characters.
   */
  abstract boolean matchesAt(String text, int at, String needle);

  /**
   * The form in which two characters differing only by case become equal — the lowercase
   * of the uppercase, so {@code ß} and {@code ẞ} still meet.
   *
   * <p>The two Turkish letters are held out of it. Left to the JDK they would fold onto
   * the Latin {@code i}: {@code ı} uppercases to {@code I}, and {@code İ} lowercases to
   * {@code i}. {@code -i} would then report {@code ısı} for a search on {@code isi} —
   * conflating two distinct letters, exactly what this search refuses to do with accents,
   * and exactly what {@code grep -i} refuses too. This is a property of the characters
   * and not of the reader, so no locale takes part: the results are the same everywhere.
   */
  private static char folded(char character) {
    if (character == DOTLESS_SMALL_I || character == DOTTED_CAPITAL_I) {
      return character;
    }
    return Character.toLowerCase(Character.toUpperCase(character));
  }
}
