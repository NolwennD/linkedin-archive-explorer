package fr.craft.linkedinarchiveexplorer.web;

import fr.craft.linkedinarchiveexplorer.application.ContentGroup;
import fr.craft.linkedinarchiveexplorer.application.SearchResults;
import fr.craft.linkedinarchiveexplorer.domain.CaseSensitivity;
import fr.craft.linkedinarchiveexplorer.domain.ContentType;
import fr.craft.linkedinarchiveexplorer.domain.Correspondent;
import fr.craft.linkedinarchiveexplorer.domain.Excerpt;
import fr.craft.linkedinarchiveexplorer.domain.SearchHit;
import fr.craft.linkedinarchiveexplorer.domain.SearchTerm;
import fr.craft.linkedinarchiveexplorer.domain.WordScope;
import static fr.craft.linkedinarchiveexplorer.web.HtmlEscaper.escape;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.teeing;
import static java.util.stream.Collectors.toCollection;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Renders {@link SearchResults} as one self-contained HTML page: a search form, then a
 * collapsible section per content type. The web twin of {@code TerminalRenderer}.
 *
 * <p>Every value taken from the archive — or typed by the user — goes through
 * {@link HtmlEscaper#escape}, because an authored post may legitimately contain markup.
 */
public final class HtmlRenderer {

  private static final String APPLICATION_NAME = "LinkedIn archive explorer";

  private static final String STYLE =
      """
      :root { color-scheme: light dark; }
      body { font-family: system-ui, sans-serif; line-height: 1.5; margin: 0 auto; max-width: 46rem; padding: 1rem 1.5rem; }
      h1 { font-size: 1.1rem; }
      h2 { display: inline; font-size: .95rem; letter-spacing: .08em; text-transform: uppercase; }
      h3 { font-size: 1rem; font-weight: normal; margin: 1.1rem 0 .1rem; overflow-wrap: anywhere; }
      form { align-items: center; display: grid; gap: .45rem .75rem; grid-template-columns: max-content minmax(0, 1fr) max-content; }
      input[type=search], select { padding: .3rem; width: 100%; }
      .options { display: flex; flex-wrap: wrap; gap: .3rem 1rem; grid-column: 2 / -1; }
      /* An overflowing path is shown by its end: the file name identifies it, /home/… does not.
         text-align keeps a short path against the left edge, and :not(:focus) hands the field
         back in plain left-to-right as soon as it is being edited. */
      #archive:not(:focus) { direction: rtl; text-align: left; }
      .error { color: #b3261e; grid-column: 2 / -1; margin: 0; }
      .hint { font-size: .78rem; grid-column: 2 / -1; margin: 0; opacity: .55; }
      @media (prefers-color-scheme: dark) { .error { color: #f2b8b5; } }
      summary { cursor: pointer; margin: 1.75rem 0 .25rem; }
      .count { opacity: .55; }
      time { font-size: .78rem; opacity: .55; }
      ol { list-style: none; margin: 0; padding: 0; }
      ul { margin: .2rem 0; padding-left: 1.2rem; }
      mark { font-weight: bold; }
      footer { border-top: 1px solid; font-size: .78rem; margin-top: 2.5rem; opacity: .55; padding-top: .5rem; }
      """;

  /**
   * The form alone, no search performed: the landing page, and the page that comes back
   * when the archive did not open — hence the search options, which must survive that.
   */
  public String renderForm(String query, boolean ignoreCase, boolean wholeWord, ArchiveField archives) {
    return page(query, ignoreCase, wholeWord, "", archives);
  }

  public String render(SearchTerm term, SearchResults results, ArchiveField archives) {
    return page(
        term.value(),
        term.caseSensitivity() == CaseSensitivity.INSENSITIVE,
        term.wordScope() == WordScope.WHOLE_WORD,
        results.groups().isEmpty() ? noResults(term) : groups(results),
        archives);
  }

  private String page(
      String query, boolean ignoreCase, boolean wholeWord, String main, ArchiveField archives) {
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
        <button type="submit">Search</button>
        <span id="options-label">Options</span>
        <div class="options" role="group" aria-labelledby="options-label">
        <label><input type="checkbox" name="i"%s> Ignore case</label>
        <label><input type="checkbox" name="w"%s> Whole word</label>
        </div>
        %s</form>
        </search>
        </header>
        <main>
        %s</main>
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
            archiveSelector(archives),
            main);
  }

  /**
   * The field rides inside the search form, so changing archive and searching are one GET
   * — no JavaScript, no server-side "current archive", and the URL says everything. Free
   * text, because an archive may live anywhere; the {@code <datalist>} merely suggests the
   * ones that were found, and {@code required} is what asks for a path when none was.
   */
  private static String archiveSelector(ArchiveField archives) {
    StringBuilder options = new StringBuilder();
    for (String suggestion : archives.suggestions()) {
      options.append("<option value=\"%s\">\n".formatted(escape(suggestion)));
    }
    return """
        %s<label for="archive">Archive</label>
        <input type="text" id="archive" name="archive" value="%s" list="archives" required\
         placeholder="/path/to/Complete_LinkedInDataExport.zip" aria-describedby="archive-hint">
        <datalist id="archives">
        %s</datalist>
        <p class="hint" id="archive-hint">Type or paste the <strong>absolute</strong> path\
         to a LinkedIn export. The archives found in data/ are suggested.</p>
        """
        .formatted(error(archives.error()), escape(archives.value()), options);
  }

  /** Above the field it is about, so the correction happens where the mistake shows. */
  private static String error(String message) {
    return message.isEmpty()
        ? ""
        : "<p class=\"error\" role=\"alert\">%s</p>\n".formatted(escape(message));
  }

  /** The term comes first, so a browser tab or a history entry identifies the search. */
  private static String title(String query) {
    return query.isEmpty() ? APPLICATION_NAME : escape(query) + " — " + APPLICATION_NAME;
  }

  private static String checked(boolean on) {
    return on ? " checked" : "";
  }

  private String groups(SearchResults results) {
    return results.groups().stream().map(this::group).collect(joining());
  }

  private String group(ContentGroup group) {
    String id = group.type().name().toLowerCase(Locale.ROOT) + "s";
    return """
        <section aria-labelledby="%s">
        <details open>
        <summary><h2 id="%s">%s <span class="count">(%d)</span></h2></summary>
        <ol>
        %s</ol>
        </details>
        </section>
        """
        .formatted(id, id, heading(group.type()), group.hits().size(), items(group));
  }

  private String items(ContentGroup group) {
    return switch (group.type()) {
      case MESSAGE ->
          group.byCorrespondent().entrySet().stream()
              .map(entry -> correspondent(entry.getKey(), entry.getValue()))
              .collect(joining());
      case ARTICLE, POST, COMMENT -> group.hits().stream().map(this::hit).collect(joining());
    };
  }

  /** Title case in the markup; the stylesheet is what shouts it in capitals. */
  private static String heading(ContentType type) {
    return switch (type) {
      case ARTICLE -> "Articles";
      case POST -> "Posts";
      case COMMENT -> "Comments";
      case MESSAGE -> "Messages";
    };
  }

  /** A person, their conversation link(s) once, and below them their matching messages. */
  private String correspondent(Correspondent who, List<SearchHit> hits) {
    record Rendered(String threads, String messages) {}
    Rendered rendered =
        hits.stream()
            .collect(
                teeing(
                    mapping(
                        SearchHit::url,
                        collectingAndThen(toCollection(LinkedHashSet::new), this::links)),
                    mapping(this::message, joining()),
                    Rendered::new));
    return """
        <li>
        <h3><strong>%s</strong>%s</h3>
        <ol>
        %s</ol>
        </li>
        """
        .formatted(escape(who.name()), rendered.threads(), rendered.messages());
  }

  private String links(Collection<String> urls) {
    return urls.stream()
        .map(url -> " <a href=\"%s\">%s</a>".formatted(escape(url), escape(url)))
        .collect(joining());
  }

  /** A message under its person: the conversation link is on the person, not repeated here. */
  private String message(SearchHit hit) {
    return """
        <li>
        <article>
        %s<ul>
        %s</ul>
        </article>
        </li>
        """
        .formatted(date(hit), excerptItems(hit));
  }

  private String hit(SearchHit hit) {
    return """
        <li>
        <article>
        <h3><a href="%s">%s</a></h3>
        %s<ul>
        %s</ul>
        </article>
        </li>
        """
            .formatted(escape(hit.url()), escape(hit.url()), date(hit), excerptItems(hit));
  }

  private static String excerptItems(SearchHit hit) {
    return hit.excerpts().stream()
        .map(excerpt -> "<li>" + highlighted(excerpt) + "</li>\n")
        .collect(joining());
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
}
