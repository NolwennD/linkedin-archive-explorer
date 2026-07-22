package fr.craft.linkedinarchiveexplorer.domain;

/**
 * A short snippet of context around one occurrence of the searched term.
 * {@code matchStart}/{@code matchEnd} locate the term inside {@code snippet}
 * (half-open range) so a renderer can highlight it.
 */
public record Excerpt(String snippet, int matchStart, int matchEnd) {

  public Excerpt {
    if (snippet == null) {
      throw new IllegalArgumentException("An excerpt snippet must not be null");
    }
    if (matchStart < 0 || matchEnd < matchStart || matchEnd > snippet.length()) {
      throw new IllegalArgumentException(
          "Match range [" + matchStart + ", " + matchEnd + ") is out of the snippet");
    }
  }
}
