package fr.craft.linkedinarchiveexplorer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QueryParametersTest {

  @Nested
  class Decoding {

    @ParameterizedTest(name = "[{0}] → q = [{1}]")
    @CsvSource({
      "'q=hello',      'hello'",
      "'q=caf%C3%A9',  'café'",
      "'q=a+b',        'a b'",
      "'q=a%20b',      'a b'",
      "'q=',           ''",
      "'q',            ''",
      "'other=x',      ''",
      "'q=one&q=two',  'one'",
      "'q=a&&w=on',    'a'",
    })
    void decodesTheTermFromTheQueryString(String rawQuery, String expected) {
      assertEquals(expected, QueryParameters.parse(rawQuery).value("q"));
    }

    @Test
    void hasNoParametersWhenTheUriCarriesNoQueryString() {
      assertEquals("", QueryParameters.parse(null).value("q"));
    }

    @Test
    void keepsAMalformedEscapeVerbatimRatherThanFailing() {
      assertEquals("%zz", QueryParameters.parse("q=%zz").value("q"));
    }
  }

  @Nested
  class Checkboxes {

    @Test
    void readsATickedCheckbox() {
      assertTrue(QueryParameters.parse("q=x&i=on").isChecked("i"));
    }

    @Test
    void anAbsentCheckboxIsNotTicked() {
      assertFalse(QueryParameters.parse("q=x").isChecked("i"));
    }

    @Test
    void aCheckboxCarryingAnotherValueIsNotTicked() {
      assertFalse(QueryParameters.parse("q=x&i=off").isChecked("i"));
    }
  }
}
