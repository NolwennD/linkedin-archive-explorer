package fr.craft.linkedinarchiveexplorer.cli;

import fr.craft.linkedinarchiveexplorer.application.SearchContentsService;
import fr.craft.linkedinarchiveexplorer.application.SearchResults;
import fr.craft.linkedinarchiveexplorer.domain.CaseSensitivity;
import fr.craft.linkedinarchiveexplorer.domain.ContentSource;
import fr.craft.linkedinarchiveexplorer.domain.SearchEngine;
import fr.craft.linkedinarchiveexplorer.domain.SearchTerm;
import fr.craft.linkedinarchiveexplorer.domain.WordScope;
import fr.craft.linkedinarchiveexplorer.infrastructure.ArchiveLocator;
import fr.craft.linkedinarchiveexplorer.infrastructure.ArticlesContentSource;
import fr.craft.linkedinarchiveexplorer.infrastructure.CommentsContentSource;
import fr.craft.linkedinarchiveexplorer.infrastructure.JdkArticleTextExtractor;
import fr.craft.linkedinarchiveexplorer.infrastructure.SharesContentSource;
import fr.craft.linkedinarchiveexplorer.infrastructure.ZipArchive;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** CLI entry point and composition root: parses arguments and wires the adapters. */
public final class Main {

  private static final Path DEFAULT_ARCHIVE_DIR = Path.of("data");

  public static void main(String[] args) {
    System.exit(new Main().run(args, System.out, System.err, styleByDefault()));
  }

  /** Colour and links are on by default in a real terminal, unless {@code NO_COLOR} is set. */
  private static boolean styleByDefault() {
    String noColor = System.getenv("NO_COLOR");
    if (noColor != null && !noColor.isEmpty()) {
      return false;
    }
    return isTerminal();
  }

  private static boolean isTerminal() {
    java.io.Console console = System.console();
    if (console == null) {
      return false;
    }
    try {
      // Java 22+: console() is non-null even when redirected; isTerminal() is authoritative.
      return (boolean) java.io.Console.class.getMethod("isTerminal").invoke(console);
    } catch (ReflectiveOperationException pre22) {
      // Java 17-21: a non-null console already means an attached terminal.
      return true;
    }
  }

  int run(String[] args, PrintStream out, PrintStream err, boolean defaultStyled) {
    Path archivePath = null;
    boolean noColor = false;
    boolean forceColor = false;
    CaseSensitivity caseSensitivity = CaseSensitivity.SENSITIVE;
    WordScope wordScope = WordScope.ANYWHERE;
    String term = null;

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      switch (arg) {
        case "--archive" -> {
          if (i + 1 >= args.length) {
            return usage(err);
          }
          archivePath = Path.of(args[++i]);
        }
        case "--no-color" -> noColor = true;
        case "--color" -> forceColor = true;
        case "--ignore-case" -> caseSensitivity = CaseSensitivity.INSENSITIVE;
        case "--word" -> wordScope = WordScope.WHOLE_WORD;
        default -> {
          if (isShortFlagBundle(arg)) {
            for (int f = 1; f < arg.length(); f++) {
              switch (arg.charAt(f)) {
                case 'i' -> caseSensitivity = CaseSensitivity.INSENSITIVE;
                case 'w' -> wordScope = WordScope.WHOLE_WORD;
              }
            }
          } else if (arg.startsWith("--") || term != null) {
            return usage(err);
          } else {
            term = arg;
          }
        }
      }
    }
    if (term == null || term.isBlank()) {
      return usage(err);
    }

    Path archive = archivePath != null ? archivePath : ArchiveLocator.mostRecent(DEFAULT_ARCHIVE_DIR).orElse(null);
    if (archive == null) {
      err.println("No archive found in " + DEFAULT_ARCHIVE_DIR + "/ (or use --archive <path>).");
      return 1;
    }
    if (!Files.isReadable(archive)) {
      err.println("Cannot read archive: " + archive);
      return 1;
    }
    out.println("Using archive: " + archive);

    boolean styled = !noColor && (forceColor || defaultStyled);
    try (ZipArchive zip = ZipArchive.open(archive)) {
      List<ContentSource> sources =
          List.of(
              new CommentsContentSource(zip),
              new SharesContentSource(zip),
              new ArticlesContentSource(zip, new JdkArticleTextExtractor()));
      SearchTerm searchTerm = new SearchTerm(term, caseSensitivity, wordScope);
      SearchResults results = new SearchContentsService(sources, new SearchEngine()).search(searchTerm);
      out.print(new TerminalRenderer(styled).render(searchTerm, results));
      return 0;
    } catch (RuntimeException failure) {
      err.println("Error: " + failure.getMessage());
      return 1;
    }
  }

  /** A single-dash cluster of known short flags, e.g. {@code -i}, {@code -w}, {@code -iw}. */
  private static boolean isShortFlagBundle(String arg) {
    if (arg.length() < 2 || arg.charAt(0) != '-' || arg.charAt(1) == '-') {
      return false;
    }
    for (int i = 1; i < arg.length(); i++) {
      if (arg.charAt(i) != 'i' && arg.charAt(i) != 'w') {
        return false;
      }
    }
    return true;
  }

  private int usage(PrintStream err) {
    err.println(
        "usage: linkedin-archive-explorer [--archive <path>] [--color|--no-color]"
            + " [-i|--ignore-case] [-w|--word] <term>");
    return 2;
  }
}
