package fr.craft.linkedinarchiveexplorer.application;

import fr.craft.linkedinarchiveexplorer.domain.ContentType;
import fr.craft.linkedinarchiveexplorer.domain.SearchHit;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Every {@link SearchHit} of one {@link ContentType}, sorted by date descending. */
public record ContentGroup(ContentType type, List<SearchHit> hits) {

  private static final Comparator<SearchHit> BY_DATE_DESCENDING =
      Comparator.comparing(hit -> hit.date().orElse(LocalDate.MIN), Comparator.reverseOrder());

  public ContentGroup(ContentType type, List<SearchHit> hits) {
    if (type == null) {
      throw new IllegalArgumentException("A content group must have a type");
    }
    if (hits == null || hits.isEmpty()) {
      throw new IllegalArgumentException("A content group must have at least one hit");
    }
    this.type = type;
    this.hits = hits.stream().sorted(BY_DATE_DESCENDING).toList();
  }
}
