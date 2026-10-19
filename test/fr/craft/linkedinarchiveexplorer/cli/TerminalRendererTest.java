package fr.craft.linkedinarchiveexplorer.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.craft.linkedinarchiveexplorer.application.ContentGroup;
import fr.craft.linkedinarchiveexplorer.application.SearchResults;
import fr.craft.linkedinarchiveexplorer.domain.Body;
import fr.craft.linkedinarchiveexplorer.domain.Content;
import fr.craft.linkedinarchiveexplorer.domain.ContentHit;
import fr.craft.linkedinarchiveexplorer.domain.ContentType;
import fr.craft.linkedinarchiveexplorer.domain.Correspondent;
import fr.craft.linkedinarchiveexplorer.domain.Excerpt;
import fr.craft.linkedinarchiveexplorer.domain.Match;
import fr.craft.linkedinarchiveexplorer.domain.Message;
import fr.craft.linkedinarchiveexplorer.domain.MessageHit;
import fr.craft.linkedinarchiveexplorer.domain.SearchHit;
import fr.craft.linkedinarchiveexplorer.domain.SearchTerm;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TerminalRendererTest {

  private static final String ESC = "\u001B";

  private static SearchResults oneComment(String url, LocalDate date, Excerpt... excerpts) {
    Content content = new Content(ContentType.COMMENT, Optional.ofNullable(date), url, "text");
    ContentGroup group = new ContentGroup(ContentType.COMMENT, List.of(new ContentHit(content, List.of(excerpts))));
    return new SearchResults(List.of(group));
  }

  private static Excerpt excerpt(String before, String match, String after) {
    return new Excerpt(before, new Match(0, match.length(), match), after);
  }

  @Test
  void reportsNoResultWhenEmpty() {
    String output = new TerminalRenderer(false).render(SearchTerm.literal("x"), new SearchResults(List.of()));

    assertTrue(output.contains("No results for \"x\""), output);
  }

  @Nested
  class Plain {

    private final TerminalRenderer renderer = new TerminalRenderer(false);

    @Test
    void showsHeadingDateExcerptAndRawUrl() {
      String output =
          renderer.render(
              SearchTerm.literal("foo"),
              oneComment("https://li/1", LocalDate.of(2024, 11, 6), excerpt("a ", "foo", " b")));

      assertTrue(output.contains("COMMENTS"), output);
      assertTrue(output.contains("[2024-11-06]"), output);
      assertTrue(output.contains("https://li/1"), output);
      assertTrue(output.contains("a foo b"), output);
    }

    @Test
    void containsNoEscapeSequences() {
      String output =
          renderer.render(
              SearchTerm.literal("foo"),
              oneComment("https://li/1", LocalDate.of(2024, 11, 6), excerpt("a ", "foo", " b")));

      assertFalse(output.contains(ESC), "plain output must not contain ANSI/OSC escapes");
    }
  }

  @Nested
  class Styled {

    private final TerminalRenderer renderer = new TerminalRenderer(true);

    @Test
    void wrapsTheMatchWithAnsiHighlight() {
      String output =
          renderer.render(
              SearchTerm.literal("foo"), oneComment("https://li/1", LocalDate.of(2024, 11, 6), excerpt("a ", "foo", " b")));

      // The whole snippet must be rendered intact — only the match wrapped, nothing duplicated.
      String highlightedSnippet = "a " + ESC + "[1;33m" + "foo" + ESC + "[0m" + " b";
      assertTrue(output.contains(highlightedSnippet), output);
    }

    @Test
    void embedsAnOsc8HyperlinkToTheUrl() {
      String output =
          renderer.render(
              SearchTerm.literal("foo"), oneComment("https://li/1", LocalDate.of(2024, 11, 6), excerpt("a ", "foo", " b")));

      assertTrue(output.contains(ESC + "]8;;https://li/1" + ESC + "\\"), output);
    }
  }

  @Nested
  class Grouping {

    @Test
    void omitsTheDateForUndatedArticles() {
      Content article = new Content(ContentType.ARTICLE, Optional.empty(), "https://li/a", "text");
      SearchResults results =
          new SearchResults(
              List.of(new ContentGroup(ContentType.ARTICLE, List.of(new ContentHit(article, List.of(excerpt("x ", "foo", "")))))));

      String output = new TerminalRenderer(false).render(SearchTerm.literal("foo"), results);

      assertTrue(output.contains("ARTICLES"), output);
      assertFalse(output.contains("["), "articles have no date bracket: " + output);
    }
  }

  @Nested
  class Messages {

    private SearchHit message(String who, String url, String thread) {
      Message message =
          new Message(
              Optional.of(LocalDate.of(2026, 7, 20)),
              thread,
              new Body("text"),
              new Correspondent(who, Optional.of(url)));
      return new MessageHit(message, List.of(excerpt("a ", "foo", " b")));
    }

    @Test
    void listsEachCorrespondentAlphabeticallyWithTheThreadLinkOnceBesideTheName() {
      SearchResults results =
          SearchResults.from(
              List.of(message("Rose", "https://li/in/rose", "https://li/t/1/"), message("Alice", "https://li/in/alice", "https://li/t/2/"),
                  message("Rose", "https://li/in/rose", "https://li/t/1/")));

      String output = new TerminalRenderer(false).render(SearchTerm.literal("foo"), results);

      assertTrue(output.contains("MESSAGES\n────────\n"), output);
      assertTrue(
          output.indexOf("Alice  https://li/t/2/\n[2026-07-20]\n   • a foo b\n")
              < output.indexOf("Rose  https://li/t/1/\n[2026-07-20]\n   • a foo b\n[2026-07-20]\n"),
          output);
    }
  }
}
