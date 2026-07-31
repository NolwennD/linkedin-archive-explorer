package fr.craft.linkedinarchiveexplorer.web;

import com.sun.net.httpserver.HttpServer;
import fr.craft.linkedinarchiveexplorer.launcher.ArchiveUnavailableException;
import fr.craft.linkedinarchiveexplorer.launcher.Explorer;
import java.io.IOException;
import java.io.PrintStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;

/**
 * Web entry point — the twin of the CLI's {@code Main}, putting the same
 * {@link Explorer} behind an HTTP server instead of a terminal.
 */
public final class WebMain {

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

    Explorer explorer;
    try {
      explorer = Explorer.open(archivePath);
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
      HttpServer server = start(explorer, port);
      Runtime.getRuntime().addShutdownHook(new Thread(() -> shutDown(server, explorer)));
      out.println("Using archive: " + explorer.archive());
      out.println("Serving on http://localhost:" + server.getAddress().getPort() + " — Ctrl-C to stop");
      return 0;
    } catch (BindException taken) {
      // Never fall back to another port: a server listening somewhere unexpected is
      // worse than one that plainly refuses to start.
      err.println(
          "Port " + port + " already in use — try: ./linkedin-archive-explorer serve --port " + (port + 1));
      closeQuietly(explorer);
      return 1;
    } catch (IOException | RuntimeException failure) {
      err.println("Error: " + failure.getMessage());
      closeQuietly(explorer);
      return 1;
    }
  }

  /**
   * Starts the server on the loopback interface, serving what {@code explorer} searches.
   * The caller keeps ownership of {@code explorer}. Port {@code 0} asks the system for a
   * free port; read the one actually bound from {@code server.getAddress()}.
   */
  static HttpServer start(Explorer explorer, int port) throws IOException {
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
    server.createContext(
        "/", new SearchHandler(explorer.service(), new HtmlRenderer(explorer.archive().toString())));
    // A single local user: sequential handling means no concurrency over the shared zip.
    server.setExecutor(null);
    server.start();
    return server;
  }

  private static void shutDown(HttpServer server, Explorer explorer) {
    server.stop(0);
    closeQuietly(explorer);
  }

  private static void closeQuietly(Explorer explorer) {
    try {
      explorer.close();
    } catch (Exception ignored) {
      // Nothing useful left to do while shutting down.
    }
  }

  private int usage(PrintStream err) {
    err.println("usage: linkedin-archive-explorer serve [--archive <path>] [--port <n>]");
    return 2;
  }
}
