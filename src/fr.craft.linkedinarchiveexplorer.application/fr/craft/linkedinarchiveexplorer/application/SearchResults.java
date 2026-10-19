package fr.craft.linkedinarchiveexplorer.application;

import fr.craft.linkedinarchiveexplorer.domain.SearchHit;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.EnumMap;
import java.util.List;

/** The outcome of a search: non-empty groups, in canonical section order. */
public record SearchResults(List<ContentGroup> groups) {

  public SearchResults(List<ContentGroup> groups) {
    if (groups == null) {
      throw new IllegalArgumentException("Search results must have a (possibly empty) group list");
    }
    this.groups = List.copyOf(groups);
  }

  public static SearchResults from(List<SearchHit> hits) {
    return new SearchResults(
        hits.stream()
            .collect(groupingBy(Section::of, () -> new EnumMap<>(Section.class), toUnmodifiableList()))
            .entrySet().stream()
            .map(group -> new ContentGroup(group.getKey(), group.getValue()))
            .toList());
  }
}
