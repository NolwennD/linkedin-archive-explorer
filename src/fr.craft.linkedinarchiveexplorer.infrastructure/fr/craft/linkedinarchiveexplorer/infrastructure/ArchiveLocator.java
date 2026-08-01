package fr.craft.linkedinarchiveexplorer.infrastructure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds the archive to search in a directory: the most recent {@code .zip}, dated from
 * the {@code _MM-DD-YYYY} in its name, falling back to its last-modified time.
 */
public final class ArchiveLocator {

  private static final Pattern DATE_IN_NAME = Pattern.compile("(\\d{2})-(\\d{2})-(\\d{4})");

  private ArchiveLocator() {}

  /** Every archive of {@code directory}, most recent first. */
  public static List<Path> all(Path directory) {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .filter(ArchiveLocator::isZip)
          .sorted(Comparator.comparing(ArchiveLocator::recencyOf).reversed())
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot list archives in " + directory, e);
    }
  }

  /** The head of {@link #all}, when there is one. */
  public static Optional<Path> mostRecent(Path directory) {
    List<Path> archives = all(directory);
    return archives.isEmpty() ? Optional.empty() : Optional.of(archives.get(0));
  }

  /**
   * A file name is an extension, not a sentence, so it is lowercased with {@link
   * Locale#ROOT} rather than the user's locale. Turkish lowercases {@code I} to the
   * dotless {@code ı}: with the default locale, {@code EXPORT.ZIP} would end in
   * {@code .zıp} and vanish from the listing on a Turkish machine only.
   */
  private static boolean isZip(Path path) {
    return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
  }

  private static Instant recencyOf(Path zip) {
    return dateInName(zip.getFileName().toString())
        .map(date -> date.atStartOfDay(ZoneOffset.UTC).toInstant())
        .orElseGet(() -> lastModified(zip));
  }

  private static Optional<LocalDate> dateInName(String name) {
    Matcher matcher = DATE_IN_NAME.matcher(name);
    if (!matcher.find()) {
      return Optional.empty();
    }
    try {
      int month = Integer.parseInt(matcher.group(1));
      int day = Integer.parseInt(matcher.group(2));
      int year = Integer.parseInt(matcher.group(3));
      return Optional.of(LocalDate.of(year, month, day));
    } catch (RuntimeException ignored) {
      return Optional.empty();
    }
  }

  private static Instant lastModified(Path zip) {
    try {
      return Files.getLastModifiedTime(zip).toInstant();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read modification time of " + zip, e);
    }
  }
}
