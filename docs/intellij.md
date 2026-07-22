# Ouvrir le projet dans IntelliJ IDEA

Ce projet se compile et se teste **sans outil de build** (voir le `README`). Rien ne
décrit donc automatiquement sa structure à un IDE. Une configuration IntelliJ minimale
est fournie — mais **`.idea/` n'est pas versionné directement** : on versionne un
**template** dans `ide/` et un **script** qui l'installe.

## Installation (une fois après le clone)

```sh
./ide/setup-intellij           # Linux / macOS   (Windows : ide\setup-intellij.cmd)
```

Le script copie le template vers `.idea/` et crée `linkedin-archive-explorer.iml` à la
racine. Ouvre ensuite le projet dans IntelliJ.

Puis **une seule étape manuelle** : si IntelliJ signale « SDK '17' is invalid », va dans
*File → Project Structure → Project* et choisis un **JDK 17 ou plus récent**. (Le nom
d'un SDK est propre à chaque machine, il ne peut pas être versionné.)

## Pourquoi une config est nécessaire

Le build `javac --module-source-path` impose **un dossier par module, nommé avec des
points** :

```
src/fr.craft.linkedinarchiveexplorer.application/
    fr/craft/linkedinarchiveexplorer/application/ContentGroup.java
```

Sans configuration, IntelliJ prend `src/` comme racine des sources et interprète le nom
pointé du dossier-module comme des segments de package. Il attend alors un package
`fr.craft.linkedinarchiveexplorer.application.fr.craft.linkedinarchiveexplorer.application`
et affiche une erreur sur chaque fichier.

## Ce que le template fait

Fichiers versionnés dans `ide/` (installés par le script) :

- `ide/linkedin-archive-explorer.iml` — déclare **chaque dossier de module comme racine
  de sources** (`src/fr.craft.linkedinarchiveexplorer.domain`, `.application`,
  `.infrastructure`, `.cli`), plus `tools/`, et `test/` comme **racine de tests**. Le
  package est alors calculé **relativement à la racine du module** → il correspond, et
  l'erreur disparaît.
- `ide/idea/modules.xml` — enregistre ce module IntelliJ.
- `ide/idea/libraries/junit_platform_console_standalone.xml` — déclare le jar JUnit de
  `lib/` (scope test) pour que les tests résolvent.
- `ide/idea/misc.xml` — niveau de langage **Java 17**.

`.idea/` et le `.iml` racine sont **gitignorés** : ce sont des artefacts générés.
IntelliJ y ajoutera ses propres fichiers volatils (`workspace.xml`, `vcs.xml`…) qui, du
coup, ne polluent pas le dépôt.

## Limite connue (cosmétique)

Le projet a **quatre `module-info.java`** (un par couche), alors qu'IntelliJ aime un
seul descripteur de module par « module IntelliJ ». IntelliJ **soulignera donc les
`module-info.java`** (« duplicate module declaration »). C'est **sans conséquence** :

- ces fichiers ne servent qu'au build `./bin/build`, qui **impose l'architecture à la
  compilation** (c'est la source de vérité, pas l'IDE) ;
- le code applicatif et les tests, eux, se résolvent correctement dans IntelliJ.

Tu peux ignorer ces avertissements. La compilation et les tests se lancent de toute
façon via `./bin/build` et `./bin/test` (ou `bin\build.cmd` / `bin\test.cmd`).
