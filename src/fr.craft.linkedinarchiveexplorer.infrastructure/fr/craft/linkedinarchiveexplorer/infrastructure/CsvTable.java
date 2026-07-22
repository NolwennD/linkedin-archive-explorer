package fr.craft.linkedinarchiveexplorer.infrastructure;

import java.util.List;

/** A parsed CSV: its header columns and data rows. */
public record CsvTable(List<String> headers, List<CsvRow> rows) {

  public CsvTable(List<String> headers, List<CsvRow> rows) {
    this.headers = List.copyOf(headers);
    this.rows = List.copyOf(rows);
  }
}
