package fr.craft.linkedinarchiveexplorer.infrastructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/** In-memory {@link ArchiveReader} for tests: entry name -> content. */
final class FakeArchiveReader implements ArchiveReader {

  private final Map<String, String> entries = new LinkedHashMap<>();

  FakeArchiveReader with(String name, String content) {
    entries.put(name, content);
    return this;
  }

  @Override
  public Optional<String> readFirst(Predicate<String> nameMatches) {
    return entries.entrySet().stream()
        .filter(entry -> nameMatches.test(entry.getKey()))
        .map(Map.Entry::getValue)
        .findFirst();
  }

  @Override
  public List<String> readAll(Predicate<String> nameMatches) {
    return entries.entrySet().stream()
        .filter(entry -> nameMatches.test(entry.getKey()))
        .map(Map.Entry::getValue)
        .toList();
  }
}
