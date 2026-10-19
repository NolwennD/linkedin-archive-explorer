package fr.craft.linkedinarchiveexplorer.infrastructure;

import fr.craft.linkedinarchiveexplorer.domain.Connection;
import fr.craft.linkedinarchiveexplorer.domain.ContentSource;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the user's connections from {@code Connections.csv}. The file opens with a few lines
 * of notes before its header row, which is where the table really starts.
 */
public final class ConnectionsContentSource implements ContentSource {

  // The header row, at the start of a line (past a BOM): notes may well mention "First Name,".
  private static final Pattern TABLE = Pattern.compile("(?m)^\uFEFF?(?=First Name,)");

  private final ArchiveReader archive;

  public ConnectionsContentSource(ArchiveReader archive) {
    this.archive = archive;
  }

  @Override
  public List<Connection> load() {
    return archive
        .readFirst(name -> name.equals("Connections.csv"))
        .map(this::toConnections)
        .orElse(List.of());
  }

  private List<Connection> toConnections(String csv) {
    Matcher table = TABLE.matcher(csv);
    if (!table.find()) {
      return List.of();
    }
    List<Connection> connections = new ArrayList<>();
    for (CsvRow row : Csv.parse(csv.substring(table.end()))) {
      // ponytail: a member who hides their profile comes with no name and no link, so there is
      // nothing to show or to click; skipped rather than listed blank.
      String url = row.get("URL");
      if (!url.isBlank()) {
        connections.add(
            new Connection(
                row.get("First Name"),
                row.get("Last Name"),
                url,
                row.get("Company"),
                row.get("Position"),
                LinkedInDates.parseDayMonthYear(row.get("Connected On"))));
      }
    }
    return connections;
  }
}
