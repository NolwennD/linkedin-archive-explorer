# Tests de performance JMH — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mesurer le pipeline complet de l'outil (dézip → CSV → HTML → recherche → rendu) sur l'archive réelle de `data/`, avec un chiffre bout-en-bout et une décomposition par étage.

**Architecture:** Tout le harnais vit dans un dossier `benchmark/` **gitignoré**, avec son propre lanceur JEP 330 sur le modèle de `bin/test`. JMH est utilisé comme processeur d'annotations `javac` + un `main()`, sans aucun build tool. Les benchmarks sont compilés sur le classpath (comme les tests), ce qui donne accès au package-private `Main.run`.

**Tech Stack:** JDK 17+ (`javac`, `java`, `ToolProvider`), JMH 1.37, Maven en téléchargeur one-off uniquement.

**Spec de référence :** [`docs/superpowers/specs/2026-07-28-jmh-benchmarks-design.md`](../specs/2026-07-28-jmh-benchmarks-design.md)

## Global Constraints

- **Runtime JDK-only** : rien de ce plan n'entre dans `dist/linkedin-explorer.jar`. Le code de `src/` n'est **pas modifié**.
- **Zéro build tool** : Maven est utilisé exactement une fois, pour `dependency:copy-dependencies`. Jamais pour compiler, jamais pour lancer.
- **Java 17 est le plancher** : le lanceur est déclaré `java --source 17`, comme `bin/test` et `bin/build`.
- **`benchmark/` est intégralement gitignoré** : un seul commit dans tout ce plan (Task 1). Les tâches 2 et 3 ne produisent **rien de committable** — c'est voulu, ne pas forcer de `git add`.
- **Les scripts se lancent depuis la racine du projet** : tous les chemins sont relatifs à la racine, comme `bin/test`.
- **Imports explicites, pas de wildcard** — convention du dépôt.
- **Text blocks uniquement s'ils le méritent** : littéral multi-ligne ou contenant des caractères pénibles à échapper (règle du `CLAUDE.md`).

## File Structure

| Fichier | Responsabilité | Versionné ? |
|---|---|---|
| `.gitignore` | Ajoute la ligne `benchmark/` | ✅ oui |
| `benchmark/pom.xml` | Manifeste de téléchargement des jars. Rien d'autre. | ❌ |
| `benchmark/bench` | Lanceur JEP 330 : compile la prod, compile les benchmarks, lance JMH | ❌ |
| `benchmark/bench.cmd` | Shim Windows d'une ligne | ❌ |
| `benchmark/src/fr/craft/linkedinarchiveexplorer/cli/PipelineBenchmark.java` | Les 7 mesures | ❌ |
| `docs/superpowers/specs/2026-07-28-jmh-benchmarks-design.md` | Reçoit une §9 « résultats de référence » en Task 4 | ✅ oui |

**Note sur la duplication :** `benchmark/bench` recopie cinq helpers de `bin/test` (`runTool`, `javaFiles`, `concat`, `deleteRecursively`, `moduleOutputClasspath`). C'est assumé : `bin/test` est versionné, `benchmark/bench` ne l'est pas — les factoriser créerait une dépendance d'un fichier committé vers un fichier absent au clone.

---

### Task 1 : Ignorer `benchmark/` et télécharger les jars JMH

**Files:**
- Modify: `.gitignore`
- Create: `benchmark/pom.xml`
- Create (téléchargé) : `benchmark/lib/*.jar`

**Interfaces:**
- Consumes: rien.
- Produces: `benchmark/lib/` contenant 4 jars, dont `jmh-core-1.37.jar` et `jmh-generator-annprocess-1.37.jar`. Task 2 construit son classpath en listant ce répertoire.

- [ ] **Step 1 : Ajouter la ligne au `.gitignore`**

Ajouter à la fin de `.gitignore` :

