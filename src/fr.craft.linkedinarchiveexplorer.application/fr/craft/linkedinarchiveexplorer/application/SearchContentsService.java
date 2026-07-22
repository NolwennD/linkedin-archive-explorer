package fr.craft.linkedinarchiveexplorer.application;

import fr.craft.linkedinarchiveexplorer.domain.Content;
import fr.craft.linkedinarchiveexplorer.domain.ContentSource;
import fr.craft.linkedinarchiveexplorer.domain.ContentType;
import fr.craft.linkedinarchiveexplorer.domain.SearchEngine;
import fr.craft.linkedinarchiveexplorer.domain.SearchHit;
import fr.craft.linkedinarchiveexplorer.domain.SearchTerm;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Loads content from every {@link ContentSource}, searches it, then groups the hits by
 * type (canonical order) with each group sorted by date descending (undated last).
 */
public final class SearchContentsService {

  private static final Comparator<SearchHit> BY_DATE_DESCENDING =
      SearchContentsService::compareByDateDescending;

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
    List<SearchHit> hits = engine.search(term, loadAllContent());

    List<ContentGroup> groups = new ArrayList<>();
    for (ContentType type : ContentType.values()) {
      List<SearchHit> ofType =
          hits.stream().filter(hit -> hit.content().type() == type).sorted(BY_DATE_DESCENDING).toList();
      if (!ofType.isEmpty()) {
        groups.add(new ContentGroup(type, ofType));
      }
    }
    return new SearchResults(groups);
  }

  private List<Content> loadAllContent() {
    List<Content> all = new ArrayList<>();
    for (ContentSource source : sources) {
      all.addAll(source.load());
    }
    return all;
  }

  private static int compareByDateDescending(SearchHit left, SearchHit right) {
    Optional<LocalDate> leftDate = left.content().date();
    Optional<LocalDate> rightDate = right.content().date();
    if (leftDate.isPresent() && rightDate.isPresent()) {
      return rightDate.get().compareTo(leftDate.get());
    }
    if (leftDate.isPresent()) {
      return -1;
    }
    if (rightDate.isPresent()) {
      return 1;
    }
    return 0;
  }
}
