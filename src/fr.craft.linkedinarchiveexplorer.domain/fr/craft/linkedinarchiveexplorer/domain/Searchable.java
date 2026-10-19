package fr.craft.linkedinarchiveexplorer.domain;

import java.util.List;

/**
 * Something the engine can look for a term in: it has a {@link #text()} and knows which
 * kind of {@link SearchHit} it turns into. Sealed, so every kind of hit has its source.
 */
public sealed interface Searchable permits Content, Message {

  Body text();

  SearchHit hit(List<Excerpt> excerpts);
}
