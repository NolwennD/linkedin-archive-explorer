package fr.craft.linkedinarchiveexplorer.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import fr.craft.linkedinarchiveexplorer.application.SearchContentsService;
import fr.craft.linkedinarchiveexplorer.domain.CaseSensitivity;
import fr.craft.linkedinarchiveexplorer.domain.SearchTerm;
import fr.craft.linkedinarchiveexplorer.domain.WordScope;
import fr.craft.linkedinarchiveexplorer.launcher.ArchiveCatalog;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Turns one HTTP request into a search and its rendered page. It holds neither search
 * logic nor rendering logic: it only translates between the two worlds.
 */
final class SearchHandler implements HttpHandler {

  private static final String HTML = "text/html; charset=utf-8";

  private static final String ARCHIVE = "archive";
  private static final long A_YEAR_IN_SECONDS = 31_536_000L;

  private final ArchiveCatalog catalog;
  private final HtmlRenderer renderer;

  SearchHandler(ArchiveCatalog catalog, HtmlRenderer renderer) {
    this.catalog = catalog;
    this.renderer = renderer;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    try {
      if (!"/".equals(exchange.getRequestURI().getPath())) {
        respond(exchange, 404, "text/plain; charset=utf-8", "Not found\n");
        return;
      }
      // The raw query, deliberately: getQuery() would decode it a second time.
      QueryParameters parameters = QueryParameters.parse(exchange.getRequestURI().getRawQuery());
      Cookies cookies = Cookies.parse(exchange.getRequestHeaders().getFirst("Cookie"));
      Optional<Path> resolved = catalog.resolve(parameters.value(ARCHIVE), cookies.value(ARCHIVE));

      if (resolved.isEmpty()) {
        // Nothing to propose: the required field is what asks the user for a path.
        respond(exchange, 200, HTML, formFor(parameters, fieldOf("", "")));
        return;
      }
      Path archive = resolved.get();
      SearchContentsService service;
      try {
        service = catalog.serviceFor(archive);
      } catch (RuntimeException unopenable) {
        // A path typed by hand is wrong now and then; that is not a server failure. The
        // page comes back with the path and the term intact, ready to be corrected — and
        // no cookie is written, so the remembered archive stays the last one that worked.
        respond(
            exchange,
            200,
            HTML,
            formFor(parameters, fieldOf(archive.toString(), messageOf(unopenable))));
        return;
      }

      String page = pageFor(parameters, service, fieldOf(archive.toString(), ""));
      // The archive actually opened, not the one asked for: a stale cookie heals itself.
      exchange.getResponseHeaders().add("Set-Cookie", cookieFor(archive));
      respond(exchange, 200, HTML, page);
    } catch (RuntimeException failure) {
      // One bad request must not take the server down with it.
      respond(exchange, 500, HTML, errorPage(failure));
    }
  }

  private String pageFor(QueryParameters parameters, SearchContentsService service, ArchiveField field) {
    if (parameters.value("q").isBlank()) {
      return formFor(parameters, field);
    }
    SearchTerm term = termOf(parameters);
    return renderer.render(term, service.search(term), field);
  }

  private String formFor(QueryParameters parameters, ArchiveField field) {
    return renderer.renderForm(
        parameters.value("q"), parameters.isChecked("i"), parameters.isChecked("w"), field);
  }

  private static SearchTerm termOf(QueryParameters parameters) {
    return new SearchTerm(
        parameters.value("q"),
        parameters.isChecked("i") ? CaseSensitivity.INSENSITIVE : CaseSensitivity.SENSITIVE,
        parameters.isChecked("w") ? WordScope.WHOLE_WORD : WordScope.ANYWHERE);
  }

  /** Suggestions are full paths: a {@code <datalist>} entry has to be a usable value. */
  private ArchiveField fieldOf(String value, String error) {
    return new ArchiveField(catalog.archives().stream().map(Path::toString).toList(), value, error);
  }

  /** {@code ArchiveUnavailableException} messages are already written for a human. */
  private static String messageOf(RuntimeException failure) {
    return failure.getMessage() == null ? failure.toString() : failure.getMessage();
  }

  /**
   * Percent-encoded, and not only for looks: a file name may hold a space, a semicolon or
   * — under Linux — a newline, which would otherwise forge a header of its own.
   */
  private static String cookieFor(Path archive) {
    String path = URLEncoder.encode(archive.toString(), StandardCharsets.UTF_8);
    return ARCHIVE
        + "="
        + path.replace("+", "%20")
        + "; Path=/; Max-Age="
        + A_YEAR_IN_SECONDS
        + "; SameSite=Strict; HttpOnly";
  }

  private static String errorPage(RuntimeException failure) {
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head><meta charset="utf-8"><title>Error — LinkedIn archive explorer</title></head>
        <body><h1>Error</h1><p>%s</p></body>
        </html>
        """
        .formatted(HtmlRenderer.escape(String.valueOf(failure.getMessage())));
  }

  private static void respond(HttpExchange exchange, int status, String contentType, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", contentType);
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }
}
