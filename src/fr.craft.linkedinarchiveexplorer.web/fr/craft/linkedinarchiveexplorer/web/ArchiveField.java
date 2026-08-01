package fr.craft.linkedinarchiveexplorer.web;

import java.util.List;

/**
 * What the page needs to draw its archive field: the path in use, the paths worth
 * suggesting, and the message to show when the last one did not open. Display data only —
 * no {@code Path}, no catalogue, so the renderer stays unable to open anything.
 *
 * @param suggestions the paths offered by the {@code <datalist>}, in the order to show.
 * @param value the path the field carries; empty when there is none to propose.
 * @param error the message to show above the field; empty when all is well.
 */
public record ArchiveField(List<String> suggestions, String value, String error) {

  public ArchiveField {
    if (suggestions == null || value == null || error == null) {
      throw new IllegalArgumentException("An archive field must have suggestions, a value and an error");
    }
    suggestions = List.copyOf(suggestions);
  }
}
