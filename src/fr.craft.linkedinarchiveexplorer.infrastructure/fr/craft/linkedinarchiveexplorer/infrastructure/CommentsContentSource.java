package fr.craft.linkedinarchiveexplorer.infrastructure;

import fr.craft.linkedinarchiveexplorer.domain.Content;
import fr.craft.linkedinarchiveexplorer.domain.ContentSource;
import fr.craft.linkedinarchiveexplorer.domain.ContentType;
import java.util.ArrayList;
import java.util.List;

/** Reads the user's comments from {@code Comments_*.csv} (columns Date, Link, Message). */
public final class CommentsContentSource implements ContentSource {

  private final ArchiveReader archive;

  public CommentsContentSource(ArchiveReader archive) {
    this.archive = archive;
  }

  @Override
  public List<Content> load() {
    return archive
        .readFirst(name -> name.startsWith("Comments") && name.endsWith(".csv"))
        .map(this::toContents)
        .orElse(List.of());
  }

  private List<Content> toContents(String csv) {
    List<Content> contents = new ArrayList<>();
    for (CsvRow row : Csv.parse(csv).rows()) {
      String url = row.get("Link");
      if (!url.isBlank()) {
        contents.add(
            new Content(ContentType.COMMENT, LinkedInDates.parse(row.get("Date")), url, row.get("Message")));
      }
    }
    return contents;
  }
}
