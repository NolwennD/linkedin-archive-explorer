package fr.craft.linkedinarchiveexplorer.web;

/** Makes a text safe to put in HTML, as content or as a quoted attribute value. */
final class HtmlEscaper {

  private HtmlEscaper() {}

  /** The ampersand goes first, or it would escape the escapes that follow. */
  static String escape(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}
