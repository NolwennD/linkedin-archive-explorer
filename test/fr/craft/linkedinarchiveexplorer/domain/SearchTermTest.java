package fr.craft.linkedinarchiveexplorer.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SearchTermTest {

  @Test
  void keepsTheSearchedValue() {
    assertEquals("Date(0,0,0)", SearchTerm.literal("Date(0,0,0)").value());
  }

  @Test
  void literalIsCaseAndAccentSensitiveMatchingAnywhere() {
    SearchTerm term = SearchTerm.literal("date");

    assertEquals(CaseSensitivity.SENSITIVE, term.caseSensitivity());
    assertEquals(WordScope.ANYWHERE, term.wordScope());
  }

  @Test
  void rejectsABlankValue() {
    assertThrows(IllegalArgumentException.class, () -> SearchTerm.literal("   "));
  }

  @Test
  void rejectsANullValue() {
    assertThrows(IllegalArgumentException.class, () -> SearchTerm.literal(null));
  }

  @Test
  void rejectsNullOptions() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SearchTerm("date", null, WordScope.ANYWHERE));
  }
}
