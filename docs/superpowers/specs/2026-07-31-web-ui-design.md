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

**Passer le plancher à 21 pour utiliser ces fabriques a été envisagé, puis écarté** — le
gain mesuré est nul :

- `SimpleFileServer` sert des fichiers depuis un répertoire. Le design n'en a **aucun**
  (CSS inline, zéro JS) : inapplicable, et cette absence est un bénéfice de sécurité (§ 4).
- `HttpHandlers.of(status, headers, body)` produit une réponse **constante** ; la nôtre est
  le résultat d'une recherche : inapplicable.
- `HttpHandlers.handleOrElse` et la fabrique `HttpServer.create(addr, backlog, path,
  handler, filters…)` économiseraient, ensemble, trois ou quatre lignes de routage.

Ce qui justifierait 21 serait les **threads virtuels** — sans objet ici, puisque le
traitement est délibérément séquentiel (§ 5). Le plancher reste donc 17, et il ne coûte
rien : ni `bin/build` ni `bin/test` ne passent de `--release`, la compilation utilise le
JDK présent (26 via `mise.toml`).

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
racine de composition — exactement ce que la couture n°1 promettait.

Le dispatch entre les deux se fait dans le lanceur `linkedin-archive-explorer` (§ 6).
Celui-ci est lui-même écrit en Java, mais c'est un **programme mono-fichier JEP 330, hors
du graphe de modules** : il ne fait que choisir la classe main passée à `java`. C'est ce
qui permet d'offrir une commande unique **sans** faire dépendre une UI de l'autre.

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

### Structure sémantique

Le HTML porte le sens, pour que le CSS n'ait presque rien à faire :

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>café — LinkedIn archive explorer</title>
</head>
<body>
  <header>
    <h1>LinkedIn archive explorer</h1>
    <search>
      <form method="get" action="/">
        <label for="q">Search</label>
        <input type="search" id="q" name="q" value="café" autofocus>
        <label><input type="checkbox" name="i" checked> Ignore case</label>
        <label><input type="checkbox" name="w"> Whole word</label>
        <button type="submit">Search</button>
      </form>
    </search>
  </header>

  <main>
    <section aria-labelledby="comments">
      <details open>
        <summary><h2 id="comments">Comments <span class="count">(41)</span></h2></summary>
        <ol>
          <li>
            <article>
              <h3><a href="https://li/1">https://li/1</a></h3>
              <time datetime="2024-11-06">2024-11-06</time>
              <ul>
                <li>…un <mark>café</mark> serré…</li>
              </ul>
            </article>
          </li>
        </ol>
      </details>
    </section>
  </main>

  <footer>Archive: data/export.zip</footer>
</body>
</html>
```

Les choix qui portent quelque chose :

- **`<search>`** plutôt que `<form role="search">` — l'élément dédié au repère de
  recherche, largement supporté depuis 2023.
- **`<ol>` pour les résultats d'un groupe** : leur ordre est *signifiant* (date
  décroissante, comme le CLI). Une liste non ordonnée mentirait sur la donnée.
- **`<article>` par résultat** — un post ou un commentaire est exactement ce que la
  spécification HTML appelle « une composition autonome ».
- **`<time datetime="…">`** au lieu des crochets `[2024-11-06]` du terminal : la date
  devient lisible par une machine.
- **`<mark>`** autour de l'occurrence — l'élément sémantique du résultat de recherche mis
  en évidence, qui se surligne sans une ligne de CSS.
- **`<title>` portant le terme** (`café — LinkedIn archive explorer`) : prolonge la
  décision « l'état vit dans l'URL » jusqu'à l'historique et aux onglets du navigateur.
- **`aria-labelledby`** relie chaque `<section>` à son `<h2>`, donc chaque groupe devient
  un repère navigable au lecteur d'écran.

Deux appels au jugement, assumés : les **extraits** d'un même résultat sont un `<ul>` et
non un `<ol>` — rien dans l'UI ne désigne « le 3ᵉ extrait », donc les numéroter
n'apporterait rien ; et le **lien est le `<h3>`** du résultat, ce qui donne un plan de
document navigable au prix d'un titre qui est une URL brute.

### Groupes repliables

Le nombre de résultats est imprévisible : un terme courant peut en produire des centaines,
et le groupe `COMMENTS` enterrerait alors les deux autres. Chaque groupe est donc
**repliable**, via `<details open>` / `<summary>` — de l'HTML pur, ce qui préserve la
décision « aucun JavaScript ».

- **Tous les groupes sont ouverts au chargement.** Pas de seuil du genre « replier
  au-delà de 20 résultats » : ce serait un nombre magique invérifiable, et une page qui
  cache des résultats sans qu'on le lui ait demandé.
- **L'état ouvert/replié ne survit pas à une nouvelle recherche** — chaque soumission est
  un chargement de page complet, et le conserver demanderait du JS ou des paramètres d'URL
  supplémentaires. C'est le prix assumé du zéro-JS.
- Le **compteur dans le `<summary>`** prend ici tout son sens : replié, un groupe annonce
  encore ce qu'il contient. Tous groupes repliés, les `<summary>` forment un sommaire.
- Note : Chromium déplie automatiquement un `<details>` fermé lors d'une recherche
  Ctrl+F. Ce n'est **pas** un comportement universel entre navigateurs — c'est un bonus,
  pas une garantie sur laquelle s'appuyer.

### Texte

**L'interface est en anglais**, comme le CLI : les mêmes titres de groupe (`Articles`,
`Posts`, `Comments`), et le même message quand rien ne sort — `No results for "<terme>".`.
Deux UI du même outil ne parlent pas deux langues.

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

Le lanceur `linkedin-archive-explorer` détecte `serve` en **premier argument**, le retire,
et lance `java -cp dist/linkedin-explorer.jar
fr.craft.linkedinarchiveexplorer.web.WebMain` avec le reste des arguments — au lieu du
`java -jar` habituel, dont le `Main-Class` reste `cli.Main`. Sans `serve`, le comportement
actuel est **strictement inchangé** — aucune régression possible sur le CLI.

`linkedin-archive-explorer.cmd` se contente de déléguer au programme Java
(`java --source 17 linkedin-archive-explorer %*`) : **rien à y changer**, Windows suit
automatiquement.

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
- *HitRendering* — date en `<time datetime>` présente / absente, lien, extraits multiples
  d'un même contenu.
- *Highlighting* — l'occurrence est dans un `<mark>`, le reste de l'extrait n'y est pas.
- *Escaping* — un contenu portant `<script>`, `&`, `"` ressort échappé ; idem pour le
  terme recherché et pour l'URL en attribut.
- *Collapsing* — chaque groupe est un `<details open>` avec son compteur dans le
  `<summary>`.
- *Form* — le formulaire est pré-rempli par le terme, et les cases `i`/`w` sont `checked`
  exactement quand les options correspondantes sont actives.
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
