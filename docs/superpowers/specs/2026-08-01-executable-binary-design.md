# Binaire exécutable — Design

_Date : 2026-08-01_

Suite du [choix de l'archive dans l'UI web](2026-08-01-web-archive-selection-design.md),
qui en était le prérequis, et de [l'UI web](2026-07-31-web-ui-design.md). S'appuie sur la
[racine de composition](2026-07-31-composition-root-design.md).

## 1. Objectif

Publier **un exécutable** offrant les mêmes fonctions que le dépôt — recherche en terminal
et page web —, à quelqu'un qui n'a ni cloné le projet, ni installé de JDK, ni envie de
retenir une ligne de commande.

Trois critères de succès :

- **Une seule commande** pour les deux interfaces.
- **Rien à installer** : le runtime Java voyage avec le programme.
- **Ni `domain`, ni `application`, ni `infrastructure` modifiés.** Comme pour l'UI web, la
  preuve que les coutures d'extension tiennent est qu'on n'ait pas à y toucher.

## 2. Deux formes de distribution, pas une

| | Pour qui | Poids | Construit |
|---|---|---|---|
| **Bundle complet**, image jlink | tout le monde | 43 Mo sur disque, **22 Mo téléchargés** | une fois par OS |
| **Bundle léger**, sans runtime | qui a déjà un JDK 17+ | **~70 Ko** | une seule fois, multiplateforme |

Le bundle complet répond à « rien à installer ». Le bundle léger existe parce que 22 Mo
pour quelqu'un qui a déjà une JVM, c'est 22 Mo de trop — et parce qu'il se construit
presque gratuitement.

### Le bundle léger n'est pas un jar nu

Publier `linkedin-explorer.jar` seul contredirait le critère « une seule commande » du § 1 :
il faudrait taper `java -jar linkedin-explorer.jar <terme>`. Ce public mérite le même geste
que l'autre. Le bundle léger contient donc, en plus du jar, les mêmes lanceurs :

```
linkedin-explorer.jar
bin/linkedin-archive-explorer        (+x, #!/bin/sh)
bin/linkedin-archive-explorer.bat
```

Chacun tient en deux lignes — un `exec java -jar` relatif à l'emplacement du script —, plus
un garde-fou : si `java` est introuvable, on le dit en une phrase lisible plutôt que de
laisser le shell répondre `command not found`. **La commande est alors rigoureusement la
même que celle du bundle complet**, donc une seule documentation et un seul réflexe à
acquérir.

Les deux lanceurs voyageant ensemble, l'artefact est unique et multiplateforme : il se
construit une seule fois, sur n'importe quel runner, sans matrice. Il est livré en
`.tar.gz` — qui conserve le bit exécutable (§ 7) — **et** en `.zip` pour Windows, où ce bit
n'a pas de sens. Même contenu, deux emballages.

Ce qu'on ne fait **pas** : vérifier la version de Java dans le script. Analyser la sortie de
`java -version` en shell portable est fragile ; on se contente de vérifier sa présence, et
la page de release annonce le prérequis. Un JDK trop ancien répondra un
`UnsupportedClassVersionError`, message ingrat mais sans ambiguïté.

### Les installeurs natifs sont écartés

Les installeurs natifs (`jpackage` : `.deb`, `.msi`, `.dmg`) sont écartés. Ils
exigent des outils par plateforme — WiX sous Windows, `fakeroot` sous Linux — et, sous
macOS, une signature sans laquelle l'installeur est purement et simplement refusé. Pour un
outil personnel, une archive à décompresser demande un geste de plus à l'utilisateur mais
supprime une chaîne entière de dépendances de construction. Le sujet pourra être rouvert si
la distribution devient large.

## 3. Le module `app` — le dispatch quitte le script

Le choix CLI/web est aujourd'hui fait par le script `linkedin-archive-explorer`, un
programme JEP 330 vivant **hors du graphe de modules**. Un binaire n'a plus de script : ce
choix doit devenir du code compilé.

Septième et dernier module :

```
fr.craft.linkedinarchiveexplorer.app   requires cli, web   (App — le dispatch)
```

- **La règle « `cli` et `web` sont frères, pas d'arête entre eux » tient.** Les deux arêtes
  partent du nouveau module ; aucune ne relie les deux UI. C'est exactement la forme qu'on
  voulait préserver : un troisième adaptateur d'UI viendrait s'ajouter ici sans toucher aux
  deux autres.
- `cli` et `web` gagnent un `exports … to fr.craft.linkedinarchiveexplorer.app`. **Export
  qualifié** : `Main` et `WebMain` deviennent visibles du seul module qui en a besoin,
  l'encapsulation reste entière pour tout le reste.
- `app` devient le `Main-Class` du jar **et** l'unique racine `--add-modules` de l'image.
- Le script `linkedin-archive-explorer` survit pour le développement, mais **se vide** : un
  `java -jar` et rien d'autre. Toute sa logique de dispatch disparaît, remplacée par du code
  compilé et testé.

### Le routage, isolé pour être testable

```java
// app
record Route(Target target, List<String> arguments) {}
enum Target { CLI, SERVE }

static Route route(String[] arguments);   // décide
public static void main(String[] args);   // exécute
```

`route` est une fonction pure : elle décide sans rien lancer, ce qui la rend testable sans
processus ni serveur (§ 9). `main` se contente d'appeler `Main.main` ou `WebMain.main` avec
les arguments restants.

## 4. Le comportement par défaut : le web

| Arguments | Effet |
|---|---|
| *(aucun)* | serveur web, et ouverture du navigateur |
| `serve [--archive p] [--port n] [--no-browser]` | idem, explicite |
| `<terme> [options]` | recherche en terminal, inchangée |

Sans argument, on ouvre la page : c'est le geste de quelqu'un qui lance le programme sans
savoir quoi taper. La forme `serve` reste, parce qu'elle est la seule à accepter `--port`.

**La règle de routage est délibérément bête** : aucun argument, ou `serve` en premier →
serveur ; **tout le reste** → terminal. Le dispatch ne regarde jamais le contenu des autres
arguments, il ne fait que compter et comparer le premier.

Ce qui a une conséquence à assumer : `--no-browser` et `--port` sont des options de
`serve`, et `linkedin-archive-explorer --no-browser` seul part en terminal, qui répond par
son message d'usage. L'alternative — deviner l'intention à partir d'options connues —
demanderait au dispatch de connaître la grammaire des deux UI, donc de dupliquer ce que
`Main` et `WebMain` savent déjà, et de se désynchroniser à la première option ajoutée.
Un message d'usage est un moindre mal comparé à cette dette.

**Ce que le binaire cherche au démarrage reste `data/`, et rien d'autre.** Par conformisme
avec ce qui existe : aucun balayage de `~/Téléchargements`, aucune heuristique. Hors d'un
dossier contenant `data/` — le cas normal d'une archive décompressée n'importe où — le
champ d'archive arrive **vide**, et la page demande un chemin, avec son `placeholder` et son
aide. Le cookie le retient ensuite pour de bon. Ce comportement est déjà en place et
vérifié : c'est ce que la spec précédente a payé d'avance.

## 5. Ouvrir le navigateur sans faire grossir l'image

Mesuré sur ce dépôt : image `cli` + `web` = **43 Mo** ; en ajoutant `java.desktop`, requis
par `Desktop.browse` = **62 Mo**. Dix-neuf mégaoctets, presque la moitié du binaire, pour
ouvrir une URL.

Donc `ProcessBuilder` et la commande du système — `xdg-open`, `open`, `cmd /c start` —, dans
le module `web`, où l'ouverture d'une page est chez elle. Le module `app` reste trivial.

Deux règles :

- **L'échec n'est jamais fatal.** Commande absente, session sans environnement graphique,
  serveur distant : on imprime l'URL et le serveur tourne quand même. Un binaire qui refuse
  de démarrer parce qu'il n'a pas su ouvrir un navigateur serait absurde.
- **`--no-browser`** existe pour les tests et pour qui préfère cliquer lui-même.

## 6. Ce que jlink embarque — et ce qu'il n'embarque pas

**Presque aucun module JDK n'est à énumérer.** `jlink` part des racines de `--add-modules`
et suit les `requires` ; la fermeture se calcule seule. Sur l'image produite :

```
fr.craft.linkedinarchiveexplorer.{app,application,cli,domain,infrastructure,launcher,web}
java.base@26.0.1
jdk.httpserver@26.0.1
jdk.zipfs@26.0.1
```

**Trois modules JDK sur les 68 du JDK.** Deux se déduisent seuls : `java.base`, obligatoire,
et `jdk.httpserver`, la seule exception à la règle `java.*` du projet. Ni `java.desktop`, ni
`java.logging`, ni `java.xml` ne s'y sont glissés.

Le `requires` écrit pour faire respecter l'architecture sert donc une seconde fois,
gratuitement, à tailler le runtime.

### Le troisième module, que le graphe ne peut pas deviner

`jdk.zipfs` doit être **nommé à la main**, et c'est le seul. `ZipArchive` ouvre l'export par
`FileSystems.newFileSystem`, dont le fournisseur est trouvé par `ServiceLoader` — pas par un
`requires`. Aucune analyse du graphe de modules ne peut le voir.

Le mode d'échec mérite d'être décrit, parce qu'il est le pire qui soit : l'image **compile,
se lie, démarre**, sert même la page d'accueil — et meurt sur la première archive ouverte,
avec `Error: Provider not found`. Rien, sur le classpath de développement, ne le laisse
présager : le JDK complet y fournit le service.

C'est exactement ce que le pas de fumée du § 9 a attrapé, au premier empaquetage réel. Sans
lui, le défaut partait en release.

Deux réponses possibles, la seconde retenue :

- **`--bind-services`**, qui lie tous les fournisseurs de services de la fermeture : la
  couverture est totale, mais on récupère les charsets, les locales et la crypto avec.
- **Nommer `jdk.zipfs`** dans `--add-modules` : **140 Ko mesurés**, et un seul module ajouté
  en connaissance de cause. C'est le choix retenu — précis, et documenté à l'endroit où il
  est fait.

Corollaire à retenir : **tout futur recours à un `ServiceLoader` échappera au calcul de
fermeture.** Le pas de fumée est ce qui le rattrapera, pas la compilation.

### Le poids, et pourquoi il ne descendra pas

| | JDK complet | Image |
|---|---|---|
| Modules JDK | 68 | 2 |
| `lib/modules` | 144 Mo | 11,6 Mo |
| Total | 309 Mo | 43 Mo |

L'élagage porte sur les **modules entiers non atteignables** — 92 % de `lib/modules`
disparaissent. Il n'y a en revanche **aucune élimination de code mort** à l'intérieur d'un
module : `java.base` part en entier. La mesure est sans appel — une image ne contenant
*que* `java.base` pèse déjà **43 Mo**, autant que la nôtre. Le plancher est la machine
virtuelle (`libjvm` : 28,9 Mo) plus `java.base` ; nos six modules et `jdk.httpserver` y sont
indiscernables.

Conséquence pratique : **aucune ingénierie de modules ne réduira ce chiffre.**
`--compress=zip-9` au lieu de `zip-6` ne gagne rien de mesurable, et coûte à la
décompression — on garde `zip-6`. Le chiffre qui compte pour une page de release est de
toute façon les **22 Mo** de l'archive comprimée.

### Une conséquence à vérifier, pas à supposer

`--bind-services` n'est pas activé, ce qui écarte les fournisseurs de services :
`jdk.charsets`, `jdk.localedata`, `jdk.crypto.ec`. L'image n'a donc que les jeux de
caractères de base et les données de locale minimales.

Vérification faite, c'est sans conséquence pour les jeux de caractères et les locales : les
huit points du code qui touchent à un encodage passent tous explicitement par
`StandardCharsets.UTF_8`, aucun n'appelle `Charset.forName` ni ne dépend du jeu par défaut ;
et la recherche insensible à la casse s'appuie sur `String.regionMatches(true, …)`,
indépendant de la locale.

Le fournisseur de système de fichiers ZIP, lui, **manquait bel et bien** — c'est l'objet de
la section précédente. La leçon est que cette analyse ne se fait pas sur pièces mais à
l'exécution : c'est le pas de fumée du § 9 qui tranche, et lui seul.

## 7. `bin/package`

Un troisième programme JEP 330, frère de `bin/build` et `bin/test`, exécuté depuis la
racine du projet, avec son `.cmd` pour Windows. Une différence de statut, qui justifiera
plus bas une entorse : les deux premiers servent au développement quotidien et ne dépendent
de rien ; celui-ci sert à publier, et tourne essentiellement en CI.

1. **Déléguer à `bin/build`** (`java --source 17 bin/build`). Aucune duplication : il
   produit déjà la compilation modulaire dans `out/` — dont `jlink` a besoin — *et* le jar
   universel. `jlink` exige la sortie **modulaire**, pas le jar classpath aplati de la
   troisième étape ; les deux artefacts continuent donc de coexister, pour deux publics.

   **`bin/build` doit d'abord être corrigé : ses deux `javac` ne passent aucun
   `--release`.** Le jar hérite donc de la version du JDK qui l'a construit — mesuré ici,
   `major version: 70`, soit **Java 26**, alors que le README annonce 17+. Publié depuis un
   runner en 25, le bundle léger réclamerait un JDK 25, ce qui viderait de son sens le
   « qui a déjà un JDK 17+ » du § 2.

   Le correctif est `--release 17` sur les deux compilations, vérifié : la compilation passe
   sans erreur et produit `major version: 61`. Bénéfice secondaire, et il n'est pas mince :
   `--release` restreint aussi la **surface d'API** à celle de Java 17, donc un usage
   accidentel d'une méthode plus récente échoue désormais sur le poste du développeur, et
   non plus seulement dans la branche 17 de la matrice CI. L'accès réflexif à
   `Console.isTerminal()` (Java 22+) n'en souffre pas : ce n'est pas une référence de
   compilation.
2. **`jlink` en process**, via `ToolProvider` comme `javac` et `jar` (vérifié : il y est
   disponible, aucun processus externe à lancer) :

   ```
   --module-path out
   --add-modules fr.craft.linkedinarchiveexplorer.app
   --launcher linkedin-archive-explorer=fr.craft.linkedinarchiveexplorer.app/….App
   --strip-debug --no-header-files --no-man-pages --compress=zip-6
   --output dist/image
   ```

   `--launcher` génère `bin/linkedin-archive-explorer` : c'est *le* binaire.
3. **Empaqueter les deux bundles** :
   - le complet, en `dist/linkedin-archive-explorer-<version>-<os>-<arch>.<ext>`, l'OS et
     l'architecture venant de `os.name` / `os.arch`, normalisés en `linux-x64`,
     `macos-aarch64`, `windows-x64` ;
   - le léger, en `dist/linkedin-archive-explorer-<version>-lite.tar.gz` **et** `.zip`,
     sans suffixe de plateforme puisqu'il n'en dépend pas.

   Les deux lanceurs du bundle léger sont écrits par `bin/package` lui-même — deux textes de
   deux lignes, plus courts que n'importe quel gabarit qu'on irait lire dans un fichier.
4. **Écrire l'empreinte de chaque archive**, dans un `<archive>.sha256` à côté d'elle.

### Les empreintes

`MessageDigest.getInstance("SHA-256")` est dans `java.base` : `bin/package` calcule les
empreintes lui-même, sans appeler `sha256sum` ni `shasum` — dont les sorties diffèrent
d'ailleurs entre Linux et macOS. Un fichier par archive plutôt qu'un `SHA256SUMS` unique,
parce que les archives naissent sur trois runners différents : un fichier global obligerait
à le rassembler dans le poste de publication, pour un confort nul côté utilisateur.

Format retenu, celui que `sha256sum -c` sait relire :

```
9f2c…e1  linkedin-archive-explorer-1.0.0-linux-x64.tar.gz
```

**Ce que ça apporte, et ce que ça n'apporte pas.** Une empreinte détecte un téléchargement
corrompu et confirme qu'on tient le fichier annoncé par la page. Elle ne protège **pas**
d'une page de release compromise — qui publierait le fichier *et* son empreinte. C'est une
somme de contrôle, pas une signature ; la confondre avec une garantie d'origine serait se
raconter une histoire. À ce prix-là (quelques lignes, aucune dépendance), elle vaut quand
même largement d'être là.

Deux mécanismes de plus fort niveau sont écartés, chacun pour sa raison, au § 11.

### Le format d'archive : le `tar` du système

Mesuré : **le zip du JDK perd le bit exécutable**, tar le conserve.

```
avant     : -rwxrwxr-x
après jar : -rw-rw-r--
après tar : -rwxrwxr-x
```

Or le lanceur généré et le `bin/java` de l'image en ont besoin. `java.util.zip` n'expose pas
les attributs Unix, donc le zip est hors jeu sur Unix.

**On appelle donc le `tar` du système**, par `ProcessBuilder`, plutôt que d'écrire un
producteur ustar à la main.

C'est une entorse à la règle qui gouverne `bin/build` et `bin/test` — hermétiques, sans rien
d'externe. Elle est assumée parce que **`bin/package` n'est pas un script de développement
quotidien** : c'est un outil de publication, exécuté par la CI sur des runners qui ont tous
`tar`. La contrainte d'autonomie protège le développeur qui clone le dépôt et veut compiler
et tester sans rien installer ; elle ne protège personne sur un poste de release.

L'alternative — cinquante lignes d'en-têtes ustar, plus leurs tests, à maintenir pour
toujours — coûtait davantage que ce qu'elle achetait.

Trois conséquences à connaître :

- **macOS utilise `bsdtar`, pas GNU tar.** `tar czf` fonctionne sur les deux ; les options
  fines (`--sort`, `--mtime`) n'existent que sur GNU et ne sont donc pas utilisées. On
  positionne `COPYFILE_DISABLE=1` sur le poste macOS, sans quoi `bsdtar` ajoute des fichiers
  `._*` d'attributs étendus dans l'archive.
- **Les archives ne sont pas reproductibles à l'octet** (horodatages). Personne ne le
  demande ; le jour où ce serait le cas, GNU tar sait le faire et macOS non, ce qui rouvrirait
  la question.
- **`bin/package` reste utilisable localement** : sous Unix `tar` est là, et sous Windows le
  chemin emprunté est celui du zip, qui ne l'appelle jamais.

Alternative écartée : **zip partout, avec un `chmod +x` à documenter**. La première chose
que ferait l'utilisateur d'un outil vendu comme simple serait de réparer des permissions.

Windows reçoit un **zip**, où la question ne se pose pas : `--launcher` y génère un `.bat`,
qui n'a pas besoin de bit exécutable — et `java.util.zip` suffit, sans rien appeler dehors.

## 8. Le workflow de release

Un `.github/workflows/release.yml` déclenché sur un tag `v*`. **La CI existante ne bouge
pas** : elle continue de tester sur JDK 17 et 25 à chaque poussée.

Une matrice de trois runners, chacun produisant l'image de **son propre** OS — `jlink` ne
croise les plateformes qu'avec le `jmods` d'un JDK étranger, et la matrice supprime ce
besoin :

| Runner | Artefact |
|---|---|
| `ubuntu-latest` | `…-linux-x64.tar.gz` |
| `macos-latest` | `…-macos-aarch64.tar.gz` |
| `windows-latest` | `…-windows-x64.zip` |

Chaque poste enchaîne `bin/test`, `bin/package`, puis le pas de fumée du § 9.

**Le bundle léger ne prend pas de poste à lui seul** : il ne dépend d'aucune plateforme, il
est donc produit par le poste Linux, qui le construit de toute façon, et publié tel quel.
Un dernier poste attache l'ensemble à la release avec le `gh` déjà présent sur les
runners — **aucune action tierce**, dans l'esprit du projet.

La page de release doit dire lequel prendre, et c'est le seul texte qui compte pour
quelqu'un qui arrive de l'extérieur : le bundle de son OS s'il ne sait pas ce qu'est un JDK,
le bundle léger s'il en a déjà un. Les `.sha256` du § 7 y sont attachés à côté de leur
archive, avec la commande de vérification — une empreinte que personne ne sait vérifier ne
sert à rien.

**Le JDK de construction est le 25** (LTS, déjà couvert par la CI) : c'est lui qui finit
embarqué dans l'image. Le plancher **17 reste celui du code source**, vérifié par la CI ;
les deux ne sont pas la même chose et ne bougent pas ensemble.

La version ne sert qu'à nommer les fichiers, lue depuis le tag. Pas de `--version` dans le
programme tant que personne ne le demande — l'ajouter voudrait dire injecter une valeur à la
construction, donc un mécanisme de plus.

## 9. Tests (TDD, red → green → refactor)

Trois niveaux, dont un que le projet n'a pas encore.

### `AppTest` — le routage, sans processus

`route(args)` étant une fonction pure, un `@ParameterizedTest` couvre les cas qui ne
diffèrent que par les entrées :

| Arguments | Cible | Arguments transmis |
|---|---|---|
| *(aucun)* | `SERVE` | *(aucun)* |
| `serve` | `SERVE` | *(aucun)* |
| `serve --port 9000` | `SERVE` | `--port 9000` |
| `kotlin` | `CLI` | `kotlin` |
| `-i kotlin` | `CLI` | `-i kotlin` |
| `--archive x.zip kotlin` | `CLI` | `--archive x.zip kotlin` |
| `--no-browser` | `CLI` | `--no-browser` |

La dernière ligne n'est pas un oubli : elle verrouille la règle bête du § 4, et le message
d'usage qui s'ensuit.

### L'ouverture du navigateur, sur un faux écrit à la main

Pas de framework de mock : un faux sur le port d'ouverture, comme partout ailleurs dans le
projet. Trois cas — la commande choisie selon l'OS, un échec qui ne remonte jamais et laisse
le serveur debout, et `--no-browser` qui saute l'étape.

### Le pas de fumée sur l'image produite

**C'est le seul test qui prouve que la fermeture jlink est complète.** Un jeu de caractères
manquant, une donnée de locale absente, un service non lié : rien de tout cela ne se voit
sur le classpath, seulement à l'exécution du binaire. Dans le workflow, après
`bin/package` :

- lancer le binaire généré sur une archive de test, et vérifier la sortie ;
- le lancer en `serve --no-browser`, et vérifier qu'il annonce son port.

Ce n'est pas un test JUnit — il porte sur un artefact, pas sur du code — mais c'est lui qui
maintient vraie la conclusion du § 6.

### Inchangés

Toutes les suites existantes. Le contenu de `domain`, `application` et `infrastructure` n'est
pas touché ; `cli` et `web` ne gagnent que leur export qualifié et, pour `web`, l'ouverture
du navigateur.

**Architecture** — `bin/test` compile `src` en modules : l'absence d'arête entre `cli` et
`web`, et le fait que `app` soit le seul à les requérir tous deux, sont vérifiés à la
compilation, sans test dédié.

## 10. Limites connues

À écrire noir sur blanc plutôt qu'à découvrir après publication.

- **macOS bloquera un binaire non signé.** Une archive téléchargée par un navigateur reçoit
  l'attribut `com.apple.quarantine`, et Gatekeeper refusera le runtime embarqué. Le remède
  est soit `xattr -d -r com.apple.quarantine <dossier>`, à documenter, soit la notarisation
  — qui suppose un compte Apple Developer payant. Pour un outil personnel, la première voie
  suffit ; il faut juste ne pas prétendre le contraire.
- **Windows affichera un avertissement SmartScreen** au premier lancement d'un `.bat` non
  signé. Sans conséquence, mais l'utilisateur doit s'y attendre.
- **Le « double-clic » n'est pas acquis partout.** `--launcher` produit un script shell sous
  Unix et un `.bat` sous Windows. Windows lance bien le `.bat` au double-clic ; sous macOS et
  Linux, un script sans extension s'ouvre plus volontiers dans un éditeur. Piste peu coûteuse
  à confirmer : livrer en plus, sous macOS, un `linkedin-archive-explorer.command` d'une
  ligne — cette extension-là se lance dans Terminal au double-clic. **À vérifier sur une
  vraie machine avant de le promettre.**
- **Pas de macOS Intel** dans la matrice initiale. `macos-13` l'ajouterait en trois lignes
  le jour où le besoin apparaît.

## 11. Délibérément laissé de côté

- **`jpackage` et les installeurs natifs** (§ 2) — outils par plateforme et signature.
- **La compilation croisée des images** (`--module-path <jdk étranger>/jmods`) : la matrice
  de runners fait le même travail sans gérer de JDK téléchargés à la main.
- **`--version`, l'auto-mise à jour, Homebrew, winget** — chacun un mécanisme entier pour un
  outil qui n'a pas encore d'utilisateur au-delà de son auteur.
- **Réduire l'image sous les 43 Mo** : la mesure du § 6 montre qu'il n'y a rien à y gagner
  tant qu'on embarque une VM.
- **La signature GPG ou minisign des archives.** Elle protège d'un canal de distribution
  compromis, mais seulement pour qui détient la clé publique et la vérifie. Sans écosystème
  de clés établi autour du projet, c'est publier un cadenas dont personne n'a la serrure —
  et une clé privée de plus à garder en secret dans la CI. Les empreintes du § 7 couvrent le
  cas réel (téléchargement corrompu) ; le reste est du théâtre tant qu'il n'y a pas de
  vérificateur.
- **L'attestation de provenance GitHub** (`actions/attest-build-provenance`, signature sans
  clé via Sigstore, vérifiable par `gh attestation verify`). Techniquement la meilleure
  réponse au problème que GPG rate, et sans clé à gérer — mais c'est une action externe,
  alors que le workflow n'en emploie aucune. À rouvrir le jour où le projet a des
  utilisateurs qui ne sont pas son auteur.
