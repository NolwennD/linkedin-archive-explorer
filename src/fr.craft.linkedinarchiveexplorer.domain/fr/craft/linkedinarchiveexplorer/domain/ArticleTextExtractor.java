package fr.craft.linkedinarchiveexplorer.domain;

/**
 * Port: extracts the searchable text, title and canonical URL from an article's
 * HTML. A JDK-only implementation ships first; a Jsoup-backed one can replace it.
 */
public interface ArticleTextExtractor {

  String text(String html);

  String title(String html);

  String url(String html);
}
