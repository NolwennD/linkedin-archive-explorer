package fr.craft.linkedinarchiveexplorer.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.craft.linkedinarchiveexplorer.domain.CaseSensitivity;
import fr.craft.linkedinarchiveexplorer.domain.SearchTerm;
import fr.craft.linkedinarchiveexplorer.domain.WordScope;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveCatalogTest {

  /** An archive holding one comment, so that searching it says which one was opened. */
  private static Path archiveAt(Path zip, String comment) throws IOException {
    String csv =
        """
        Date,Link,Message
        2024-11-06 12:49:49,https://li/1,"%s"
        """
            .formatted(comment);
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
      out.putNextEntry(new ZipEntry("Comments_1.csv"));
      out.write(csv.getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
    return zip;
  }

  /** Three archives whose recency is unambiguous: new.zip is the most recent. */
  private static void threeArchivesIn(Path directory) throws IOException {
    archiveAt(directory.resolve("old.zip"), "an old comment");
    archiveAt(directory.resolve("mid.zip"), "a middling comment");
    archiveAt(directory.resolve("new.zip"), "a recent comment");
    Files.setLastModifiedTime(
        directory.resolve("old.zip"), FileTime.from(Instant.now().minus(2, ChronoUnit.DAYS)));
    Files.setLastModifiedTime(
        directory.resolve("mid.zip"), FileTime.from(Instant.now().minus(1, ChronoUnit.DAYS)));
    Files.setLastModifiedTime(directory.resolve("new.zip"), FileTime.from(Instant.now()));
  }

  private static List<String> namesOf(List<Path> archives) {
    return archives.stream().map(path -> path.getFileName().toString()).toList();
  }

  private static String nameOf(java.util.Optional<Path> archive) {
    return archive.map(path -> path.getFileName().toString()).orElse("");
  }

  @Nested
  class Suggestions {

    @Test
    void listsTheArchivesOfTheDirectoryMostRecentFirst(@TempDir Path directory) throws IOException {
      threeArchivesIn(directory);

      try (ArchiveCatalog catalog = ArchiveCatalog.of(null, directory)) {
        assertEquals(List.of("new.zip", "mid.zip", "old.zip"), namesOf(catalog.archives()));
      }
    }

    @Test
    void putsTheExplicitArchiveFirstEvenWhenItLivesElsewhere(
        @TempDir Path directory, @TempDir Path elsewhere) throws IOException {
      threeArchivesIn(directory);
      Path explicit = archiveAt(elsewhere.resolve("explicit.zip"), "an explicit comment");

      try (ArchiveCatalog catalog = ArchiveCatalog.of(explicit, directory)) {
        assertEquals(
            List.of("explicit.zip", "new.zip", "mid.zip", "old.zip"), namesOf(catalog.archives()));
      }
    }

    @Test
    void doesNotListTheExplicitArchiveTwiceWhenItComesFromTheDirectory(@TempDir Path directory)
        throws IOException {
      threeArchivesIn(directory);

      try (ArchiveCatalog catalog = ArchiveCatalog.of(directory.resolve("mid.zip"), directory)) {
        assertEquals(List.of("mid.zip", "new.zip", "old.zip"), namesOf(catalog.archives()));
      }
    }

    @Test
    void seesAnArchiveDroppedInTheDirectoryAfterTheStart(@TempDir Path directory) throws IOException {
      threeArchivesIn(directory);

      try (ArchiveCatalog catalog = ArchiveCatalog.of(null, directory)) {
        archiveAt(directory.resolve("later.zip"), "a later comment");

        assertTrue(namesOf(catalog.archives()).contains("later.zip"), "the listing must be re-read");
      }
    }

    @Test
    void suggestsAbsolutePathsEvenWhenTheDirectoryIsRelative() {
      // test/data holds the versioned corrupted.zip; nothing is opened here, only listed.
      try (ArchiveCatalog catalog = ArchiveCatalog.of(null, Path.of("test/data"))) {
        assertTrue(
            catalog.archives().stream().allMatch(Path::isAbsolute),
            "a suggestion is pasted back later, from who knows which directory: " + catalog.archives());
      }
    }

    @Test
    void suggestsNothingWhenTheDirectoryIsEmpty(@TempDir Path empty) {
      try (ArchiveCatalog catalog = ArchiveCatalog.of(null, empty)) {
        assertEquals(List.of(), catalog.archives());
      }
    }
  }

  @Nested
  class Precedence {

    @Test
    void takesThePathTypedInThePage(@TempDir Path directory) throws IOException {
      threeArchivesIn(directory);

      try (ArchiveCatalog catalog = ArchiveCatalog.of(null, directory)) {
        assertEquals("mid.zip", nameOf(catalog.resolve(directory.resolve("mid.zip").toString(), "")));
      }
    }

    @Test
    void fallsBackToTheExplicitArchiveOfThisLaunch(@TempDir Path directory) throws IOException {
      threeArchivesIn(directory);

      try (ArchiveCatalog catalog = ArchiveCatalog.of(directory.resolve("old.zip"), directory)) {
        assertEquals("old.zip", nameOf(catalog.resolve("", directory.resolve("mid.zip").toString())));
      }
    }

    @Test
    void fallsBackToTheCookieWhenNothingElseWasGiven(@TempDir Path directory) throws IOException {
      threeArchivesIn(directory);

      try (ArchiveCatalog catalog = ArchiveCatalog.of(null, directory)) {
        assertEquals("mid.zip", nameOf(catalog.resolve("", directory.resolve("mid.zip").toString())));
      }
    }

    @Test
    void fallsBackToTheMostRecentOfTheDirectory(@TempDir Path directory) throws IOException {
      threeArchivesIn(directory);

      try (ArchiveCatalog catalog = ArchiveCatalog.of(null, directory)) {
        assertEquals("new.zip", nameOf(catalog.resolve("", "")));
      }
    }

    @Test
    void resolvesToNothingWhenTheDirectoryIsEmptyAndNothingWasGiven(@TempDir Path empty) {
      try (ArchiveCatalog catalog = ArchiveCatalog.of(null, empty)) {
        assertTrue(catalog.resolve("", "").isEmpty(), "the page must ask the user for a path");
      }
    }

    @Test
    void turnsARelativeTypedPathIntoAnAbsoluteOne(@TempDir Path directory) throws IOException {
      threeArchivesIn(directory);

      try (ArchiveCatalog catalog = ArchiveCatalog.of(null, directory)) {
        Path resolved = catalog.resolve("test/data/corrupted.zip", "").orElseThrow();

        // It goes into a cookie that outlives this launch, and its working directory.
        assertTrue(resolved.isAbsolute(), resolved.toString());
        assertEquals(Path.of("test/data/corrupted.zip").toAbsolutePath(), resolved);
      }
    }

    @Test
    void keepsATypedPathThatDoesNotExistSoTheUserSeesTheirOwnMistake(@TempDir Path directory)
        throws IOException {
      threeArchivesIn(directory);
      String typo = directory.resolve("mdi.zip").toString();

      try (ArchiveCatalog catalog = ArchiveCatalog.of(null, directory)) {
        assertEquals(typo, catalog.resolve(typo, "").orElseThrow().toString());
      }
    }

    @Test
    void dropsACookiePointingAtAnArchiveThatIsGone(@TempDir Path directory) throws IOException {
      threeArchivesIn(directory);
      String gone = directory.resolve("deleted.zip").toString();

      try (ArchiveCatalog catalog = ArchiveCatalog.of(null, directory)) {
        assertEquals("new.zip", nameOf(catalog.resolve("", gone)), "a stale cookie must heal itself");
      }
    }
  }

  @Nested
  class Wiring {

    @Test
    void searchesTheArchiveItIsAskedFor(@TempDir Path directory) throws IOException {
      threeArchivesIn(directory);

      try (ArchiveCatalog catalog = ArchiveCatalog.of(null, directory)) {
        assertEquals(1, hitsFor(catalog, directory.resolve("mid.zip"), "middling"));
        assertEquals(0, hitsFor(catalog, directory.resolve("new.zip"), "middling"));
      }
    }

    @Test
    void searchesTheFormerArchiveAgainAfterHavingSwitchedAway(@TempDir Path directory)
        throws IOException {
      threeArchivesIn(directory);

      try (ArchiveCatalog catalog = ArchiveCatalog.of(null, directory)) {
        hitsFor(catalog, directory.resolve("mid.zip"), "middling");
        hitsFor(catalog, directory.resolve("old.zip"), "old");

        assertEquals(1, hitsFor(catalog, directory.resolve("mid.zip"), "middling"));
      }
    }

    @Test
    void refusesAPathThatCannotBeRead(@TempDir Path directory) throws IOException {
      threeArchivesIn(directory);
      Path absent = directory.resolve("absent.zip");

      try (ArchiveCatalog catalog = ArchiveCatalog.of(null, directory)) {
        ArchiveUnavailableException failure =
            assertThrows(ArchiveUnavailableException.class, () -> catalog.serviceFor(absent));

        assertEquals("Cannot read archive: " + absent, failure.getMessage());
      }
    }

    private int hitsFor(ArchiveCatalog catalog, Path archive, String term) {
      return catalog
          .serviceFor(archive)
          .search(new SearchTerm(term, CaseSensitivity.SENSITIVE, WordScope.ANYWHERE))
          .groups()
          .size();
    }
  }

  @Nested
  class LaunchDefault {

    @Test
    void startsWithoutAnArchiveWhenTheDirectoryIsEmpty(@TempDir Path empty) {
      // The page asks for a path instead: refusing to start would leave nowhere to say so.
      try (ArchiveCatalog catalog = ArchiveCatalog.of(null, empty)) {
        assertTrue(catalog.resolve("", "").isEmpty());
      }
    }

    @Test
    void refusesToStartWhenTheExplicitArchiveCannotBeRead(@TempDir Path directory) {
      Path absent = directory.resolve("absent.zip");

      ArchiveUnavailableException failure =
          assertThrows(ArchiveUnavailableException.class, () -> ArchiveCatalog.of(absent, directory));

      assertEquals("Cannot read archive: " + absent, failure.getMessage());
    }
  }
}
