module fr.craft.linkedinarchiveexplorer.launcher {
  requires fr.craft.linkedinarchiveexplorer.domain;
  requires transitive fr.craft.linkedinarchiveexplorer.application;
  requires fr.craft.linkedinarchiveexplorer.infrastructure;

  exports fr.craft.linkedinarchiveexplorer.launcher;
}
