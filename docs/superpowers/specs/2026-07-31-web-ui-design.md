# UI web — Design

_Date : 2026-07-31_

Extension de [l'outil d'exploration d'archive LinkedIn](2026-07-22-linkedin-archive-explorer-design.md),
qui réutilise les [options de recherche](2026-07-24-search-options-design.md) telles
quelles. À lire en complément de ces deux designs.

## 1. Objectif

Chercher dans l'archive **depuis une page web** et y lire les résultats, sans rien
changer au CLI ni au cœur de recherche.

Le design initial annonçait deux coutures d'extension, dont la première : « le cœur de
recherche (`domain` + `application`) est indépendant du CLI, donc une future UI web peut
le réutiliser inchangé. » Ce document réalise cette promesse. Elle est tenue **sans
modifier une ligne de `domain`, `application` ou `infrastructure`** — c'est le critère de
succès du travail.

## 2. La décision qui gouverne tout : `jdk.httpserver`

Le JDK embarque un serveur HTTP : `com.sun.net.httpserver.HttpServer`, module
`jdk.httpserver`, présent depuis Java 6 dans **tous** les JDK.

Ce package n'est pas un package `java.*`. La contrainte du projet devient donc :

> **JDK-only : aucun artefact externe.** Le code applicatif n'utilise que des API livrées
> avec le JDK. `java.*` (Java SE) reste la règle ; la seule exception est
> `com.sun.net.httpserver` (module `jdk.httpserver`), API JDK-specific documentée et
> stable, utilisée **uniquement dans le module `web`**.

C'est l'esprit de la règle d'origine — *rien à télécharger, rien à builder* — plutôt que
sa lettre. Aucun jar ne s'ajoute à `lib/`, aucun build tool n'apparaît, et les trois
modules du cœur restent strictement `java.*`.

Deux alternatives ont été écartées :

- **Écrire un serveur HTTP à la main sur `java.net.ServerSocket`** : parsing de requête,
  réponses, keep-alive — du code non trivial à écrire et à tester, pour une pureté
  d'import discutable.
- **Générer un HTML statique depuis le CLI** (`--html`) : zéro serveur, mais aussi zéro
  champ de recherche — il faudrait relancer le CLI à chaque terme, ce qui n'est pas
  l'objectif.

### Ce qui n'est pas utilisable

- **`jwebserver` / JEP 408** (Java 18+) ne sert que des **fichiers statiques** : hors
  sujet pour une recherche dynamique.
- Les fabriques **`HttpHandlers`** et **`SimpleFileServer`** sont Java 18+. Le projet
  garde son plancher **JDK 17** : on n'utilise que l'API `HttpServer` / `HttpHandler` /
  `HttpExchange`, disponible depuis Java 6.

### Résolution du module à l'exécution

Le jar tourne en **classpath** (les `module-info.class` sont ignorés à l'exécution, cf.
design initial). Le code du classpath vit dans le module sans nom, dont l'ensemble racine
par défaut inclut tout module système exportant au moins un package sans qualification —
ce qui est le cas de `jdk.httpserver`. Aucun `--add-modules` ne devrait donc être
nécessaire.

**À vérifier à la première exécution.** Si la résolution échoue, le repli est une seule
option dans le script de lancement : `--add-modules jdk.httpserver`.

## 3. Architecture — un module pair du CLI

Un cinquième module, **frère** du module `cli`, pas son client :

```
fr.craft.linkedinarchiveexplorer.domain          requires nothing
fr.craft.linkedinarchiveexplorer.application     requires domain
fr.craft.linkedinarchiveexplorer.infrastructure  requires domain
fr.craft.linkedinarchiveexplorer.cli             requires les trois ci-dessus   (inchangé)
fr.craft.linkedinarchiveexplorer.web             requires les trois ci-dessus + jdk.httpserver
```

**Aucune arête entre `cli` et `web`.** Ce sont deux adaptateurs UI jumeaux, chacun sa
racine de composition — exactement ce que la couture n°1 promettait. Le dispatch entre
les deux se fait dans le script de lancement (§ 6), pas en Java : c'est le seul moyen
d'offrir une commande unique **sans** faire dépendre une UI de l'autre.

```
src/fr.craft.linkedinarchiveexplorer.web/
  module-info.java
  fr/craft/linkedinarchiveexplorer/web/
    WebMain.java          racine de composition : arguments, ouverture du zip, démarrage
    SearchHandler.java    HttpHandler : requête → SearchTerm → service → HTML → réponse
    HtmlRenderer.java     SearchTerm + SearchResults → String (fonction pure)
    QueryParameters.java  décodage de la query string
```

```java
module fr.craft.linkedinarchiveexplorer.web {
  requires fr.craft.linkedinarchiveexplorer.domain;
  requires fr.craft.linkedinarchiveexplorer.application;
  requires fr.craft.linkedinarchiveexplorer.infrastructure;
  requires jdk.httpserver;
}
```

`bin/build` et `bin/test` compilent déjà `$(find src -name '*.java')` avec
`--module-source-path src` : le nouveau module est pris **automatiquement**, et
`requires jdk.httpserver` est vérifié à la compilation comme toutes les autres arêtes.

### Les quatre classes

**`HtmlRenderer`** — jumeau exact de `TerminalRenderer` : il prend un `SearchTerm` et un
`SearchResults` et rend une `String`. Aucune I/O, aucune dépendance HTTP. C'est une
fonction pure, donc c'est là que porte l'essentiel des tests.

**`QueryParameters`** — décodage de la query string : `%C3%A9`, `+`, paramètre absent,
paramètre dupliqué, `=` manquant. Petit et tordu, donc isolé dans son propre type et testé
seul plutôt que noyé dans le handler.

**`SearchHandler`** — l'`HttpHandler`. Traduit une requête en `SearchTerm`, appelle
`SearchContentsService`, passe le résultat au `HtmlRenderer`, écrit la réponse. Il ne
contient ni logique de recherche ni logique de rendu.

**`WebMain`** — racine de composition, l'exact pendant de `Main` : lit les arguments,
localise l'archive, ouvre le `ZipArchive`, câble les trois `ContentSource` et le
`SearchContentsService`, démarre le serveur. Expose une méthode package-private qui
démarre le serveur et **retourne le port effectivement lié**, pour que les tests puissent
utiliser le port éphémère (§ 8).

## 4. La page

### Routes

| Requête | Réponse |
|---|---|
| `GET /` | 200 — formulaire vide, aucune recherche exécutée |
| `GET /?q=<terme>[&i=on][&w=on]` | 200 — formulaire pré-rempli + résultats |
| `GET /?q=` (vide ou blanc) | 200 — identique à `GET /` |
| tout autre chemin | 404, texte brut |
| erreur pendant le traitement | 500 + page minimale, **le serveur survit** |

L'état de la recherche vit **entièrement dans l'URL**. Deux conséquences gratuites : une
recherche est partageable et bookmarkable, et le bouton « précédent » du navigateur
devient un historique de recherches.

Le formulaire est un `GET` : le champ texte `q`, deux cases à cocher `i` et `w` — qui
correspondent exactement à `-i`/`--ignore-case` et `-w`/`--word` du CLI. Une case cochée
émet `on`, décochée n'émet rien : c'est la sémantique HTML standard, et elle donne des URL
lisibles.

### Contenu

Les résultats sont groupés par type dans l'ordre canonique (`ContentType.values()`), avec
le **nombre de résultats** dans le titre du groupe :

```
ARTICLES (3)
POSTS (12)
COMMENTS (41)
```

Chaque résultat : sa date entre crochets quand elle existe (les articles n'en ont pas), un
lien `<a href>` vers LinkedIn, puis ses extraits. Dans chaque extrait, l'occurrence est
enveloppée dans un **`<mark>`** — l'élément HTML sémantique pour un résultat de recherche
mis en évidence, qui se surligne tout seul sans une ligne de CSS.

**Le texte de l'interface est en anglais**, comme celui du CLI : les mêmes titres de
groupe (`ARTICLES`, `POSTS`, `COMMENTS`), et le même message quand rien ne sort —
`No results for "<terme>".`. Deux UI du même outil ne parlent pas deux langues.

Un pied de page affiche l'archive utilisée, en écho au `Using archive: …` du CLI : utile
quand plusieurs exports traînent dans `data/`.

Une balise `<style>` inline, sobre, d'une vingtaine de lignes. **Aucun JavaScript**, aucun
fichier statique à servir, donc aucune route de fichiers et aucun risque de traversée de
chemin.

## 5. Cycle de vie et concurrence

`WebMain` ouvre le `ZipArchive` **une fois** au démarrage et construit les trois
`ContentSource` et le `SearchContentsService` **une fois**.

Le serveur tourne avec **`setExecutor(null)`** : le JDK traite alors les requêtes
**séquentiellement**, sur le thread du serveur. Un seul utilisateur, en local — donc aucune
question de concurrence sur le `ZipFile` partagé, et pas un verrou à écrire. C'est la
configuration la plus simple qui marche, et elle marche.

Chaque requête refait `load()` puis `search()`, exactement comme une exécution du CLI.
**Pas de cache** : les [benchmarks JMH](2026-07-28-jmh-benchmarks-design.md) mesurent
~10,5 ms bout-en-bout, dont ~10,1 ms de chargement. C'est imperceptible pour un humain
devant un navigateur, et le cache serait de la complexité pure. Si la mesure change un
jour, `SearchContentsService` est le point d'insertion évident.

Arrêt par Ctrl-C ; un shutdown hook ferme le `ZipArchive`.

## 6. Lancement

```bash
./linkedin-archive-explorer serve [--archive <path>] [--port <n>]
```

Le script détecte `serve` en **premier argument**, le retire, et lance la classe main du
module `web` avec le reste des arguments. Sans `serve`, le comportement actuel est
**strictement inchangé** — aucune régression possible sur le CLI. Un `.cmd` équivalent
pour Windows, comme pour les autres scripts.

- **`--port`** : port d'écoute, **8080 par défaut**.
- **`--archive`** : même sémantique que le CLI (défaut : le `.zip` le plus récent de
  `data/`).

**Pas de repli automatique sur un autre port.** 8080 est le port le plus disputé qui soit ;
si un serveur démarrait silencieusement ailleurs que là où l'utilisateur l'attend, ce
serait pire qu'un échec net. Le message nomme le problème et la sortie :

```
Port 8080 already in use — try: ./linkedin-archive-explorer serve --port 8081
```

Au démarrage, le serveur imprime son URL sur la sortie standard. Il n'ouvre **pas** le
navigateur lui-même : cela impliquerait `java.awt.Desktop`, donc le module `java.desktop`,
pour un comportement fragile en headless.

## 7. Sécurité et vie privée

Ce sont les deux points non négociables du design, parce que le rendu serveur d'un contenu
personnel les rend tous deux réels.

### Écoute sur la loopback uniquement

Le serveur se lie à `InetAddress.getLoopbackAddress()` — **jamais** `0.0.0.0`. Une archive
LinkedIn est un export personnel : elle n'a rien à faire sur le réseau local, encore moins
sur un réseau partagé. C'est un argument de constructeur, pas une option en ligne de
commande : il n'y a pas de raison légitime de l'ouvrir.

### Échappement HTML systématique

Tout ce qui vient de l'archive **et** le terme recherché sont échappés (`&`, `<`, `>`,
`"`) avant insertion dans la page. Sans cela, un post de l'utilisateur contenant
`<script>` s'exécuterait dans sa propre page : l'archive contient du texte rédigé par un
humain, y compris, dans un projet de développeur, du code.

L'échappement est la responsabilité du `HtmlRenderer`, appliqué à **chaque** insertion —
texte des extraits, terme, et URL en valeur d'attribut `href`. Il est couvert par ses
propres tests (§ 8), pas seulement par inspection.

## 8. Tests (TDD, red → green → refactor)

Un diamant, comme le reste du projet : le poids sur les tests sociables, la vraie frontière
d'I/O maîtrisée, aucun framework de mock.

**`HtmlRendererTest`** — le gros du poids, calqué sur `TerminalRendererTest`. Classes
`@Nested` par facette, fixtures inline :

- *Grouping* — ordre canonique des types, compteur par groupe, groupe vide absent.
- *HitRendering* — date présente / absente, lien, extraits multiples d'un même contenu.
- *Highlighting* — l'occurrence est dans un `<mark>`, le reste de l'extrait n'y est pas.
- *Escaping* — un contenu portant `<script>`, `&`, `"` ressort échappé ; idem pour le
  terme recherché et pour l'URL en attribut.
- *EmptyResults* — le message « aucun résultat » et le formulaire toujours présent.

**`QueryParametersTest`** — un `@ParameterizedTest` avec `name =` sur les cas de décodage :
terme accentué (`%C3%A9`), espace en `+` et en `%20`, paramètre absent, valeur vide,
paramètre dupliqué, `=` manquant.

**`WebAcceptanceTest`** — sociable, jumeau de `MainAcceptanceTest` : construit une archive
zip à la volée, démarre `WebMain` sur le **port 0** (le système attribue un port libre,
donc aucune collision en CI ni avec un 8080 déjà occupé), interroge le serveur avec
`java.net.http.HttpClient`, vérifie le HTML rendu, puis arrête le serveur. Couvre le
câblage réel : `HttpServer` + handler + renderer + zip. Au moins : une recherche avec
résultats, une sans résultat, une avec `i=on`, et un chemin inconnu en 404.

**Architecture** — `bin/test` compile `src` en modules : l'absence d'arête `cli ↔ web` et
la présence de `requires jdk.httpserver` sont vérifiées à la compilation, sans test dédié.

## 9. Délibérément laissé de côté

- **Pagination et plafond de résultats** — la page défile ; on verra si ça gêne un jour.
- **Cache** — les benchmarks disent que ça n'en vaut pas la peine (§ 5).
- **Sélecteur d'archive dans la page** — impliquerait d'ouvrir et fermer des `ZipArchive`
  au fil des requêtes, donc de l'état mutable partagé, pour un besoin rare : redémarrer
  avec `--archive` suffit.
- **JavaScript** (recherche au fil de la frappe, rechargement partiel) — du code front non
  couvert par la suite JUnit, pour un confort marginal sur une page qui répond en ~10 ms.
- **API JSON** — il faudrait sérialiser à la main, sans bibliothèque, et versionner un
  contrat. Le jour où un vrai client la réclame, le `SearchHandler` est le bon endroit.
- **Ouverture automatique du navigateur** (§ 6), **HTTPS**, **authentification** — sans
  objet pour un serveur en loopback.

## 10. Documentation à mettre à jour

- **`CLAUDE.md`** : la contrainte reformulée (§ 2), le module `web` dans le schéma
  d'architecture, la commande `serve` dans la section *Commands*.
- **[Design initial](2026-07-22-linkedin-archive-explorer-design.md)** : la couture
  d'extension n°1 cesse d'être une promesse — la noter comme réalisée, avec un pointeur
  vers ce document.
