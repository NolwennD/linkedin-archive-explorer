package fr.craft.linkedinarchiveexplorer.application;

import java.util.List;

/** The outcome of a search: non-empty groups, in canonical content-type order. */
public record SearchResults(List<ContentGroup> groups) {

  public SearchResults(List<ContentGroup> groups) {
    if (groups == null) {
      throw new IllegalArgumentException("Search results must have a (possibly empty) group list");
    }
    this.groups = List.copyOf(groups);
  }
}
