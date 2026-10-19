package fr.craft.linkedinarchiveexplorer.domain;

import java.util.List;

/**
 * Port: a source of {@link Searchable} items to be searched (comments, posts, articles,
 * messages…). An adapter narrows the return type to the one kind it reads.
 */
public interface ContentSource {

  List<? extends Searchable> load();
}
