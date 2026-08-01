package fr.craft.linkedinarchiveexplorer.launcher;

import fr.craft.linkedinarchiveexplorer.application.SearchContentsService;
import fr.craft.linkedinarchiveexplorer.infrastructure.ArchiveLocator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The archives a UI may suggest, and the one it currently searches. Answers a different
 * question from {@link Explorer} — <em>which archives are around, and which is open</em> —
 * and leans on it for the wiring of each one.
 *
 * <p>Lives here rather than in a UI module because listing files and opening zips belong
 * to the infrastructure, which no UI module is allowed to name. The precedence rule of
 * {@link #resolve} therefore becomes testable without a server.
 *
 * <p>Owns the open archive: close it (try-with-resources) when the UI is done.
 */
public final class ArchiveCatalog implements AutoCloseable {

  private static final Path DEFAULT_ARCHIVE_DIRECTORY = Path.of("data");

  private final Path explicitArchive;
  private final Path directory;
  private Explorer open;

  private ArchiveCatalog(Path explicitArchive, Path directory, Explorer open) {
    this.explicitArchive = explicitArchive;
    this.directory = directory;
    this.open = open;
  }

  /**
   * @param explicitArchive the {@code --archive} value, suggested first and taken as the
   *     default of this launch, or {@code null} to only suggest the default directory.
   * @throws ArchiveUnavailableException if {@code explicitArchive} cannot be opened — what
   *     the user named on the command line is checked at once, there being a terminal to
   *     say so on. An empty directory, on the other hand, is not a failure: the page asks.
   */
  public static ArchiveCatalog of(Path explicitArchive) {
    return of(explicitArchive, DEFAULT_ARCHIVE_DIRECTORY);
  }

  /** Same, with the directory to list made explicit. */
  public static ArchiveCatalog of(Path explicitArchive, Path directory) {
    Explorer open = explicitArchive == null ? null : Explorer.open(explicitArchive);
    return new ArchiveCatalog(explicitArchive, directory, open);
  }

  /**
   * The archives worth suggesting: the explicit one first when there is one, then the
   * directory's, most recent first. Re-read at every call, so an archive dropped in the
   * directory shows up without a restart.
   */
  public List<Path> archives() {
    List<Path> archives = new ArrayList<>();
    if (explicitArchive != null) {
      archives.add(absolute(explicitArchive));
    }
    for (Path archive : ArchiveLocator.all(directory)) {
      if (!isSameFile(archive, explicitArchive)) {
        archives.add(absolute(archive));
      }
    }
    return List.copyOf(archives);
  }

  /**
   * The archive to search for this request, empty when there is none to propose and the
   * page must ask for one.
   *
   * <p>Order: the path typed in the page, then the explicit archive of this launch —
   * typed just now, so a present intention — then the cookie, a past one, then the most
   * recent of the directory.
   *
   * <p>A typed path is returned <em>as it came</em>, even when nothing is there: the user
   * has to see their own typo rather than be silently sent elsewhere. A cookie, which
   * nobody typed today, is dropped when it no longer leads anywhere.
   */
  public Optional<Path> resolve(String fromQuery, String fromCookie) {
    if (fromQuery != null && !fromQuery.isBlank()) {
      return Optional.of(absolute(Path.of(fromQuery)));
    }
    if (explicitArchive != null) {
      return Optional.of(absolute(explicitArchive));
    }
    if (fromCookie != null && !fromCookie.isBlank() && Files.isReadable(Path.of(fromCookie))) {
      return Optional.of(absolute(Path.of(fromCookie)));
    }
    return ArchiveLocator.all(directory).stream().findFirst().map(ArchiveCatalog::absolute);
  }

  /**
   * The search core wired over {@code archive}. One archive stays open at a time: the
   * previous one is closed on a change, so a switch costs a reopen and a repeated search
   * costs nothing. Safe without synchronisation because the server handles requests
   * sequentially.
   *
   * @throws ArchiveUnavailableException if the path leads to no readable archive.
   */
  public SearchContentsService serviceFor(Path archive) {
    if (open != null && isSameFile(open.archive(), archive)) {
      return open.service();
    }
    Explorer previous = open;
    open = null;
    if (previous != null) {
      previous.close();
    }
    open = Explorer.open(archive);
    return open.service();
  }

  @Override
  public void close() {
    if (open != null) {
      open.close();
      open = null;
    }
  }

  /**
   * Everything the catalogue hands out is absolute, because it does not stay in this
   * process: a suggestion is pasted back later, and a cookie outlives the launch — and its
   * working directory. {@code data/x.zip} would then mean whatever the next launch happens
   * to sit in, or nothing at all.
   *
   * <p>A relative path typed in the page is read against the directory the server runs
   * from, which is the only thing it can reasonably mean.
   */
  private static Path absolute(Path path) {
    return path.toAbsolutePath().normalize();
  }

  /** Compared absolute, so that {@code data/x.zip} and {@code ./data/x.zip} are one archive. */
  private static boolean isSameFile(Path one, Path other) {
    if (one == null || other == null) {
      return false;
    }
    return absolute(one).equals(absolute(other));
  }
}
