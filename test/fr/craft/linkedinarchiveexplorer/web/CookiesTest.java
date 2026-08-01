package fr.craft.linkedinarchiveexplorer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CookiesTest {

  @Nested
  class Reading {

    @ParameterizedTest(name = "[{0}] → archive = [{1}]")
    @CsvSource({
      "'archive=export.zip',                  'export.zip'",
      "'other=x; archive=export.zip',         'export.zip'",
      "'archive=export.zip; other=x',         'export.zip'",
      "'other=x;archive=export.zip',          'export.zip'",
      "'archive=one.zip; archive=two.zip',    'one.zip'",
      "'archive=',                            ''",
      "'archive',                             ''",
      "'other=x',                             ''",
      "'',                                    ''",
    })
    void readsTheArchiveFromTheCookieHeader(String header, String expected) {
      assertEquals(expected, Cookies.parse(header).value("archive"));
    }

    @Test
    void hasNoCookieWhenTheRequestCarriesNoHeader() {
      assertEquals("", Cookies.parse(null).value("archive"));
    }
  }

  @Nested
  class Decoding {

    @ParameterizedTest(name = "[{0}] → archive = [{1}]")
    @CsvSource({
      "'archive=my%20export.zip',      'my export.zip'",
      "'archive=caf%C3%A9.zip',        'café.zip'",
      "'archive=a%3Bb.zip',            'a;b.zip'",
    })
    void decodesThePercentEncodedValue(String header, String expected) {
      assertEquals(expected, Cookies.parse(header).value("archive"));
    }

    @Test
    void keepsAMalformedEscapeVerbatimRatherThanFailing() {
      assertEquals("%zz.zip", Cookies.parse("archive=%zz.zip").value("archive"));
    }

    @Test
    void keepsAPlusSignAsItselfRatherThanAsASpace() {
      // A cookie is not a query string: "+" has no special meaning there, and a file
      // named "a+b.zip" must survive the round trip.
      assertEquals("a+b.zip", Cookies.parse("archive=a+b.zip").value("archive"));
    }
  }
}
