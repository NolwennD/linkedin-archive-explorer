package fr.craft.linkedinarchiveexplorer.domain;

import java.util.Optional;

/**
 * The person (or, for a group conversation, the title or list of people) a private message
 * is filed under. The profile link is empty when there is no single person to link to.
 */
public record Correspondent(String name, Optional<String> profileUrl) {

  public Correspondent {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("A correspondent must have a name");
    }
    if (profileUrl == null) {
      throw new IllegalArgumentException("A correspondent profile url must be an Optional, not null");
    }
  }
}
