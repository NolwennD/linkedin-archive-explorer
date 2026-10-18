package fr.craft.linkedinarchiveexplorer.domain;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A single piece of authored content (article, post, comment or private message),
 * unified across sources. {@code date} is empty when the source carries no date (e.g.
 * articles). {@code correspondent} is only there for a message: it is who the message is
 * filed under, and {@code url} then leads to the whole conversation.
 */
public record Content(
    ContentType type, Optional<LocalDate> date, String url, Body text, Optional<Correspondent> correspondent) {

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
    if (correspondent == null) {
      throw new IllegalArgumentException("A content correspondent must be an Optional, not null");
    }
    if (type == ContentType.MESSAGE && correspondent.isEmpty()) {
      throw new IllegalArgumentException("A message must have a correspondent");
    }
  }

  public Content(ContentType type, Optional<LocalDate> date, String url, Body text) {
    this(type, date, url, text, Optional.empty());
  }

  /** Convenience: sources read plain text, so wrap a raw string as the {@link Body}. */
  public Content(ContentType type, Optional<LocalDate> date, String url, String text) {
    this(type, date, url, new Body(text));
  }

  public Content(ContentType type, String url, String text) {
    this(type, Optional.empty(), url, new Body(text));
  }
}
