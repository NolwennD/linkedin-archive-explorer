package fr.craft.linkedinarchiveexplorer.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveLocatorTest {

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
