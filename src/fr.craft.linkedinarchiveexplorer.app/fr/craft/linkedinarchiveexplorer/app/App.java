package fr.craft.linkedinarchiveexplorer.app;

import fr.craft.linkedinarchiveexplorer.cli.Main;
import fr.craft.linkedinarchiveexplorer.web.WebMain;
import java.util.Arrays;
import java.util.List;

/**
 * The single entry point of the packaged program: decides which UI the arguments call for,
 * then hands them over untouched.
 *
 * <p>This decision used to live in the {@code linkedin-archive-explorer} launch script,
 * outside the module graph. A binary has no script, so it becomes compiled — and tested —
 * code. The module requires {@code cli} and {@code web} without introducing an edge
 * <em>between</em> them: they stay siblings, and a third UI would join here.
 */
public final class App {

  private static final String SERVE = "serve";

  private App() {}

  enum Target {
    CLI,
    SERVE
  }

  /** What to run, and with which arguments. */
  record Route(Target target, List<String> arguments) {}

  /**
   * Nothing at all, or {@code serve} first, opens the page; everything else goes to the
   * terminal.
   *
   * <p>The rule is deliberately blunt: it counts the arguments and compares the first, and
   * never looks at the rest. Guessing an intent from a known option — treating a lone
   * {@code --no-browser} as a request to serve — would mean teaching this module the
   * grammar of both UIs, duplicating what {@code Main} and {@code WebMain} already parse
   * and drifting from them at the first option added. A usage message is the lesser evil.
   */
  static Route route(String[] arguments) {
    if (arguments.length == 0) {
      return new Route(Target.SERVE, List.of());
    }
    if (SERVE.equals(arguments[0])) {
      return new Route(Target.SERVE, List.of(Arrays.copyOfRange(arguments, 1, arguments.length)));
    }
    return new Route(Target.CLI, List.of(arguments));
  }

  public static void main(String[] args) {
    Route route = route(args);
    String[] arguments = route.arguments().toArray(String[]::new);
    if (route.target() == Target.SERVE) {
      WebMain.main(arguments);
    } else {
      Main.main(arguments);
    }
  }
}
