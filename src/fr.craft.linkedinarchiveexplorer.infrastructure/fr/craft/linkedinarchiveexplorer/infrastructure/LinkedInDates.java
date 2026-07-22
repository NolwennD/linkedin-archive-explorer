package fr.craft.linkedinarchiveexplorer.infrastructure;

import java.time.LocalDate;
import java.util.Optional;

/** Parses the LinkedIn CSV date format ("yyyy-MM-dd HH:mm:ss") into a {@link LocalDate}. */
final class LinkedInDates {

  private LinkedInDates() {}

  static Optional<LocalDate> parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(LocalDate.parse(raw.strip().substring(0, 10)));
    } catch (RuntimeException ignored) {
      return Optional.empty();
    }
  }
}
