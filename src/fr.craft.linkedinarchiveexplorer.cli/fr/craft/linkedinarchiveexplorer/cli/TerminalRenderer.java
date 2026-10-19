package fr.craft.linkedinarchiveexplorer.cli;

import fr.craft.linkedinarchiveexplorer.application.ContentGroup;
import fr.craft.linkedinarchiveexplorer.application.SearchResults;
import fr.craft.linkedinarchiveexplorer.domain.ContentType;
import fr.craft.linkedinarchiveexplorer.domain.Correspondent;
import fr.craft.linkedinarchiveexplorer.domain.Excerpt;
import fr.craft.linkedinarchiveexplorer.domain.MessageHit;
import fr.craft.linkedinarchiveexplorer.domain.SearchHit;
import fr.craft.linkedinarchiveexplorer.domain.SearchTerm;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.teeing;
import static java.util.stream.Collectors.toCollection;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Renders {@link SearchResults} for a terminal: groups by type, one clickable link per
 * content, its excerpts with the term highlighted. In {@code styled} mode it uses ANSI
 * colours and OSC 8 hyperlinks; otherwise it emits plain, pipe-friendly text (raw URLs,
 * no escapes).
 */
public final class TerminalRenderer {

  private static final String NEWLINE = "\n";
  private static final String ESC = "\u001B";
  private static final String RESET = ESC + "[0m";
  private static final String BOLD = ESC + "[1m";
  private static final String HIGHLIGHT = ESC + "[1;33m"; // bold yellow
  private static final String UNDERLINE = ESC + "[4m";

  private final boolean styled;

  public TerminalRenderer(boolean styled) {
    this.styled = styled;
  }

  public String render(SearchTerm term, SearchResults results) {
    if (results.groups().isEmpty()) {
      return "No results for \"" + term.value() + "\"." + NEWLINE;
    }
    return results.groups().stream().map(this::group).collect(joining(NEWLINE)) + NEWLINE;
  }

  private String group(ContentGroup group) {
    String heading = heading(group.type());
    return bold(heading) + NEWLINE + "─".repeat(heading.length()) + NEWLINE + body(group);
  }

  private String body(ContentGroup group) {
    return switch (group.type()) {
      case MESSAGE ->
          group.byCorrespondent().entrySet().stream()
              .map(entry -> correspondent(entry.getKey(), entry.getValue()))
              .collect(joining());
      case ARTICLE, POST, COMMENT ->
          group.hits().stream().map(hit -> hit(hitHeader(hit), hit)).collect(joining());
    };
  }

  /** The conversation links, each once, beside the name: a person may have several threads. */
  private String correspondent(Correspondent who, List<MessageHit> hits) {
    record Rendered(String threads, String messages) {}
    Rendered rendered =
        hits.stream()
            .collect(
                teeing(
                    mapping(
                        MessageHit::url,
                        collectingAndThen(toCollection(LinkedHashSet::new), this::links)),
                    mapping(hit -> hit(formatDate(hit).stripTrailing(), hit), joining()),
                    Rendered::new));
    return bold(who.name()) + rendered.threads() + NEWLINE + rendered.messages();
  }

  private String links(Collection<String> urls) {
    return urls.stream().map(url -> "  " + link(url)).collect(joining());
  }

  private String hit(String header, SearchHit hit) {
    String excerpts =
        hit.excerpts().stream().map(excerpt -> "   • " + highlighted(excerpt) + NEWLINE).collect(joining());
    return (header.isEmpty() ? "" : header + NEWLINE) + excerpts;
  }

  private static String heading(ContentType type) {
    return switch (type) {
      case ARTICLE -> "ARTICLES";
      case POST -> "POSTS";
      case COMMENT -> "COMMENTS";
      case MESSAGE -> "MESSAGES";
    };
  }

  private String hitHeader(SearchHit hit) {
    return formatDate(hit) + link(hit.url());
  }

  private static String formatDate(SearchHit hit) {
    return hit.date().map(day -> "[" + day + "] ").orElse("");
  }

  /**
   * The URL is always shown as visible text so terminals that auto-linkify plain URLs
   * make it clickable. When styled, it is additionally wrapped in an OSC 8 hyperlink
   * (and underlined) for terminals that support real hyperlinks.
   */
  private String link(String url) {
    if (!styled) {
      return url;
    }
    return ESC + "]8;;" + url + ESC + "\\" + UNDERLINE + url + RESET + ESC + "]8;;" + ESC + "\\";
  }

  private String highlighted(Excerpt excerpt) {
    return excerpt.render(this::emphasise);
  }

  private String emphasise(String match) {
    return styled ? HIGHLIGHT + match + RESET : match;
  }

  private String bold(String text) {
    return styled ? BOLD + text + RESET : text;
  }
}
