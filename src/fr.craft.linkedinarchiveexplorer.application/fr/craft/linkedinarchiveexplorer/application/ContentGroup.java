package fr.craft.linkedinarchiveexplorer.application;

import fr.craft.linkedinarchiveexplorer.domain.ContentType;
import fr.craft.linkedinarchiveexplorer.domain.Correspondent;
import fr.craft.linkedinarchiveexplorer.domain.MessageHit;
import fr.craft.linkedinarchiveexplorer.domain.SearchHit;
import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Every {@link SearchHit} of one {@link ContentType}, sorted by date descending. */
public record ContentGroup(ContentType type, List<SearchHit> hits) {

  private static final Comparator<SearchHit> BY_DATE_DESCENDING =
      Comparator.comparing(hit -> hit.date().orElse(LocalDate.MIN), Comparator.reverseOrder());

  // Case does not decide the order (accents only break ties); the profile link keeps two people
  // of the same name apart, so the map never merges them. Locale.ROOT: the French collation
  // rules live in jdk.localedata, which the native image does not carry.
  private static final Comparator<Correspondent> ALPHABETICALLY =
      Comparator.comparing(Correspondent::name, collator())
          .thenComparing(Correspondent::name)
          .thenComparing(correspondent -> correspondent.profileUrl().orElse(""));

  private static Collator collator() {
    Collator collator = Collator.getInstance(Locale.ROOT);
    collator.setStrength(Collator.SECONDARY);
    return collator;
  }

  public ContentGroup(ContentType type, List<SearchHit> hits) {
    if (type == null) {
      throw new IllegalArgumentException("A content group must have a type");
    }
    if (hits == null || hits.isEmpty()) {
      throw new IllegalArgumentException("A content group must have at least one hit");
    }
    if (hits.stream().anyMatch(hit -> hit.type() != type)) {
      throw new IllegalArgumentException("A content group can only hold hits of its own type");
    }
    this.type = type;
    this.hits = hits.stream().sorted(BY_DATE_DESCENDING).toList();
  }

  /** The hits of a message group filed by correspondent, alphabetically, each newest first. */
  public Map<Correspondent, List<MessageHit>> byCorrespondent() {
    Map<Correspondent, List<MessageHit>> filed = new TreeMap<>(ALPHABETICALLY);
    for (SearchHit hit : hits) {
      if (hit instanceof MessageHit message) {
        filed.computeIfAbsent(message.correspondent(), key -> new ArrayList<>()).add(message);
      }
    }
    return filed;
  }
}
