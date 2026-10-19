package fr.craft.linkedinarchiveexplorer.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** One matched item and every excerpt where the term occurs in it. */
public sealed interface SearchHit permits ContentHit, MessageHit, ConnectionHit {

  /** The only thing every kind of hit has in common for the results: when it dates from. */
  Optional<LocalDate> date();

  /** What every kind of hit requires of its excerpts: at least one, and unmodifiable. */
  static List<Excerpt> requireExcerpts(List<Excerpt> excerpts) {
    if (excerpts == null || excerpts.isEmpty()) {
      throw new IllegalArgumentException("A search hit must have at least one excerpt");
    }
    return List.copyOf(excerpts);
  }
}
