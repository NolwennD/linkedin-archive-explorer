package fr.craft.linkedinarchiveexplorer.infrastructure;

import fr.craft.linkedinarchiveexplorer.domain.Content;
import fr.craft.linkedinarchiveexplorer.domain.ContentSource;
import fr.craft.linkedinarchiveexplorer.domain.ContentType;
import java.util.ArrayList;
import java.util.List;

/** Reads the user's posts from {@code Shares_*.csv} (columns Date, ShareLink, ShareCommentary). */
public final class SharesContentSource implements ContentSource {

  private final ArchiveReader archive;

  public SharesContentSource(ArchiveReader archive) {
    this.archive = archive;
  }

  @Override
  public List<Content> load() {
    return archive
        .readFirst(name -> name.startsWith("Shares") && name.endsWith(".csv"))
        .map(this::toContents)
        .orElse(List.of());
  }

  private List<Content> toContents(String csv) {
    List<Content> contents = new ArrayList<>();
    for (CsvRow row : Csv.parse(csv).rows()) {
      String url = row.get("ShareLink");
      if (!url.isBlank()) {
        contents.add(
            new Content(
                ContentType.POST, LinkedInDates.parse(row.get("Date")), url, row.get("ShareCommentary")));
      }
    }
    return contents;
  }
}
