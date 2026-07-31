# Racine de composition unique — Design

_Date : 2026-07-31_

Refactoring de l'[outil d'exploration d'archive LinkedIn](2026-07-22-linkedin-archive-explorer-design.md),
rendu nécessaire par l'arrivée de [l'UI web](2026-07-31-web-ui-design.md). Aucun
changement de comportement : ni le CLI, ni la page web, ni le cœur de recherche ne
changent pour l'utilisateur.

## 1. Le problème

L'UI web a été ajoutée sans toucher `domain`, `application` ni `infrastructure` — la
couture d'extension a tenu. Mais elle a laissé le projet avec **deux racines de
composition** qui câblent la même chose :

```java
// Main.run(…) et WebMain.start(…), à l'identique
List<ContentSource> sources =
    List.of(
        new CommentsContentSource(zip),
        new SharesContentSource(zip),
        new ArticlesContentSource(zip, new JdkArticleTextExtractor()));
new SearchContentsService(sources, new SearchEngine());
```

Et, juste avant, la même résolution d'archive : `DEFAULT_ARCHIVE_DIR`,
`ArchiveLocator.mostRecent`, `Files.isReadable`, les deux mêmes messages d'erreur,
`ZipArchive.open`.

Deux conséquences :

- **Le jour de l'extracteur Jsoup** (couture d'extension n° 2 du design initial), ou
  d'une quatrième source de contenu, il faut brancher **deux endroits**, et rien ne le
  rappelle. Une seule des deux UI mise à jour compile parfaitement.
- **`cli` et `web` `requires infrastructure`**, donc rien n'empêche un `SearchHandler`
  d'appeler `Csv.parse` ou d'ouvrir le zip lui-même. Le projet a choisi de faire
  vérifier ses frontières par `javac` plutôt que par la relecture ; celle-ci manque.

À noter : qu'une racine de composition connaisse `infrastructure` n'est **pas** le
défaut — c'est sa définition, quelqu'un doit nommer les adapters concrets. Le défaut est
qu'il y en ait deux, et que ce soient les modules d'UI.

## 2. La décision : un module `launcher`

Un cinquième module porte la racine de composition, désormais unique.

```
domain          ← rien
application     ← domain
infrastructure  ← domain
launcher        ← domain, application, infrastructure       ← le seul à connaître les adapters
cli             ← domain, application, launcher
web             ← domain, application, launcher, jdk.httpserver
```

`cli` et `web` restent **frères, sans arête entre eux** ; ils perdent seulement le droit
de câbler. La règle nouvelle, et vérifiée par le compilateur : **aucun module d'UI ne
peut plus atteindre `infrastructure`**. Un `new ZipArchive(…)` dans `web` ne compile
plus.

Ils gardent `requires application` : ils *utilisent* le cas d'usage (`SearchContentsService`,
`SearchResults`), ils ne le *construisent* plus. Et `requires domain` pour `SearchTerm`,
`CaseSensitivity`, `WordScope`.

> **Nom** : le module s'appelle `launcher`. Le script `linkedin-archive-explorer`, qui
> dispatche `serve` et vit hors du graphe de modules, est appelé partout dans la
> documentation **« le script de lancement »**, pour lever l'ambiguïté.

### Alternatives écartées

- **`ServiceLoader` / `uses` + `provides` du JPMS.** Découplage total : plus aucun module
  ne nommerait `infrastructure`. Coût réel : des fichiers `META-INF/services` à produire
  (le jar est packagé par `bin/build` sans notion de ressources), une fabrique en deux
  temps pour transmettre le chemin d'archive (`ServiceLoader` n'instancie que des
  constructeurs sans argument), et surtout un câblage devenu **implicite** — introuvable
  en lisant le code. Pour dix lignes de wiring dans un outil local, le change ne vaut pas
  la peine.
- **Un conteneur d'injection de dépendances.** Exclu par la contrainte « aucun artefact
  externe ».
- **Une fabrique dans `infrastructure`** (`LinkedInArchive.open(path)`). Supprime la
  duplication sans nouveau module, mais laisse `cli` et `web` avec leur
  `requires infrastructure` : le second problème resterait entier.

## 3. Le type `Explorer`

Un seul type public dans le module, une seule façon de démarrer.

```java
package fr.craft.linkedinarchiveexplorer.launcher;

/** Racine de composition : résout l'archive, l'ouvre, câble les adapters. */
public final class Explorer implements AutoCloseable {

  /** @param explicitArchive la valeur de --archive, ou null pour la plus récente dans data/. */
  public static Explorer open(Path explicitArchive);

  public Path archive();

  public SearchContentsService service();

  @Override public void close();   // pas d'exception checked : utilisable en try-with-resources sans bruit
}
```

`open` absorbe tout ce qui était dupliqué : le répertoire `data/` par défaut,
`ArchiveLocator.mostRecent`, le contrôle de lisibilité, `ZipArchive.open`, les trois
`ContentSource`, `new SearchContentsService(sources, new SearchEngine())`.

