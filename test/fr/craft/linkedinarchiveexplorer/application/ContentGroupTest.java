package fr.craft.linkedinarchiveexplorer.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fr.craft.linkedinarchiveexplorer.domain.Body;
import fr.craft.linkedinarchiveexplorer.domain.Content;
import fr.craft.linkedinarchiveexplorer.domain.ContentHit;
import fr.craft.linkedinarchiveexplorer.domain.Message;
import fr.craft.linkedinarchiveexplorer.domain.MessageHit;
import fr.craft.linkedinarchiveexplorer.domain.ContentType;
import fr.craft.linkedinarchiveexplorer.domain.Correspondent;
import fr.craft.linkedinarchiveexplorer.domain.Excerpt;
import fr.craft.linkedinarchiveexplorer.domain.Match;
import fr.craft.linkedinarchiveexplorer.domain.SearchHit;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ContentGroupTest {

  @Test
  void refusesAHitOfAnotherTypeThanItsOwn() {
    ContentHit comment =
        new ContentHit(
            new Content(ContentType.COMMENT, "https://li/c/1", "x"),
            List.of(new Excerpt("", new Match(0, 1, "x"), "")));

    assertThrows(
        IllegalArgumentException.class, () -> new ContentGroup(ContentType.MESSAGE, List.of(comment)));
  }

  private static SearchHit message(String who, String day) {
    Message message =
        new Message(
            Optional.of(LocalDate.parse(day)),
            "https://li/thread/" + who + "/",
            new Body("x"),
            new Correspondent(who, Optional.of("https://li/in/" + who)));
    return new MessageHit(message, List.of(new Excerpt("", new Match(0, 1, "x"), "")));
  }

  @Nested
  class ByCorrespondent {

    @Test
    void filesMessagesByCorrespondentAlphabeticallyIgnoringCaseAndNewestFirst() {
      ContentGroup group =
          new ContentGroup(
              ContentType.MESSAGE,
              List.of(
                  message("zoé", "2024-01-01"),
                  message("Zoe", "2024-01-01"),
                  message("bob", "2024-01-01"),
                  message("Alice", "2024-01-01"),
                  message("Alice", "2024-03-01")));

      assertEquals(
          List.of("Alice", "bob", "Zoe", "zoé"),
          group.byCorrespondent().keySet().stream().map(Correspondent::name).toList());
      assertEquals(
          List.of(LocalDate.parse("2024-03-01"), LocalDate.parse("2024-01-01")),
          group.byCorrespondent().values().iterator().next().stream().map(hit -> hit.date().orElseThrow()).toList());
    }

    @Test
    void keepsTwoGroupsApartWhoseTitlesOnlyDifferByCase() {
      List<SearchHit> hits = List.of(group("Team"), group("team"));

      assertEquals(2, new ContentGroup(ContentType.MESSAGE, hits).byCorrespondent().size());
    }

    private SearchHit group(String title) {
      Message message =
          new Message(
              Optional.empty(),
              "https://li/thread/" + title + "/",
              new Body("x"),
              new Correspondent(title, Optional.empty()));
      return new MessageHit(message, List.of(new Excerpt("", new Match(0, 1, "x"), "")));
    }

    @Test
    void keepsTwoPeopleOfTheSameNameApart() {
      SearchHit first = message("Alice", "2024-01-01");
      Message other =
          new Message(
              Optional.empty(),
              "https://li/thread/other/",
              new Body("x"),
              new Correspondent("Alice", Optional.of("https://li/in/alice2")));
      SearchHit second = new MessageHit(other, first.excerpts());

      assertEquals(2, new ContentGroup(ContentType.MESSAGE, List.of(first, second)).byCorrespondent().size());
    }
  }
}
