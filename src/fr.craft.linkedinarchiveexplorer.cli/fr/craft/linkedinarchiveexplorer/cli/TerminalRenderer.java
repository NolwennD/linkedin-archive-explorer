package fr.craft.linkedinarchiveexplorer.cli;

import fr.craft.linkedinarchiveexplorer.application.ContentGroup;
import fr.craft.linkedinarchiveexplorer.application.SearchResults;
import fr.craft.linkedinarchiveexplorer.application.Section;
import fr.craft.linkedinarchiveexplorer.domain.Connection;
import fr.craft.linkedinarchiveexplorer.domain.ConnectionHit;
import fr.craft.linkedinarchiveexplorer.domain.ConnectionMatch;
import fr.craft.linkedinarchiveexplorer.domain.Content;
import fr.craft.linkedinarchiveexplorer.domain.ContentHit;
import fr.craft.linkedinarchiveexplorer.domain.Correspondent;
import fr.craft.linkedinarchiveexplorer.domain.Excerpt;
import fr.craft.linkedinarchiveexplorer.domain.Message;
import fr.craft.linkedinarchiveexplorer.domain.MessageHit;
import fr.craft.linkedinarchiveexplorer.domain.SearchHit;
import fr.craft.linkedinarchiveexplorer.domain.SearchTerm;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.teeing;
import static java.util.stream.Collectors.toCollection;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

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
    String heading = heading(group.section());
    return bold(heading) + NEWLINE + "─".repeat(heading.length()) + NEWLINE + body(group);
  }

  private String body(ContentGroup group) {
    return switch (group.section()) {
      case MESSAGE ->
          group.byCorrespondent().entrySet().stream()
              .map(entry -> correspondent(entry.getKey(), entry.getValue()))
              .collect(joining());
      case ARTICLE, POST, COMMENT, CONNECTION -> group.hits().stream().map(this::hit).collect(joining());
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
                        hit -> hit.message().url(),
                        collectingAndThen(toCollection(LinkedHashSet::new), this::links)),
                    mapping(this::hit, joining()),
                    Rendered::new));
    return bold(who.name()) + rendered.threads() + NEWLINE + rendered.messages();
  }

  private String links(Collection<String> urls) {
    return urls.stream().map(url -> "  " + link(url)).collect(joining());
  }

  private String hit(SearchHit hit) {
    return switch (hit) {
      case ConnectionHit(Connection connection, List<ConnectionMatch> matches) ->
          formatDate(connection.connectedOn())
              + bold(connection.fullName())
              + "  "
              + link(connection.profileUrl())
              + NEWLINE
              + labelled(matches);
      case ContentHit(Content content, List<Excerpt> excerpts) ->
          formatDate(content.date()) + link(content.url()) + NEWLINE + bullets(excerpts);
      // The conversation link is on the correspondent, not repeated on each message.
      case MessageHit(Message message, List<Excerpt> excerpts) ->
          message.date().map(day -> "[" + day + "]" + NEWLINE).orElse("") + bullets(excerpts);
    };
  }

  private String labelled(List<ConnectionMatch> matches) {
    return matches.stream()
        .map(match -> bullet(match.field().label() + ": ", match.excerpt()))
        .collect(joining());
  }

  private String bullets(List<Excerpt> excerpts) {
    return excerpts.stream().map(excerpt -> bullet("", excerpt)).collect(joining());
  }

  private String bullet(String label, Excerpt excerpt) {
    return "   • " + label + highlighted(excerpt) + NEWLINE;
  }

  private static String heading(Section section) {
    return switch (section) {
      case ARTICLE -> "ARTICLES";
      case POST -> "POSTS";
      case COMMENT -> "COMMENTS";
      case MESSAGE -> "MESSAGES";
      case CONNECTION -> "CONNECTIONS";
    };
  }

  private static String formatDate(Optional<LocalDate> date) {
    return date.map(day -> "[" + day + "] ").orElse("");
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
