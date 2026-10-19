package fr.craft.linkedinarchiveexplorer.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Literal, case- and accent-sensitive substring search (grep-style). Each item is
 * reported at most once.
 */
public final class SearchEngine {

  public List<SearchHit> search(SearchTerm term, Collection<? extends Searchable> searchables) {
    return searchables.stream().map(searchable -> searchable.find(term)).flatMap(Optional::stream).toList();
  }
}
