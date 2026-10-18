package fr.craft.linkedinarchiveexplorer.cli;

import fr.craft.linkedinarchiveexplorer.application.ContentGroup;
import fr.craft.linkedinarchiveexplorer.application.SearchResults;
import fr.craft.linkedinarchiveexplorer.domain.ContentType;
import fr.craft.linkedinarchiveexplorer.domain.Excerpt;
import fr.craft.linkedinarchiveexplorer.domain.SearchHit;
import fr.craft.linkedinarchiveexplorer.domain.SearchTerm;
import java.util.List;

/**
 * Renders {@link SearchResults} for a terminal: groups by type, one clickable link per
 * content, its excerpts with the term highlighted. In {@code styled} mode it uses ANSI
 * colours and OSC 8 hyperlinks; otherwise it emits plain, pipe-friendly text (raw URLs,
 * no escapes).
 */
public final class TerminalRenderer {

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
      return "No results for \"" + term.value() + "\".\n";
    }
    StringBuilder output = new StringBuilder();
    for (ContentGroup group : results.groups()) {
      String heading = heading(group.type());
      output.append(bold(heading)).append('\n');
      output.append("─".repeat(heading.length())).append('\n');
      if (group.type() == ContentType.MESSAGE) {
        group.byCorrespondent().forEach((who, hits) -> {
          output.append(bold(who.name()));
          threads(hits).forEach(url -> output.append("  ").append(link(url)));
          output.append('\n');
          hits.forEach(hit -> appendHit(output, formateDate(hit).stripTrailing(), hit));
        });
      } else {
        group.hits().forEach(hit -> appendHit(output, hitHeader(hit), hit));
      }
      output.append('\n');
    }
    return output.toString();
  }

  /** The conversation links, each once: a person may have several threads. */
  private static List<String> threads(List<SearchHit> hits) {
    return hits.stream().map(SearchHit::url).distinct().toList();
  }

  private void appendHit(StringBuilder output, String header, SearchHit hit) {
    if (!header.isEmpty()) {
      output.append(header).append('\n');
    }
    for (Excerpt excerpt : hit.excerpts()) {
      output.append("   • ").append(highlighted(excerpt)).append('\n');
    }
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
    return formateDate(hit) + link(hit.url());
  }

  private static String formateDate(SearchHit hit) {
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
