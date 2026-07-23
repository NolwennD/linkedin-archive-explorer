package fr.craft.linkedinarchiveexplorer.domain;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A single piece of authored content (article, post or comment), unified across
 * sources. {@code date} is empty when the source carries no date (e.g. articles).
 */
public record Content(ContentType type, Optional<LocalDate> date, String url, Body text) {

  public Content {
    if (type == null) {
      throw new IllegalArgumentException("A content must have a type");
    }
    if (date == null) {
      throw new IllegalArgumentException("A content date must be an Optional, not null");
    }
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("A content must have a url");
    }
    if (text == null) {
      throw new IllegalArgumentException("A content text must not be null");
    }
  }

  /** Convenience: sources read plain text, so wrap a raw string as the {@link Body}. */
  public Content(ContentType type, Optional<LocalDate> date, String url, String text) {
    this(type, date, url, new Body(text));
  }

  public Content(ContentType type, String url, String text) {
    this(type, Optional.empty(), url, new Body(text));
  }
}
