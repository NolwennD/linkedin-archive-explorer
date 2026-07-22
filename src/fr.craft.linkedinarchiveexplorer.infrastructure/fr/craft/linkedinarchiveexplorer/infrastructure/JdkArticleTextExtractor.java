package fr.craft.linkedinarchiveexplorer.infrastructure;

import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.DOTALL;

import fr.craft.linkedinarchiveexplorer.domain.ArticleTextExtractor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JDK-only article extraction: strips tags with regexes, good enough for LinkedIn's
 * exported article HTML. Behind the {@link ArticleTextExtractor} port so a Jsoup-backed
 * implementation could replace it.
 */
public final class JdkArticleTextExtractor implements ArticleTextExtractor {

  private static final Pattern TITLE = Pattern.compile("<title>(.*?)</title>", CASE_INSENSITIVE | DOTALL);
  private static final Pattern H1_HREF =
      Pattern.compile("<h1\\b[^>]*>.*?href=\"([^\"]*)\"", CASE_INSENSITIVE | DOTALL);
  private static final Pattern HEAD = Pattern.compile("<head\\b.*?</head>", CASE_INSENSITIVE | DOTALL);
  private static final Pattern SCRIPT_OR_STYLE =
      Pattern.compile("<(script|style)\\b.*?</\\1>", CASE_INSENSITIVE | DOTALL);
  private static final Pattern TAG = Pattern.compile("<[^>]+>");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  @Override
  public String text(String html) {
    String withoutHead = HEAD.matcher(html).replaceAll(" ");
    String withoutCode = SCRIPT_OR_STYLE.matcher(withoutHead).replaceAll(" ");
    String withoutTags = TAG.matcher(withoutCode).replaceAll(" ");
    return WHITESPACE.matcher(unescape(withoutTags)).replaceAll(" ").trim();
  }

  @Override
  public String title(String html) {
    Matcher matcher = TITLE.matcher(html);
    return matcher.find() ? unescape(matcher.group(1)).trim() : "";
  }

  @Override
  public String url(String html) {
    Matcher matcher = H1_HREF.matcher(html);
    return matcher.find() ? matcher.group(1) : "";
  }

  private String unescape(String text) {
    return text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&");
  }
}
