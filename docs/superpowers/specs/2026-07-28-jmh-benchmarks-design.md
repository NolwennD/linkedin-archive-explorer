# Tests de performance JMH — Design

_Date : 2026-07-28_

Outillage de mesure pour [l'outil d'exploration d'archive LinkedIn](2026-07-22-linkedin-archive-explorer-design.md).
Les contraintes techniques du projet (runtime JDK pur, zéro build tool, architecture
hexagonale par modules) restent inchangées — ce document explique précisément comment
elles le restent.

## 1. Objectif

Mesurer le **pipeline complet** de l'outil sur une archive réelle : dézip, parsing CSV,
extraction HTML, recherche, rendu. Deux usages :

- un chiffre bout-en-bout, comparable d'une version du code à l'autre ;
- une décomposition par étage, pour savoir **où part le temps** avant d'optimiser quoi
  que ce soit.

Hors périmètre : les micro-benchmarks du cœur de recherche
(`SearchTerm.occurrencesIn`, `CaseSensitivity.matchesAt`). Ils sont légitimes — le scan
est un O(n·m) naïf là où `String.indexOf` est intrinsifié par le JIT — mais c'est un
autre chantier.

## 2. Décision structurante — tout hors dépôt

L'ensemble du chantier vit dans un dossier **`benchmark/` gitignoré**. Rien n'est
committé sauf la ligne `benchmark/` du `.gitignore` et ce document.

Conséquence directe : les contraintes dures du `CLAUDE.md` restent **littéralement
vraies** et n'ont pas à être amendées.

| Contrainte | Statut |
|---|---|
| « Runtime is JDK-only », application n'importe que `java.*` | Intact — JMH n'entre jamais dans `dist/linkedin-explorer.jar` |
| « The only third-party jar is JUnit » | Intact — les jars JMH ne sont pas dans le dépôt |
| « Zero build tool » | Intact — voir §4, le lanceur est un programme JEP 330 |
| « Maven only as a one-off downloader » | Exception déjà admise, réutilisée telle quelle |

Le prix à payer : après un clone, les benchmarks n'existent pas. Ce document est la
recette pour les remonter.

## 3. Arborescence

```
benchmark/                 (gitignoré dans son intégralité)
  pom.xml                  manifeste de téléchargement uniquement — jamais de build Maven
  lib/                     les 4 jars (voir §5)
  src/fr/craft/linkedinarchiveexplorer/cli/PipelineBenchmark.java
  out/                     classes compilées + META-INF/BenchmarkList généré
  results/                 sorties JSON de JMH, horodatées
  bench                    programme JEP 330 : compile la prod, compile les benchmarks, lance JMH
  bench.cmd                shim Windows d'une ligne
```

Le lanceur vit **dans `benchmark/`**, pas dans `bin/` : un `bin/bench` versionné
pointerait vers un dossier absent au clone.

## 4. Le lanceur `benchmark/bench`

Même squelette que [`bin/test`](../../../bin/test) — programme JEP 330 lancé par
`java --source 17`, `ToolProvider.findFirst("javac")` pour les appels d'outils, aucun
build tool. Trois étapes :

1. **Compilation de la production en modules** vers `out/`, avec la commande exacte de
   `bin/test`. Le contrôle d'architecture par le JPMS est ainsi obtenu gratuitement : une
   dépendance interdite fait échouer la campagne avant qu'elle ne démarre.

2. **Compilation des benchmarks sur le classpath**, avec le processeur d'annotations :

   ```
   javac -cp <jars JMH>:<répertoires de modules out>
         -processorpath <jmh-core>:<jmh-generator-annprocess>
         -d benchmark/out
         benchmark/src/**/*.java
   ```

   C'est le processeur d'annotations qui génère les classes de benchmark *et* les
   ressources `META-INF/BenchmarkList` et `META-INF/CompilerHints` dans `-d`.

3. **Lancement** :

   ```
   java -cp benchmark/out:<jars JMH>:<répertoires de modules out>
        org.openjdk.jmh.Main -rf json -rff benchmark/results/<horodatage>.json
   ```

   Pas d'uber-jar nécessaire : JMH forke ses JVM de mesure en réutilisant
   `java.class.path`, et un classpath de répertoires lui convient.

Tout argument passé à `./benchmark/bench` est **transmis tel quel à JMH**, après les
options par défaut, ce qui permet de les écraser :

```bash
./benchmark/bench -f 1 -wi 1 -i 3     # aller-retour rapide
./benchmark/bench endToEnd            # un seul benchmark (le filtre JMH est un regex)
```

## 5. Dépendances et téléchargement

| jar | rôle |
|---|---|
| `org.openjdk.jmh:jmh-core:1.37` | le runtime |
| `org.openjdk.jmh:jmh-generator-annprocess:1.37` | le processeur d'annotations |
| `net.sf.jopt-simple:jopt-simple` | transitive de `jmh-core` |
| `org.apache.commons:commons-math3` | transitive de `jmh-core` (statistiques) |

Environ 3 Mo au total. Les deux transitives ne sont pas nommées explicitement : le
`pom.xml` ne déclare que les deux artefacts JMH, et le téléchargement les résout.

```bash
mvn -f benchmark/pom.xml dependency:copy-dependencies -DoutputDirectory=lib
```

Opération one-off. Le `pom.xml` n'est **jamais** utilisé pour compiler ou lancer quoi que
ce soit — c'est un manifeste de téléchargement, rien d'autre. `benchmark/bench` échoue
avec un message explicite si `benchmark/lib/` est vide, en rappelant cette commande.

**Version à vérifier** : 1.37 est la dernière version connue au moment de la rédaction ;
si une plus récente existe, elle est préférable. JMH est par ailleurs sensible aux JDK
très récents — si la campagne se comporte mal sur le JDK 26 de `mise.toml`, le repli est
de la lancer sur le JDK 17 plancher.

## 6. Les benchmarks

Une seule classe : `fr.craft.linkedinarchiveexplorer.cli.PipelineBenchmark`.

Le package `cli` est **imposé**, pas choisi : `Main.run` est package-private. Le
benchmark y accède par le même mécanisme que
[`MainAcceptanceTest`](../../../test/fr/craft/linkedinarchiveexplorer/cli/MainAcceptanceTest.java)
— compilation sur le classpath et non en module, donc les frontières JPMS ne
s'appliquent pas. C'est accessoirement le package du composition root, soit le bon
endroit d'où mesurer l'ensemble.

### État

`@State(Scope.Benchmark)`. Au `@Setup(Level.Trial)` :

- l'archive est résolue par `ArchiveLocator.mostRecent(Path.of("data"))` — échec net et
  explicite si `data/` est vide, plutôt qu'une mesure silencieusement fausse ;
- le terme recherché est **fixe** (pas de `@Param`), littéral, options par défaut.

### Les mesures

| benchmark | région mesurée | préparé en `@Setup` |
|---|---|---|
| `endToEnd` | `new Main().run(args, flux nul, flux nul, false)` | — |
| `openArchive` | `ZipArchive.open` puis `close` | — |
| `loadComments` | `CommentsContentSource.load()` | zip ouvert |
| `loadShares` | `SharesContentSource.load()` | zip ouvert |
| `loadArticles` | `ArticlesContentSource.load()` | zip ouvert |
| `search` | `SearchEngine.search(term, contents)` | contenus chargés |
| `render` | `TerminalRenderer(false).render(term, results)` | résultats calculés |

`endToEnd` reçoit `--no-color` et écrit dans un `PrintStream` nul : on mesure le calcul,
pas le terminal.

Chaque méthode **retourne** sa valeur, pour que JMH la consomme et que le JIT n'élimine
pas le calcul mort.

### Réglages

`Mode.AverageTime`, `TimeUnit.MILLISECONDS`. Budget annoté au niveau de la classe :
`@Fork(2)`, `@Warmup(iterations = 3, time = 5)`, `@Measurement(iterations = 5, time = 5)`
— soit environ 80 s par benchmark, une campagne complète autour de 9 minutes,
réductible par les arguments CLI de §4.

## 7. Ce que les chiffres disent — et ne disent pas

**Le cache disque est chaud.** `endToEnd` réouvre le zip et recharge tout à chaque
invocation, ce qui est fidèle au comportement réel — mais dès la fin de la chauffe, l'OS
sert les octets depuis la mémoire. Le résultat mesure donc le **coût CPU** (inflate,
parsing CSV, extraction HTML), pas le premier lancement à froid. C'est ce qu'il faut pour
comparer deux versions du code ; ce n'est pas le temps ressenti au tout premier
`./linkedin-archive-explorer`.

**La somme des étages ≠ le tout.** Allocation partagée, GC, effets de cache : l'écart
entre `endToEnd` et la somme des autres mesures est de l'information, pas un défaut du
harnais.

**Les chiffres ne sont pas portables.** Ils dépendent de l'archive locale, de la machine
et du JDK. Ils servent à comparer *avant/après* sur un même poste, pas à établir une
référence absolue. `benchmark/results/` est horodaté pour cela.

## 8. Ce qui est explicitement écarté

- **Pas de seuil de non-régression, pas de CI.** La CI ne voit rien du dossier
  `benchmark/`. Des chiffres dépendant de la machine n'ont pas leur place dans un
  `pull_request`.
- **Pas de `@Param`.** Un terme fixe suffit pour l'objectif de §1. La matrice
  terme fréquent/rare × options `-i`/`-w` est un ajout ultérieur trivial.
- **Pas d'archive de test dédiée.** On mesure sur l'archive réelle de `data/`, déjà
  gitignorée. Cohérent avec la décision « fixtures inline » du projet : aucun jeu de
  données versionné.

## 9. Résultats de référence

_Mesuré le 2026-07-28. JDK 26.0.1 (Temurin-26.0.1+8), Linux 7.0.0-28-generic, archive de
699 907 octets._
_Ces chiffres ne sont pas portables (§7) : ils servent de point de comparaison
avant/après sur ce poste, pas de référence absolue._

| Benchmark | Score (ms/op) | Erreur |
|---|---|---|
| `endToEnd` | 10,549 | ± 0,484 |
| `openArchive` | 0,015 | ± 0,001 |
| `loadComments` | 5,839 | ± 0,305 |
| `loadShares` | 2,180 | ± 0,142 |
| `loadArticles` | 0,171 | ± 0,006 |
| `search` | 1,900 | ± 0,076 |
| `render` | 0,043 | ± 0,003 |

**Lecture :** `loadComments` domine largement (5,839 ms/op, 55 % de `endToEnd`), loin
devant `loadShares` (2,180 ms/op, 21 %) ; la somme des six étages (10,148 ms/op) est
proche de `endToEnd` (10,549 ms/op), l'écart de 0,401 ms/op restant de l'information
(allocation partagée, GC) plutôt qu'un défaut du harnais. `search`, bien qu'opérant
entièrement en mémoire sur des contenus déjà chargés, n'est pas négligeable (1,900 ms/op,
18 % de `endToEnd`) ; mais les trois chargeurs réunis pèsent 78 %, et c'est donc là que se joue l'essentiel du temps.
