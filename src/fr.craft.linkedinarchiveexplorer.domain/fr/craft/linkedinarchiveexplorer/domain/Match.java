package fr.craft.linkedinarchiveexplorer.domain;

/**
 * A located occurrence: its position {@code [start, end)} in the searched text and the
 * actual matched fragment {@code value} (its real case, which may differ from the search
 * term under a case-insensitive search). It is the unit a renderer highlights.
 */
public record Match(int start, int end, String value) {

  public Match {
    if (value == null) {
      throw new IllegalArgumentException("A match must have a non-null value");
    }
    if (start < 0 || end < start) {
      throw new IllegalArgumentException("A match span must satisfy 0 <= start <= end");
    }
    if (value.length() != end - start) {
      throw new IllegalArgumentException("A match value length must equal end - start");
    }
  }
}
