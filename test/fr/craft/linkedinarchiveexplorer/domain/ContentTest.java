package fr.craft.linkedinarchiveexplorer.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContentTest {

  @Test
  void aMessageIsAMessageNotAContent() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Content(ContentType.MESSAGE, Optional.empty(), "https://li/t/1/", "hello"));
  }
}
