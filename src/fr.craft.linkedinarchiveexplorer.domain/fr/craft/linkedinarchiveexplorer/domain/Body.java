package fr.craft.linkedinarchiveexplorer.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * The searchable text of a piece of content. It knows how to find a term within itself
 * and yield one context {@link Excerpt} per non-overlapping occurrence, with about
 * {@value #CONTEXT} characters of surrounding context (whitespace flattened, ellipsis on
 * truncated ends).
 */
public record Body(String value) {

  private static final int CONTEXT = 40;
  private static final String ELLIPSIS = "…";

  public Body {
    if (value == null) {
      throw new IllegalArgumentException("A body text must not be null");
    }
  }

  /** One {@link Excerpt} per non-overlapping occurrence of {@code term}, in order. */
  public List<Excerpt> excerptsFor(SearchTerm term) {
    String needle = term.value();
    List<Excerpt> excerpts = new ArrayList<>();
    for (int at = value.indexOf(needle); at != -1; at = value.indexOf(needle, at + needle.length())) {
      excerpts.add(excerptAt(term, at));
    }
    return excerpts;
  }

  private Excerpt excerptAt(SearchTerm term, int matchStart) {
    int matchEnd = matchStart + term.value().length();
    int windowStart = Math.max(0, matchStart - CONTEXT);
    int windowEnd = Math.min(value.length(), matchEnd + CONTEXT);

    String prefix = windowStart > 0 ? ELLIPSIS : "";
    String suffix = windowEnd < value.length() ? ELLIPSIS : "";

    String before = prefix + flatten(value.substring(windowStart, matchStart));
    String after = flatten(value.substring(matchEnd, windowEnd)) + suffix;
    return new Excerpt(before, term, after);
  }

  private static String flatten(String raw) {
    StringBuilder flattened = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      flattened.append(c == '\n' || c == '\r' || c == '\t' ? ' ' : c);
    }
    return flattened.toString();
  }
}
