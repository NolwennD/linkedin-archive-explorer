package fr.craft.linkedinarchiveexplorer.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveLocatorTest {

  @Nested
  class MostRecent {

    @Test
    void picksTheArchiveWithTheMostRecentDateInItsName(@TempDir Path dir) throws IOException {
      Files.createFile(dir.resolve("Complete_LinkedInDataExport_07-20-2026.zip"));
      Path newer = Files.createFile(dir.resolve("Complete_LinkedInDataExport_07-21-2026.zip"));

      assertEquals(newer, ArchiveLocator.mostRecent(dir).orElseThrow());
    }

    @Test
    void fallsBackToModificationTimeWhenTheNameHasNoDate(@TempDir Path dir) throws IOException {
      Path older = Files.createFile(dir.resolve("old.zip"));
      Path newer = Files.createFile(dir.resolve("new.zip"));
      Files.setLastModifiedTime(older, FileTime.from(Instant.now().minus(2, ChronoUnit.DAYS)));
      Files.setLastModifiedTime(newer, FileTime.from(Instant.now()));

      assertEquals(newer, ArchiveLocator.mostRecent(dir).orElseThrow());
    }

    @Test
    void returnsEmptyWhenThereIsNoZip(@TempDir Path dir) throws IOException {
      Files.createFile(dir.resolve("notes.txt"));

      assertTrue(ArchiveLocator.mostRecent(dir).isEmpty());
    }

    @Test
    void returnsEmptyWhenTheDirectoryDoesNotExist(@TempDir Path dir) {
      assertTrue(ArchiveLocator.mostRecent(dir.resolve("nope")).isEmpty());
    }
  }

  @Nested
  class Listing {

    @Test
    void listsEveryArchiveMostRecentFirst(@TempDir Path dir) throws IOException {
      Path oldest = Files.createFile(dir.resolve("Complete_LinkedInDataExport_07-19-2026.zip"));
      Path newest = Files.createFile(dir.resolve("Complete_LinkedInDataExport_07-21-2026.zip"));
      Path middle = Files.createFile(dir.resolve("Complete_LinkedInDataExport_07-20-2026.zip"));

      assertEquals(List.of(newest, middle, oldest), ArchiveLocator.all(dir));
    }

    @Test
    void leavesOutWhatIsNotAZip(@TempDir Path dir) throws IOException {
      Path zip = Files.createFile(dir.resolve("export.zip"));
      Files.createFile(dir.resolve("notes.txt"));

      assertEquals(List.of(zip), ArchiveLocator.all(dir));
    }

    @Test
    void listsNothingWhenTheDirectoryDoesNotExist(@TempDir Path dir) {
      assertEquals(List.of(), ArchiveLocator.all(dir.resolve("nope")));
    }

    @Test
    void startsWithTheSameArchiveThatMostRecentPicks(@TempDir Path dir) throws IOException {
      Files.createFile(dir.resolve("Complete_LinkedInDataExport_07-20-2026.zip"));
      Files.createFile(dir.resolve("Complete_LinkedInDataExport_07-21-2026.zip"));

      assertEquals(ArchiveLocator.mostRecent(dir).orElseThrow(), ArchiveLocator.all(dir).get(0));
    }
  }

  /**
   * The extension test must not go through the default locale. In Turkish, lowercasing
   * {@code I} yields the dotless {@code ı}, so {@code ".ZIP"} becomes {@code ".zıp"} and
   * the archive silently disappears from the listing — on the user's machine only.
   */
  @Nested
  class UnderATurkishLocale {

    private Locale previous;

    @BeforeEach
    void switchLocale() {
      previous = Locale.getDefault();
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    }

    @AfterEach
    void restoreLocale() {
      Locale.setDefault(previous);
    }

    @Test
    void stillRecognisesAnUppercaseExtension(@TempDir Path dir) throws IOException {
      Path zip = Files.createFile(dir.resolve("EXPORT.ZIP"));

      assertEquals(List.of(zip), ArchiveLocator.all(dir));
    }
  }
}
