package fr.craft.linkedinarchiveexplorer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HtmlEscaperTest {

  @Test
  void replacesEveryCharacterThatCouldBreakOutOfMarkup() {
    assertEquals("&amp;&lt;&gt;&quot;&#39;", HtmlEscaper.escape("&<>\"'"));
  }

  @Test
  void doesNotEscapeTheEscapesItProduces() {
    assertEquals("&amp;lt;", HtmlEscaper.escape("&lt;"));
  }
}
