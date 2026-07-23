package fr.craft.linkedinarchiveexplorer.application;

import fr.craft.linkedinarchiveexplorer.domain.ContentType;
import fr.craft.linkedinarchiveexplorer.domain.SearchHit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The outcome of a search: non-empty groups, in canonical content-type order. */
public record SearchResults(List<ContentGroup> groups) {

  private static final Comparator<SearchHit> BY_DATE_DESCENDING =
      Comparator.comparing((SearchHit hit) -> hit.date().orElse(null),
          Comparator.nullsLast(Comparator.<LocalDate>reverseOrder()));

  public SearchResults(List<ContentGroup> groups) {
    if (groups == null) {
      throw new IllegalArgumentException("Search results must have a (possibly empty) group list");
    }
    this.groups = List.copyOf(groups);
  }

  public static SearchResults from(List<SearchHit> hits) {
    List<ContentGroup> groups = new ArrayList<>();
    for (ContentType type : ContentType.values()) {
      List<SearchHit> ofType =
          hits.stream().filter(hit -> hit.type() == type).sorted(BY_DATE_DESCENDING).toList();
      if (!ofType.isEmpty()) {
        groups.add(new ContentGroup(type, ofType));
      }
    }
    return new SearchResults(groups);
  }
}
