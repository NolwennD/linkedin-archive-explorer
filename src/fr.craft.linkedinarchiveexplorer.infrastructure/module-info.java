module fr.craft.linkedinarchiveexplorer.infrastructure {
  requires fr.craft.linkedinarchiveexplorer.domain;

  // Reads no type from it: jdk.zipfs exports nothing, it only provides the
  // FileSystemProvider that ZipArchive reaches through FileSystems.newFileSystem. The
  // requires is here so that jlink, which follows requires and not ServiceLoader, keeps
  // it in the image — without it the image starts and dies on the first archive with
  // "Provider not found".
  requires jdk.zipfs;

  exports fr.craft.linkedinarchiveexplorer.infrastructure;
}
