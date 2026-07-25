package fr.craft.linkedinarchiveexplorer.application;

import static fr.craft.linkedinarchiveexplorer.domain.ContentType.ARTICLE;
import static fr.craft.linkedinarchiveexplorer.domain.ContentType.COMMENT;
import static fr.craft.linkedinarchiveexplorer.domain.ContentType.POST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.craft.linkedinarchiveexplorer.domain.Content;
import fr.craft.linkedinarchiveexplorer.domain.ContentSource;
import fr.craft.linkedinarchiveexplorer.domain.ContentType;
import fr.craft.linkedinarchiveexplorer.domain.SearchEngine;
import fr.craft.linkedinarchiveexplorer.domain.SearchTerm;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SearchContentsServiceTest {

  private static Content content(ContentType type, Optional<LocalDate> date, String text) {
    return new Content(type, date, "https://example/x", text);
  }

  private static Content dated(ContentType type, String date, String text) {
    return content(type, Optional.of(LocalDate.parse(date)), text);
  }

  private static SearchResults search(String term, Content... contents) {
    SearchContentsService service =
        new SearchContentsService(List.of(new FakeContentSource(contents)), new SearchEngine());
    return service.search(SearchTerm.literal(term));
  }

  @Test
  void returnsNoGroupWhenNothingMatches() {
    assertTrue(search("absent", dated(COMMENT, "2024-01-01", "hello")).groups().isEmpty());
  }

  @Test
  void groupsMatchesByTypeInCanonicalOrderArticlePostComment() {
    SearchResults results =
        search(
            "x",
            dated(COMMENT, "2024-01-01", "x comment"),
            dated(POST, "2024-01-01", "x post"),
            content(ARTICLE, Optional.empty(), "x article"));

    assertEquals(List.of(ARTICLE, POST, COMMENT), results.groups().stream().map(ContentGroup::type).toList());
  }

  @Test
  void omitsTypesWithoutAnyMatch() {
    SearchResults results = search("x", dated(COMMENT, "2024-01-01", "x only a comment"));

    assertEquals(List.of(COMMENT), results.groups().stream().map(ContentGroup::type).toList());
  }

  @Test
  void sortsEachGroupByDateDescending() {
    SearchResults results =
        search(
            "x",
            dated(COMMENT, "2024-01-01", "x old"),
            dated(COMMENT, "2024-03-01", "x recent"),
            dated(COMMENT, "2024-02-01", "x middle"));

    List<String> texts = results.groups().get(0).hits().stream().map(h -> h.content().text().value()).toList();
    assertEquals(List.of("x recent", "x middle", "x old"), texts);
  }

  @Test
  void placesUndatedContentAfterDatedWithinAGroup() {
    SearchResults results =
        search(
            "x",
            content(POST, Optional.empty(), "x undated"),
            dated(POST, "2024-01-01", "x dated"));

    List<String> texts = results.groups().get(0).hits().stream().map(h -> h.content().text().value()).toList();
    assertEquals(List.of("x dated", "x undated"), texts);
  }

  @Test
  void aggregatesContentFromEverySource() {
    SearchContentsService service =
        new SearchContentsService(
            List.of(
                new FakeContentSource(dated(COMMENT, "2024-01-01", "x from source one")),
                new FakeContentSource(dated(POST, "2024-01-01", "x from source two"))),
            new SearchEngine());

    SearchResults results = service.search(SearchTerm.literal("x"));

    assertEquals(2, results.groups().size());
  }

  private record FakeContentSource(List<Content> contents) implements ContentSource {
    FakeContentSource(Content... contents) {
      this(List.of(contents));
    }

    @Override
    public List<Content> load() {
      return contents;
    }
  }
}
