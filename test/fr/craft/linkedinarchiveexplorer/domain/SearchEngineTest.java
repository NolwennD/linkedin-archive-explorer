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
    return ENGINE.search(SearchTerm.literal(term), List.of(contents));
  }

  private static List<SearchHit> searchIgnoringCase(String term, Content... contents) {
    SearchTerm searchTerm = new SearchTerm(term, CaseSensitivity.INSENSITIVE, WordScope.ANYWHERE);
    return ENGINE.search(searchTerm, List.of(contents));
  }

  private static List<SearchHit> searchWholeWord(String term, Content... contents) {
    SearchTerm searchTerm = new SearchTerm(term, CaseSensitivity.SENSITIVE, WordScope.WHOLE_WORD);
    return ENGINE.search(searchTerm, List.of(contents));
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
  class CaseInsensitive {

    @Test
    void findsAnOccurrenceRegardlessOfCase() {
      assertEquals(1, searchIgnoringCase("date", comment("the Date here")).size());
      // the literal (case-sensitive) search must NOT find it — the option makes the difference
      assertTrue(search("date", comment("the Date here")).isEmpty());
    }

    @Test
    void highlightsTheRealCaseOfTheText() {
      List<SearchHit> hits = searchIgnoringCase("date", comment("the DATE format"));

      assertEquals(new Match(4, 8, "DATE"), hits.get(0).excerpts().get(0).match());
    }

    @Test
    void staysSensitiveToAccents() {
      assertTrue(searchIgnoringCase("developpe", comment("je développe")).isEmpty());
    }

    @ParameterizedTest(name = "no match: {2}")
    @CsvSource({
      "isi, ısı, the dotless ı is a letter of its own and not an i",
      "isi, İSİ, the dotted İ carries its dot the way an accent carries its own",
      "ISI, ısı, nor does the capital I reach the dotless ı"
    })
    void doesNotConflateTheTurkishIWithTheLatinOne(String term, String text, String reason) {
      assertTrue(searchIgnoringCase(term, comment(text)).isEmpty());
    }

    @Test
    void stillIgnoresCaseBetweenTheTurkishLettersThemselves() {
      assertEquals(1, searchIgnoringCase("ısı", comment("une ısı ici")).size());
      assertEquals(1, searchIgnoringCase("İSTANBUL", comment("İstanbul")).size());
    }
  }

  @Nested
  class WholeWord {

    @ParameterizedTest(name = "matches whole word: {1}")
    @CsvSource({
      "dev, un dev senior,        surrounded by spaces",
      "dev, 'le dev, ici',        followed by punctuation",
      "dev, dev,                  the whole text is the word",
      "Date, bug Date(0000) here, followed by a parenthesis"
    })
    void acceptsAnOccurrenceThatFormsAWholeWord(String term, String text, String reason) {
      assertEquals(1, searchWholeWord(term, comment(text)).size(), reason);
    }

    @ParameterizedTest(name = "rejects partial word: {1}")
    @CsvSource({
      "dev, un développeur,  followed by a letter",
      "dev, mode_dev actif,  preceded by an underscore",
      "Date, un DateFormat,  followed by a letter"
    })
    void rejectsAnOccurrenceInsideALargerWord(String term, String text, String reason) {
      assertTrue(searchWholeWord(term, comment(text)).isEmpty(), reason);
    }

    @Test
    void treatsAccentedLettersAsWordCharacters() {
      assertEquals(1, searchWholeWord("café", comment("un café !")).size());
      assertTrue(searchWholeWord("café", comment("de la caféine")).isEmpty());
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
      assertEquals(new Match(6, 11, "world"), excerpt.match());
    }

    @Test
    void keepsAboutFortyCharactersOfContextAndMarksTruncation() {
      String before = "0123456789012345678901234567890123456789EXTRA"; // 45 chars before
      String after = "EXTRA0123456789012345678901234567890123456789"; // 45 chars after
      Excerpt excerpt = firstExcerptOf("TERM", before + "TERM" + after);

      // 40 chars kept on each side, the extra 5 dropped, ellipsis on both ends.
      assertTrue(excerpt.render(Function.identity()).startsWith("…"), excerpt.render(Function.identity()));
      assertTrue(excerpt.render(Function.identity()).endsWith("…"), excerpt.render(Function.identity()));
      assertEquals(new Match(45, 49, "TERM"), excerpt.match());
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
      assertEquals(new Match(7, 11, "term"), excerpt.match());
    }
  }
}
