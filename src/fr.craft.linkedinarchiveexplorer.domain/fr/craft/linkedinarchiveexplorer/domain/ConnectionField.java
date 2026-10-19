package fr.craft.linkedinarchiveexplorer.domain;

import java.util.function.Function;

/** A part of a {@link Connection} the search looks into, as the user would name it. */
public enum ConnectionField {
  NAME("Name", Connection::fullName),
  COMPANY("Company", Connection::company),
  POSITION("Position", Connection::position);

  private final String label;
  private final Function<Connection, String> value;

  ConnectionField(String label, Function<Connection, String> value) {
    this.label = label;
    this.value = value;
  }

  public String label() {
    return label;
  }

  String valueOf(Connection connection) {
    return value.apply(connection);
  }
}
