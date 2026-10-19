package fr.craft.linkedinarchiveexplorer.infrastructure;

import fr.craft.linkedinarchiveexplorer.domain.Body;
import fr.craft.linkedinarchiveexplorer.domain.ContentSource;
import fr.craft.linkedinarchiveexplorer.domain.Correspondent;
import fr.craft.linkedinarchiveexplorer.domain.Message;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads the private messages from {@code messages.csv}. The export carries no link to a
 * single message, only to its conversation, so every message of a conversation links to the
 * whole thread. Each is filed under the other person of the conversation; the user is the
 * profile found in the most conversations (the export never says who owns it).
 */
public final class MessagesContentSource implements ContentSource {

  private static final String THREAD = "https://www.linkedin.com/messaging/thread/";

  private final ArchiveReader archive;

  public MessagesContentSource(ArchiveReader archive) {
    this.archive = archive;
  }

  @Override
  public List<Message> load() {
    return archive
        .readFirst(name -> name.equals("messages.csv"))
        .map(this::toContents)
        .orElse(List.of());
  }

  private static final List<String> COLUMNS =
      List.of("CONVERSATION ID", "CONVERSATION TITLE", "FROM", "SENDER PROFILE URL", "TO", "RECIPIENT PROFILE URLS", "DATE", "CONTENT");

  private List<Message> toContents(String csv) {
    List<CsvRow> rows = Csv.parse(csv);
    // A file of another shape must not stop the comments, posts and articles from loading.
    if (rows.isEmpty() || !rows.getFirst().headers().containsAll(COLUMNS)) {
      return List.of();
    }
    String me = userProfile(rows);
    Map<String, SequencedSet<Correspondent>> others = new LinkedHashMap<>();
    for (CsvRow row : rows) {
      SequencedSet<Correspondent> participants = others.computeIfAbsent(row.get("CONVERSATION ID"), id -> new LinkedHashSet<>());
      participate(participants, me, List.of(row.get("FROM")), List.of(row.get("SENDER PROFILE URL")));
      participate(participants, me, split(row.get("TO")), split(row.get("RECIPIENT PROFILE URLS")));
    }
    List<Message> messages = new ArrayList<>();
    for (CsvRow row : rows) {
      String id = row.get("CONVERSATION ID");
      if (!id.isBlank() && !row.get("CONTENT").isBlank()) {
        messages.add(
            new Message(
                LinkedInDates.parse(row.get("DATE")),
                THREAD + id + "/",
                new Body(row.get("CONTENT")),
                filedUnder(others.get(id), row.get("CONVERSATION TITLE"))));
      }
    }
    return messages;
  }

  /** The profile present in the most conversations — the user is in all of them. */
  private static String userProfile(List<CsvRow> rows) {
    Map<String, Set<String>> conversationsByProfile = new HashMap<>();
    for (CsvRow row : rows) {
      Stream.concat(Stream.of(row.get("SENDER PROFILE URL")), split(row.get("RECIPIENT PROFILE URLS")).stream())
          .map(String::strip)
          .filter(url -> !url.isBlank())
          .forEach(url -> conversationsByProfile.computeIfAbsent(url, key -> new HashSet<>()).add(row.get("CONVERSATION ID")));
    }
    List<Map.Entry<String, Set<String>>> mostPresentFirst =
        conversationsByProfile.entrySet().stream()
            .sorted(Comparator.comparingInt((Map.Entry<String, Set<String>> entry) -> entry.getValue().size()).reversed())
            .toList();
    // A tie at the top leaves the user unknown rather than guessed (e.g. a single conversation).
    boolean tied =
        mostPresentFirst.size() > 1
            && mostPresentFirst.getFirst().getValue().size() == mostPresentFirst.get(1).getValue().size();
    return mostPresentFirst.isEmpty() || tied ? "" : mostPresentFirst.getFirst().getKey();
  }

  /** Names and profile links are comma-separated, in the same order. */
  private static List<String> split(String commaSeparated) {
    return Arrays.asList(commaSeparated.split(",", -1));
  }

  // ponytail: if a name itself contains a comma the two lists no longer line up; the whole
  // field is then taken as one participant instead of guessing where to cut.
  private static void participate(SequencedSet<Correspondent> participants, String me, List<String> names, List<String> urls) {
    if (names.size() != urls.size()) {
      names = List.of(String.join(",", names));
      urls = List.of("");
    }
    for (int i = 0; i < names.size(); i++) {
      String name = names.get(i).strip();
      String url = urls.get(i).strip();
      if (!name.isBlank() && (me.isEmpty() || !url.equals(me))) {
        participants.add(new Correspondent(name, url.isBlank() ? Optional.empty() : Optional.of(url)));
      }
    }
  }

  /** The other person when there is exactly one; else the conversation title, else everyone. */
  private static Correspondent filedUnder(SequencedSet<Correspondent> others, String title) {
    if (others.size() == 1) {
      return others.getFirst();
    }
    String everyone = others.stream().map(Correspondent::name).collect(Collectors.joining(", "));
    String name = !title.isBlank() ? title : !everyone.isEmpty() ? everyone : "(unknown)";
    return new Correspondent(name, Optional.empty());
  }
}
