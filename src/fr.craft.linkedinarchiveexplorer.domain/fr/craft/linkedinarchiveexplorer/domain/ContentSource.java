package fr.craft.linkedinarchiveexplorer.domain;

import java.util.List;

/** Port: a source of {@link Content} to be searched (comments, posts, articles…). */
public interface ContentSource {

  List<Content> load();
}
