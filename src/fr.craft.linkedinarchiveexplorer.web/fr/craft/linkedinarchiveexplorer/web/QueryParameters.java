package fr.craft.linkedinarchiveexplorer.web;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The decoded parameters of a query string. The first occurrence of a name wins, so a
 * hand-edited URL carrying {@code q} twice still has one unambiguous meaning.
 */
public record QueryParameters(Map<String, String> values) {

  public QueryParameters {
    if (values == null) {
      throw new IllegalArgumentException("Query parameters must have a (possibly empty) map");
    }
    values = Map.copyOf(values);
  }

  /** Parses a raw query string; {@code null} — no {@code ?} in the URI — yields no parameters. */
  public static QueryParameters parse(String rawQuery) {
    Map<String, String> values = new LinkedHashMap<>();
    if (rawQuery == null) {
      return new QueryParameters(values);
    }
    for (String pair : rawQuery.split("&")) {
      if (pair.isEmpty()) {
        continue;
      }
      int equals = pair.indexOf('=');
      String name = equals < 0 ? pair : pair.substring(0, equals);
      String value = equals < 0 ? "" : pair.substring(equals + 1);
      values.putIfAbsent(decode(name), decode(value));
    }
    return new QueryParameters(values);
  }

  /** The value of {@code name}, or an empty string when it is absent. */
  public String value(String name) {
    return values.getOrDefault(name, "");
  }

  /** A checkbox submits {@code on} when ticked, and nothing at all otherwise. */
  public boolean isChecked(String name) {
    return "on".equals(value(name));
  }

  /** A malformed escape must not break the page, so the raw text is kept as it came. */
  private static String decode(String text) {
    try {
      return URLDecoder.decode(text, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException malformed) {
      return text;
    }
  }
}
