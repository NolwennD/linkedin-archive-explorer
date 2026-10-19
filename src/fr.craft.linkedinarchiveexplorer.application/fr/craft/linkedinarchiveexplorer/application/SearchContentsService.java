package fr.craft.linkedinarchiveexplorer.application;

import fr.craft.linkedinarchiveexplorer.domain.ContentSource;
import fr.craft.linkedinarchiveexplorer.domain.SearchEngine;
import fr.craft.linkedinarchiveexplorer.domain.Searchable;
import fr.craft.linkedinarchiveexplorer.domain.SearchTerm;

import java.util.List;

public final class SearchContentsService {

  private final SearchEngine engine;
  private final List<? extends Searchable> content;

  public SearchContentsService(List<ContentSource> sources, SearchEngine engine) {
    if (sources == null || engine == null) {
      throw new IllegalArgumentException("Sources and engine are required");
    }
    this.engine = engine;
    this.content = loadAllContent(sources);
  }

  public SearchResults search(SearchTerm term) {
    return SearchResults.from(engine.search(term, content));
  }

  private static List<? extends Searchable> loadAllContent(List<ContentSource> sources) {
    return sources.stream().flatMap(source -> source.load().stream()).toList();
  }

}
