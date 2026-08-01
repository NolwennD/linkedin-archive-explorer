# Options de recherche — Design

_Date : 2026-07-24_

Extension de [l'outil d'exploration d'archive LinkedIn](2026-07-22-linkedin-archive-explorer-design.md).
À lire en complément de ce design initial ; les contraintes techniques (runtime JDK
pur, zéro build tool, architecture hexagonale par modules) restent inchangées.

## 1. Objectif

Ajouter deux options de recherche, style `grep`, **combinables** et **désactivées par
défaut** (le comportement littéral actuel reste le comportement par défaut) :

- **`-i` / `--ignore-case`** — insensible à la **casse**, mais **toujours sensible aux
  accents**.
- **`-w` / `--word`** — n'accepte que les occurrences formant un **mot entier**.

## 2. Décisions

### Nommage des flags — style `grep`
`-i`/`--ignore-case` et `-w`/`--word`, cohérent avec l'esprit « grep-style » de l'outil.
Court et long ; pas de regroupement `-iw` en v1 (chaque flag est un token séparé,
trivial à ajouter plus tard).

### `-i` : casse seule, accents conservés
`-i` ignore **uniquement** la casse. Les accents restent significatifs.

- `Date` = `date` = `DATE`
- `developpe` ≠ `développe`
- `isi` ≠ `ısı` et `isi` ≠ `İSİ` *(ajouté le 2026-08-01, voir ci-dessous)*

#### Le `I` turc — correction du 2026-08-01
Le `ı` sans point (U+0131) et le `İ` avec point (U+0130) sont des **lettres à part
entière** de l'alphabet turc, pas des variantes du `i` latin. La règle « seule la casse
est ignorée » leur applique donc le même traitement qu'aux accents : ils ne rencontrent
que leur propre paire de casse.

L'implémentation initiale s'appuyait sur `String.regionMatches(true, …)`, qui rapproche
deux caractères dès que leurs majuscules **ou** leurs minuscules coïncident. Or
`Character.toUpperCase('ı')` vaut `'I'` et `Character.toLowerCase('İ')` vaut `'i'` : les
quatre lettres se rejoignaient par ce pivot, et `-i` remontait `ısı` pour une recherche de
`isi`. `grep -i` ne le fait pas ; l'outil ne le fait plus non plus.

La comparaison passe désormais par une fonction de repliement,
`minuscule(majuscule(c))`, dont les deux lettres turques sont exclues. Étant une vraie
fonction, elle rend la relation **transitive**, ce que l'ancien « ou » n'était pas. Sur
l'ensemble du BMP le changement se réduit à neuf paires : les quatre confusions turques
disparaissent, et le thêta grec `ϑ`/`ϴ` se rapproche — ce que fait le repliement Unicode
de référence.

Rien de tout cela ne dépend de la locale : c'est une propriété des caractères, pas du
lecteur, et les résultats sont les mêmes partout.

La locale, elle, était en cause dans un **second** défaut trouvé le même jour, ailleurs :
`ArchiveLocator` mettait l'extension en minuscules sans préciser de locale. Sur une
machine turque `EXPORT.ZIP` devenait `.zıp`, et l'archive disparaissait simplement de la
liste — chez cet utilisateur seulement. Un nom de fichier n'est pas une phrase :
`Locale.ROOT`.

### `-w` : mot entier, caractères de mot Unicode
Un « caractère de mot » est une **lettre Unicode**, un **chiffre**, ou `_`
(`Character.isLetterOrDigit(c) || c == '_'`). Les lettres accentuées (`é`, `à`, `ç`…)
comptent donc comme des lettres. Sémantique `grep` : on ne regarde **que les voisins
dans le texte** (le caractère juste avant et juste après l'occurrence), pas les bords du
terme recherché.

Une occurrence `[début, fin)` est un mot entier si :
- `début == 0` **ou** `texte[début-1]` n'est **pas** un caractère de mot ; **et**
- `fin == longueur` **ou** `texte[fin]` n'est **pas** un caractère de mot.

## 3. Comportement — exemples

### Défaut (aucune option) — littéral, sensible casse + accents

```bash
linkedin-archive-explorer "Date"
```
| Texte source | Match ? | Surligné |
|---|---|---|
| `…un bug Date(0,0,0) sur…` | ✅ | `Date` |
| `…la date de publication…` | ❌ (casse) | — |

```bash
linkedin-archive-explorer "developpeur"
```
| Texte source | Match ? |
|---|---|
| `…je suis developpeur backend…` | ✅ |
| `…je suis développeur backend…` | ❌ (accent) |

### `-i` / `--ignore-case` — casse ignorée, accents conservés

```bash
linkedin-archive-explorer -i "date"
```
| Texte source | Match ? | Surligné (**casse réelle du texte**) |
|---|---|---|
| `…un bug Date(0,0,0)…` | ✅ | `Date` |
| `…la date de publi…` | ✅ | `date` |
| `…le DATE_FORMAT ISO…` | ✅ | `DATE` |
| `…la dâte mal saisie…` | ❌ (accent, toujours sensible) | — |

Point clé : le terme cherché est `date`, mais l'extrait surligne `Date` / `DATE` — le
**texte réel de l'archive**, pas la requête.

### `-w` / `--word` — mot entier

```bash
linkedin-archive-explorer -w "dev"
```
| Texte source | Match ? | Pourquoi |
|---|---|---|
| `…un dev senior…` | ✅ | entouré d'espaces |
| `…le dev, ici…` | ✅ | `,` = séparateur |
| `…un développeur…` | ❌ | `dev` suivi de `e` (lettre) |
| `…mode_dev actif…` | ❌ | précédé de `_` |
| `dev` (début/fin de texte) | ✅ | bords = frontières |

```bash
linkedin-archive-explorer -w "Date"
```
| Texte source | Match ? | Pourquoi |
|---|---|---|
| `…bug Date(0,0,0)…` | ✅ | `(` = séparateur |
| `…un DateFormat…` | ❌ | suivi de `F` (lettre) |

Cas accentué : `-w "café"` matche `café !` (le `!` sépare) mais **pas** `caféine`
(le `é` est suivi de `i`, qui continue le mot).

### `-i -w` combinés — mot entier, casse ignorée

```bash
linkedin-archive-explorer -i -w "post"
```
| Texte source | Match ? | Surligné |
|---|---|---|
| `…mon Post LinkedIn…` | ✅ | `Post` |
| `…un POST HTTP…` | ✅ | `POST` |
| `…je poste demain…` | ❌ | `post` suivi de `e` |
| `…le repost viral…` | ❌ | précédé de `re` |

## 4. Algorithme de recherche

On garde le style **explicite, écrit à la main** (pas de `java.util.regex`). On remplace
`value.indexOf(needle)` par un balayage fondé sur `String.regionMatches` :

Aucune branche `if` sur les options dans le moteur : ce sont les **enums qui agissent**
(voir §5). Le balayage délègue à `caseSensitivity` la comparaison et à `wordScope` le
test de frontière :

```
pour at de 0 à texte.length() - needle.length() :
    si caseSensitivity.matchesAt(texte, at, needle)
       et wordScope.allows(texte, at, at + needle.length()) :
        enregistrer le match [at, at + needle.length())
        at += needle.length()      // occurrences non chevauchantes
```

- **Insensibilité à la casse** : `caseSensitivity.matchesAt(...)` s'appuie sur
  `String.regionMatches` (`SENSITIVE` → `ignoreCase=false`, `INSENSITIVE` → `true`).
  La comparaison se fait caractère par caractère et **ne modifie jamais la longueur**,
  contrairement à `toLowerCase()` (`ß`→`ss`, `İ`→2 caractères). La zone matchée fait
  donc **toujours exactement** `needle.length()` caractères.
- **Frontière** : `wordScope.allows(...)` applique la règle de §2 (`ANYWHERE` → toujours
  vrai ; `WHOLE_WORD` → voisins dans le texte non-mots), avec
  `isWordChar(c) = Character.isLetterOrDigit(c) || c == '_'`.

**Conséquence importante** : la zone matchée est le **texte réel de la source** (sa vraie
casse), qui peut différer du terme recherché avec `-i`. L'extrait surligne donc le texte
matché, **pas** le terme — c'est le rôle du type `Match` (§5).

## 5. Modèle de domaine

- Deux **enums porteurs de comportement** plutôt que des booléens (éviter les primitifs
  **et** Tell-Don't-Ask : l'enum sait faire, le moteur ne teste rien).
  - `CaseSensitivity { SENSITIVE, INSENSITIVE }` avec
    `boolean matchesAt(String text, int at, String needle)` — chaque valeur appelle
    `text.regionMatches(ignoreCase, at, needle, 0, needle.length())` avec son propre
    `ignoreCase`.
  - `WordScope { ANYWHERE, WHOLE_WORD }` avec `boolean allows(String text, int start, int end)`
    — `ANYWHERE` renvoie toujours `true` ; `WHOLE_WORD` vérifie que les voisins dans le
    texte ne sont pas des caractères de mot. Le helper `isWordChar` est privé à l'enum.
  ```java
  enum WordScope {
    ANYWHERE   { boolean allows(String t, int start, int end) { return true; } },
    WHOLE_WORD { boolean allows(String t, int start, int end) {
                   return freeAt(t, start - 1) && freeAt(t, end); } };
    abstract boolean allows(String t, int start, int end);
    private static boolean freeAt(String t, int i) {
      return i < 0 || i >= t.length() || !isWordChar(t.charAt(i));
    }
  }
  ```
- Nouveau **type `Match`** — un fragment de source **localisé** : sa position dans le
  texte **et** le texte réellement trouvé. C'est l'unité que l'on surligne.
  ```java
  record Match(int start, int end, String value)
  ```
  Compact constructor : `value` non nul, `0 <= start <= end`, et invariant
  `value.length() == end - start`. `value` est le fragment réel de la source (sa vraie
  casse, potentiellement différente du terme avec `-i`).
- `SearchTerm` porte ces options et **possède la recherche** (Tell-Don't-Ask : le terme
  sait se trouver lui-même) :
  ```java
  record SearchTerm(String value, CaseSensitivity caseSensitivity, WordScope wordScope)
      List<Match> occurrencesIn(String text)   // nouveau comportement
  static SearchTerm literal(String value)       // = SENSITIVE, ANYWHERE
  ```
  Le compact constructor valide `value` (non nul, non blank) et les options (non nulles).
  Le factory `literal(...)` garde les appels par défaut concis (tests, cas usuel).
- `Excerpt` ne stocke plus un `SearchTerm` mais le **`Match`** qu'il entoure :
  ```java
  record Excerpt(String before, Match match, String after)
      String render(Function<String,String> emphasis)  // before + emphasis(match.value()) + after
  ```

`Body.excerptsFor(term)` délègue la localisation à `term.occurrencesIn(value)` et ne
garde que son vrai rôle : à partir de chaque `Match` (`start`/`end`), construire la
fenêtre de contexte (±40 caractères, sauts de ligne aplatis, ellipses) autour du
fragment.

`SearchEngine`, `SearchContentsService`, `SearchResults` : **signatures inchangées**,
elles transportent simplement le `SearchTerm` enrichi.

## 6. CLI

`Main` parse `-i`/`--ignore-case` et `-w`/`--word` vers les deux enums, puis construit
`new SearchTerm(term, caseSensitivity, wordScope)`. Ligne d'usage mise à jour :

```
usage: linkedin-archive-explorer [--archive <path>] [--color|--no-color] [-i|--ignore-case] [-w|--word] <term>
```

Les formes courtes se regroupent dans les deux ordres : `-iw` et `-wi` valent `-i -w`.
Un cluster à tiret simple n'est reconnu que s'il ne contient que des lettres connues
(`i`, `w`) ; sinon l'argument suit le traitement habituel (terme de recherche).

## 7. Tests (TDD, red → green → refactor)

Nouveaux groupes `@Nested` (dans `SearchEngineTest` ou un `SearchTermTest` dédié) :

- **CaseInsensitive** — `date` trouve `Date` ; l'extrait surligne la casse d'origine
  (`Date`, `DATE`) ; les accents restent significatifs (`date` ne trouve pas `dâte`).
- **WholeWord** — `dev` ne matche pas dans `développeur` ni `mode_dev` ; ponctuation et
  bords de texte sont des frontières ; `_` et lettres accentuées n'en sont pas ; le cas
  de référence `-w "Date"` matche dans `Date(0,0,0)`.
- **Combined** `-i -w`, et **défaut inchangé** (littéral, sensible casse + accents).
- Le comportement des enums (`WordScope.allows`, `CaseSensitivity.matchesAt`) est
  testable en isolation ; on privilégie néanmoins des tests sociables via `SearchTerm`.
- Mise à jour des tests `Excerpt` / `Body` pour le passage de `match` au type `Match`
  (position + fragment réel), + validation du compact constructor de `Match`.
- Tests de parsing d'arguments dans `Main` pour les nouveaux flags (si cette couverture
  existe déjà).
