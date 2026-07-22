package fr.craft.linkedinarchiveexplorer.infrastructure;

import java.util.List;

/** One data row, addressable by header column name. */
public record CsvRow(List<String> headers, List<String> values) {

  public CsvRow(List<String> headers, List<String> values) {
    this.headers = List.copyOf(headers);
    this.values = List.copyOf(values);
  }

  public String get(String column) {
    int index = headers.indexOf(column);
    if (index < 0) {
      throw new IllegalArgumentException("Unknown column: " + column);
    }
    return index < values.size() ? values.get(index) : "";
  }
}
