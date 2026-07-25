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
  };

  /** Whether an occurrence spanning {@code [start, end)} of {@code text} is accepted. */
  abstract boolean allows(String text, int start, int end);
}
