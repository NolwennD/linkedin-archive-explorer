package fr.craft.linkedinarchiveexplorer.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.craft.linkedinarchiveexplorer.application.ContentGroup;
import fr.craft.linkedinarchiveexplorer.application.SearchResults;
import fr.craft.linkedinarchiveexplorer.domain.CaseSensitivity;
import fr.craft.linkedinarchiveexplorer.domain.ContentType;
import fr.craft.linkedinarchiveexplorer.domain.SearchTerm;
import fr.craft.linkedinarchiveexplorer.domain.WordScope;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExplorerTest {

  private static final String COMMENTS =
      """
      Date,Link,Message
      2024-11-06 12:49:49,https://li/comment,"a comment about kotlin"
      """;

  private static final String SHARES =
      """
      Date,ShareLink,ShareCommentary,Visibility
      2024-01-02 08:00:00,https://li/post,"a post about kotlin",MEMBER_NETWORK
      """;

  private static final String ARTICLE =
      """
      <html><head><title>T</title></head>
      <body>
        <h1><a href="https://li/article">My Article</a></h1>
        <p>an article about kotlin</p>
      </body></html>
      """;

  private static Path archiveAt(Path zip, Map<String, String> entries) throws IOException {
    Files.createDirectories(zip.getParent());
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
      for (Map.Entry<String, String> entry : entries.entrySet()) {
        out.putNextEntry(new ZipEntry(entry.getKey()));
        out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
      }
    }
    return zip;
  }

  private static Path fullArchiveAt(Path zip) throws IOException {
    return archiveAt(
        zip,
        Map.of(
            "Comments_1.csv", COMMENTS,
            "Shares_1.csv", SHARES,
            "Articles/Articles/a.html", ARTICLE));
  }

  @Nested
  class ArchiveResolution {

    @Test
    void opensTheArchiveGivenExplicitly(@TempDir Path directory) throws IOException {
      Path zip = fullArchiveAt(directory.resolve("export.zip"));

      try (Explorer explorer = Explorer.open(zip)) {
        assertEquals(zip, explorer.archive());
      }
    }

    @Test
    void fallsBackToTheMostRecentArchiveOfTheDefaultDirectory(@TempDir Path directory) throws IOException {
      fullArchiveAt(directory.resolve("Basic_LinkedInDataExport_01-02-2024.zip"));
      Path newest = fullArchiveAt(directory.resolve("Basic_LinkedInDataExport_06-15-2025.zip"));

      try (Explorer explorer = Explorer.open(null, directory)) {
        assertEquals(newest, explorer.archive());
      }
    }

    @Test
    void refusesToStartWhenTheDefaultDirectoryHoldsNoArchive(@TempDir Path empty) {
      ArchiveUnavailableException failure =
          assertThrows(ArchiveUnavailableException.class, () -> Explorer.open(null, empty));

      assertEquals("No archive found in " + empty + "/ (or use --archive <path>).", failure.getMessage());
    }

    @Test
    void refusesToStartWhenTheArchiveCannotBeRead(@TempDir Path directory) {
      Path absent = directory.resolve("absent.zip");

      ArchiveUnavailableException failure =
          assertThrows(ArchiveUnavailableException.class, () -> Explorer.open(absent));

      assertEquals("Cannot read archive: " + absent, failure.getMessage());
    }
  }

  @Nested
  class Wiring {

    @Test
    void searchesEveryContentSourceOfTheArchive(@TempDir Path directory) throws IOException {
      try (Explorer explorer = Explorer.open(fullArchiveAt(directory.resolve("export.zip")))) {
        SearchResults results =
            explorer.service().search(new SearchTerm("kotlin", CaseSensitivity.SENSITIVE, WordScope.ANYWHERE));

        assertEquals(
            List.of(ContentType.ARTICLE, ContentType.POST, ContentType.COMMENT),
            results.groups().stream().map(ContentGroup::type).toList());
      }
    }

    @Test
    void appliesTheSearchOptionsThroughTheWiredEngine(@TempDir Path directory) throws IOException {
      try (Explorer explorer = Explorer.open(fullArchiveAt(directory.resolve("export.zip")))) {
        SearchTerm sensitive = new SearchTerm("Kotlin", CaseSensitivity.SENSITIVE, WordScope.ANYWHERE);
        SearchTerm insensitive = new SearchTerm("Kotlin", CaseSensitivity.INSENSITIVE, WordScope.ANYWHERE);

        assertTrue(explorer.service().search(sensitive).groups().isEmpty());
        assertEquals(3, explorer.service().search(insensitive).groups().size());
      }
    }
  }
}
