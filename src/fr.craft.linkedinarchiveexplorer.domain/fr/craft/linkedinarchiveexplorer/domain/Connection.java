package fr.craft.linkedinarchiveexplorer.domain;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Someone the user is connected with. Searched field by field (see {@link ConnectionField}),
 * never as one concatenated text, so a match cannot straddle a company and a position.
 */
public record Connection(
    String firstName,
    String lastName,
    String profileUrl,
    String company,
    String position,
    Optional<LocalDate> connectedOn)
    implements Searchable {

  public Connection {
    if (firstName == null || lastName == null || company == null || position == null) {
      throw new IllegalArgumentException("A connection must have a (possibly empty) name, company and position");
    }
    if (profileUrl == null || profileUrl.isBlank()) {
      throw new IllegalArgumentException("A connection must have a profile url");
    }
    if (connectedOn == null) {
      throw new IllegalArgumentException("A connection date must be an Optional, not null");
    }
  }

  /** "First Last", so that a search for the whole name finds the person. */
  public String fullName() {
    return (firstName + " " + lastName).strip();
  }

  @Override
  public Optional<SearchHit> find(SearchTerm term) {
    List<ConnectionMatch> matches =
        Arrays.stream(ConnectionField.values())
            .flatMap(
                field ->
                    Excerpt.excerptsFor(term, new Body(field.valueOf(this))).stream()
                        .map(excerpt -> new ConnectionMatch(field, excerpt)))
            .toList();
    return matches.isEmpty() ? Optional.empty() : Optional.of(new ConnectionHit(this, matches));
  }
}
