package fr.craft.linkedinarchiveexplorer.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Literal, case- and accent-sensitive substring search (grep-style). Each content is
 * reported at most once.
 */
public final class SearchEngine {

  public List<SearchHit> search(SearchTerm term, Collection<Content> contents) {
    List<SearchHit> hits = new ArrayList<>();
    for (Content content : contents) {
      List<Excerpt> excerpts = Excerpt.excerptsFor(term, content.text());
      if (!excerpts.isEmpty()) {
        hits.add(new SearchHit(content, excerpts));
      }
    }
    return hits;
  }
}
