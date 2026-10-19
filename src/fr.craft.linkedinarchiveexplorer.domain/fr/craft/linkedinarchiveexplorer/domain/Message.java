package fr.craft.linkedinarchiveexplorer.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * A private message, filed under its {@code correspondent}. The export carries no link to a
 * single message, so {@code url} leads to the whole conversation.
 */
public record Message(Optional<LocalDate> date, String url, Body text, Correspondent correspondent)
    implements Searchable {

  public Message {
    if (date == null) {
      throw new IllegalArgumentException("A message date must be an Optional, not null");
    }
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("A message must have a url");
    }
    if (text == null) {
      throw new IllegalArgumentException("A message text must not be null");
    }
    if (correspondent == null) {
      throw new IllegalArgumentException("A message must have a correspondent");
    }
  }

  @Override
  public Optional<SearchHit> find(SearchTerm term) {
    List<Excerpt> excerpts = Excerpt.excerptsFor(term, text);
    return excerpts.isEmpty() ? Optional.empty() : Optional.of(new MessageHit(this, excerpts));
  }
}
