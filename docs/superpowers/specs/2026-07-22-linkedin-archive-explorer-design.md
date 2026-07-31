# LinkedIn Archive Explorer — Design (v1)

_Date : 2026-07-22_

## 1. Objectif

Fournir un outil pour **explorer une archive de données LinkedIn** et retrouver les
**commentaires, posts et articles** contenant un terme recherché, en affichant pour
chaque résultat un extrait de contexte et un **lien cliquable** vers le contenu sur
LinkedIn.

Cas d'usage de référence : rechercher `Date(0,0,0)` et lister les contenus qui en
parlent, avec leur URL LinkedIn.

## 2. Contraintes techniques (décidées)

- **Langage** : Java. Une version Rust pourra suivre plus tard (hors périmètre v1).
- **Runtime JDK pur** : le code applicatif n'utilise **que la bibliothèque standard**
  du JDK. Aucune dépendance runtime (pas de Jsoup, pas de librairie CSV…).
- **Zero build tool** : pas de Maven ni Gradle. Compilation et packaging via les
  outils fournis par le JDK (`javac`, `jar`, `java`).
- **Portabilité** : doit se réutiliser facilement sur Linux, Windows et macOS, sans
  setup lourd. Distribution sous forme d'un **jar autonome** (`java -jar …`).
- **Version Java** : 26 (épinglée via `mise` ; c'est aussi la version ambiante).
- **Tests** : JUnit 5 via le fat-jar `junit-platform-console-standalone.jar` déposé
  dans `lib/` (un seul jar, aucune résolution de dépendances).

## 3. Contraintes fonctionnelles (décidées)

### Recherche
- **Littérale, type `grep`** : recherche d'une **sous-chaîne exacte**.
- **Sensible à la casse et aux accents** (`Date` ≠ `date`, `developpeur` ≠ `développeur`).

### Périmètre des contenus (v1)
- **Commentaires** — `Comments_*.csv`
- **Posts / partages** — `Shares_*.csv`
- **Articles** — `Articles/**/*.html`

### Affichage des résultats
- **Regroupés par type** : `ARTICLES`, `POSTS`, `COMMENTS` (seuls les groupes
  non vides sont affichés).
- **Triés par date décroissante** au sein de chaque groupe. Les articles n'ont pas de
  date (voir §7) : ils sont affichés sans date, en fin de leur groupe.
- **Extrait de contexte** : le terme trouvé entouré d'environ **40 caractères** de
  part et d'autre. Les retours à la ligne du contenu sont remplacés par des espaces
  dans l'extrait pour rester lisible sur une ligne. Aux bords du texte, la fenêtre est
  tronquée sans « déborder ».
- **Liens cliquables dans le terminal** via les séquences d'échappement **OSC 8**. Les
  URLs des CSV (percent-encodées) sont cliquables telles quelles et conservées en
  l'état.
- **Surlignage** du terme recherché en **couleur ANSI**.
- **Dédoublonnage au niveau du contenu** : un contenu qui matche plusieurs fois
  n'apparaît **qu'une seule fois**, mais **tous ses extraits** sont listés en dessous
  (un par occurrence).

### Sélection de l'archive
- Par défaut, le CLI cherche les archives dans `data/` et prend la **plus récente** :
  date extraite du nom de fichier (`..._MM-DD-YYYY.zip`), **fallback** sur la date de
  modification du fichier si le nom n'est pas parsable.
- Affiche `Using archive: <fichier>`.
- `--archive <path>` force un fichier précis.
- Aucune archive trouvée → erreur claire, code de retour ≠ 0.

## 3bis. Édition du code Java — manuelle (jdtls écarté après validation)

Décision initiale : piloter la création/refacto via **jdtls**
(`.claude/tools/jdtls-driver.py`). **Validation faite en début d'implémentation** :
sur notre structure **multi-module** (un dossier par module sous `src/<module>/`),
jdtls en mode invisible-project **n'infère pas la racine des sources** et crée les
types **sans déclaration `package`** → `error: unnamed package is not allowed in named
modules`. jdtls ne gère donc pas correctement cette disposition.

**Conséquence** : le code Java est **écrit à la main**, sans jdtls. La boucle
`javac --module-source-path src -d out` (avec les `module-info.java`) compile
correctement et sert de garde-fou. Le driver `jdtls-driver.py` a été **supprimé du
projet** (inutile ici).

## 4. Architecture — hexagonale légère (ports & adapters)

```
infrastructure/primary   → CLI + rendu terminal          (driving adapter)
        │
application              → SearchContentsService (orchestration)
        │
domain                   → SearchEngine + modèle + PORTS  (cœur réutilisable)
        │
infrastructure/secondary → lecture zip / CSV / HTML       (driven adapters)
```

Le `domain` ne dépend que de `java.*`. C'est le cœur réutilisable par une future
interface (UI web) sans réécriture.

### Architecture garantie par le système de modules Java (JPMS)

À la place d'ArchUnit (indisponible sans build tool), les frontières sont **imposées
par `javac`** grâce à un `module-info.java` par couche. Un module ne voit que ce qu'il
`requires`, et le JPMS **interdit les cycles** → une dépendance interdite ne compile
pas.

```
module fr.craft.linkedinarchiveexplorer.domain         { exports …domain; }                    // requires RIEN (hors java.base)
module fr.craft.linkedinarchiveexplorer.application     { requires …domain; exports …application; }  // PAS de requires infrastructure
module fr.craft.linkedinarchiveexplorer.infrastructure  { requires …domain; exports …infrastructure; } // implémente les ports
module fr.craft.linkedinarchiveexplorer.cli             { requires …domain, …application, …infrastructure; } // racine de composition (Main + TerminalRenderer)
```

Règles ainsi garanties **à la compilation** :
- `domain` isolé (ne `requires` rien) ;
- `application` ne peut pas référencer `infrastructure` (absent de son `requires`) →
  le wiring des ports se fait uniquement dans `cli` (racine de composition), par
  injection des interfaces définies dans `domain` ;
- aucun cycle entre couches (interdit par le JPMS).

> **Amendé le 2026-07-31.** L'arrivée de l'UI web a donné une **seconde** racine de
> composition, qui dupliquait le wiring. Un module `launcher` (le type `Explorer`) la
> porte désormais seul : `cli` et `web` ne `requires` plus `infrastructure` du tout.
> Voir [le design de la racine de composition](2026-07-31-composition-root-design.md).

**Compilation vs exécution** : on compile en multi-module (donc enforcement archi),
mais on **package et exécute en classpath simple, un seul jar** (`java -jar …`). Les
`module-info.class` sont alors ignorés au runtime (module anonyme) — l'encapsulation
runtime n'apporte rien pour un CLI local ; seule compte la vérification à la
compilation. Voir §10 pour le build.

### Deux coutures d'extension explicites
1. **Interface utilisateur** : le moteur de recherche (`domain` + `application`) est
   isolé du CLI. Une future UI web branche un autre adapter primaire sur le même cœur.

   > **Réalisée le 2026-07-31.** Le module `web` a été ajouté sans modifier une ligne de
   > `domain`, `application` ni `infrastructure` — la couture a tenu.
   > Voir [le design de l'UI web](2026-07-31-web-ui-design.md).
2. **Extraction HTML** : derrière le port `ArticleTextExtractor`. Implémentation
   **JDK pur** en v1 ; une future implémentation basée sur **Jsoup** la remplacera sans
   toucher au reste.

Racine de package : `fr.craft.linkedinarchiveexplorer`.

## 5. Composants

### Domaine (`domain`) — modèle + cœur, aucune I/O
Records validés dans leur constructeur compact (design type-driven) :

- `ContentType` — enum `ARTICLE | POST | COMMENT`.
- `Content` — `(ContentType type, Optional<LocalDate> date, String url, String text)` :
  une unité de contenu unifiée, quelle que soit sa source.
- `Excerpt` — `(String snippet, int matchStart, int matchEnd)` : un extrait de ~40
  caractères + la position du terme dans l'extrait (pour le surlignage).
- `SearchHit` — `(Content content, List<Excerpt> excerpts)` : un contenu et **tous**
  ses extraits (dédoublonnage au niveau contenu).
- `SearchTerm` — `(String value)` : la requête littérale (non vide).
- **`SearchEngine`** — logique grep pure : pour chaque `Content`, trouve toutes les
  occurrences du terme, construit les `Excerpt` (fenêtre 40 caractères, gestion des
  bords, remplacement des retours-ligne par des espaces), et n'émet qu'un `SearchHit`
  par contenu matché. Aucune I/O.

### Ports (interfaces dans `domain`)
- `ContentSource` — fournit un flux/liste de `Content`.
- `ArticleTextExtractor` — `String extractText(String html)` / `String extractTitle(String html)`
  / `String extractUrl(String html)`.

### Application (`application`)
- **`SearchContentsService`** — orchestration :
  1. charge tous les `Content` via les `ContentSource`,
  2. applique `SearchEngine`,
  3. **groupe par type** et **trie chaque groupe par date décroissante** (articles sans
     date en fin),
  4. renvoie une structure ordonnée prête à afficher.

### Adapters secondaires (module `infrastructure`) — JDK pur
- **`ZipArchive`** — ouvre le `.zip` via **ZIP FileSystem** (`java.nio.file`), sans
  extraction sur disque ; expose les entrées.
- **`Csv`** — petit parseur **RFC 4180 maison** : champs entre guillemets, guillemets
  échappés (`""`), champs multi-lignes, virgules dans un champ. (Morceau technique clé.)
- **`CommentsContentSource`** — `Comments_*.csv` → `Content(COMMENT, date, Link, Message)`.
- **`SharesContentSource`** — `Shares_*.csv` → `Content(POST, date, ShareLink, ShareCommentary)`.
- **`ArticlesContentSource`** — `Articles/**/*.html` → `Content(ARTICLE, ∅, url, texte)`
  via l'`ArticleTextExtractor`.
- **`JdkArticleTextExtractor implements ArticleTextExtractor`** — extraction en JDK
  pur : retrait des balises pour le texte ; titre depuis `<title>` ; URL depuis le
  lien du `<h1>`. ⟵ *couture Jsoup.*

### Adapter primaire + racine de composition (module `cli`)
- **`Main`** — point d'entrée **et** racine de composition (instancie et câble les
  adapters secondaires derrière les ports). Parse les arguments :
  `linkedin-archive-explorer [--archive <path>] [--no-color] <term>`. Auto-détecte l'archive
  la plus récente dans `data/` si `--archive` est absent.
- **`TerminalRenderer`** — produit la sortie : groupes, tri, **liens OSC 8**,
  **surlignage ANSI**, liste d'extraits. Détecte si la sortie n'est pas un terminal
  (redirection/pipe) et désactive alors couleurs + OSC 8 ; `--no-color` force la
  désactivation.

## 6. Flux de données

```
args (terme) ─▶ Main ─▶ SearchContentsService
                              │  (ContentSource : zip ─▶ CSV/HTML ─▶ Content)
                              ▼
                         SearchEngine  (filtre + extraits + dédup)
                              ▼
                         tri / groupe par type
                              ▼
                         TerminalRenderer ─▶ stdout (liens cliquables + surlignage)
```

## 7. Spécificités des données constatées

- **URL d'un article** : présente dans le HTML, dans le lien du titre
  `<h1><a href="https://www.linkedin.com/pulse/…">`. On l'extrait de là.
- **Titre d'un article** : balise `<title>`.
- **Date d'un article** : **absente** de l'export HTML LinkedIn. Les articles sont donc
  affichés **sans date** (et placés en fin de leur groupe pour le tri).
- **URLs des commentaires/posts** : percent-encodées dans les CSV
  (`urn%3Ali%3Aactivity%3A…`), cliquables telles quelles → conservées sans décodage.
- **Encodage** : lecture en **UTF-8** partout (accents français).

## 8. Gestion d'erreurs

- Archive introuvable / illisible → message clair, code retour ≠ 0.
- Terme de recherche manquant → affichage de l'aide d'usage.
- Ligne CSV corrompue → **ignorée et comptée** ; un avertissement final indique
  « N lignes ignorées » le cas échéant.
- Aucun résultat → message « No results for "…" ».

## 9. Stratégie de test (JUnit 5 standalone, outside-in / testing-diamond)

Dans l'esprit du projet voisin (TDD, diamant plutôt que pyramide). En Java pur, pas
de framework de mock : les collaborateurs aux frontières (accès au zip, sources de
contenu) sont substitués par des **implémentations factices** écrites à la main sur
les ports du domaine.

- **`Csv`** : guillemets, `""` échappés, champs multi-lignes, virgule dans un champ.
- **`SearchEngine`** : sensibilité casse/accents, dédoublonnage, extraits multiples,
  fenêtre de 40 caractères aux bords (début/fin de texte).
- **`ContentSource`** : lecture depuis les **fixtures synthétiques** (§11bis.A) zippées
  à la volée par un test-helper.
- **`TerminalRenderer`** : ordre des groupes, tri par date, format OSC 8, marqueurs de
  surlignage (assertions sur la chaîne produite).
- **Test d'acceptance** : le CLI exécuté sur un zip de fixtures construit inline via
  `--archive`, assertion sur la sortie rendue (`MainAcceptanceTest`).

Les données de test sont détaillées en **§11bis**.

## 10. Layout du projet & outillage

```
linkedin-archive-explorer/
  src/                                     (un dossier par MODULE, --module-source-path)
    fr.craft.linkedinarchiveexplorer.domain/
      module-info.java
      fr/craft/linkedinarchiveexplorer/domain/…
    fr.craft.linkedinarchiveexplorer.application/     module-info.java + …application/…
    fr.craft.linkedinarchiveexplorer.infrastructure/  module-info.java + …infrastructure/…
    fr.craft.linkedinarchiveexplorer.cli/             module-info.java + …cli/… (Main)
  test/                                    (tests ; fixtures inline, zippées à la volée)
  tools/mutation_testing.py                (harnais de mutation testing manuel)
  lib/junit-platform-console-standalone-<version>.jar
  data/                     (les archives .zip — gitignored)
  bin/build | bin/build.cmd (JEP 330 : javac/jar via ToolProvider → un jar classpath)
  bin/test  | bin/test.cmd   (JEP 330 : compile modulaire + tests classpath, junit standalone)
  linkedin-archive-explorer (lanceur JEP 330 : build si besoin puis java -jar)
  mise.toml                 (java = 26)
  .editorconfig
  .mcp.json                 (context7)
  .gitignore
  CLAUDE.md                 (conventions : hexagonal+JPMS, TDD, coutures)
```

### Build & tests (JDK pur, multi-module)
- **Build** : `javac --module-source-path src -d out $(sources)` compile toutes les
  couches en vérifiant les frontières inter-modules ; puis `jar` fusionne `out/` en un
  **unique** `linkedin-explorer.jar` (avec `Main-Class`), lancé en classpath via
  `java -jar` — les `module-info` sont ignorés au runtime.
- **Tests** : après le compile modulaire (l'enforcement d'archi est déjà obtenu là),
  les tests sont compilés et exécutés **en classpath** : classes de production
  (`out/<module>/`) + `junit-platform-console-standalone.jar`, lancé via
  `java -jar … execute --scan-class-path`. Plus simple que `--patch-module` et
  suffisant ici (les tests n'ont pas besoin de la sémantique module). Encapsulé dans
  `bin/test`/`bin/test.cmd`. **Validé** : boucle rouge→vert opérationnelle.

### Repris du projet voisin `chatbot`
- `mise` (épinglage `java = 26`).
- `.editorconfig`.
- `.mcp.json` (serveur MCP context7).
- Les **conventions** du `CLAUDE.md` : architecture hexagonale, TDD outside-in,
  implémentations factices à la main (pas de framework de mock), records validés en
  constructeur compact — réécrites pour ce projet. (Édition Java **manuelle** : jdtls
  écarté, voir §3bis.)

### Écartés (incompatibles avec « zero build tool » / « JDK pur »)
- Maven / `mvnw`, Spring Boot / Seed4J (runtime).
- Checkstyle, JaCoCo (exécutés via plugins Maven).
- **ArchUnit** → remplacé par le **système de modules Java (JPMS)**, qui impose
  l'architecture à la compilation (voir §4).
- Prettier, Husky, lint-staged, pnpm (chaîne Node).

## 11bis. Jeux de données de test

### Fixtures **inline** (choix retenu — ne pas versionner de fichiers de fixture)
- **Décision (2026-07-22)** : les fixtures sont écrites **directement dans les tests**
  (blocs de texte pour le CSV/HTML, petits helpers qui zippent à la volée via
  `ZipOutputStream`). **Pas** de dossier `test-resources/fixtures/` avec des fichiers
  versionnés — l'inline est jugé suffisant et plus lisible.
- **Anonymes par construction** : noms/URLs inventés dans les tests, au bon format.
- **Couverture** : les cas délicats (casse/accents, formes Unicode NFC vs NFD, scripts
  non-latins, champ CSV multi-lignes, `""` échappés, virgule dans un champ, dédup,
  extraits aux bords, groupes) sont couverts par les tests unitaires (`CsvTest`,
  `SearchEngineTest`, `ContentSourcesTest`, `TerminalRendererTest`) et le **test
  d'acceptance** (`MainAcceptanceTest`, qui construit un mini-zip inline et exerce le
  CLI via `--archive`, y compris sur du contenu non-latin).

> **Note (2026-07-23)** : un générateur d'« archive synthétique grandeur nature »
> reproduisant les 39 fichiers de l'export réel a été prototypé (`SyntheticArchive`)
> puis **abandonné** — il n'apportait pas de couverture au-delà des fixtures inline.
> Les deux seules propriétés qui lui étaient propres ont été reprises dans les tests
> existants : la **non-normalisation NFC vs NFD** dans `SearchEngineTest`, et le
> **round-trip de contenu non-latin** (CJK, cyrillique, grec, arabe) dans
> `MainAcceptanceTest`. La structure réelle de l'archive reste documentée dans
> [docs/archive-structure.md](../../archive-structure.md) comme référence de l'app.

**Hors périmètre maintenant** : la **gestion** de la recherche non-latine (les tests
**figent d'abord** le comportement actuel — le support recherche viendra ensuite).

## 11. Hors périmètre v1 (YAGNI)

- Recherche par mots-clés / regex / fuzzy (v1 = sous-chaîne littérale uniquement).
- Interface web (le cœur est prévu pour, mais non implémentée en v1).
- Intégration Jsoup (la couture existe, l'implémentation viendra plus tard).
- Fusion multi-archives (v1 = archive la plus récente).
- Autres sources de contenu (réactions, votes, messages privés…).
