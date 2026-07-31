package fr.craft.linkedinarchiveexplorer.web;

import fr.craft.linkedinarchiveexplorer.application.ContentGroup;
import fr.craft.linkedinarchiveexplorer.application.SearchResults;
import fr.craft.linkedinarchiveexplorer.domain.CaseSensitivity;
import fr.craft.linkedinarchiveexplorer.domain.ContentType;
import fr.craft.linkedinarchiveexplorer.domain.Excerpt;
import fr.craft.linkedinarchiveexplorer.domain.SearchHit;
import fr.craft.linkedinarchiveexplorer.domain.SearchTerm;
import fr.craft.linkedinarchiveexplorer.domain.WordScope;
import java.util.Locale;

/**
 * Renders {@link SearchResults} as one self-contained HTML page: a search form, then a
 * collapsible section per content type. The web twin of {@code TerminalRenderer}.
 *
 * <p>Every value taken from the archive — or typed by the user — goes through
 * {@link #escape}, because an authored post may legitimately contain markup.
 */
public final class HtmlRenderer {

  private static final String APPLICATION_NAME = "LinkedIn archive explorer";

  private static final String STYLE =
      """
      :root { color-scheme: light dark; }
      body { font-family: system-ui, sans-serif; line-height: 1.5; margin: 0 auto; max-width: 46rem; padding: 1rem 1.5rem; }
      h1 { font-size: 1.1rem; }
      h2 { display: inline; font-size: .95rem; letter-spacing: .08em; text-transform: uppercase; }
      h3 { font-size: .8rem; font-weight: normal; margin: 1.1rem 0 .1rem; overflow-wrap: anywhere; }
      label { margin-right: .75rem; }
      input[type=search] { min-width: 16rem; padding: .3rem; }
      summary { cursor: pointer; margin: 1.75rem 0 .25rem; }
      .count { opacity: .55; }
      time { font-size: .78rem; opacity: .55; }
      ol { list-style: none; margin: 0; padding: 0; }
      ul { margin: .2rem 0; padding-left: 1.2rem; }
      mark { font-weight: bold; }
      footer { border-top: 1px solid; font-size: .78rem; margin-top: 2.5rem; opacity: .55; padding-top: .5rem; }
      """;

  private final String archiveLabel;

  public HtmlRenderer(String archiveLabel) {
    if (archiveLabel == null) {
      throw new IllegalArgumentException("A renderer must know which archive it reports on");
    }
    this.archiveLabel = archiveLabel;
  }

  /** The landing page: an empty form, no search performed. */
  public String renderEmptyForm() {
    return page("", false, false, "");
  }

  public String render(SearchTerm term, SearchResults results) {
    return page(
        term.value(),
        term.caseSensitivity() == CaseSensitivity.INSENSITIVE,
        term.wordScope() == WordScope.WHOLE_WORD,
        results.groups().isEmpty() ? noResults(term) : groups(results));
  }

  private String page(String query, boolean ignoreCase, boolean wholeWord, String main) {
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>%s</title>
        <style>
        %s</style>
        </head>
        <body>
        <header>
        <h1>%s</h1>
        <search>
        <form method="get" action="/">
        <label for="q">Search</label>
        <input type="search" id="q" name="q" value="%s" required autofocus>
        <label><input type="checkbox" name="i"%s> Ignore case</label>
        <label><input type="checkbox" name="w"%s> Whole word</label>
        <button type="submit">Search</button>
        </form>
        </search>
        </header>
        <main>
        %s</main>
        <footer>Archive: %s</footer>
        </body>
        </html>
        """
        .formatted(
            title(query),
            STYLE,
            APPLICATION_NAME,
            escape(query),
            checked(ignoreCase),
            checked(wholeWord),
            main,
            escape(archiveLabel));
  }

  /** The term comes first, so a browser tab or a history entry identifies the search. */
  private static String title(String query) {
    return query.isEmpty() ? APPLICATION_NAME : escape(query) + " — " + APPLICATION_NAME;
  }

  private static String checked(boolean on) {
    return on ? " checked" : "";
  }

  private String groups(SearchResults results) {
    StringBuilder html = new StringBuilder();
    for (ContentGroup group : results.groups()) {
      String id = group.type().name().toLowerCase(Locale.ROOT) + "s";
      html.append(
          """
          <section aria-labelledby="%s">
          <details open>
          <summary><h2 id="%s">%s <span class="count">(%d)</span></h2></summary>
          <ol>
          """
              .formatted(id, id, heading(group.type()), group.hits().size()));
      for (SearchHit hit : group.hits()) {
        html.append(hit(hit));
      }
      html.append("</ol>\n</details>\n</section>\n");
    }
    return html.toString();
  }

  /** Title case in the markup; the stylesheet is what shouts it in capitals. */
  private static String heading(ContentType type) {
    return switch (type) {
      case ARTICLE -> "Articles";
      case POST -> "Posts";
      case COMMENT -> "Comments";
    };
  }

  private String hit(SearchHit hit) {
    StringBuilder excerpts = new StringBuilder();
    for (Excerpt excerpt : hit.excerpts()) {
      excerpts.append("<li>").append(highlighted(excerpt)).append("</li>\n");
    }
    return """
        <li>
        <article>
        <h3><a href="%s">%s</a></h3>
        %s<ul>
        %s</ul>
        </article>
        </li>
        """
        .formatted(escape(hit.url()), escape(hit.url()), date(hit), excerpts);
  }

  private static String date(SearchHit hit) {
    return hit.date()
        .map(
            day ->
                """
                <time datetime="%s">%s</time>
                """
                    .formatted(day, day))
        .orElse("");
  }

  /**
   * Composed part by part rather than through {@code Excerpt.render}: that helper only
   * transforms the match, and here the surrounding text must be escaped too.
   */
  private static String highlighted(Excerpt excerpt) {
    return escape(excerpt.before())
        + "<mark>"
        + escape(excerpt.match().value())
        + "</mark>"
        + escape(excerpt.after());
  }

  private static String noResults(SearchTerm term) {
    return """
        <p>No results for "%s".</p>
        """
        .formatted(escape(term.value()));
  }

  /** The ampersand goes first, or it would escape the escapes that follow. */
  static String escape(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}