```gitignore

# Harnais de performance JMH : entièrement hors dépôt (jars, sources, résultats).
# La recette pour le remonter après un clone est dans
# docs/superpowers/specs/2026-07-28-jmh-benchmarks-design.md
benchmark/
```

- [ ] **Step 2 : Vérifier que l'ignore fonctionne avant de créer quoi que ce soit**

```bash
mkdir -p benchmark && touch benchmark/probe && git check-ignore -v benchmark/probe && rm benchmark/probe
```

Attendu : une ligne `.gitignore:<numéro>:benchmark/	benchmark/probe`. Si la commande ne sort rien, l'ignore ne prend pas — corriger avant d'aller plus loin.

- [ ] **Step 3 : Committer (le seul commit de ce plan)**

```bash
git add .gitignore && git commit -m "chore: ignorer le harnais de performance benchmark/"
```

- [ ] **Step 4 : Écrire le manifeste de téléchargement**

Créer `benchmark/pom.xml` :

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
  MANIFESTE DE TÉLÉCHARGEMENT UNIQUEMENT.

  Ce POM n'est jamais utilisé pour compiler ni pour lancer quoi que ce soit — la
  contrainte « zéro build tool » du projet reste entière. Il ne sert qu'à résoudre les
  jars JMH et leurs transitives (jopt-simple, commons-math3) vers benchmark/lib/ :

    mvn -q -f benchmark/pom.xml dependency:copy-dependencies -DoutputDirectory=lib
-->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>fr.craft</groupId>
  <artifactId>linkedin-archive-explorer-benchmark-deps</artifactId>
  <version>0</version>
  <packaging>pom</packaging>

  <properties>
    <jmh.version>1.37</jmh.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.openjdk.jmh</groupId>
      <artifactId>jmh-core</artifactId>
      <version>${jmh.version}</version>
    </dependency>
    <dependency>
      <groupId>org.openjdk.jmh</groupId>
      <artifactId>jmh-generator-annprocess</artifactId>
      <version>${jmh.version}</version>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 5 : Télécharger les jars**

⚠️ **Cette étape accède au réseau et écrit des jars sur le disque. Demander l'accord explicite avant de la lancer.**

```bash
mvn -q -f benchmark/pom.xml dependency:copy-dependencies -DoutputDirectory=lib
```

`-DoutputDirectory=lib` est relatif au `basedir` du POM, donc les jars atterrissent dans `benchmark/lib/`.

- [ ] **Step 6 : Vérifier le contenu de `benchmark/lib/`**

```bash
ls -1 benchmark/lib/
```

Attendu : exactement quatre jars —

```
commons-math3-3.6.1.jar
jmh-core-1.37.jar
jmh-generator-annprocess-1.37.jar
jopt-simple-5.0.4.jar
```

Les numéros de version des deux transitives peuvent différer selon ce que résout JMH 1.37 : ce n'est pas un problème, le lanceur liste le répertoire au lieu de nommer les fichiers. En revanche, **quatre jars est le compte attendu** — s'il y en a moins, `jmh-core` n'a pas tiré ses transitives et la compilation de Task 2 échouera.

- [ ] **Step 7 : Vérifier que le dépôt est resté propre**

```bash
git status --short
```

Attendu : **aucune sortie**. Si `benchmark/` apparaît, l'ignore du Step 1 n'a pas pris.

---

### Task 2 : Le lanceur `benchmark/bench` et un premier benchmark bout-en-bout

Cette tâche valide la chaîne d'outillage entière — traitement d'annotations, génération de `META-INF/BenchmarkList`, classpath, fork des JVM de mesure — sur **un seul** benchmark. C'est le point de risque du plan, donc il est levé en premier.

**Files:**
- Create: `benchmark/bench`
- Create: `benchmark/bench.cmd`
- Create: `benchmark/src/fr/craft/linkedinarchiveexplorer/cli/PipelineBenchmark.java`

