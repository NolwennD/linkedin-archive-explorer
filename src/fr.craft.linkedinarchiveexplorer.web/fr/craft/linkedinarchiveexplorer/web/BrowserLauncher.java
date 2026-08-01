package fr.craft.linkedinarchiveexplorer.web;

/**
 * Opens a page in whatever browser the user has. A port, so that the server can be started
 * in a test without a window appearing.
 */
interface BrowserLauncher {

  /** Opens {@code url}, or gives up quietly — never throws (see {@link SystemBrowser}). */
  void open(String url);

  /** What {@code --no-browser} installs. */
  BrowserLauncher NONE = url -> {};
}
