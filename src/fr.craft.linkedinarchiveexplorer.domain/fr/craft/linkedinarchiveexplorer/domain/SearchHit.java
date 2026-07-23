package fr.craft.linkedinarchiveexplorer.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

  public ContentType type() {
    return content.type();
  }

  public String url() {
    return content.url();
  }

  public Optional<LocalDate> date() {
    return content.date();
  }
}