- **La signature de code** (Authenticode, notarisation Apple) : la seule qui ferait taire
  les avertissements du § 10, et la seule qui coûte de l'argent — compte Apple Developer
  annuel, certificat Windows. Hors sujet pour un outil personnel.
- **Un troisième adaptateur d'UI.** Le module `app` en accueillerait un sans que `cli` ni
  `web` bougent ; c'est la couture, pas le sujet.

## 12. Documentation à mettre à jour

- **`CLAUDE.md`** : le module `app` dans le schéma d'architecture, le fait que le `serve`
  n'est plus dispatché par le script de lancement, et `bin/package` dans les *Commands*.
  La section décrit les scripts comme « pure-JDK » : il faudra y distinguer les deux qui le
  restent — `bin/build` et `bin/test` — de `bin/package`, qui appelle le `tar` du système
  parce qu'il publie plutôt qu'il ne développe (§ 7).
- **`README.md`** : une section « Installation » — télécharger, décompresser, lancer —
  présentant les **deux** bundles et le critère de choix, plus la note macOS du § 10. Le
  prérequis « JDK 17+ » du bundle léger y devient une promesse tenue, ce qu'il n'était pas
  avant le correctif `--release` du § 7.
- **[UI web](2026-07-31-web-ui-design.md)** : son § 9 écartait l'ouverture automatique du
  navigateur ; la noter comme réalisée ici, avec le pourquoi (le contexte a changé : un
  binaire lancé sans terminal n'a personne pour cliquer sur l'URL).
