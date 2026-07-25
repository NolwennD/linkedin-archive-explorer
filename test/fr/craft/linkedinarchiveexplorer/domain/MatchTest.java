package fr.craft.linkedinarchiveexplorer.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MatchTest {

  @Test
  void keepsItsPositionAndFoundText() {
    Match match = new Match(6, 11, "world");

    assertEquals(6, match.start());
    assertEquals(11, match.end());
    assertEquals("world", match.value());
  }

  @Test
  void rejectsANullValue() {
    assertThrows(IllegalArgumentException.class, () -> new Match(0, 0, null));
  }

  @Test
  void rejectsANegativeStart() {
    assertThrows(IllegalArgumentException.class, () -> new Match(-1, 3, "abc"));
  }

  @Test
  void rejectsAnEndBeforeItsStart() {
    assertThrows(IllegalArgumentException.class, () -> new Match(5, 4, ""));
  }

  @Test
  void rejectsALengthThatDisagreesWithTheSpan() {
    // value length must equal end - start
    assertThrows(IllegalArgumentException.class, () -> new Match(0, 5, "abc"));
  }
}
