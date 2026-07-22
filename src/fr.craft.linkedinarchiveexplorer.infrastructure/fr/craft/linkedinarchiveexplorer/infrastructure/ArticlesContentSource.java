package fr.craft.linkedinarchiveexplorer.infrastructure;

import fr.craft.linkedinarchiveexplorer.domain.ArticleTextExtractor;
import fr.craft.linkedinarchiveexplorer.domain.Content;
import fr.craft.linkedinarchiveexplorer.domain.ContentSource;
import fr.craft.linkedinarchiveexplorer.domain.ContentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads the user's articles from the {@code Articles/**.html} files in the archive. */
public final class ArticlesContentSource implements ContentSource {

  private final ArchiveReader archive;
  private final ArticleTextExtractor extractor;

  public ArticlesContentSource(ArchiveReader archive, ArticleTextExtractor extractor) {
    this.archive = archive;
    this.extractor = extractor;
  }

  @Override
  public List<Content> load() {
    List<Content> contents = new ArrayList<>();
    for (String html : archive.readAll(name -> name.contains("Articles") && name.endsWith(".html"))) {
      String url = extractor.url(html);
      if (!url.isBlank()) {
        contents.add(new Content(ContentType.ARTICLE, Optional.empty(), url, extractor.text(html)));
      }
    }
    return contents;
  }
}
