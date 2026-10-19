package fr.craft.linkedinarchiveexplorer.application;

import fr.craft.linkedinarchiveexplorer.domain.ConnectionHit;
import fr.craft.linkedinarchiveexplorer.domain.ContentHit;
import fr.craft.linkedinarchiveexplorer.domain.MessageHit;
import fr.craft.linkedinarchiveexplorer.domain.SearchHit;

/**
 * A section of the results: what the hits are grouped under and the order of the groups.
 * It is about presenting a search, not about what the items are, hence not in the domain.
 */
public enum Section {
  ARTICLE,
  POST,
  COMMENT,
  MESSAGE,
  CONNECTION;

  public static Section of(SearchHit hit) {
    return switch (hit) {
      case ContentHit content ->
          switch (content.content().type()) {
            case ARTICLE -> ARTICLE;
            case POST -> POST;
            case COMMENT -> COMMENT;
          };
      case MessageHit message -> MESSAGE;
      case ConnectionHit connection -> CONNECTION;
    };
  }
}
