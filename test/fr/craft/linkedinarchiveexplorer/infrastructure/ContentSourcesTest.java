package fr.craft.linkedinarchiveexplorer.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.craft.linkedinarchiveexplorer.domain.Body;
import fr.craft.linkedinarchiveexplorer.domain.Content;
import fr.craft.linkedinarchiveexplorer.domain.ContentType;
import java.time.LocalDate;
import java.util.List;
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
      Content comment = contents.get(0);
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
      Content post = contents.get(0);
      assertEquals(ContentType.POST, post.type());
      assertEquals("https://li/post", post.url());
      assertEquals(new Body("my post"), post.text());
      assertEquals(LocalDate.of(2024, 1, 2), post.date().orElseThrow());
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
      Content article = contents.get(0);
      assertEquals(ContentType.ARTICLE, article.type());
      assertEquals("https://www.linkedin.com/pulse/my-article", article.url());
      assertTrue(article.date().isEmpty());
      assertTrue(article.text().value().contains("kotlin"), article.text().value());
    }
  }
}
