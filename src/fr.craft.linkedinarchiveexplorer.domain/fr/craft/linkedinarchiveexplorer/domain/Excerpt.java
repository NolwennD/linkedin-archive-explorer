package fr.craft.linkedinarchiveexplorer.domain;

import java.util.function.Function;

/**
 * A short snippet of context around one occurrence of the searched term: the text
 * {@code before} the match, the matched {@link SearchTerm} itself, and the text
 * {@code after} — so a renderer can highlight the match without any index arithmetic.
 */
public record Excerpt(String before, SearchTerm match, String after) {

  public Excerpt {
    if (before == null || match == null || after == null) {
      throw new IllegalArgumentException("An excerpt must have non-null before/match/after parts");
    }
  }

  /** The snippet with the match transformed by {@code emphasis} (e.g. wrapped in colour). */
  public String render(Function<String, String> emphasis) {
    return before + emphasis.apply(match.value()) + after;
  }

}
