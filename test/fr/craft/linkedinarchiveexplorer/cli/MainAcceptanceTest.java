package fr.craft.linkedinarchiveexplorer.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MainAcceptanceTest {

  private final ByteArrayOutputStream out = new ByteArrayOutputStream();
  private final ByteArrayOutputStream err = new ByteArrayOutputStream();

  private static final String ESC = "\u001B";

  private int run(String... args) {
    return run(false, args);
  }

  private int run(boolean defaultStyled, String... args) {
    return new Main().run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
        new PrintStream(err, true, StandardCharsets.UTF_8), defaultStyled);
  }

  private String out() {
    return out.toString(StandardCharsets.UTF_8);
  }

  private static Path archiveWithComment(String message) throws IOException {
    String csv =
        """
        Date,Link,Message
        2024-11-06 12:49:49,https://li/1,"%s"
        """
            .formatted(message);
    Path zip = Files.createTempFile("archive", ".zip");
    zip.toFile().deleteOnExit();
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
      out.putNextEntry(new ZipEntry("Comments_1.csv"));
      out.write(csv.getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
    return zip;
  }

  @Test
  void searchesTheArchiveAndRendersMatches() throws IOException {
    Path archive = archiveWithComment("about Date(0,0,0) here");

    int code = run("--archive", archive.toString(), "Date(0,0,0)");

    assertEquals(0, code);
    assertTrue(out().contains("Using archive: " + archive), out());
    assertTrue(out().contains("COMMENTS"), out());
    assertTrue(out().contains("https://li/1"), out());
    assertTrue(out().contains("Date(0,0,0)"), out());
  }

  @Test
  void reportsNoResultWhenTheTermIsAbsent() throws IOException {
    Path archive = archiveWithComment("nothing relevant");

    assertEquals(0, run("--archive", archive.toString(), "absent"));
    assertTrue(out().contains("No results"), out());
  }

  @Test
  void stylesByDefaultWhenInATerminal() throws IOException {
    Path archive = archiveWithComment("about Date(0,0,0) here");

    run(true, "--archive", archive.toString(), "Date(0,0,0)");

    assertTrue(out().contains(ESC), "expected ANSI/OSC escapes when styled by default");
  }

  @Test
  void noColorOverridesTheStyledDefault() throws IOException {
    Path archive = archiveWithComment("about Date(0,0,0) here");

    run(true, "--archive", archive.toString(), "--no-color", "Date(0,0,0)");

    assertFalse(out().contains(ESC), "--no-color must win over the styled default");
  }

  @Test
  void colorForcesStylingEvenWhenDefaultIsOff() throws IOException {
    Path archive = archiveWithComment("about Date(0,0,0) here");

    run(false, "--archive", archive.toString(), "--color", "Date(0,0,0)");

    assertTrue(out().contains(ESC), "--color must force styling");
  }

  @Test
  void failsWhenTheArchiveDoesNotExist() {
    assertEquals(1, run("--archive", "/no/such/archive.zip", "term"));
  }

  @Test
  void showsUsageWhenTheTermIsMissing() {
    assertEquals(2, run("--archive", "/whatever.zip"));
  }

  @Test
  void showsUsageWhenGivenASecondSearchTerm() {
    assertEquals(2, run("--archive", "/whatever.zip", "first", "second"));
  }

  @Test
  void showsUsageForAnUnknownOption() {
    assertEquals(2, run("--archive", "/whatever.zip", "--bogus", "term"));
  }

  @Test
  void showsUsageWhenArchiveFlagHasNoValue() {
    assertEquals(2, run("term", "--archive"));
  }

  @ParameterizedTest(name = "finds non-latin {0}")
  @ValueSource(strings = {"咖啡", "кофе", "καφές", "قهوة"})
  void searchesNonLatinContentEndToEnd(String term) throws IOException {
    Path archive = archiveWithComment("un long texte contenant " + term + " en plein milieu");

    assertEquals(0, run("--archive", archive.toString(), term));
    assertTrue(out().contains("COMMENTS"), out());
    assertTrue(out().contains(term), out());
  }
}
