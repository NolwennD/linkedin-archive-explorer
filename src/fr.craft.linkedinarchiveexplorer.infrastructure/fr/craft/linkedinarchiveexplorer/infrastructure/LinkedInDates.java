package fr.craft.linkedinarchiveexplorer.infrastructure;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Parses the LinkedIn CSV date formats into a {@link LocalDate}: "yyyy-MM-dd HH:mm:ss" for most
 * files, "dd MMM yyyy" ("18 Jul 2026") for the connections.
 */
final class LinkedInDates {

  private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);

  private LinkedInDates() {}

  static Optional<LocalDate> parse(String raw) {
    return parse(raw, text -> LocalDate.parse(text.substring(0, 10)));
  }

  static Optional<LocalDate> parseDayMonthYear(String raw) {
    return parse(raw, text -> LocalDate.parse(text, DAY_MONTH_YEAR));
  }

  /** Empty for anything blank or unreadable: a date the export got wrong must not stop a search. */
  private static Optional<LocalDate> parse(String raw, Function<String, LocalDate> reader) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(reader.apply(raw.strip()));
    } catch (RuntimeException ignored) {
      return Optional.empty();
    }
  }
}
