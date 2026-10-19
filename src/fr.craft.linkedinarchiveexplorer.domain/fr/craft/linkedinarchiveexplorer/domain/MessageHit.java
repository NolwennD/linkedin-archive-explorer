package fr.craft.linkedinarchiveexplorer.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** A matched private message, filed under its correspondent. */
public record MessageHit(Message message, List<Excerpt> excerpts) implements SearchHit {

  public MessageHit {
    if (message == null) {
      throw new IllegalArgumentException("A search hit must reference a message");
    }
    excerpts = SearchHit.requireExcerpts(excerpts);
  }

  public Correspondent correspondent() {
    return message.correspondent();
  }

  @Override
  public Optional<LocalDate> date() {
    return message.date();
  }
}