**Interfaces:**
- Consumes: `benchmark/lib/*.jar` (Task 1).
- Produces:
  - `./benchmark/bench [options JMH…]` — compile puis lance, en transmettant ses arguments à JMH.
  - `benchmark/out/` — classes compilées + `META-INF/BenchmarkList`.
  - `benchmark/results/<yyyy-MM-dd-HHmmss>.json` — rapport JMH.
  - La classe `fr.craft.linkedinarchiveexplorer.cli.PipelineBenchmark`, que Task 3 étend.

- [ ] **Step 1 : Choisir le terme mesuré**

Le terme doit avoir des occurrences dans l'archive, sinon `search` et `render` mesureraient du vide. Vérifier :

```bash
./linkedin-archive-explorer --no-color craft | head -20
```

Attendu : au moins un résultat. Si la sortie est vide, essayer un autre mot fréquent (`java`, `équipe`, `2024`…) et retenir celui qui sort des résultats. **Reporter le mot choisi dans la constante `TERM` du Step 3.**

- [ ] **Step 2 : Écrire le lanceur**

Créer `benchmark/bench` :

```java
#!/usr/bin/env -S java --source 17

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Pure-JDK benchmark runner — a JEP 330 single-file source-code program (run as
 * {@code ./benchmark/bench}, no build tool involved). Compiles the modular production
 * tree (same command as {@code bin/test}, so the architecture is checked before any
 * measurement), then compiles the benchmarks on the classpath with the JMH annotation
 * processor and hands over to {@code org.openjdk.jmh.Main}. Run from the project root.
 *
 * <p>Every argument is forwarded to JMH: {@code ./benchmark/bench -f 1 -wi 1 -i 3} for a
 * quick round-trip, {@code ./benchmark/bench endToEnd} to run a single benchmark.
 */
public final class Bench {

  private static final Path ROOT = Path.of("benchmark");
  private static final Path LIB = ROOT.resolve("lib");
  private static final Path SRC = ROOT.resolve("src");
  private static final Path OUT = ROOT.resolve("out");
  private static final Path RESULTS = ROOT.resolve("results");

  public static void main(String[] args) throws IOException, InterruptedException {
    String jmh = jmhJars();

    deleteRecursively(OUT);
    deleteRecursively(Path.of("out"));
    Files.createDirectories(OUT);
    Files.createDirectories(RESULTS);

    // 1. Production compile as modules — identical to bin/test, so a forbidden
    //    inter-module dependency fails here rather than mid-campaign.
    runTool("javac", concat(List.of("--module-source-path", "src", "-d", "out"), javaFiles("src")));

    String moduleCp = moduleOutputClasspath();

    // 2. Benchmarks compile on the classpath (so they can reach package-private members,
    //    like the tests do). The annotation processor generates the benchmark classes and
    //    the META-INF/BenchmarkList resource into -d.
    runTool("javac", concat(
        List.of(
            "-cp", jmh + File.pathSeparator + moduleCp,
            "-processorpath", jmh,
            "-d", OUT.toString()),
        javaFiles(SRC.toString())));

    // 3. Hand over to JMH. It forks its own measurement JVMs, rebuilding their classpath
    //    from java.class.path — a classpath of directories is enough, no uber-jar needed.
    List<String> command = new ArrayList<>(List.of(
        "java",
        "-cp", OUT + File.pathSeparator + jmh + File.pathSeparator + moduleCp,
        "org.openjdk.jmh.Main"));
    List<String> forwarded = List.of(args);
    if (!forwarded.contains("-rf") && !forwarded.contains("-rff")) {
      command.addAll(List.of("-rf", "json", "-rff", RESULTS.resolve(timestamp() + ".json").toString()));
    }
    command.addAll(forwarded);

    System.exit(new ProcessBuilder(command).inheritIO().start().waitFor());
  }

  /** Every jar in benchmark/lib, joined — the JMH runtime, its processor and transitives. */
  private static String jmhJars() throws IOException {
    List<String> jars = List.of();
    if (Files.isDirectory(LIB)) {
      try (Stream<Path> paths = Files.list(LIB)) {
        jars = paths.map(Path::toString).filter(p -> p.endsWith(".jar")).sorted().toList();
      }
    }
    if (jars.isEmpty()) {
      System.err.println("""
          No JMH jar found in benchmark/lib/. Download them once with:
            mvn -q -f benchmark/pom.xml dependency:copy-dependencies -DoutputDirectory=lib""");
      System.exit(1);
    }
    return String.join(File.pathSeparator, jars);
  }

  private static String timestamp() {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss").format(LocalDateTime.now());
  }

  /** Colon/semicolon-joined list of every compiled module output directory under out/. */
  private static String moduleOutputClasspath() throws IOException {
    try (Stream<Path> dirs = Files.list(Path.of("out"))) {
      return dirs.filter(Files::isDirectory).map(Path::toString)
          .collect(Collectors.joining(File.pathSeparator));
    }
  }

  private static void runTool(String name, List<String> arguments) {
    ToolProvider tool = ToolProvider.findFirst(name)
        .orElseThrow(() -> new IllegalStateException("JDK tool not found: " + name));
    int status = tool.run(System.out, System.err, arguments.toArray(String[]::new));
    if (status != 0) {
      System.err.println(name + " failed (exit " + status + ")");
      System.exit(status);
    }
  }

  private static List<String> javaFiles(String dir) throws IOException {
    try (Stream<Path> paths = Files.walk(Path.of(dir))) {
      return paths.filter(p -> p.toString().endsWith(".java")).map(Path::toString).sorted().toList();
    }
  }

  private static List<String> concat(List<String> head, List<String> tail) {
    List<String> all = new ArrayList<>(head);
    all.addAll(tail);
    return all;
  }

  private static void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(root)) {
      paths.sorted(Comparator.reverseOrder()).forEach(p -> {
        try {
          Files.delete(p);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    }
  }
}
```

