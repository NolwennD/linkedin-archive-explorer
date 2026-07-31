package fr.craft.linkedinarchiveexplorer.cli;

import fr.craft.linkedinarchiveexplorer.application.SearchResults;
import fr.craft.linkedinarchiveexplorer.domain.CaseSensitivity;
import fr.craft.linkedinarchiveexplorer.domain.SearchTerm;
import fr.craft.linkedinarchiveexplorer.domain.WordScope;
import fr.craft.linkedinarchiveexplorer.launcher.ArchiveUnavailableException;
import fr.craft.linkedinarchiveexplorer.launcher.Explorer;
import java.io.PrintStream;
import java.nio.file.Path;

/** CLI entry point: parses arguments, then renders what the {@link Explorer} finds. */
public final class Main {

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

    boolean styled = !noColor && (forceColor || defaultStyled);
    try (Explorer explorer = Explorer.open(archivePath)) {
      out.println("Using archive: " + explorer.archive());
      SearchTerm searchTerm = new SearchTerm(term, caseSensitivity, wordScope);
      SearchResults results = explorer.service().search(searchTerm);
      out.print(new TerminalRenderer(styled).render(searchTerm, results));
      return 0;
    } catch (ArchiveUnavailableException unavailable) {
      // Its message is already written for the user: no "Error: " prefix.
      err.println(unavailable.getMessage());
      return 1;
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
