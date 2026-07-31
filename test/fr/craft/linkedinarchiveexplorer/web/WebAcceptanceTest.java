package fr.craft.linkedinarchiveexplorer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import fr.craft.linkedinarchiveexplorer.launcher.Explorer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class WebAcceptanceTest {

  /** The versioned damaged archive (a real zip with its END header truncated away). */
  private static final Path CORRUPTED_ARCHIVE = Path.of("test/data/corrupted.zip");

  /** A test body running against an already-started server. */
  private interface ServerTest {
    void run(HttpServer server) throws Exception;
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

  /** Starts the server on an ephemeral port, so tests never collide with a real one. */
  private static void serving(Path archive, ServerTest test) throws Exception {
    try (Explorer explorer = Explorer.open(archive)) {
      HttpServer server = WebMain.start(explorer, 0);
      try {
        test.run(server);
      } finally {
        server.stop(0);
      }
    }
  }

  private static HttpResponse<String> get(HttpServer server, String pathAndQuery) throws Exception {
    URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + pathAndQuery);
    return HttpClient.newHttpClient()
        .send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  @Test
  void servesTheLandingPageWithAnEmptyForm() throws Exception {
    serving(
        archiveWithComment("nothing special"),
        server -> {
          HttpResponse<String> response = get(server, "/");

          assertEquals(200, response.statusCode());
          assertTrue(response.body().contains("<input type=\"search\""), response.body());
          assertFalse(response.body().contains("No results"), response.body());
        });
  }

  @Test
  void searchesTheArchiveAndHighlightsTheMatch() throws Exception {
    serving(
        archiveWithComment("un café serré le matin"),
        server -> {
          HttpResponse<String> response = get(server, "/?q=caf%C3%A9");

          assertEquals(200, response.statusCode());
          assertTrue(response.body().contains("Comments"), response.body());
          assertTrue(response.body().contains("<mark>café</mark>"), response.body());
          assertTrue(response.body().contains("https://li/1"), response.body());
        });
  }

  @Test
  void reportsNoResultWhenTheTermIsAbsent() throws Exception {
    serving(
        archiveWithComment("nothing relevant"),
        server -> assertTrue(get(server, "/?q=absent").body().contains("No results for \"absent\".")));
  }

  @Test
  void treatsABlankTermAsNoSearchAtAll() throws Exception {
    serving(
        archiveWithComment("nothing relevant"),
        server -> assertFalse(get(server, "/?q=%20").body().contains("No results"), "a blank term is not a search"));
  }

  @Test
  void appliesTheIgnoreCaseCheckbox() throws Exception {
    serving(
        archiveWithComment("about Date here"),
        server -> {
          assertTrue(get(server, "/?q=date&i=on").body().contains("<mark>Date</mark>"));
          assertTrue(get(server, "/?q=date").body().contains("No results"));
        });
  }

  @Test
  void appliesTheWholeWordCheckbox() throws Exception {
    // Unaccented on purpose: with "développeur" the term would already miss on the accent,
    // and the test would pass without saying anything about whole-word matching.
    serving(
        archiveWithComment("un developpeur ici"),
        server -> {
          assertTrue(get(server, "/?q=dev&w=on").body().contains("No results"));
          assertTrue(get(server, "/?q=dev").body().contains("<mark>dev</mark>"));
        });
  }

  @Test
  void neverLetsArchiveMarkupBecomeLiveMarkup() throws Exception {
    serving(
        archiveWithComment("<script>alert(1)</script> is not code here"),
        server -> {
          String body = get(server, "/?q=alert").body();

          assertTrue(body.contains("&lt;script&gt;"), body);
          assertFalse(body.contains("<script"), "the archive must never inject a script tag");
        });
  }

  @Test
  void answers404OnAnyOtherPath() throws Exception {
    serving(
        archiveWithComment("nothing relevant"),
        server -> assertEquals(404, get(server, "/somewhere-else").statusCode()));
  }

  @Test
  void refusesToStartOnACorruptedArchive() {
    ByteArrayOutputStream err = new ByteArrayOutputStream();

    int status =
        new WebMain()
            .run(
                new String[] {"--archive", CORRUPTED_ARCHIVE.toString(), "--port", "0"},
                new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

    assertEquals(1, status);
    // A stack trace on the terminal is not a diagnosis: the message must be readable.
    assertTrue(
        err.toString(StandardCharsets.UTF_8).startsWith("Error: Cannot open archive: " + CORRUPTED_ARCHIVE),
        err.toString(StandardCharsets.UTF_8));
  }

  @Test
  void listensOnTheLoopbackInterfaceOnly() throws Exception {
    serving(
        archiveWithComment("nothing relevant"),
        server ->
            assertTrue(
                server.getAddress().getAddress().isLoopbackAddress(),
                "a personal archive must not be exposed on the network"));
  }
}
