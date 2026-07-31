package fr.craft.linkedinarchiveexplorer.launcher;

/**
 * No archive to search: none found, or the one given cannot be read. Its message is
 * written for the user — the caller prints it as is.
 */
public final class ArchiveUnavailableException extends RuntimeException {

  ArchiveUnavailableException(String message) {
    super(message);
  }
}
