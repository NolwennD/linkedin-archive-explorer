package fr.craft.linkedinarchiveexplorer.domain;

/** One occurrence of the term, and the {@link ConnectionField} it was found in. */
public record ConnectionMatch(ConnectionField field, Excerpt excerpt) {

  public ConnectionMatch {
    if (field == null || excerpt == null) {
      throw new IllegalArgumentException("A connection match must have a field and an excerpt");
    }
  }
}