Rendre exécutable :

```bash
chmod +x benchmark/bench
```

- [ ] **Step 3 : Écrire le shim Windows**

Créer `benchmark/bench.cmd` (une ligne, terminaisons CRLF conformément au `.gitattributes` — même si le fichier n'est pas versionné, autant rester cohérent) :

```bat
@cd /d "%~dp0.." && java --source 17 benchmark\bench %*
```

- [ ] **Step 4 : Écrire le benchmark bout-en-bout, seul**

Créer `benchmark/src/fr/craft/linkedinarchiveexplorer/cli/PipelineBenchmark.java`. Le package `cli` est imposé : `Main.run` est package-private, et la compilation classpath donne le même accès qu'aux tests.

**Remplacer la valeur de `TERM` par le mot retenu au Step 1.**

```java
package fr.craft.linkedinarchiveexplorer.cli;

import fr.craft.linkedinarchiveexplorer.infrastructure.ArchiveLocator;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Macro benchmarks of the whole pipeline, measured on the real archive in data/.
 * Lives in the cli package because Main.run is package-private.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(2)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 5)
public class PipelineBenchmark {

  private static final String TERM = "craft";
  private static final Path ARCHIVE_DIR = Path.of("data");

  private Path archive;
  private PrintStream discard;

  @Setup(Level.Trial)
  public void setUp() {
    archive = ArchiveLocator.mostRecent(ARCHIVE_DIR).orElseThrow(() ->
        new IllegalStateException("No .zip archive in " + ARCHIVE_DIR.toAbsolutePath()));
    discard = new PrintStream(OutputStream.nullOutputStream());
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    discard.close();
  }

  /** The user-visible run: open, load everything, search, render. Output goes nowhere. */
  @Benchmark
  public int endToEnd() {
    return new Main().run(
        new String[] {"--archive", archive.toString(), "--no-color", TERM},
        discard, discard, false);
  }
}
```

- [ ] **Step 5 : Vérifier le message d'erreur quand les jars manquent**

```bash
mv benchmark/lib benchmark/lib.off && ./benchmark/bench; mv benchmark/lib.off benchmark/lib
```

Attendu : sortie sur stderr `No JMH jar found in benchmark/lib/.` suivie de la commande `mvn`, et code de sortie 1. Le lanceur ne doit **pas** partir en `NoSuchFileException`.

- [ ] **Step 6 : Lancer le benchmark en mode rapide**

```bash
./benchmark/bench -f 1 -wi 1 -i 2 -w 2 -r 2 endToEnd
```

(`-w`/`-r` = durée d'une itération de chauffe / de mesure, ici 2 s, pour un aller-retour d'environ 10 s.)

Attendu, dans cet ordre :
1. les deux compilations passent sans erreur ;
2. JMH affiche `# JMH version: 1.37`, `# Benchmark: fr.craft.linkedinarchiveexplorer.cli.PipelineBenchmark.endToEnd` ;
3. un tableau final du type

```
Benchmark                    Mode  Cnt   Score   Error  Units
PipelineBenchmark.endToEnd   avgt    2  xx,xxx          ms/op
```

**Si `Unable to find the resource: /META-INF/BenchmarkList`** : le processeur d'annotations n'a pas tourné — vérifier que `-processorpath` pointe bien sur les jars et que `jmh-generator-annprocess` est présent dans `benchmark/lib/`.

**Si JMH refuse de démarrer sur le JDK 26** de `mise.toml` : relancer sous le JDK 17 plancher, par exemple

```bash
JAVA_HOME=$(mise where java@17) ./benchmark/bench -f 1 -wi 1 -i 2 endToEnd
```

et noter le repli pour la Task 4.

- [ ] **Step 7 : Vérifier que le rapport JSON a été écrit**

```bash
ls -1 benchmark/results/
```

Attendu : un fichier `<yyyy-MM-dd-HHmmss>.json` non vide.

- [ ] **Step 8 : Vérifier que le dépôt est resté propre**

```bash
git status --short
```

Attendu : **aucune sortie**. Pas de commit pour cette tâche — tout est gitignoré, c'est le comportement recherché.

---

### Task 3 : Les six benchmarks par étage

**Files:**
- Modify: `benchmark/src/fr/craft/linkedinarchiveexplorer/cli/PipelineBenchmark.java` (remplacement intégral)

**Interfaces:**
- Consumes: le lanceur et la classe de Task 2.
- Produces: sept `@Benchmark` — `endToEnd`, `openArchive`, `loadComments`, `loadShares`, `loadArticles`, `search`, `render`.

- [ ] **Step 1 : Remplacer intégralement `PipelineBenchmark.java`**

**Conserver la valeur de `TERM` retenue en Task 2 Step 1.**

```java
package fr.craft.linkedinarchiveexplorer.cli;

import fr.craft.linkedinarchiveexplorer.application.SearchResults;
import fr.craft.linkedinarchiveexplorer.domain.Content;
import fr.craft.linkedinarchiveexplorer.domain.ContentSource;
import fr.craft.linkedinarchiveexplorer.domain.SearchEngine;
import fr.craft.linkedinarchiveexplorer.domain.SearchHit;
import fr.craft.linkedinarchiveexplorer.domain.SearchTerm;
import fr.craft.linkedinarchiveexplorer.infrastructure.ArchiveLocator;
import fr.craft.linkedinarchiveexplorer.infrastructure.ArticlesContentSource;
import fr.craft.linkedinarchiveexplorer.infrastructure.CommentsContentSource;
import fr.craft.linkedinarchiveexplorer.infrastructure.JdkArticleTextExtractor;
import fr.craft.linkedinarchiveexplorer.infrastructure.SharesContentSource;
import fr.craft.linkedinarchiveexplorer.infrastructure.ZipArchive;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Macro benchmarks of the whole pipeline, measured on the real archive in data/:
 * one end-to-end figure plus a stage-by-stage breakdown. Lives in the cli package
 * because Main.run is package-private — the same classpath trick the tests use.
 *
 * <p>Every method returns its value so JMH consumes it and the JIT cannot fold the
 * work away as dead code.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(2)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 5)
public class PipelineBenchmark {

  private static final String TERM = "craft";
  private static final Path ARCHIVE_DIR = Path.of("data");

  private Path archive;
  private SearchTerm term;
  private PrintStream discard;

  private ZipArchive zip;
  private ContentSource comments;
  private ContentSource shares;
  private ContentSource articles;
  private List<Content> contents;
  private SearchResults results;

  @Setup(Level.Trial)
  public void setUp() {
    archive = ArchiveLocator.mostRecent(ARCHIVE_DIR).orElseThrow(() ->
        new IllegalStateException("No .zip archive in " + ARCHIVE_DIR.toAbsolutePath()));
    term = SearchTerm.literal(TERM);
    discard = new PrintStream(OutputStream.nullOutputStream());

    zip = ZipArchive.open(archive);
    comments = new CommentsContentSource(zip);
    shares = new SharesContentSource(zip);
    articles = new ArticlesContentSource(zip, new JdkArticleTextExtractor());

    contents = List.of(comments, shares, articles).stream()
        .flatMap(source -> source.load().stream())
        .toList();
    results = SearchResults.from(new SearchEngine().search(term, contents));

    // Fail loudly rather than measure emptiness.
    if (contents.isEmpty()) {
      throw new IllegalStateException("Archive loaded no content at all: " + archive);
    }
    if (results.groups().isEmpty()) {
      throw new IllegalStateException(
          "Term '" + TERM + "' matches nothing — pick a term present in the archive");
    }
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    zip.close();
    discard.close();
  }

  /** The user-visible run: open, load everything, search, render. Output goes nowhere. */
  @Benchmark
  public int endToEnd() {
    return new Main().run(
        new String[] {"--archive", archive.toString(), "--no-color", TERM},
        discard, discard, false);
  }

  /** Opening alone: the zip central directory is read, no entry is inflated yet. */
  @Benchmark
  public ZipArchive openArchive() {
    try (ZipArchive opened = ZipArchive.open(archive)) {
      return opened; // returned closed, purely so JMH consumes the result
    }
  }

  @Benchmark
  public List<Content> loadComments() {
    return comments.load();
  }

  @Benchmark
  public List<Content> loadShares() {
    return shares.load();
  }

  /**
   * CSV plus HTML extraction through JdkArticleTextExtractor. Its rank among the load*
   * benchmarks tracks the archive's article count, not the per-article cost.
   */
  @Benchmark
  public List<Content> loadArticles() {
    return articles.load();
  }

  @Benchmark
  public List<SearchHit> search() {
    return new SearchEngine().search(term, contents);
  }

  @Benchmark
  public String render() {
    return new TerminalRenderer(false).render(term, results);
  }
}
```

- [ ] **Step 2 : Vérifier que les sept benchmarks sont découverts**

```bash
./benchmark/bench -l
```

Attendu : les sept noms listés, tous préfixés `fr.craft.linkedinarchiveexplorer.cli.PipelineBenchmark.` —
`endToEnd`, `loadArticles`, `loadComments`, `loadShares`, `openArchive`, `render`, `search`.

- [ ] **Step 3 : Passe rapide sur les sept**

```bash
./benchmark/bench -f 1 -wi 1 -i 2 -w 2 -r 2
```

Attendu : un tableau final à sept lignes, chacune avec un `Score` numérique et l'unité `ms/op`. Aucune ligne ne doit afficher `NaN` ni `≈ 10⁻⁶`.

Contrôles de cohérence — si l'un échoue, la mesure est fausse et il faut comprendre pourquoi avant la Task 4 :
- `endToEnd` doit être **le plus lent** de tous ;
- `openArchive` doit être nettement plus rapide que n'importe quel `load*` ;
- `search` et `render` opèrent sur des données déjà en mémoire : ils doivent être très inférieurs à la somme des `load*`.

- [ ] **Step 4 : Vérifier que le dépôt est resté propre**

```bash
git status --short
```

Attendu : **aucune sortie**.

---

### Task 4 : Campagne de référence et consignation des chiffres

**Files:**
- Modify: `docs/superpowers/specs/2026-07-28-jmh-benchmarks-design.md` (ajout d'une §9)

**Interfaces:**
- Consumes: les sept benchmarks de Task 3.
- Produces: une §9 « Résultats de référence » datée dans le spec — la seule trace versionnée des mesures.

- [ ] **Step 1 : Lancer la campagne complète**

Environ neuf minutes. Fermer les autres applications gourmandes ; brancher la machine sur secteur si c'est un portable (la mise à l'échelle de fréquence fausse les mesures).

```bash
./benchmark/bench
```

Attendu : le tableau final à sept lignes, cette fois avec une colonne `Error` renseignée (`± x,xxx`).

- [ ] **Step 2 : Relever le contexte de mesure**

```bash
java -version 2>&1 | head -2 && uname -sr && ls -l data/*.zip
```

Ces trois informations rendent les chiffres interprétables plus tard.

- [ ] **Step 3 : Ajouter la §9 au spec**

Ajouter à la fin de `docs/superpowers/specs/2026-07-28-jmh-benchmarks-design.md`, en remplaçant chaque valeur entre chevrons par ce qui a été mesuré au Step 1 et relevé au Step 2 :

```markdown
## 9. Résultats de référence

_Mesuré le <date>. JDK <version>, <OS>, archive de <taille>._
_Ces chiffres ne sont pas portables (§7) : ils servent de point de comparaison
avant/après sur ce poste, pas de référence absolue._

| Benchmark | Score (ms/op) | Erreur |
|---|---|---|
| `endToEnd` | <score> | ± <erreur> |
| `openArchive` | <score> | ± <erreur> |
| `loadComments` | <score> | ± <erreur> |
| `loadShares` | <score> | ± <erreur> |
| `loadArticles` | <score> | ± <erreur> |
| `search` | <score> | ± <erreur> |
| `render` | <score> | ± <erreur> |

**Lecture :** <une à trois phrases — quel étage domine, et l'écart entre endToEnd et la
somme des étages.>
```

- [ ] **Step 4 : Committer**

```bash
git add docs/superpowers/specs/2026-07-28-jmh-benchmarks-design.md
git commit -m "docs: résultats de référence des benchmarks JMH"
```

- [ ] **Step 5 : Vérification finale**

```bash
git status --short && ./bin/test 2>&1 | tail -5
```

Attendu : `git status` muet, et la suite de tests toujours verte — aucune modification n'a touché `src/` ni `test/`.

---

## Points de vigilance pour l'exécutant

1. **Ne pas chercher à committer `benchmark/`.** Trois des quatre tâches ne produisent aucun commit. C'est la décision centrale du spec (§2), pas un oubli.
2. **Ne pas ajouter de tests JUnit sur le harnais.** Ils seraient eux-mêmes gitignorés, donc perdus au clone. La vérification passe par l'exécution, d'où les étapes « Attendu : … » détaillées.
3. **Ne pas toucher à `src/`.** Si un benchmark ne compile pas, la cause est dans le harnais, jamais dans la production.
4. **Les chiffres du premier run peuvent surprendre.** L'archive fait 700 Ko : `endToEnd` sera probablement de l'ordre de quelques dizaines de millisecondes, pas de la seconde. Ce n'est pas une erreur de mesure.
