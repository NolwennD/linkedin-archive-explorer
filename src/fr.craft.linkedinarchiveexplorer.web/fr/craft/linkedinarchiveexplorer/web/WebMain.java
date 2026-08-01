package fr.craft.linkedinarchiveexplorer.web;

import com.sun.net.httpserver.HttpServer;
import fr.craft.linkedinarchiveexplorer.launcher.ArchiveCatalog;
import fr.craft.linkedinarchiveexplorer.launcher.ArchiveUnavailableException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;

/**
 * Web entry point — the twin of the CLI's {@code Main}, putting the same
 * {@link ArchiveCatalog} behind an HTTP server instead of a terminal.
 */
public final class WebMain {

  private static final int DEFAULT_PORT = 8080;
  private static final String NO_BROWSER = "--no-browser";

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
        case NO_BROWSER -> { /* read by opensBrowser, nothing to collect here */ }
        default -> {
          return usage(err);
        }
      }
    }

    ArchiveCatalog catalog;
    try {
      catalog = ArchiveCatalog.of(archivePath);
    } catch (ArchiveUnavailableException unavailable) {
      // Its message is already written for the user: no "Error: " prefix.
      err.println(unavailable.getMessage());
      return 1;
    } catch (RuntimeException failure) {
      // A damaged archive must not reach the terminal as a stack trace.
      err.println("Error: " + failure.getMessage());
      return 1;
    }

    try {
      HttpServer server = start(catalog, port);
      Runtime.getRuntime().addShutdownHook(new Thread(() -> shutDown(server, catalog)));
      announce(server, catalog, out, opensBrowser(args) ? new SystemBrowser() : BrowserLauncher.NONE);
      return 0;
    } catch (BindException taken) {
      // Never fall back to another port: a server listening somewhere unexpected is
      // worse than one that plainly refuses to start.
      err.println(
          "Port " + port + " already in use — try: ./linkedin-archive-explorer serve --port " + (port + 1));
      closeQuietly(catalog);
      return 1;
    } catch (IOException | RuntimeException failure) {
      err.println("Error: " + failure.getMessage());
      closeQuietly(catalog);
      return 1;
    }
  }

  /**
   * Starts the server on the loopback interface, serving what {@code explorer} searches.
   * The caller keeps ownership of {@code explorer}. Port {@code 0} asks the system for a
   * free port; read the one actually bound from {@code server.getAddress()}.
   */
  static HttpServer start(ArchiveCatalog catalog, int port) throws IOException {
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
    server.createContext("/", new SearchHandler(catalog, new HtmlRenderer()));
    // A single local user: sequential handling means no concurrency over the shared zip.
    server.setExecutor(null);
    server.start();
    return server;
  }

  /**
   * Says where to look, then takes the user there. The URL is read back from the server
   * rather than from the requested port, so {@code --port 0} lands on the right page.
   *
   * <p>Printed <em>before</em> opening: if no browser can be opened, the address is still
   * on the terminal.
   */
  static void announce(
      HttpServer server, ArchiveCatalog catalog, PrintStream out, BrowserLauncher browser) {
    String url = "http://localhost:" + server.getAddress().getPort();
    out.println(archiveLine(catalog));
    out.println("Serving on " + url + " — Ctrl-C to stop");
    browser.open(url);
  }

  /** Whether to take the user to the page. Opt-out, because opening it is the point. */
  static boolean opensBrowser(String[] args) {
    for (String argument : args) {
      if (NO_BROWSER.equals(argument)) {
        return false;
      }
    }
    return true;
  }

  /**
   * What the terminal says about the archive at start-up. An empty {@code data/} is no
   * longer a reason to refuse: the page asks for a path, so the server has to come up for
   * the user to be told anything at all.
   */
  static String archiveLine(ArchiveCatalog catalog) {
    return catalog
        .resolve("", "")
        .map(archive -> "Using archive: " + archive)
        .orElse("No archive found — the page will ask for one.");
  }

  private static void shutDown(HttpServer server, ArchiveCatalog catalog) {
    server.stop(0);
    closeQuietly(catalog);
  }

  private static void closeQuietly(ArchiveCatalog catalog) {
    try {
      catalog.close();
    } catch (Exception ignored) {
      // Nothing useful left to do while shutting down.
    }
  }

  private int usage(PrintStream err) {
    err.println(
        "usage: linkedin-archive-explorer [serve] [--archive <path>] [--port <n>] [--no-browser]");
    return 2;
  }
}
