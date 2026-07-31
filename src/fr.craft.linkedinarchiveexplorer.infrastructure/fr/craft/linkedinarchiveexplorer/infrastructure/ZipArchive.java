package fr.craft.linkedinarchiveexplorer.infrastructure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Reads a {@code .zip} export in place through the ZIP {@link FileSystem}. */
public final class ZipArchive implements ArchiveReader, AutoCloseable {

  private final FileSystem fileSystem;

  private ZipArchive(FileSystem fileSystem) {
    this.fileSystem = fileSystem;
  }

  public static ZipArchive open(Path zip) {
    try {
      return new ZipArchive(FileSystems.newFileSystem(zip));
    } catch (IOException e) {
      // Relay the JDK's diagnosis ("zip END header not found", …) rather than restate it:
      // it alone distinguishes a truncated archive from a file that is not a zip at all.
      throw new UncheckedIOException("Cannot open archive: " + zip + " — " + e.getMessage(), e);
    }
  }

  @Override
  public Optional<String> readFirst(Predicate<String> nameMatches) {
    return matching(nameMatches).stream().findFirst().map(this::read);
  }

  @Override
  public List<String> readAll(Predicate<String> nameMatches) {
    return matching(nameMatches).stream().map(this::read).toList();
  }

  private List<Path> matching(Predicate<String> nameMatches) {
    List<Path> files = new ArrayList<>();
    for (Path root : fileSystem.getRootDirectories()) {
      try (var walk = Files.walk(root)) {
        walk.filter(Files::isRegularFile)
            .filter(path -> nameMatches.test(relativeName(path)))
            .forEach(files::add);
      } catch (IOException e) {
        throw new UncheckedIOException("Cannot list archive entries", e);
      }
    }
    return files;
  }

  private String relativeName(Path path) {
    String name = path.toString();
    return name.startsWith("/") ? name.substring(1) : name;
  }

  private String read(Path path) {
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read archive entry: " + path, e);
    }
  }

  @Override
  public void close() {
    try {
      fileSystem.close();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot close archive", e);
    }
  }
}
