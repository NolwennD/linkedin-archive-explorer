module fr.craft.linkedinarchiveexplorer.cli {
  requires fr.craft.linkedinarchiveexplorer.domain;
  requires fr.craft.linkedinarchiveexplorer.application;
  requires fr.craft.linkedinarchiveexplorer.launcher;

  exports fr.craft.linkedinarchiveexplorer.cli to fr.craft.linkedinarchiveexplorer.app;
}
