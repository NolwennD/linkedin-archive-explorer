package fr.craft.linkedinarchiveexplorer.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import fr.craft.linkedinarchiveexplorer.application.SearchContentsService;
import fr.craft.linkedinarchiveexplorer.domain.CaseSensitivity;
import fr.craft.linkedinarchiveexplorer.domain.SearchTerm;
import fr.craft.linkedinarchiveexplorer.domain.WordScope;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Turns one HTTP request into a search and its rendered page. It holds neither search
 * logic nor rendering logic: it only translates between the two worlds.
 */
final class SearchHandler implements HttpHandler {

  private static final String HTML = "text/html; charset=utf-8";

  private final SearchContentsService service;
  private final HtmlRenderer renderer;

  SearchHandler(SearchContentsService service, HtmlRenderer renderer) {
    this.service = service;
    this.renderer = renderer;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    try {
      if (!"/".equals(exchange.getRequestURI().getPath())) {
        respond(exchange, 404, "text/plain; charset=utf-8", "Not found\n");
        return;
      }
      respond(exchange, 200, HTML, pageFor(exchange.getRequestURI().getRawQuery()));
    } catch (RuntimeException failure) {
      // One bad request must not take the server down with it.
      respond(exchange, 500, HTML, errorPage(failure));
    }
  }

  /** The raw query, deliberately: {@code getQuery()} would decode it a second time. */
  private String pageFor(String rawQuery) {
    QueryParameters parameters = QueryParameters.parse(rawQuery);
    String query = parameters.value("q");
    if (query.isBlank()) {
      return renderer.renderEmptyForm();
    }
    SearchTerm term =
        new SearchTerm(
            query,
            parameters.isChecked("i") ? CaseSensitivity.INSENSITIVE : CaseSensitivity.SENSITIVE,
            parameters.isChecked("w") ? WordScope.WHOLE_WORD : WordScope.ANYWHERE);
    return renderer.render(term, service.search(term));
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
