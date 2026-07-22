package fr.craft.linkedinarchiveexplorer.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SearchTermTest {

  @Test
  void keepsTheSearchedValue() {
    assertEquals("Date(0,0,0)", new SearchTerm("Date(0,0,0)").value());
  }

  @Test
  void rejectsABlankValue() {
    assertThrows(IllegalArgumentException.class, () -> new SearchTerm("   "));
  }

  @Test
  void rejectsANullValue() {
    assertThrows(IllegalArgumentException.class, () -> new SearchTerm(null));
  }
}
