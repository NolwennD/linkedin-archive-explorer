package fr.craft.linkedinarchiveexplorer.domain;

/**
 * Whether a {@link SearchTerm} occurrence must form a whole word. Each value knows how to
 * accept or reject an occurrence given its position (Tell-Don't-Ask), so the search loop
 * never branches on the option.
 */
public enum WordScope {

  /** Any occurrence counts, even inside a larger word. */
  ANYWHERE {
    @Override
    boolean allows(String text, int start, int end) {
      return true;
    }
  },

  /**
   * Only occurrences bordered by non-word characters (or the text edges) count. A word
   * character is a Unicode letter, a digit, or {@code _} — so accented letters keep a
   * word together ({@code dev} does not match inside {@code développeur}).
   */
  WHOLE_WORD {
    @Override
    boolean allows(String text, int start, int end) {
      return freeAt(text, start - 1) && freeAt(text, end);
    }
  };

  /** Whether an occurrence spanning {@code [start, end)} of {@code text} is accepted. */
  abstract boolean allows(String text, int start, int end);

  /** A position is "free" when it is off the text or not a word character. */
  private static boolean freeAt(String text, int index) {
    return index < 0 || index >= text.length() || !isWordChar(text.charAt(index));
  }

  private static boolean isWordChar(char c) {
    return Character.isLetterOrDigit(c) || c == '_';
  }
}
