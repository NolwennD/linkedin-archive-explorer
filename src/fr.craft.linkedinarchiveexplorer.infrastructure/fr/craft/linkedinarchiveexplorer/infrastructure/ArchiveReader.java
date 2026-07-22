package fr.craft.linkedinarchiveexplorer.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Reads text entries out of a LinkedIn export archive by matching their (archive-
 * relative) path. The seam lets content sources be tested against a fake reader.
 */
public interface ArchiveReader {

  Optional<String> readFirst(Predicate<String> nameMatches);

  List<String> readAll(Predicate<String> nameMatches);
}