**Erreurs.** `open` lève une `ArchiveUnavailableException` (unchecked, publique, dans le
même package) qui porte le message déjà rédigé — « No archive found in data/ (or use
--archive <path>). » ou « Cannot read archive: <path> ». Chaque `Main` fait
`err.println(failure.getMessage()); return 1;`. Le message reste écrit une fois, et
`launcher` ne connaît aucun `PrintStream` : la politique d'affichage reste à l'UI.

**Pourquoi exposer `service()` plutôt que déléguer `search(term)`.** `TerminalRenderer` et
`SearchHandler` travaillent déjà sur `SearchContentsService` / `SearchResults` ; déléguer
ajouterait une couche de transfert sans rien encapsuler de plus. `Explorer` est une
fabrique, pas un collaborateur à substituer : il n'a donc **pas** d'interface de port.

## 4. Ce que deviennent les deux UI

**`cli`** — `Main` garde le parsing d'arguments, la politique de couleur, le rendu :

```java
try (Explorer explorer = Explorer.open(archivePath)) {
  out.println("Using archive: " + explorer.archive());
  SearchTerm searchTerm = new SearchTerm(term, caseSensitivity, wordScope);
  SearchResults results = explorer.service().search(searchTerm);
  out.print(new TerminalRenderer(styled).render(searchTerm, results));
  return 0;
} catch (ArchiveUnavailableException unavailable) {
  err.println(unavailable.getMessage());          // sans préfixe, comme aujourd'hui
  return 1;
} catch (RuntimeException failure) {
  err.println("Error: " + failure.getMessage());  // inchangé
  return 1;
}
```

Deux `catch` ordonnés, du plus précis au plus général — et non un multi-catch, illégal
entre une classe et sa sous-classe. Cette distinction n'est pas cosmétique : les messages
d'archive introuvable ou illisible s'affichent aujourd'hui **sans** le préfixe
`Error: `, et le refactoring ne doit rien changer aux sorties.

**`web`** — `WebMain` garde le parsing d'arguments, le port, la politique sur
`BindException` (jamais de repli sur un autre port) et le shutdown hook.
`start(ZipArchive, String, int)` devient **`start(Explorer, int)`** : le cycle de vie du
zip appartient désormais à l'`Explorer`, donc `closeQuietly` disparaît au profit de
`explorer.close()`, et le libellé d'archive passé à `HtmlRenderer` vient de
`explorer.archive()`.

Aucun changement dans `HtmlRenderer`, `SearchHandler`, `QueryParameters`,
`TerminalRenderer`.

## 5. Tests (TDD, red → green → refactor)

- **Nouveau `ExplorerTest`** (`test/…/launcher/`), en `@Nested` par facette :
  - `ArchiveResolution` — chemin explicite honoré ; à défaut, la plus récente de `data/` ;
    absente → `ArchiveUnavailableException` au message attendu ; illisible → idem.
    (`@ParameterizedTest` là où seuls les entrées diffèrent.)
  - `Wiring` — un test sociable sur un zip construit à la volée : le service rendu par
    `Explorer.open` trouve bien un hit dans un commentaire, un post et un article. **Ce
    câblage n'est couvert par aucun test aujourd'hui** ; c'est le gain de couverture du
    refactoring.
- **`WebAcceptanceTest`** — `Explorer.open(archive)` au lieu de `ZipArchive.open(archive)`,
  `WebMain.start(explorer, 0)` ; son `import …infrastructure.ZipArchive` disparaît. Le
  reste du test est inchangé, ce qui est le contrôle de non-régression du refactoring.
- **`MainAcceptanceTest`** — inchangé : il passe par `run(args…)`.

Fixtures inline, fakes écrits à la main : conventions du projet inchangées.

## 6. Build et exécution

Aucun changement. `bin/build` et `bin/test` parcourent `src` avec
`--module-source-path` : un nouveau répertoire de module est pris en compte sans toucher
aux scripts. Le jar reste un classpath plat dont les `module-info.class` sont ignorés à
l'exécution ; `--main-class` reste `…cli.Main` et le script de lancement continue de
dispatcher `serve` vers `…web.WebMain`.

## 7. Documentation à mettre à jour

- `CLAUDE.md` § Architecture : le graphe de modules, la règle « `cli` et `web` sont
  frères », et le § Layout.
- [Design initial](2026-07-22-linkedin-archive-explorer-design.md) § 4 : le graphe et la
  phrase « le wiring des ports se fait uniquement dans `cli` ».
- [Design de l'UI web](2026-07-31-web-ui-design.md) § 3 : le graphe, et une note datée
  signalant que la racine de composition a été extraite.

## 8. Délibérément laissé de côté

- Toute forme de découverte automatique des adapters (§ 2).
- Toute mutualisation entre `Main` et `WebMain` du **parsing d'arguments** : les deux
  grammaires diffèrent (`--color`, `-i`, `-w` d'un côté ; `--port` de l'autre) et une
  option commune de plus ne justifierait pas un parseur partagé.
- Tout changement de comportement observable : mêmes sorties, mêmes codes de retour,
  mêmes messages.
