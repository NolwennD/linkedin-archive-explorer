package fr.craft.linkedinarchiveexplorer.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Literal, case- and accent-sensitive substring search (grep-style). Each content is
 * reported at most once; it is asked to yield its own excerpts (see {@link Body}).
 */
public final class SearchEngine {

  public List<SearchHit> search(SearchTerm term, Collection<Content> contents) {
    List<SearchHit> hits = new ArrayList<>();
    for (Content content : contents) {
      List<Excerpt> excerpts = content.text().excerptsFor(term);
      if (!excerpts.isEmpty()) {
        hits.add(new SearchHit(content, excerpts));
      }
    }
    return hits;
  }
}
