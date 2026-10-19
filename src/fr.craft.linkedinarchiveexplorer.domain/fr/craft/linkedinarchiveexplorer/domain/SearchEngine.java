package fr.craft.linkedinarchiveexplorer.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Literal, case- and accent-sensitive substring search (grep-style). Each item is
 * reported at most once.
 */
public final class SearchEngine {

  public List<SearchHit> search(SearchTerm term, Collection<? extends Searchable> searchables) {
    List<SearchHit> hits = new ArrayList<>();
    for (Searchable searchable : searchables) {
      List<Excerpt> excerpts = Excerpt.excerptsFor(term, searchable.text());
      if (!excerpts.isEmpty()) {
        hits.add(searchable.hit(excerpts));
      }
    }
    return hits;
  }
}
