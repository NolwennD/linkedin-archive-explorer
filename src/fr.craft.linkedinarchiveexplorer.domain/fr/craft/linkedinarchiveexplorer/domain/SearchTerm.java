package fr.craft.linkedinarchiveexplorer.domain;

public record SearchTerm(String value) {

  public SearchTerm {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("A search term must not be null or blank");
    }
  }
}
