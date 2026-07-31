package fr.craft.linkedinarchiveexplorer.launcher;

import fr.craft.linkedinarchiveexplorer.application.SearchContentsService;
import fr.craft.linkedinarchiveexplorer.domain.ContentSource;
import fr.craft.linkedinarchiveexplorer.domain.SearchEngine;
import fr.craft.linkedinarchiveexplorer.infrastructure.ArchiveLocator;
import fr.craft.linkedinarchiveexplorer.infrastructure.ArticlesContentSource;
import fr.craft.linkedinarchiveexplorer.infrastructure.CommentsContentSource;
import fr.craft.linkedinarchiveexplorer.infrastructure.JdkArticleTextExtractor;
import fr.craft.linkedinarchiveexplorer.infrastructure.SharesContentSource;
import fr.craft.linkedinarchiveexplorer.infrastructure.ZipArchive;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The composition root: resolves the archive, opens it, and wires every adapter behind a
 * ready-to-use {@link SearchContentsService}. The single place naming the concrete
 * infrastructure — the UI modules cannot even see it — so a new adapter is branched here
 * once, for every UI at the same time.
 *
 * <p>Owns the open archive: close it (try-with-resources) when the search is over.
 */
public final class Explorer implements AutoCloseable {

  private static final Path DEFAULT_ARCHIVE_DIRECTORY = Path.of("data");

  private final Path archive;
  private final ZipArchive zip;
  private final SearchContentsService service;

  private Explorer(Path archive, ZipArchive zip, SearchContentsService service) {
    this.archive = archive;
    this.zip = zip;
    this.service = service;
  }

  /**
   * @param explicitArchive the {@code --archive} value, or {@code null} to search the
   *     most recent archive of the default {@code data/} directory.
   * @throws ArchiveUnavailableException if there is no readable archive to open.
   */
  public static Explorer open(Path explicitArchive) {
    return open(explicitArchive, DEFAULT_ARCHIVE_DIRECTORY);
  }

  /** Same, with the default directory made explicit — the seam the tests open. */
  static Explorer open(Path explicitArchive, Path defaultDirectory) {
    Path archive = resolve(explicitArchive, defaultDirectory);
    ZipArchive zip = ZipArchive.open(archive);
    List<ContentSource> sources =
        List.of(
            new CommentsContentSource(zip),
            new SharesContentSource(zip),
            new ArticlesContentSource(zip, new JdkArticleTextExtractor()));
    return new Explorer(archive, zip, new SearchContentsService(sources, new SearchEngine()));
  }

  private static Path resolve(Path explicitArchive, Path defaultDirectory) {
    Path archive =
        explicitArchive != null
            ? explicitArchive
            : ArchiveLocator.mostRecent(defaultDirectory)
                .orElseThrow(
                    () ->
                        new ArchiveUnavailableException(
                            "No archive found in " + defaultDirectory + "/ (or use --archive <path>)."));
    if (!Files.isReadable(archive)) {
      throw new ArchiveUnavailableException("Cannot read archive: " + archive);
    }
    return archive;
  }

  public Path archive() {
    return archive;
  }

  public SearchContentsService service() {
    return service;
  }

  @Override
  public void close() {
    zip.close();
  }
}
