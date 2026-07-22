package fr.craft.linkedinarchiveexplorer.infrastructure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
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

  public static Optional<Path> mostRecent(Path directory) {
    if (!Files.isDirectory(directory)) {
      return Optional.empty();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".zip"))
          .max(Comparator.comparing(ArchiveLocator::recencyOf));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot list archives in " + directory, e);
    }
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
