package fr.craft.linkedinarchiveexplorer.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hands the URL to the system's own opener — {@code xdg-open}, {@code open} or
 * {@code start}.
 *
 * <p>Deliberately not {@code java.awt.Desktop.browse}: it would drag in the whole
 * {@code java.desktop} module, measured at <strong>+19 MB</strong> on the jlink image
 * (43 → 62 MB), nearly half the binary again to open a URL.
 */
final class SystemBrowser implements BrowserLauncher {

  private final List<String> opener;

  SystemBrowser() {
    this(openerFor(System.getProperty("os.name", "")));
  }

  /** The opener made explicit — the seam the tests use, so none of them opens a window. */
  SystemBrowser(List<String> opener) {
    this.opener = List.copyOf(opener);
  }

  /**
   * Anything that is not macOS or Windows gets {@code xdg-open}, the freedesktop
   * convention. A wrong guess costs an unopened window, never a failure.
   *
   * <p>The empty argument after {@code start} is not a typo: {@code start} reads a first
   * quoted argument as the window title, so the URL needs one in front of it.
   */
  static List<String> openerFor(String osName) {
    String system = osName.toLowerCase(Locale.ROOT);
    if (system.contains("mac")) {
      return List.of("open");
    }
    if (system.contains("windows")) {
      return List.of("cmd", "/c", "start", "");
    }
    return List.of("xdg-open");
  }

  /** The full command line for {@code url} on {@code osName}. */
  static List<String> commandFor(String osName, String url) {
    List<String> command = new ArrayList<>(openerFor(osName));
    command.add(url);
    return List.copyOf(command);
  }

  /**
   * Never fatal. No graphical session, no opener installed, a locked-down machine: the URL
   * has already been printed, and a server that refused to run because it could not open a
   * browser would be absurd.
   */
  @Override
  public void open(String url) {
    List<String> command = new ArrayList<>(opener);
    command.add(url);
    try {
      new ProcessBuilder(command).start();
    } catch (Exception unopened) {
      // Nothing to say that the printed URL does not already say.
    }
  }
}
