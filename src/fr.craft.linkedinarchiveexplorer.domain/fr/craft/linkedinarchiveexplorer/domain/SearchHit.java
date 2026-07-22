package fr.craft.linkedinarchiveexplorer.domain;

import java.util.List;

/** One matched content and every excerpt where the term occurs in it. */
public record SearchHit(Content content, List<Excerpt> excerpts) {

  public SearchHit(Content content, List<Excerpt> excerpts) {
    if (content == null) {
      throw new IllegalArgumentException("A search hit must reference a content");
    }
    if (excerpts == null || excerpts.isEmpty()) {
      throw new IllegalArgumentException("A search hit must have at least one excerpt");
    }
    this.content = content;
    this.excerpts = List.copyOf(excerpts);
  }
}
