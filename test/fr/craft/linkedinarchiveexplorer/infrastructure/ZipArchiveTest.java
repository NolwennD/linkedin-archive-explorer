package fr.craft.linkedinarchiveexplorer.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ZipArchiveTest {

  /**
   * A real zip with its END header truncated away — the one fixture that cannot be built
   * inline, since every zip writer refuses to produce a damaged one. Read from the
   * project root, like every other {@code ./bin/test} path.
   */
  static final Path CORRUPTED_ARCHIVE = Path.of("test/data/corrupted.zip");

  private static Path zipWith(Map<String, String> entries) throws IOException {
    Path zip = Files.createTempFile("archive", ".zip");
    zip.toFile().deleteOnExit();
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
      for (Map.Entry<String, String> entry : entries.entrySet()) {
        out.putNextEntry(new ZipEntry(entry.getKey()));
        out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
      }
    }
    return zip;
  }

  @Nested
  class Reading {

    @Test
    void readsAFirstEntryMatchingItsName() throws IOException {
      Path zip = zipWith(Map.of("Comments_1.csv", "Date,Message"));

      try (ZipArchive archive = ZipArchive.open(zip)) {
        assertEquals("Date,Message", archive.readFirst(name -> name.startsWith("Comments")).orElseThrow());
      }
    }

    @Test
    void readsEveryMatchingEntryIncludingNestedOnes() throws IOException {
      Path zip = zipWith(Map.of("Articles/Articles/a.html", "one", "Articles/Articles/b.html", "two"));

      try (ZipArchive archive = ZipArchive.open(zip)) {
        List<String> htmls = archive.readAll(name -> name.endsWith(".html"));
        assertEquals(2, htmls.size());
        assertTrue(htmls.contains("one") && htmls.contains("two"));
      }
    }

    @Test
    void readsEntriesAsUtf8() throws IOException {
      Path zip = zipWith(Map.of("Profile.csv", "développeur"));

      try (ZipArchive archive = ZipArchive.open(zip)) {
        assertEquals("développeur", archive.readFirst(name -> name.equals("Profile.csv")).orElseThrow());
      }
    }

    @Test
    void returnsEmptyWhenNothingMatches() throws IOException {
      Path zip = zipWith(Map.of("Profile.csv", "x"));

      try (ZipArchive archive = ZipArchive.open(zip)) {
        assertTrue(archive.readFirst(name -> name.equals("absent.csv")).isEmpty());
      }
    }
  }

  @Nested
  class Opening {

    @Test
    void tellsWhyACorruptedArchiveCannotBeOpened() {
      UncheckedIOException failure =
          assertThrows(UncheckedIOException.class, () -> ZipArchive.open(CORRUPTED_ARCHIVE));

      assertTrue(
          failure.getMessage().startsWith("Cannot open archive: " + CORRUPTED_ARCHIVE), failure.getMessage());
      // The JDK's own diagnosis, whatever it words it — never restated, never dropped.
      assertTrue(failure.getMessage().endsWith(failure.getCause().getMessage()), failure.getMessage());
    }
  }
}
