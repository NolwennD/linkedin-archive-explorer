package fr.craft.linkedinarchiveexplorer.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JdkArticleTextExtractorTest {

  private final JdkArticleTextExtractor extractor = new JdkArticleTextExtractor();

  private static final String ARTICLE =
      """
      <html>
      <head>
        <title>My Article</title>
        <style> body { color: red; } </style>
      </head>
      <body>
        <h1><a href="https://www.linkedin.com/pulse/my-article">My Article</a></h1>
        <p>Hello &amp; welcome to <b>coding</b>.</p>
      </body>
      </html>
      """;

  @Nested
  class Title {

    @Test
    void readsTheTitleTag() {
      assertEquals("My Article", extractor.title(ARTICLE));
    }
  }

  @Nested
  class Url {

    @Test
    void readsTheCanonicalUrlFromTheH1Anchor() {
      assertEquals("https://www.linkedin.com/pulse/my-article", extractor.url(ARTICLE));
    }
  }

  @Nested
  class Text {

    @Test
    void keepsTheBodyTextAndUnescapesEntities() {
      String text = extractor.text(ARTICLE);

      assertTrue(text.contains("Hello & welcome to"), text);
      assertTrue(text.contains("coding"), text);
    }

    @Test
    void dropsStyleAndScriptContent() {
      assertFalse(extractor.text(ARTICLE).contains("color"), "style content leaked into text");
    }

    @Test
    void removesAllTags() {
      assertFalse(extractor.text(ARTICLE).contains("<"), "tags leaked into text");
    }
  }
}
