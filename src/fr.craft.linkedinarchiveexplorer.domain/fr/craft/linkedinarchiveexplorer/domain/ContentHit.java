package fr.craft.linkedinarchiveexplorer.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** A matched article, post or comment. */
public record ContentHit(Content content, List<Excerpt> excerpts) implements SearchHit {

  public ContentHit {
    if (content == null) {
      throw new IllegalArgumentException("A search hit must reference a content");
    }
    excerpts = SearchHit.requireExcerpts(excerpts);
  }

  @Override
  public ContentType type() {
    return content.type();
  }

  @Override
  public String url() {
    return content.url();
  }

  @Override
  public Optional<LocalDate> date() {
    return content.date();
  }
}
