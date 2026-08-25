package fr.craft.linkedinarchiveexplorer.domain;

import java.util.List;
import java.util.function.Function;

/**
 * A short snippet of context around one occurrence of the searched term: the text
 * {@code before} the match, the {@link Match} itself, and the text {@code after} — so a
 * renderer can highlight the match without any index arithmetic. About {@value #CONTEXT}
 * characters each side (whitespace flattened, ellipsis on truncated ends).
 */
public record Excerpt(String before, Match match, String after) {

  private static final int CONTEXT = 40;
  private static final String ELLIPSIS = "…";

  public Excerpt {
    if (before == null || match == null || after == null) {
      throw new IllegalArgumentException("An excerpt must have non-null before/match/after parts");
    }
  }

  /** One {@link Excerpt} per non-overlapping occurrence of {@code term} in {@code body}, in order. */
  public static List<Excerpt> excerptsFor(SearchTerm term, Body body) {
    String text = body.value();
    return term.occurrencesIn(text).stream().map(match -> excerptAt(match, text)).toList();
  }

  private static Excerpt excerptAt(Match match, String text) {
    int matchStart = match.start();
    int windowStart = Math.max(0, matchStart - CONTEXT);
    String prefix = windowStart > 0 ? ELLIPSIS : "";
    String before = prefix + flatten(text.substring(windowStart, matchStart));

    int matchEnd = match.end();
    int windowEnd = Math.min(text.length(), matchEnd + CONTEXT);
    String suffix = windowEnd < text.length() ? ELLIPSIS : "";
    String after = flatten(text.substring(matchEnd, windowEnd)) + suffix;

    return new Excerpt(before, match, after);
  }

  private static String flatten(String raw) {
    return raw.replaceAll("[\\n\\r\\t]", " ");
  }

  /** The snippet with the match transformed by {@code emphasis} (e.g. wrapped in colour). */
  public String render(Function<String, String> emphasis) {
    return before + emphasis.apply(match.value()) + after;
  }

}
