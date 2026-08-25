package fr.craft.linkedinarchiveexplorer.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * The {@code name=value} splitting shared by {@link QueryParameters} and {@link Cookies}.
 * The first occurrence of a name wins, so a duplicated one still has one unambiguous
 * meaning; a pair with no {@code =} is a name with an empty value.
 */
final class Pairs {

  private Pairs() {}

  static Map<String, String> split(String text, String separator, UnaryOperator<String> decode) {
    if (text == null) {
      return Map.of();
    }
    Map<String, String> values = new LinkedHashMap<>();
    for (String pair : text.split(separator)) {
      if (pair.isBlank()) {
        continue;
      }
      int equals = pair.indexOf('=');
      values.putIfAbsent(
          decode.apply(equals < 0 ? pair : pair.substring(0, equals)),
          decode.apply(equals < 0 ? "" : pair.substring(equals + 1)));
    }
    return values;
  }
}
