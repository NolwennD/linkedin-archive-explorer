package fr.craft.linkedinarchiveexplorer.domain;

/** The searchable text of a piece of content. */
public record Body(String value) {

  public Body {
    if (value == null) {
      throw new IllegalArgumentException("A body text must not be null");
    }
  }
}
