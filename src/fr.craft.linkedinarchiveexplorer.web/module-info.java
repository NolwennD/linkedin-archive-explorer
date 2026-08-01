module fr.craft.linkedinarchiveexplorer.web {
  requires fr.craft.linkedinarchiveexplorer.domain;
  requires fr.craft.linkedinarchiveexplorer.application;
  requires fr.craft.linkedinarchiveexplorer.launcher;
  requires jdk.httpserver;

  exports fr.craft.linkedinarchiveexplorer.web to fr.craft.linkedinarchiveexplorer.app;
}
