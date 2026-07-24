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

```
pour at de 0 à texte.length() - needle.length() :
    si texte.regionMatches(ignoreCase, at, needle, 0, needle.length())
       et (!motEntier || frontièreOk(texte, at, at + needle.length())) :
        enregistrer le match [at, at + needle.length())
        at += needle.length()      // occurrences non chevauchantes
```

- **Insensibilité à la casse** : fournie directement — et **correctement** — par
  `regionMatches(true, …)`. La comparaison se fait caractère par caractère et **ne
  modifie jamais la longueur**, contrairement à `toLowerCase()` (`ß`→`ss`, `İ`→2
  caractères). La zone matchée fait donc **toujours exactement** `needle.length()`
  caractères.
- **`frontièreOk`** applique la règle de §2 (voisins dans le texte uniquement),
  avec `isWordChar(c) = Character.isLetterOrDigit(c) || c == '_'`.

**Conséquence importante** : la zone matchée est le **texte réel de la source** (sa vraie
casse), qui peut différer du terme recherché avec `-i`. L'extrait surligne donc le texte
matché, **pas** le terme.

## 5. Modèle de domaine

- Deux **enums** plutôt que des booléens (principe « éviter les primitifs » du projet) :
  - `CaseSensitivity { SENSITIVE, INSENSITIVE }`
  - `WordScope { ANYWHERE, WHOLE_WORD }`
- `SearchTerm` porte ces options et **possède la recherche** (Tell-Don't-Ask : le terme
  sait se trouver lui-même) :
  ```java
  record SearchTerm(String value, CaseSensitivity caseSensitivity, WordScope wordScope)
      List<Match> occurrencesIn(String text)   // nouveau comportement
  static SearchTerm literal(String value)       // = SENSITIVE, ANYWHERE
  ```
  Le compact constructor valide `value` (non nul, non blank) et les options (non nulles).
  Le factory `literal(...)` garde les appels par défaut concis (tests, cas usuel).
- Nouveau `record Match(int start, int end)` (validé : `0 <= start <= end`).
- `Excerpt` ne stocke plus un `SearchTerm` mais le **texte matché réel** sous forme de
  `String` :
  ```java
  record Excerpt(String before, String match, String after)
      String render(Function<String,String> emphasis)  // before + emphasis(match) + after
  ```

`Body.excerptsFor(term)` délègue la localisation à `term.occurrencesIn(value)` et ne
garde que son vrai rôle : construire la fenêtre de contexte (±40 caractères, sauts de
ligne aplatis, ellipses) et extraire le texte matché de chaque `Match`.

`SearchEngine`, `SearchContentsService`, `SearchResults` : **signatures inchangées**,
elles transportent simplement le `SearchTerm` enrichi.

## 6. CLI

`Main` parse `-i`/`--ignore-case` et `-w`/`--word` vers les deux enums, puis construit
`new SearchTerm(term, caseSensitivity, wordScope)`. Ligne d'usage mise à jour :

```
usage: linkedin-archive-explorer [--archive <path>] [--color|--no-color] [-i|--ignore-case] [-w|--word] <term>
```

Parsing simple : `-i` et `-w` sont des tokens séparés (pas de `-iw` en v1).

## 7. Tests (TDD, red → green → refactor)

Nouveaux groupes `@Nested` (dans `SearchEngineTest` ou un `SearchTermTest` dédié) :

- **CaseInsensitive** — `date` trouve `Date` ; l'extrait surligne la casse d'origine
  (`Date`, `DATE`) ; les accents restent significatifs (`date` ne trouve pas `dâte`).
- **WholeWord** — `dev` ne matche pas dans `développeur` ni `mode_dev` ; ponctuation et
  bords de texte sont des frontières ; `_` et lettres accentuées n'en sont pas ; le cas
  de référence `-w "Date"` matche dans `Date(0,0,0)`.
- **Combined** `-i -w`, et **défaut inchangé** (littéral, sensible casse + accents).
- Mise à jour des tests `Excerpt` / `Body` pour le passage de `match` en `String`.
- Tests de parsing d'arguments dans `Main` pour les nouveaux flags (si cette couverture
  existe déjà).
