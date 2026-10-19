package fr.craft.linkedinarchiveexplorer.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MessageTest {

  private static final Correspondent ROSE = new Correspondent("Rose", Optional.empty());

  private static Message message(String text) {
    return new Message(Optional.empty(), "https://li/t/1/", new Body(text), ROSE);
  }

  @Test
  void aMessageCannotExistWithoutItsCorrespondent() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Message(Optional.empty(), "https://li/t/1/", new Body("hello"), null));
  }

  @Test
  void theEngineTurnsAMatchedMessageIntoAMessageHitFiledUnderItsCorrespondent() {
    List<SearchHit> hits = new SearchEngine().search(SearchTerm.literal("ell"), List.of(message("hello")));

    MessageHit hit = assertInstanceOf(MessageHit.class, hits.getFirst());
    assertEquals(ROSE, hit.correspondent());
  }

  @Test
  void aHitCannotExistWithoutAnExcerpt() {
    assertThrows(IllegalArgumentException.class, () -> new MessageHit(message("hello"), List.of()));
  }
}
