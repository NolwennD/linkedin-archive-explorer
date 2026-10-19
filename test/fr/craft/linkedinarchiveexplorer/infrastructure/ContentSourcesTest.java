package fr.craft.linkedinarchiveexplorer.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.craft.linkedinarchiveexplorer.domain.Body;
import fr.craft.linkedinarchiveexplorer.domain.Content;
import fr.craft.linkedinarchiveexplorer.domain.ContentType;
import fr.craft.linkedinarchiveexplorer.domain.Correspondent;
import fr.craft.linkedinarchiveexplorer.domain.Message;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ContentSourcesTest {

  @Nested
  class Comments {

    @Test
    void mapsEachRowToACommentContent() {
      String csv =
          """
          Date,Link,Message
          2024-11-06 12:49:49,https://li/1,"hello, world"
          """;
      List<Content> contents = new CommentsContentSource(new FakeArchiveReader().with("Comments_1.csv", csv)).load();

      assertEquals(1, contents.size());
      Content comment = contents.getFirst();
      assertEquals(ContentType.COMMENT, comment.type());
      assertEquals("https://li/1", comment.url());
      assertEquals(new Body("hello, world"), comment.text());
      assertEquals(LocalDate.of(2024, 11, 6), comment.date().orElseThrow());
    }

    @Test
    void yieldsNothingWhenThereIsNoCommentsFile() {
      assertTrue(new CommentsContentSource(new FakeArchiveReader()).load().isEmpty());
    }
  }

  @Nested
  class Posts {

    @Test
    void mapsSharesToPostContent() {
      String csv =
          """
          Date,ShareLink,ShareCommentary,Visibility
          2024-01-02 08:00:00,https://li/post,"my post",MEMBER_NETWORK
          """;
      List<Content> contents = new SharesContentSource(new FakeArchiveReader().with("Shares_1.csv", csv)).load();

      assertEquals(1, contents.size());
      Content post = contents.getFirst();
      assertEquals(ContentType.POST, post.type());
      assertEquals("https://li/post", post.url());
      assertEquals(new Body("my post"), post.text());
      assertEquals(LocalDate.of(2024, 1, 2), post.date().orElseThrow());
    }
  }

  @Nested
  class Messages {

    private static final String HEADER =
        "CONVERSATION ID,CONVERSATION TITLE,FROM,SENDER PROFILE URL,TO,RECIPIENT PROFILE URLS,DATE,SUBJECT,CONTENT,FOLDER,ATTACHMENTS,IS MESSAGE DRAFT,IS CONVERSATION DRAFT\n";
    private static final String ME = "me,https://li/in/me";

    private static List<Message> load(String... rows) {
      return new MessagesContentSource(
              new FakeArchiveReader().with("messages.csv", HEADER + String.join("\n", rows) + "\n"))
          .load();
    }

    @Test
    void filesAMessageUnderTheOtherPersonAndLinksToTheThread() {
      List<Message> contents =
          load(
              "c1,,Rose,https://li/in/rose,me,https://li/in/me,2026-07-20 15:59:21 UTC,,hello there,INBOX,,No,No",
              "c1,,me,https://li/in/me,Rose,https://li/in/rose,2026-07-19 10:00:00 UTC,,my answer,INBOX,,No,No",
              "c2,,me,https://li/in/me,Paul,https://li/in/paul,2026-07-18 10:00:00 UTC,,other chat,INBOX,,No,No");

      assertEquals(3, contents.size());
      Message message = contents.getFirst();
      assertEquals("https://www.linkedin.com/messaging/thread/c1/", message.url());
      assertEquals(new Body("hello there"), message.text());
      assertEquals(LocalDate.of(2026, 7, 20), message.date().orElseThrow());
      assertEquals(new Correspondent("Rose", Optional.of("https://li/in/rose")), message.correspondent());
      assertEquals(message.correspondent(), contents.get(1).correspondent());
    }

    @Test
    void filesAGroupConversationUnderItsTitleElseUnderEveryoneButTheUser() {
      List<Message> contents =
          load(
              "g1,Round table,Ann,https://li/in/ann,\"me,Bob\",\"https://li/in/me,https://li/in/bob\",2026-01-01 00:00:00 UTC,,hi,INBOX,,No,No",
              "g2,,Ann,https://li/in/ann,\"me,Bob\",\"https://li/in/me,https://li/in/bob\",2026-01-01 00:00:00 UTC,,hi,INBOX,,No,No",
              "c3,,me,https://li/in/me,Paul,https://li/in/paul,2026-01-01 00:00:00 UTC,,yo,INBOX,,No,No");

      assertEquals(new Correspondent("Round table", Optional.empty()), contents.getFirst().correspondent());
      assertEquals(new Correspondent("Ann, Bob", Optional.empty()), contents.get(1).correspondent());
    }

    @Test
    void stillFindsTheMessagesWhenTheUserCannotBeTold() {
      List<Message> contents =
          load("c1,,Rose,https://li/in/rose,me,https://li/in/me,2026-07-20 15:59:21 UTC,,hello,INBOX,,No,No");

      assertEquals(1, contents.size());
      assertEquals(new Body("hello"), contents.getFirst().text());
    }

    @Test
    void keepsAnUnlinkedParticipantWhenNoProfileLinkAtAll() {
      List<Message> contents = load("c1,,Rose,,Paul,,2026-07-20 15:59:21 UTC,,hello,INBOX,,No,No");

      assertEquals(1, contents.size());
      assertTrue(contents.getFirst().correspondent().name().contains("Rose"));
    }

    @Test
    void yieldsNothingRatherThanFailingWhenTheFileHasAnotherShape() {
      List<Message> contents =
          new MessagesContentSource(new FakeArchiveReader().with("messages.csv", "FROM,CONTENT\nRose,hello\n")).load();

      assertTrue(contents.isEmpty());
    }

    @Test
    void skipsMessagesWithoutTextAndYieldsNothingWithoutTheFile() {
      assertTrue(load("c1,,Rose,https://li/in/rose,me,https://li/in/me,2026-07-20 15:59:21 UTC,,,INBOX,,No,No").isEmpty());
      assertTrue(new MessagesContentSource(new FakeArchiveReader()).load().isEmpty());
    }
  }

  @Nested
  class Articles {

    private static final String HTML =
        """
        <html><head><title>T</title></head>
        <body>
          <h1><a href="https://www.linkedin.com/pulse/my-article">My Article</a></h1>
          <p>the body talks about kotlin</p>
        </body></html>
        """;

    @Test
    void mapsEachArticleHtmlToAnUndatedArticleContent() {
      List<Content> contents =
          new ArticlesContentSource(
                  new FakeArchiveReader().with("Articles/Articles/a.html", HTML), new JdkArticleTextExtractor())
              .load();

      assertEquals(1, contents.size());
      Content article = contents.getFirst();
      assertEquals(ContentType.ARTICLE, article.type());
      assertEquals("https://www.linkedin.com/pulse/my-article", article.url());
      assertTrue(article.date().isEmpty());
      assertTrue(article.text().value().contains("kotlin"), article.text().value());
    }
  }
}
