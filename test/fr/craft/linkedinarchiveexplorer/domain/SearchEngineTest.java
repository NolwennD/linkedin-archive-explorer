package fr.craft.linkedinarchiveexplorer.domain;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SearchEngineTest {

  private static final SearchEngine ENGINE = new SearchEngine();

  private static Content comment(String text) {
    return new Content(ContentType.COMMENT, Optional.empty(), "https://example/1", text);
  }

  private static List<SearchHit> search(String term, Content... contents) {
    return ENGINE.search(new SearchTerm(term), List.of(contents));
  }

  @Nested
  class Matching {

    @ParameterizedTest(name = "no match: {2}")
    @CsvSource({
      "absent,      hello world,          term simply absent",
      "date,        Date is capitalized,  wrong case (case sensitive)",
      "developpeur, je suis développeur,  missing accent (accent sensitive)"
    })
    void doesNotMatchWhenTheLiteralIsAbsent(String term, String text, String reason) {
      assertTrue(search(term, comment(text)).isEmpty());
    }

    @Test
    void reportsOneHitWithOneExcerptForASingleOccurrence() {
      List<SearchHit> hits = search("world", comment("hello world"));

      assertEquals(1, hits.size());
      assertEquals(1, hits.get(0).excerpts().size());
    }

    @Test
    void isSensitiveToUnicodeNormalizationForm() {
      // NFC "café" (U+00E9) and NFD "café" (e + combining U+0301) are distinct code
      // points; a literal search deliberately does not normalize, so neither form
      // matches the other.
      assertTrue(search("café", comment("café")).isEmpty(), "NFC term must not match NFD text");
      assertTrue(search("café", comment("café")).isEmpty(), "NFD term must not match NFC text");
    }
  }

  @Nested
  class Deduplication {

    @Test
    void reportsAContentOnceButKeepsEveryExcerpt() {
      List<SearchHit> hits = search("foo", comment("foo then foo again and foo"));

      assertEquals(1, hits.size());
      assertEquals(3, hits.get(0).excerpts().size());
    }

    @Test
    void countsOccurrencesWithoutOverlapping() {
      // "aa" occurs twice in "aaaa" when matches do not overlap (positions 0 and 2).
      List<SearchHit> hits = search("aa", comment("aaaa"));

      assertEquals(1, hits.size());
      assertEquals(2, hits.get(0).excerpts().size());
    }
  }

  @Nested
  class ContextExcerpts {

    private Excerpt firstExcerptOf(String term, String text) {
      return search(term, comment(text)).get(0).excerpts().get(0);
    }

    @Test
    void locatesTheTermInsideTheSnippet() {
      Excerpt excerpt = firstExcerptOf("world", "hello world");

      assertEquals("hello world", excerpt.render(Function.identity()));
      assertEquals(new SearchTerm("world"), excerpt.match());
    }

    @Test
    void keepsAboutFortyCharactersOfContextAndMarksTruncation() {
      String before = "0123456789012345678901234567890123456789EXTRA"; // 45 chars before
      String after = "EXTRA0123456789012345678901234567890123456789"; // 45 chars after
      Excerpt excerpt = firstExcerptOf("TERM", before + "TERM" + after);

      // 40 chars kept on each side, the extra 5 dropped, ellipsis on both ends.
      assertTrue(excerpt.render(Function.identity()).startsWith("…"), excerpt.render(Function.identity()));
      assertTrue(excerpt.render(Function.identity()).endsWith("…"), excerpt.render(Function.identity()));
      assertEquals(new SearchTerm("TERM"), excerpt.match());
    }

    @Test
    void doesNotAddEllipsisWhenContextFitsWithinBounds() {
      Excerpt excerpt = firstExcerptOf("world", "hello world");

      assertTrue(!excerpt.render(Function.identity()).startsWith("…") && !excerpt.render(Function.identity()).endsWith("…"));
    }

    @ParameterizedTest(name = "flattens {0}")
    @ValueSource(strings = {"\n", "\r", "\t"})
    void flattensWhitespaceToStayOnOneLine(String whitespace) {
      Excerpt excerpt = firstExcerptOf("term", "before" + whitespace + "term" + whitespace + "after");

      assertFalse(excerpt.render(Function.identity()).contains(whitespace), excerpt.render(Function.identity()));
      assertEquals(new SearchTerm("term"), excerpt.match());
    }
  }
}
