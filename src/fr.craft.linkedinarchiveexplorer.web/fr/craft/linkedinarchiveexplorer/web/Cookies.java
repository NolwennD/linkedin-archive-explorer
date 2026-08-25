package fr.craft.linkedinarchiveexplorer.web;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The decoded cookies of a request. The twin of {@link QueryParameters}, and it follows
 * the same two rules for the same reasons: the first occurrence of a name wins, so a
 * duplicated cookie still has one unambiguous meaning, and a malformed escape is kept as
 * it came rather than breaking the page.
 */
public record Cookies(Map<String, String> values) {

  public Cookies {
    if (values == null) {
      throw new IllegalArgumentException("Cookies must have a (possibly empty) map");
    }
    values = Map.copyOf(values);
  }

  /** Parses a {@code Cookie} header; {@code null} — no cookie at all — yields none. */
  public static Cookies parse(String header) {
    return new Cookies(Pairs.split(header, ";", text -> decode(text.trim())));
  }

  /** The value of {@code name}, or an empty string when it is absent. */
  public String value(String name) {
    return values.getOrDefault(name, "");
  }

  /**
   * Percent-decoding only: unlike a query string, a cookie gives {@code +} no special
   * meaning, so it is escaped before decoding to survive as itself.
   */
  private static String decode(String text) {
    try {
      return URLDecoder.decode(text.replace("+", "%2B"), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException malformed) {
      return text;
    }
  }
}
