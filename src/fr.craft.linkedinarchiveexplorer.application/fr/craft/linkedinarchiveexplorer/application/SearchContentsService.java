package fr.craft.linkedinarchiveexplorer.application;

import fr.craft.linkedinarchiveexplorer.domain.Content;
import fr.craft.linkedinarchiveexplorer.domain.ContentSource;
import fr.craft.linkedinarchiveexplorer.domain.SearchEngine;
import fr.craft.linkedinarchiveexplorer.domain.SearchHit;
import fr.craft.linkedinarchiveexplorer.domain.SearchTerm;

import java.util.List;

/**
 * Loads content from every {@link ContentSource}, searches it, then groups the hits by
 * type (canonical order) with each group sorted by date descending (undated last).
 */
public final class SearchContentsService {

  private final List<ContentSource> sources;
  private final SearchEngine engine;

  public SearchContentsService(List<ContentSource> sources, SearchEngine engine) {
    if (sources == null || engine == null) {
      throw new IllegalArgumentException("Sources and engine are required");
    }
    this.sources = List.copyOf(sources);
    this.engine = engine;
  }

  public SearchResults search(SearchTerm term) {
    return SearchResults.from(engine.search(term, loadAllContent()));
  }

  private List<Content> loadAllContent() {
    return sources.stream().flatMap(source -> source.load().stream()).toList();
  }

}
