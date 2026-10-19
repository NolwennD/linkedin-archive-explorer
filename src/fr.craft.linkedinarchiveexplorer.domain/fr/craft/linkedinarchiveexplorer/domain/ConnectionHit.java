package fr.craft.linkedinarchiveexplorer.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** A matched connection, with where in it the term occurs. */
public record ConnectionHit(Connection connection, List<ConnectionMatch> matches) implements SearchHit {

  public ConnectionHit {
    if (connection == null) {
      throw new IllegalArgumentException("A search hit must reference a connection");
    }
    if (matches == null || matches.isEmpty()) {
      throw new IllegalArgumentException("A search hit must have at least one match");
    }
    matches = List.copyOf(matches);
  }

  @Override
  public Optional<LocalDate> date() {
    return connection.connectedOn();
  }
}
