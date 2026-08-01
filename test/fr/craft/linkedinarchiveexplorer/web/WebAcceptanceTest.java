package fr.craft.linkedinarchiveexplorer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import fr.craft.linkedinarchiveexplorer.launcher.ArchiveCatalog;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebAcceptanceTest {

  /** The versioned damaged archive (a real zip with its END header truncated away). */
  private static final Path CORRUPTED_ARCHIVE = Path.of("test/data/corrupted.zip");

  /** A test body running against an already-started server. */
  private interface ServerTest {
    void run(HttpServer server) throws Exception;
  }

  private static Path archiveIn(Path directory, String name, String message) throws IOException {
    String csv =
        """
        Date,Link,Message
        2024-11-06 12:49:49,https://li/1,"%s"
        """
            .formatted(message);
    Path zip = directory.resolve(name);
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
      out.putNextEntry(new ZipEntry("Comments_1.csv"));
      out.write(csv.getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
    return zip;
  }

  /** One archive, alone in a directory of its own, so no other zip can interfere. */
  private static Path archiveWithComment(String message) throws IOException {
    Path directory = Files.createTempDirectory("archive");
    directory.toFile().deleteOnExit();
    return archiveIn(directory, "export.zip", message);
  }

  /** Starts the server on an ephemeral port, so tests never collide with a real one. */
  private static void serving(Path archive, ServerTest test) throws Exception {
    servingCatalog(ArchiveCatalog.of(archive, archive.getParent()), test);
  }

  /** Serves every archive of {@code directory}, with none singled out on the command line. */
  private static void servingDirectory(Path directory, ServerTest test) throws Exception {
    servingCatalog(ArchiveCatalog.of(null, directory), test);
  }

  private static void servingCatalog(ArchiveCatalog catalog, ServerTest test) throws Exception {
    try (ArchiveCatalog open = catalog) {
      HttpServer server = WebMain.start(open, 0);
      try {
        test.run(server);
      } finally {
        server.stop(0);
      }
    }
  }

  private static HttpResponse<String> get(HttpServer server, String pathAndQuery) throws Exception {
    return get(server, pathAndQuery, null);
  }

  private static HttpResponse<String> get(HttpServer server, String pathAndQuery, String cookie)
      throws Exception {
    URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + pathAndQuery);
    HttpRequest.Builder request = HttpRequest.newBuilder(uri);
    if (cookie != null) {
      request.header("Cookie", cookie);
    }
    return HttpClient.newHttpClient()
        .send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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

  @Nested
  class ChoosingTheArchive {

    /** Two archives, each recognisable by the comment it holds; "new.zip" is the default. */
    private Path twoArchivesIn(Path directory) throws IOException {
      archiveIn(directory, "old.zip", "an alpha comment");
      archiveIn(directory, "new.zip", "a beta comment");
      Files.setLastModifiedTime(
          directory.resolve("old.zip"), FileTime.from(Instant.now().minus(2, ChronoUnit.DAYS)));
      Files.setLastModifiedTime(directory.resolve("new.zip"), FileTime.from(Instant.now()));
      return directory;
    }

    private String path(Path directory, String name) {
      return directory.resolve(name).toString();
    }

    private String encoded(Path directory, String name) {
      return URLEncoder.encode(path(directory, name), StandardCharsets.UTF_8);
    }

    @Test
    void suggestsEveryArchiveOfTheDirectoryAndStartsOnTheMostRecent(@TempDir Path directory)
        throws Exception {
      servingDirectory(
          twoArchivesIn(directory),
          server -> {
            String body = get(server, "/").body();

            assertTrue(body.contains("value=\"" + path(directory, "new.zip") + "\" list="), body);
            assertTrue(body.contains("<option value=\"" + path(directory, "new.zip") + "\">"), body);
            assertTrue(body.contains("<option value=\"" + path(directory, "old.zip") + "\">"), body);
          });
    }

    @Test
    void searchesThePathGivenInTheQuery(@TempDir Path directory) throws Exception {
      servingDirectory(
          twoArchivesIn(directory),
          server -> {
            String body = get(server, "/?q=alpha&archive=" + encoded(directory, "old.zip")).body();

            assertTrue(body.contains("<mark>alpha</mark>"), body);
            assertTrue(get(server, "/?q=alpha").body().contains("No results"), "the default is new.zip");
          });
    }

    @Test
    void remembersTheChoiceInACookie(@TempDir Path directory) throws Exception {
      servingDirectory(
          twoArchivesIn(directory),
          server -> {
            HttpResponse<String> response =
                get(server, "/?q=alpha&archive=" + encoded(directory, "old.zip"));

            assertEquals(
                "archive="
                    + encoded(directory, "old.zip")
                    + "; Path=/; Max-Age=31536000; SameSite=Strict; HttpOnly",
                response.headers().firstValue("Set-Cookie").orElseThrow());
          });
    }

    @Test
    void searchesTheArchiveTheCookieRemembers(@TempDir Path directory) throws Exception {
      servingDirectory(
          twoArchivesIn(directory),
          server ->
              assertTrue(
                  get(server, "/?q=alpha", "archive=" + encoded(directory, "old.zip"))
                      .body()
                      .contains("<mark>alpha</mark>")));
    }

    @Test
    void letsTheQueryOverrideTheCookie(@TempDir Path directory) throws Exception {
      servingDirectory(
          twoArchivesIn(directory),
          server -> {
            String body =
                get(
                        server,
                        "/?q=beta&archive=" + encoded(directory, "new.zip"),
                        "archive=" + encoded(directory, "old.zip"))
                    .body();

            assertTrue(body.contains("<mark>beta</mark>"), body);
          });
    }

    @Test
    void showsTheProblemAndKeepsThePathWhenItDoesNotOpen(@TempDir Path directory) throws Exception {
      servingDirectory(
          twoArchivesIn(directory),
          server -> {
            HttpResponse<String> response =
                get(server, "/?q=beta&archive=" + encoded(directory, "typo.zip"));

            assertEquals(200, response.statusCode());
            assertTrue(
                response.body().contains("Cannot read archive: " + path(directory, "typo.zip")),
                response.body());
            assertTrue(response.body().contains("value=\"" + path(directory, "typo.zip") + "\""), "kept");
            assertTrue(response.body().contains("value=\"beta\""), "the term is kept too");
            assertTrue(
                response.headers().firstValue("Set-Cookie").isEmpty(),
                "only an archive that opened deserves to be remembered");
          });
    }

    @Test
    void asksForAPathWhenTheDirectoryIsNotEvenThere(@TempDir Path directory) throws Exception {
      // An archive unpacked anywhere has no data/ at all. For a binary that is the normal
      // case, not an edge one, so it must come up exactly like an empty directory does.
      servingDirectory(
          directory.resolve("no-such-directory"),
          server -> {
            HttpResponse<String> response = get(server, "/");

            assertEquals(200, response.statusCode(), "a missing directory is not a failure");
            assertTrue(response.body().contains("name=\"archive\" value=\"\""), response.body());
          });
    }

    @Test
    void asksForAPathWhenTheDirectoryHoldsNoArchive(@TempDir Path empty) throws Exception {
      servingDirectory(
          empty,
          server -> {
            HttpResponse<String> response = get(server, "/");

            assertEquals(200, response.statusCode(), "an empty directory is not a reason to refuse");
            assertTrue(response.body().contains("name=\"archive\" value=\"\" list="), response.body());
            assertTrue(response.body().contains("<datalist id=\"archives\">\n</datalist>"), response.body());
          });
    }
  }

  @Nested
  class OpeningTheBrowser {

    /** A hand-written fake on the port: no mock framework, and nothing really opens. */
    private static final class RecordingBrowser implements BrowserLauncher {
      private final List<String> opened = new ArrayList<>();

      @Override
      public void open(String url) {
        opened.add(url);
      }
    }

    @Test
    void opensThePageOnThePortThatWasActuallyBound(@TempDir Path directory) throws Exception {
      // Port 0 means "any free port", so the URL can only be right if it is read back
      // from the server rather than from the requested port.
      RecordingBrowser browser = new RecordingBrowser();
      Path archive = archiveIn(directory, "export.zip", "nothing relevant");

      servingCatalog(
          ArchiveCatalog.of(archive, directory),
          server -> {
            WebMain.announce(server, ArchiveCatalog.of(archive, directory), quiet(), browser);

            assertEquals(
                List.of("http://localhost:" + server.getAddress().getPort()), browser.opened);
          });
    }

    @Test
    void opensNothingWhenAskedNotTo() {
      assertFalse(WebMain.opensBrowser(new String[] {"--no-browser"}));
      assertTrue(WebMain.opensBrowser(new String[] {}));
      assertTrue(WebMain.opensBrowser(new String[] {"--port", "9000"}));
    }

    private static PrintStream quiet() {
      return new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
    }
  }

  @Nested
  class StartupMessage {

    @Test
    void namesTheArchiveItWillSearch(@TempDir Path directory) throws Exception {
      Path archive = archiveIn(directory, "export.zip", "nothing relevant");

      try (ArchiveCatalog catalog = ArchiveCatalog.of(null, directory)) {
        assertEquals("Using archive: " + archive, WebMain.archiveLine(catalog));
      }
    }

    @Test
    void saysSoWhenThereIsNoArchiveToStartOn(@TempDir Path empty) {
      try (ArchiveCatalog catalog = ArchiveCatalog.of(null, empty)) {
        assertEquals("No archive found — the page will ask for one.", WebMain.archiveLine(catalog));
      }
    }
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
