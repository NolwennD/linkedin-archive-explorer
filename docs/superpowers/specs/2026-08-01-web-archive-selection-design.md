# Choix de l'archive dans l'UI web — Design

_Date : 2026-08-01_

Extension de [l'UI web](2026-07-31-web-ui-design.md), qui s'appuie sur la
[racine de composition](2026-07-31-composition-root-design.md). À lire en complément de
ces deux designs.

Prérequis d'un second chantier — la publication d'un **binaire exécutable** réunissant
CLI et web — qui fera l'objet de sa propre spec. Ce document ne traite que de la
sélection d'archive.

## 1. Objectif

Choisir **depuis la page** l'archive dans laquelle on cherche, et retenir ce choix d'une
session à l'autre. Au démarrage, le champ est prérempli avec la plus récente de `data/` ;
si `data/` est vide, il reste vide et la page demande un chemin.

Le CLI ne bouge pas — c'est un critère de succès, pas un effet de bord.

## 2. Ce que ce document renverse

La spec de l'UI web avait explicitement écarté le sélecteur d'archive :

> **Sélecteur d'archive dans la page** — impliquerait d'ouvrir et fermer des `ZipArchive`
> au fil des requêtes, donc de l'état mutable partagé, pour un besoin rare : redémarrer
> avec `--archive` suffit.

Le raisonnement était juste dans son contexte, et c'est ce contexte qui change.
« Redémarrer avec `--archive` » suppose un terminal, un répertoire courant, et un
utilisateur qui connaît le chemin de son archive. Le binaire à venir se lance d'un
double-clic et ouvre un navigateur : il n'y a plus ni terminal où retaper une option, ni
répertoire courant qui veuille dire quelque chose. Le besoin cesse d'être rare le jour où
c'est le seul moyen.

L'état mutable, lui, reste bien réel — il est traité au § 3.5, et le coût est plus faible
que prévu parce que le serveur est séquentiel.

## 3. Architecture — un catalogue dans `launcher`

### 3.1 Le type

```java
// fr.craft.linkedinarchiveexplorer.launcher — exporté
public final class ArchiveCatalog implements AutoCloseable {

  /** data/, plus le fichier de --archive quand il est donné (ouvert sur-le-champ, § 6). */
  public static ArchiveCatalog of(Path explicitArchive);
  public static ArchiveCatalog of(Path explicitArchive, Path directory);

  /** Les archives à suggérer, plus récente d'abord. Relu à chaque appel. */
  public List<Path> archives();

  /** Le chemin à ouvrir pour cette requête (§ 5.3) ; vide quand il n'y en a aucun. */
  public Optional<Path> resolve(String fromQuery, String fromCookie);

  /** Le cœur câblé sur cette archive. Une seule archive reste ouverte à la fois. */
  public SearchContentsService serviceFor(Path archive);

  @Override public void close();
}
```

`Explorer` ne change pas : il reste « le cœur câblé sur **une** archive », et
`ArchiveCatalog` s'appuie dessus. Le catalogue répond à une autre question — *quelles
archives sont autour, et laquelle est ouverte*. Les deux responsabilités sont distinctes
et le restent.

Côté `infrastructure`, un seul ajout : `ArchiveLocator.all(directory)`, dont `mostRecent`
devient la tête. Le tri par date-dans-le-nom puis date de modification est déjà écrit et
ne bouge pas.

`WebMain` remplace son `Explorer.open(…)` par `ArchiveCatalog.of(…)`, le passe au
`SearchHandler`, et le referme dans le hook d'arrêt existant.

### 3.2 Pourquoi `launcher`, et pas `web`

Contrainte dure : **aucun module d'UI ne peut `requires infrastructure`**. Énumérer des
fichiers et ouvrir des zips est du ressort de `infrastructure` ; le seul module autorisé à
le nommer est `launcher`, la racine de composition. Mettre le catalogue ailleurs ne
compilerait pas — le module system tranche à notre place.

Bénéfice concret et non théorique : la règle de préséance (§ 5.3) devient testable **sans
HTTP ni serveur**. Si elle avait dû être vérifiée à travers une requête, ce serait le
signe qu'elle est au mauvais niveau.

Aucune arête de module n'est ajoutée : `web` requiert déjà `launcher`.

### 3.3 Le champ porte un chemin libre — ce que ça coûte

Le contrôle est un **champ texte**, pas une liste fermée (§ 4.1). Le serveur ouvre donc le
chemin qu'on lui donne, sans le confronter à un ensemble autorisé.

C'est un choix, et il a un prix qu'il vaut mieux écrire que découvrir : **le serveur peut
ouvrir n'importe quel fichier lisible par l'utilisateur.** Une page hostile ouverte dans un
autre onglet peut émettre une requête vers `localhost` — elle ne pourra pas en lire la
réponse (politique d'origine), mais le serveur agira. Le risque est faible et il n'est pas
nul.

Il est accepté parce que `--archive` offre déjà exactement cette capacité, sur un serveur
en loopback, pour un seul utilisateur, sur ses propres fichiers. La contrepartie obtenue
est décisive : une archive peut vivre **n'importe où**, ce qui est le cas normal dès lors
qu'un binaire est lancé hors du dépôt.

Une variante fermée avait été retenue puis abandonnée : la requête ne portait qu'un *nom
de fichier*, confronté à `archives()`, ce qui rendait une traversée de répertoire
impossible par construction. Elle tombe avec la liste déroulante, parce qu'elle interdit
précisément ce qu'on veut maintenant permettre.

### 3.4 Tout ce que le catalogue publie est absolu

`archives()` et `resolve` rendent des chemins **absolus et normalisés**, quelle que soit la
forme du répertoire de départ (`data/` est relatif) ou de ce que l'utilisateur a saisi.

Ce n'est pas une préférence d'écriture : ce que le catalogue publie **ne reste pas dans ce
processus**. Une suggestion est recollée dans le champ plus tard, et le cookie survit au
lancement — donc à son répertoire courant. `data/x.zip` désignerait alors ce que le
lancement suivant a sous les pieds, ou plus rien du tout ; le binaire de la spec B, lancé
d'un double-clic, n'a même pas de répertoire courant qui veuille dire quelque chose.

Un chemin relatif saisi dans la page est lu **par rapport au répertoire d'où tourne le
serveur** — la seule chose qu'il puisse raisonnablement signifier — puis rendu absolu.

**Une exception, délibérée** : l'archive de `--archive` est ouverte au démarrage *telle
qu'elle a été tapée*, pour que son diagnostic renvoie à l'utilisateur ce qu'il a écrit
(`Cannot open archive: test/data/corrupted.zip`) plutôt qu'un chemin absolu qu'il n'a jamais
saisi. Elle n'est absolutisée qu'en devenant suggestion ou valeur de champ.

### 3.5 Une seule archive ouverte à la fois

`serviceFor` garde l'archive ouverte et referme la précédente quand on en change. Deux
raisons :

- **Pas de course.** Le serveur est séquentiel (`setExecutor(null)`, décision de la spec
  web § 5) : un seul thread traite les requêtes, l'état mutable n'est jamais partagé.
- **Pas de régression.** Le design actuel ouvre le zip une fois pour toutes ; rouvrir à
  chaque requête serait un coût payé sur toutes les recherches pour un changement rare.

Ce cache à une place est une optimisation **non observable de l'extérieur**. Elle n'est
donc pas testée directement (§ 7) : seul est vérifié le comportement visible, à savoir que
chaque archive rend bien ses propres résultats.

`archives()`, en revanche, **relit le répertoire à chaque appel**. C'est un `Files.list`
sur un petit répertoire, et ça permet de déposer un nouvel export dans `data/` sans
redémarrer — ce qui compte pour un serveur qu'on lance une fois et qu'on laisse ouvert.

### 3.6 Alternatives écartées

- **Un `ArchiveCatalog` dans `web`** : ne compile pas (§ 3.2).
- **Rouvrir le zip à chaque requête**, sans état : plus simple, mais dégrade toutes les
  recherches pour supprimer un état que le modèle séquentiel rend inoffensif.
- **Garder plusieurs archives ouvertes** (un cache par archive) : des descripteurs de
  fichiers retenus indéfiniment, pour un utilisateur qui en consulte une à la fois.
- **Un état « archive courante » côté serveur**, changé par un second formulaire : deux
  clics pour changer, une même URL qui ne rend plus la même page, et un choix perdu à
  chaque arrêt — donc sans valeur pour le binaire à venir.

## 4. La page

### 4.1 Un champ texte à suggestions, dans le formulaire existant

Une seule `<form method="get" action="/">`, donc un seul GET porte tout :
`?q=…&i=on&w=on&archive=<chemin>`.

```html
<label for="archive">Archive</label>
<input type="text" id="archive" name="archive" value="data/export_2026.zip" list="archives" required>
<datalist id="archives">
<option value="data/export_2026.zip">
<option value="data/export_2025.zip">
</datalist>
```

Trois décisions tiennent dans ces quatre lignes :

- **Texte libre**, parce qu'une archive n'est pas forcément dans `data/`.
- **`<datalist>`**, pour ne pas perdre la découverte de `data/` en échange : c'est un champ
  de saisie *et* un menu de suggestions, en HTML natif, sans JavaScript. Les suggestions
  sont des **chemins complets** — une entrée de `datalist` doit être une valeur utilisable
  telle quelle.
- **`required`**, qui fait tout le travail quand `data/` est vide : le champ arrive vide,
  et le navigateur refuse de soumettre tant qu'on n'a pas donné de chemin. Pas de page
  spéciale « aucune archive » à écrire.
- **Un `placeholder` et une aide permanente**, parce que le premier lancement du binaire
  montrera un champ vide :

  ```html
  <input … placeholder="/path/to/Complete_LinkedInDataExport.zip" aria-describedby="archive-hint">
  <p class="hint" id="archive-hint">Type or paste the <strong>absolute</strong> path to a LinkedIn export. The archives found in data/ are suggested.</p>
  ```

  Le `placeholder` montre la *forme* attendue et s'efface dès qu'on saisit ; l'aide dit
  *d'où* vient la valeur et reste visible. `aria-describedby` la rattache au champ, un
  lecteur d'écran l'annonçant alors avec lui. Elle est rendue même sans suggestion — c'est
  précisément là qu'elle sert.

  **L'aide réclame un chemin absolu, le code accepte pourtant le relatif** (§ 3.4), et
  l'écart est voulu : un chemin relatif reste commode en développement, où l'on lance
  depuis la racine du dépôt, mais c'est un piège pour un binaire double-cliqué dont le
  répertoire courant est imprévisible. On conseille donc l'absolu sans rien interdire —
  une validation qui refuserait le relatif ne protégerait personne et casserait un usage
  légitime.

Le `<footer>Archive: …</footer>` disparaît : le champ dit la même chose, sous une forme
actionnable.

**Conséquence assumée** : sans JavaScript, changer d'archive ne recharge rien tout seul, et
`q` est `required` lui aussi. On change donc d'archive **en relançant une recherche**. Pour
une page dont l'unique objet est de chercher, c'est cohérent ; ça surprend une fois, d'où
cette ligne.

### 4.2 Mise en page — une grille, pas des rangées

Le formulaire est une **grille à trois colonnes** — *libellé · contrôle · bouton*. Ce n'est
pas cosmétique : c'est ce qui garantit que le champ de recherche et le champ d'archive ont
**exactement la même largeur**, puisqu'ils partagent une colonne, sans aucune valeur codée
en dur. La colonne des libellés se dimensionne sur le plus large des trois.

Le groupe de cases à cocher reçoit un libellé `Options`, relié par
`role="group"` + `aria-labelledby` et non par un `for` : un groupe n'a pas de contrôle
unique à désigner.

La colonne des contrôles est `minmax(0, 1fr)`, ce qui empêche un nom d'archive long de
pousser la page — le seul élément du formulaire dont la longueur est imprévisible. Les
extraits, eux, sont bornés par construction, et le corps de page est plafonné à `46rem` :
le formulaire s'aligne donc sur la colonne des résultats.

**Un chemin absolu (§ 3.4) déborde du champ, et c'est sa fin qui compte** — le nom du
fichier identifie l'archive, `/home/…` ou `C:\Users\…` non. D'où :

```css
#archive:not(:focus) { direction: rtl; text-align: left; }
```

`direction: rtl` ancre le débordement sur la fin du texte plutôt que sur son début.
`text-align: left` rattrape le seul effet indésirable — mesuré : sans lui, un chemin court
se colle à 295 px du bord gauche d'un champ de 400 px. `:not(:focus)` rend le champ en
écriture normale dès qu'on clique dedans, pour que l'édition ne soit pas déroutante.

La mise en garde habituelle sur `direction: rtl` — l'algorithme bidi déplace les
séparateurs neutres de tête — a été **vérifiée et ne s'applique pas ici** : mesure faite,
un chemin POSIX comme `/home/…/x.zip` n'est pas réordonné, son `/` initial restant collé à
un `home` fortement gauche-droite. Un chemin Windows commençant par `C:` l'est encore
moins.

### 4.3 `HtmlRenderer` devient sans état

Le champ `archiveLabel` et l'argument de constructeur disparaissent : le choix varie à
chaque requête, il devient donc un paramètre de rendu.

```java
// web — données d'affichage, rien d'autre
record ArchiveField(List<String> suggestions, String value, String error) {}

public String renderForm(String query, boolean ignoreCase, boolean wholeWord, ArchiveField archives);
public String render(SearchTerm term, SearchResults results, ArchiveField archives);
```

`renderForm` remplace l'ancien `renderEmptyForm` : la page « formulaire seul » sert
maintenant deux cas — l'accueil, et le retour après une archive qui n'ouvre pas — et ce
second cas doit **conserver le terme et les options déjà saisis**.

Le renderer ne manipule ni `Path` ni catalogue : le handler convertit. `SearchHandler` peut
dès lors garder une seule instance, sans état à invalider.

Les chemins passent par `escape` **en valeur d'attribut comme en suggestion**. Un fichier
peut légitimement s'appeler `<script>.zip` sous Linux, et la contrainte dure — *rien de
l'archive n'atteint la page sans échappement* — ne fait pas d'exception pour les noms de
fichiers.

## 5. Le protocole

### 5.1 Lire le cookie

Un type jumeau de `QueryParameters`, dans `web` :

```java
record Cookies(Map<String, String> values) {
  static Cookies parse(String header);   // "a=1; b=2"
  String value(String name);
}
```

Mêmes règles que son jumeau, pour les mêmes raisons : première occurrence gagnante — un
en-tête dupliqué garde un sens unique —, et un échappement malformé est conservé tel quel
plutôt que de casser la page. L'en-tête est lu par
`exchange.getRequestHeaders().getFirst("Cookie")`, et son absence (`null`) rend un
`Cookies` vide.

**Une divergence avec son jumeau, et elle compte** : un cookie n'est pas une query string,
`+` n'y a aucun sens spécial. Il est donc échappé avant décodage, sans quoi un fichier
nommé `a+b.zip` reviendrait `a b.zip`. L'encodage fait le trajet inverse.

### 5.2 Poser le cookie

Sur la réponse de chaque page de recherche rendue **dont l'archive s'est ouverte** :

```
Set-Cookie: archive=<chemin URL-encodé>; Path=/; Max-Age=31536000; SameSite=Strict; HttpOnly
```

- **URL-encodé** : un chemin contient des `/`, et un nom de fichier peut contenir un
  espace, un `;`, une virgule, de l'accentué — et, sous Linux, un retour à la ligne.
  L'encodage n'est donc pas cosmétique : il ferme aussi l'injection d'en-tête par CRLF.
- **Pas de `Secure`** : on sert en `http://localhost`.
- **`HttpOnly`** est gratuit, la page n'ayant pas de JavaScript.
- **`Max-Age` d'un an** : le choix doit survivre au navigateur fermé, c'est tout l'objet.

Un cookie n'est pas isolé par port : le choix survit à un `--port` différent, mais il est
aussi visible de tout autre serveur écoutant sur `localhost`. La valeur est un chemin ;
elle indique donc où se trouve l'archive LinkedIn sur la machine. Sur un poste personnel
servi en loopback, c'est accepté en connaissance de cause.

### 5.3 Préséance

Pour chaque requête, dans l'ordre :

| # | Source | Sens |
|---|--------|------|
| 1 | `archive=` dans l'URL | Le chemin saisi à l'instant, dans la page |
| 2 | `--archive` de ce lancement | L'intention explicite de la ligne de commande |
| 3 | Le cookie `archive`, **s'il mène encore quelque part** | Le dernier choix d'une session précédente |
| 4 | La plus récente de `data/` | Le défaut historique, inchangé |
| — | *rien* | Le champ arrive vide et `required` demande un chemin |

L'option de la ligne de commande passe devant le cookie parce qu'elle exprime une intention
**présente** — elle vient d'être tapée — là où le cookie exprime une intention passée.

La règle entière appartient au catalogue, pas au handler : ce dernier ne fait que
transmettre deux chaînes.

### 5.4 Ce qui se répare tout seul, et ce qui ne doit pas

La distinction gouverne tout le traitement d'erreur :

- **Un chemin saisi est rendu tel quel, même s'il ne mène nulle part.** L'utilisateur vient
  de le taper ; l'envoyer silencieusement ailleurs lui cacherait sa propre faute de frappe.
  Il obtient le message (§ 6).
- **Un cookie qui ne mène plus nulle part est abandonné**, et on passe au candidat suivant.
  Personne ne l'a tapé aujourd'hui, et une archive effacée entre deux sessions est un cas
  normal, pas une faute.
- **Le cookie n'est réécrit que lorsque l'archive s'est ouverte.** Il signifie « la dernière
  archive qui a marché », pas « la dernière tentée » — sinon le lancement suivant rouvrirait
  le chemin fautif et réafficherait la même erreur.

## 6. Erreurs

Un champ libre rend la saisie fausse **ordinaire**, là où une liste fermée la rendait
impossible. Le traitement d'erreur cesse donc d'être reportable.

**Un chemin qui n'ouvre pas** — faute de frappe, fichier absent, zip abîmé — rend la page
normale, avec un `<p class="error" role="alert">` **au-dessus du champ**, le chemin fautif
**conservé dans le champ** et le terme de recherche **conservé aussi**. On corrige là où la
faute s'affiche, sans rien retaper. Le message est celui d'`ArchiveUnavailableException`,
déjà écrit pour un humain — repris tel quel, échappé, sans préfixe `Error:`.

**Statut HTTP : 200.** La requête a été comprise et reçoit l'interface de l'application,
avec un formulaire utilisable ; l'échec porte sur le contenu, pas sur la transaction. Aucun
client ne dépend du code (ni JS, ni API), donc rien ne justifie de compliquer.

**`data/` vide n'est plus un refus de démarrer.** Le serveur monte, la ligne de terminal dit
`No archive found — the page will ask for one.`, et le champ vide et `required` fait le
reste. Un programme double-cliqué qui refuse de s'ouvrir n'a aucun moyen de dire pourquoi ;
c'est la dette signalée lors de la première passe, et elle est payée ici.

**`--archive` faux reste un refus immédiat, sortie 1.** Ce que l'utilisateur nomme sur la
ligne de commande est vérifié tout de suite, l'opérateur étant devant son terminal pour le
lire ; ce que le programme devine se rattrape dans la page. `ArchiveCatalog.of` n'ouvre donc
d'emblée que l'archive explicite.

**Ce qui ne change pas.** Le port déjà pris reste une erreur de démarrage, sans repli sur un
autre port. Le `catch (RuntimeException)` global du handler et sa page 500 restent en
dernier recours — mais deviennent vraiment exceptionnels, les échecs d'archive étant
désormais traités comme une page normale.

**Reste hors périmètre** : détecter qu'un zip valide n'est pas un export LinkedIn.
`ArchiveReader.readFirst` rend un `Optional`, donc un zip quelconque s'ouvre et rend zéro
résultat sans rien dire. Le domaine n'a pas de notion de validité d'archive ; l'introduire
dépasse ce sujet.

## 7. Tests (TDD, red → green → refactor)

Diamant habituel : la confiance vient de tests sociables sur les vrais collaborateurs.
Fixtures **inline** — répertoires temporaires et zips écrits à la volée —, aucune nouvelle
fixture versionnée.

### `ArchiveCatalogTest` — `launcher`, sans HTTP

- `Suggestions` : ordre plus récente d'abord ; l'explicite en tête, sans doublon quand il
  vient du répertoire ; une archive déposée après le démarrage apparaît ; rien à suggérer
  quand le répertoire est vide.
- `Precedence` : URL, puis explicite, puis cookie, puis la plus récente — et **vide** quand
  il n'y a rien. Plus les deux cas qui portent la § 5.4 : un chemin saisi inexistant est
  rendu tel quel, un cookie périmé est abandonné.
- `Wiring` : chaque archive rend ses propres résultats ; on revient à une archive quittée ;
  un chemin illisible lève `ArchiveUnavailableException`. Le cache à une place n'est pas
  testé (§ 3.5).
- `LaunchDefault` : un répertoire vide démarre, un `--archive` illisible non.

### `CookiesTest` — `web`

Jumeau de `QueryParametersTest`, mêmes cas : en-tête absent, espace après le `;`, première
occurrence gagnante, valeur encodée, échappement malformé conservé — **plus le `+`
littéral**, qui est la divergence de la § 5.1.

### `ArchiveLocatorTest` — `infrastructure`

Un `@Nested` pour `all(directory)` : ordre, non-zips écartés, répertoire absent. Les cas de
`mostRecent` restent tels quels.

### `HtmlRendererTest` — `web`

- `ArchiveField_` : champ texte requis portant le chemin ; une `<option>` par suggestion
  dans l'ordre reçu ; champ vide et `datalist` vide quand il n'y a rien ; un chemin
  contenant `<script>` et un guillemet échappé dans le champ **et** dans les suggestions.
- `ArchiveError` : le bandeau paraît avec le message, le chemin fautif et le terme sont
  conservés, aucun bandeau sans erreur, et le message est échappé.

### `WebAcceptanceTest` — le trajet complet

Contre un vrai serveur sur port 0, avec deux archives zippées à la volée :

- les suggestions listent le répertoire et le champ démarre sur la plus récente ;
- `?q=…&archive=<chemin>` cherche dans l'archive désignée ;
- la réponse porte `Set-Cookie: archive=<chemin encodé>; Path=/; …` ;
- le cookie seul suffit à la requête suivante, et l'URL le bat ;
- un chemin qui n'ouvre pas rend 200, le message, le chemin et le terme — et **aucun
  cookie** ;
- un répertoire vide rend 200 avec un champ vide.

`StartupMessage` couvre la ligne de terminal dans ses deux cas, sans démarrer de serveur.

### Inchangés

`MainAcceptanceTest` et `ExplorerTest`. Le CLI ne bouge pas, et c'est un résultat, pas un
oubli.

**Architecture** — `bin/test` compile `src` en modules : l'absence de
`web → infrastructure` est vérifiée à la compilation, sans test dédié.

## 8. Délibérément laissé de côté

- **Le vrai sélecteur de fichier natif.** `<input type="file">` ne donne **jamais** le
  chemin — `value` vaut `C:\fakepath\…` par décision de sécurité, et ce qui part est le
  *contenu* du fichier en `POST multipart`. La File System Access API ne rend qu'un handle
  opaque, le glisser-déposer rien de plus. Restaient deux voies, écartées : parser le
  multipart à la main et **recopier** l'archive quelque part, ou ouvrir un dialogue natif
  depuis le serveur (`java.awt.FileDialog`, `zenity`) — mesuré à **+19 Mo sur l'image
  jlink** pour `java.desktop`, et une fenêtre qui s'ouvre à côté du navigateur.
- **Naviguer le système de fichiers depuis la page** (le serveur liste les répertoires, on
  clique de proche en proche) : donne un vrai chemin sans transfert ni JavaScript, mais
  demande une vue de navigation entière. À reconsidérer si le champ texte lasse.
- **Balayer d'autres répertoires que `data/`** (`~/Téléchargements`, le répertoire courant)
  — sujet de la spec du binaire ; le catalogue est déjà taillé pour en recevoir plusieurs.
- **Choisir l'archive côté CLI autrement que par `--archive`** — le terminal a déjà la
  complétion de chemins, qui fait mieux qu'une liste.
- **Changer d'archive sans relancer de recherche** (§ 4.1) — demanderait du JavaScript ou
  un second formulaire, pour un confort marginal.

## 9. Documentation à mettre à jour

- **`CLAUDE.md`** : `ArchiveCatalog` comme second habitant de `launcher`, à côté
  d'`Explorer`.
- **[UI web](2026-07-31-web-ui-design.md)** : le point « sélecteur d'archive dans la page »
  de son § 9 cesse d'être écarté — le noter comme réalisé, avec un pointeur vers ce
  document.
- **`README.md`** : le champ, les suggestions et la mémorisation du choix, dans la section
  web.
