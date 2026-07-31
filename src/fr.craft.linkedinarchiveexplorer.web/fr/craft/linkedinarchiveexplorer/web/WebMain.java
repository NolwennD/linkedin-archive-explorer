package fr.craft.linkedinarchiveexplorer.web;

import com.sun.net.httpserver.HttpServer;
import fr.craft.linkedinarchiveexplorer.application.SearchContentsService;
import fr.craft.linkedinarchiveexplorer.domain.ContentSource;
import fr.craft.linkedinarchiveexplorer.domain.SearchEngine;
import fr.craft.linkedinarchiveexplorer.infrastructure.ArchiveLocator;
import fr.craft.linkedinarchiveexplorer.infrastructure.ArticlesContentSource;
import fr.craft.linkedinarchiveexplorer.infrastructure.CommentsContentSource;
import fr.craft.linkedinarchiveexplorer.infrastructure.JdkArticleTextExtractor;
import fr.craft.linkedinarchiveexplorer.infrastructure.SharesContentSource;
import fr.craft.linkedinarchiveexplorer.infrastructure.ZipArchive;
import java.io.IOException;
import java.io.PrintStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Web entry point and composition root — the twin of the CLI's {@code Main}, wiring the
 * same adapters behind an HTTP server instead of a terminal.
 */
public final class WebMain {

  private static final Path DEFAULT_ARCHIVE_DIR = Path.of("data");
  private static final int DEFAULT_PORT = 8080;

  public static void main(String[] args) {
    int status = new WebMain().run(args, System.out, System.err);
    if (status != 0) {
      System.exit(status);
    }
    // On success the server's own non-daemon thread keeps the JVM alive until Ctrl-C.
  }

  int run(String[] args, PrintStream out, PrintStream err) {
    Path archivePath = null;
    int port = DEFAULT_PORT;

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--archive" -> {
          if (i + 1 >= args.length) {
            return usage(err);
          }
          archivePath = Path.of(args[++i]);
        }
        case "--port" -> {
          if (i + 1 >= args.length) {
            return usage(err);
          }
          try {
            port = Integer.parseInt(args[++i]);
          } catch (NumberFormatException notANumber) {
            return usage(err);
          }
        }
        default -> {
          return usage(err);
        }
      }
    }

    Path archive =
        archivePath != null ? archivePath : ArchiveLocator.mostRecent(DEFAULT_ARCHIVE_DIR).orElse(null);
    if (archive == null) {
      err.println("No archive found in " + DEFAULT_ARCHIVE_DIR + "/ (or use --archive <path>).");
      return 1;
    }
    if (!Files.isReadable(archive)) {
      err.println("Cannot read archive: " + archive);
      return 1;
    }

    ZipArchive zip = ZipArchive.open(archive);
    try {
      HttpServer server = start(zip, archive.toString(), port);
      Runtime.getRuntime().addShutdownHook(new Thread(() -> shutDown(server, zip)));
      out.println("Using archive: " + archive);
      out.println("Serving on http://localhost:" + server.getAddress().getPort() + " — Ctrl-C to stop");
      return 0;
    } catch (BindException taken) {
      // Never fall back to another port: a server listening somewhere unexpected is
      // worse than one that plainly refuses to start.
      err.println(
          "Port " + port + " already in use — try: ./linkedin-archive-explorer serve --port " + (port + 1));
      closeQuietly(zip);
      return 1;
    } catch (IOException | RuntimeException failure) {
      err.println("Error: " + failure.getMessage());
      closeQuietly(zip);
      return 1;
    }
  }

  /**
   * Wires the adapters onto an already-open archive and starts the server on the loopback
   * interface. The caller keeps ownership of {@code zip}. Port {@code 0} asks the system
   * for a free port; read the one actually bound from {@code server.getAddress()}.
   */
  static HttpServer start(ZipArchive zip, String archiveLabel, int port) throws IOException {
    List<ContentSource> sources =
        List.of(
            new CommentsContentSource(zip),
            new SharesContentSource(zip),
            new ArticlesContentSource(zip, new JdkArticleTextExtractor()));
    SearchContentsService service = new SearchContentsService(sources, new SearchEngine());

    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
    server.createContext("/", new SearchHandler(service, new HtmlRenderer(archiveLabel)));
    // A single local user: sequential handling means no concurrency over the shared zip.
    server.setExecutor(null);
    server.start();
    return server;
  }

  private static void shutDown(HttpServer server, ZipArchive zip) {
    server.stop(0);
    closeQuietly(zip);
  }

  private static void closeQuietly(ZipArchive zip) {
    try {
      zip.close();
    } catch (Exception ignored) {
      // Nothing useful left to do while shutting down.
    }
  }

  private int usage(PrintStream err) {
    err.println("usage: linkedin-archive-explorer serve [--archive <path>] [--port <n>]");
    return 2;
  }
}
