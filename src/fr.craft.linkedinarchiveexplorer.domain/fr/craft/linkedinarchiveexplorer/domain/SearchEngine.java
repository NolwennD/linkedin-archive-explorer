package fr.craft.linkedinarchiveexplorer.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Literal, case- and accent-sensitive substring search (grep-style). Every content is
 * reported at most once, carrying one {@link Excerpt} per occurrence with about
 * {@value #CONTEXT} characters of surrounding context.
 */
public final class SearchEngine {

  private static final int CONTEXT = 40;
  private static final String ELLIPSIS = "…";

  public List<SearchHit> search(SearchTerm term, Collection<Content> contents) {
    List<SearchHit> hits = new ArrayList<>();
    for (Content content : contents) {
      List<Excerpt> excerpts = excerptsOf(content.text(), term.value());
      if (!excerpts.isEmpty()) {
        hits.add(new SearchHit(content, excerpts));
      }
    }
    return hits;
  }

  private List<Excerpt> excerptsOf(String text, String term) {
    List<Excerpt> excerpts = new ArrayList<>();
    for (int at = text.indexOf(term); at != -1; at = text.indexOf(term, at + term.length())) {
      excerpts.add(excerptAt(text, at, term.length()));
    }
    return excerpts;
  }

  private Excerpt excerptAt(String text, int matchStart, int termLength) {
    int matchEnd = matchStart + termLength;
    int windowStart = Math.max(0, matchStart - CONTEXT);
    int windowEnd = Math.min(text.length(), matchEnd + CONTEXT);

    String prefix = windowStart > 0 ? ELLIPSIS : "";
    String suffix = windowEnd < text.length() ? ELLIPSIS : "";
    String snippet = prefix + flatten(text.substring(windowStart, windowEnd)) + suffix;

    int snippetMatchStart = prefix.length() + (matchStart - windowStart);
    return new Excerpt(snippet, snippetMatchStart, snippetMatchStart + termLength);
  }

  private String flatten(String text) {
    StringBuilder flattened = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      flattened.append(c == '\n' || c == '\r' || c == '\t' ? ' ' : c);
    }
    return flattened.toString();
  }
}
