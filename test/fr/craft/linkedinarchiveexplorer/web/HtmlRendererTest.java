package fr.craft.linkedinarchiveexplorer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.craft.linkedinarchiveexplorer.application.ContentGroup;
import fr.craft.linkedinarchiveexplorer.application.SearchResults;
import fr.craft.linkedinarchiveexplorer.domain.CaseSensitivity;
import fr.craft.linkedinarchiveexplorer.domain.Content;
import fr.craft.linkedinarchiveexplorer.domain.ContentType;
import fr.craft.linkedinarchiveexplorer.domain.Excerpt;
import fr.craft.linkedinarchiveexplorer.domain.Match;
import fr.craft.linkedinarchiveexplorer.domain.SearchHit;
import fr.craft.linkedinarchiveexplorer.domain.SearchTerm;
import fr.craft.linkedinarchiveexplorer.domain.WordScope;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HtmlRendererTest {

  private static final LocalDate A_DAY = LocalDate.of(2024, 11, 6);

  private final HtmlRenderer renderer = new HtmlRenderer("data/export.zip");

  private static Excerpt excerpt(String before, String match, String after) {
    return new Excerpt(before, new Match(0, match.length(), match), after);
  }

  private static SearchHit hit(ContentType type, String url, LocalDate date, Excerpt... excerpts) {
    Content content = new Content(type, Optional.ofNullable(date), url, "text");
    return new SearchHit(content, List.of(excerpts));
  }

  private static SearchResults results(SearchHit... hits) {
    return SearchResults.from(List.of(hits));
  }

  private static SearchResults oneComment(Excerpt... excerpts) {
    return results(hit(ContentType.COMMENT, "https://li/1", A_DAY, excerpts));
  }

  private String render(SearchTerm term, SearchResults results) {
    return renderer.render(term, results);
  }

  private String renderFound(String term) {
    return render(SearchTerm.literal(term), oneComment(excerpt("a ", term, " b")));
  }

  @Nested
  class PageSkeleton {

    @Test
    void declaresTheDoctypeLanguageAndCharset() {
      String page = renderFound("foo");

      assertTrue(page.startsWith("<!DOCTYPE html>"), page);
      assertTrue(page.contains("<html lang=\"en\">"), page);
      assertTrue(page.contains("<meta charset=\"utf-8\">"), page);
    }

    @Test
    void putsTheSearchedTermFirstInTheTitle() {
      assertTrue(renderFound("café").contains("<title>café — LinkedIn archive explorer</title>"));
    }

    @Test
    void namesTheArchiveInTheFooter() {
      assertTrue(renderFound("foo").contains("<footer>Archive: data/export.zip</footer>"));
    }

    @Test
    void carriesNoScriptAtAll() {
      assertFalse(renderFound("foo").contains("<script"), "the page must stay JavaScript-free");
    }
  }

  @Nested
  class Grouping {

    @Test
    void ordersGroupsByCanonicalContentType() {
      String page =
          render(
              SearchTerm.literal("foo"),
              results(
                  hit(ContentType.COMMENT, "https://li/c", A_DAY, excerpt("", "foo", "")),
                  hit(ContentType.ARTICLE, "https://li/a", null, excerpt("", "foo", ""))));

      assertTrue(page.indexOf("Articles") < page.indexOf("Comments"), page);
    }

    @Test
    void countsTheHitsOfEachGroup() {
      String page =
          render(
              SearchTerm.literal("foo"),
              results(
                  hit(ContentType.COMMENT, "https://li/1", A_DAY, excerpt("", "foo", "")),
                  hit(ContentType.COMMENT, "https://li/2", A_DAY, excerpt("", "foo", ""))));

      assertTrue(page.contains("<span class=\"count\">(2)</span>"), page);
    }

    @Test
    void omitsAGroupWithNoHit() {
      assertFalse(renderFound("foo").contains("Articles"), "no article matched");
    }
  }

  @Nested
  class Collapsing {

    @Test
    void makesEachGroupCollapsibleAndOpenByDefault() {
      String page = renderFound("foo");

      assertTrue(page.contains("<details open>"), page);
      assertTrue(page.contains("<summary>"), page);
    }
  }

  @Nested
  class HitRendering {

    @Test
    void linksToTheContentUrl() {
      assertTrue(renderFound("foo").contains("<a href=\"https://li/1\">https://li/1</a>"));
    }

    @Test
    void rendersTheDateAsAMachineReadableTime() {
      assertTrue(renderFound("foo").contains("<time datetime=\"2024-11-06\">2024-11-06</time>"));
    }

    @Test
    void omitsTheTimeElementForUndatedArticles() {
      String page =
          render(
              SearchTerm.literal("foo"),
              results(hit(ContentType.ARTICLE, "https://li/a", null, excerpt("", "foo", ""))));

      assertFalse(page.contains("<time"), "articles carry no date: " + page);
    }

    @Test
    void listsEveryExcerptOfAHit() {
      String page =
          render(
              SearchTerm.literal("foo"),
              oneComment(excerpt("first ", "foo", " one"), excerpt("second ", "foo", " one")));

      assertTrue(page.contains("first <mark>foo</mark> one"), page);
      assertTrue(page.contains("second <mark>foo</mark> one"), page);
    }
  }

  @Nested
  class Highlighting {

    @Test
    void wrapsOnlyTheMatchInAMarkElement() {
      assertTrue(renderFound("foo").contains("a <mark>foo</mark> b"));
    }
  }

  @Nested
  class Escaping {

    @Test
    void escapesMarkupComingFromTheArchive() {
      String page =
          render(
              SearchTerm.literal("alert"),
              oneComment(excerpt("<script>", "alert", "(1)</script>")));

      assertTrue(page.contains("&lt;script&gt;"), page);
      assertFalse(page.contains("<script>"), "archive markup must never become live markup");
    }

    @Test
    void escapesAnAmpersandBeforeAnythingElse() {
      String page = render(SearchTerm.literal("x"), oneComment(excerpt("a &lt; b ", "x", "")));

      assertTrue(page.contains("a &amp;lt; b"), page);
    }

    @Test
    void escapesTheSearchTermInTheFormValue() {
      String page = render(SearchTerm.literal("<b>"), oneComment(excerpt("a ", "<b>", " b")));

      assertTrue(page.contains("value=\"&lt;b&gt;\""), page);
    }

    @Test
    void escapesTheUrlUsedAsAnAttributeValue() {
      String page =
          render(
              SearchTerm.literal("foo"),
              results(hit(ContentType.COMMENT, "https://li/1?a=1&b=2", A_DAY, excerpt("", "foo", ""))));

      assertTrue(page.contains("href=\"https://li/1?a=1&amp;b=2\""), page);
    }
  }

  @Nested
  class Form {

    @Test
    void prefillsTheSearchInputWithTheTerm() {
      assertTrue(renderFound("café").contains("value=\"café\""));
    }

    @Test
    void leavesBothOptionsUntickedForALiteralTerm() {
      String page = renderFound("foo");

      assertFalse(page.contains("name=\"i\" checked"), page);
      assertFalse(page.contains("name=\"w\" checked"), page);
    }

    @Test
    void ticksIgnoreCaseWhenTheTermIgnoresCase() {
      SearchTerm term = new SearchTerm("foo", CaseSensitivity.INSENSITIVE, WordScope.ANYWHERE);

      String page = render(term, oneComment(excerpt("a ", "foo", " b")));

      assertTrue(page.contains("name=\"i\" checked"), page);
      assertFalse(page.contains("name=\"w\" checked"), page);
    }

    @Test
    void ticksWholeWordWhenTheTermMatchesWholeWordsOnly() {
      SearchTerm term = new SearchTerm("foo", CaseSensitivity.SENSITIVE, WordScope.WHOLE_WORD);

      String page = render(term, oneComment(excerpt("a ", "foo", " b")));

      assertTrue(page.contains("name=\"w\" checked"), page);
      assertFalse(page.contains("name=\"i\" checked"), page);
    }
  }

  @Nested
  class EmptyResults {

    @Test
    void reportsNoResultForTheTerm() {
      String page = render(SearchTerm.literal("absent"), new SearchResults(List.of()));

      assertTrue(page.contains("No results for \"absent\"."), page);
    }

    @Test
    void stillRendersTheFormSoAnotherSearchIsPossible() {
      String page = render(SearchTerm.literal("absent"), new SearchResults(List.of()));

      assertTrue(page.contains("<input type=\"search\""), page);
    }
  }

  @Nested
  class LandingPage {

    @Test
    void hasAnEmptyInputAndNoResultMessage() {
      String page = renderer.renderEmptyForm();

      assertTrue(page.contains("value=\"\""), page);
      assertFalse(page.contains("No results"), page);
    }

    @Test
    void titlesThePageWithTheApplicationNameAlone() {
      assertTrue(renderer.renderEmptyForm().contains("<title>LinkedIn archive explorer</title>"));
    }
  }

  @Nested
  class Escape {

    @Test
    void replacesEveryCharacterThatCouldBreakOutOfMarkup() {
      assertEquals("&amp;&lt;&gt;&quot;&#39;", HtmlRenderer.escape("&<>\"'"));
    }
  }
}
