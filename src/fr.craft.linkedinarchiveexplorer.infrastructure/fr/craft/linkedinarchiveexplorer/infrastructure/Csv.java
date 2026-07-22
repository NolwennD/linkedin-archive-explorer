package fr.craft.linkedinarchiveexplorer.infrastructure;

import java.util.ArrayList;
import java.util.List;

/**
 * A small RFC 4180 CSV reader (JDK-only). Handles quoted fields, escaped {@code ""}
 * quotes, embedded commas and newlines, and CRLF or LF record separators. Blank lines
 * are skipped; surrounding spaces are preserved.
 */
public final class Csv {

  private Csv() {}

  public static CsvTable parse(String content) {
    List<List<String>> records = parseRecords(content);
    if (records.isEmpty()) {
      return new CsvTable(List.of(), List.of());
    }
    List<String> headers = records.get(0);
    List<CsvRow> rows = new ArrayList<>();
    for (int r = 1; r < records.size(); r++) {
      rows.add(new CsvRow(headers, records.get(r)));
    }
    return new CsvTable(headers, rows);
  }

  public static List<List<String>> parseRecords(String content) {
    List<List<String>> records = new ArrayList<>();
    List<String> fields = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean inQuotes = false;
    boolean recordStarted = false;

    for (int i = 0; i < content.length(); i++) {
      char c = content.charAt(i);
      if (inQuotes) {
        if (c == '"' && i + 1 < content.length() && content.charAt(i + 1) == '"') {
          field.append('"');
          i++;
        } else if (c == '"') {
          inQuotes = false;
        } else {
          field.append(c);
        }
      } else if (c == '"') {
        inQuotes = true;
        recordStarted = true;
      } else if (c == ',') {
        fields.add(field.toString());
        field.setLength(0);
        recordStarted = true;
      } else if (c == '\n' || c == '\r') {
        if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
          i++;
        }
        if (recordStarted) {
          fields.add(field.toString());
          field.setLength(0);
          records.add(fields);
          fields = new ArrayList<>();
          recordStarted = false;
        }
      } else {
        field.append(c);
        recordStarted = true;
      }
    }
    if (recordStarted) {
      fields.add(field.toString());
      records.add(fields);
    }
    return records;
  }
}
