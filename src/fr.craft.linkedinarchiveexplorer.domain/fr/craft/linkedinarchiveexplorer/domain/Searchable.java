package fr.craft.linkedinarchiveexplorer.domain;

import java.util.Optional;

/**
 * Something the engine can look for a term in. It knows what it is made of and which kind
 * of {@link SearchHit} it turns into, so the engine needs to know neither. Sealed, so every
 * kind of hit has its source.
 */
public sealed interface Searchable permits Content, Message, Connection {

  /** The hit when {@code term} occurs in this item, empty otherwise. */
  Optional<SearchHit> find(SearchTerm term);
}
