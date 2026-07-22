# Structure de l'archive LinkedIn — source de vérité

Relevé à partir d'un vrai export « Complete_LinkedInDataExport ». Sert de référence
pour l'app (quels fichiers lire, quels en-têtes, quels cas particuliers). **39 fichiers**,
dont quelques-uns dans des sous-dossiers.

## Ce que l'application lit

Seuls ces contenus sont recherchés ; le reste doit être **ignoré** :

| Type | Fichier | Colonnes utilisées |
|---|---|---|
| Commentaires | `Comments_<id>.csv` | `Date`, `Link`, `Message` |
| Posts | `Shares_<id>.csv` | `Date`, `ShareLink`, `ShareCommentary` |
| Articles | `Articles/Articles/<slug>.html` | `<title>`, lien du `<h1>`, corps |

- **Format de date** (Comments/Shares) : `yyyy-MM-dd HH:mm:ss` (ex. `2026-07-20 10:25:25`) ; l'app ne garde que la date.
- **Suffixe `<id>`** : identifiant numérique du membre (ici `252701747`) présent sur certains fichiers (voir table).

## Les 39 entrées (chemin → en-tête / 1re ligne)

| Chemin | En-tête (colonnes) |
|---|---|
| `Ads Clicked.csv` | `Ad clicked Date,Ad Title/Id` |
| `Ad_Targeting.csv` | `Member Age,Buyer Groups,Company Names,…` (⚠️ colonnes **dupliquées** : `Company Names`, `Job Titles`) |
| `Comments_<id>.csv` | `Date,Link,Message` |
| `Company Follows.csv` | `Organization,Followed On` |
| `Connections.csv` | ⚠️ **préambule** (voir plus bas) puis `First Name,Last Name,URL,Email Address,Company,Position,Connected On` |
| `Education.csv` | `School Name,Start Date,End Date,Notes,Degree Name,Activities` |
| `Email Addresses.csv` | `Email Address,Confirmed,Primary,Updated On` |
| `Events.csv` | `Event Name,Event Time,Status,External Url` |
| `guide_messages.csv` | schéma « messagerie » (voir plus bas) |
| `Inferences_about_you.csv` | `Category,Type of inference,Description,Inference` |
| `InstantReposts_<id>.csv` | `Date,Link` |
| `Invitations.csv` | `From,To,Sent At,Message,Direction,inviterProfileUrl,inviteeProfileUrl` |
| `Job Applicant Saved Screening Question Responses.csv` | `Question,Answer` |
| `Jobs/Job Applications.csv` | `Application Date,Contact Email,Contact Phone Number,Company Name,Job Title,Job Url,Resume Name,Question And Answers` |
| `Jobs/Job Seeker Preferences.csv` | `Locations,Industries,Company Employee Count,…,Semantic Preferences` |
| `LAN Ads Engagement.csv` | `Action,Date,Ad Title/Id,Page/App` |
| `learning_coach_messages.csv` | schéma « messagerie » |
| `LearningCoachMessages.csv` | ⚠️ pas d'en-tête : littéralement `No conversations found` (fichier « vide ») |
| `Learning.csv` | `Content Title,Content Description,Content Type,…,Notes taken on videos (if taken),` (⚠️ **virgule finale** = dernière colonne vide) |
| `learning_role_play_messages.csv` | schéma « messagerie » |
| `Member_Follows_<id>.csv` | `Date,Status,FullName` |
| `messages.csv` | schéma « messagerie », mais en-tête **entièrement entre guillemets** (voir plus bas) |
| `PhoneNumbers.csv` | `Extension,Number,Type` |
| `Positions.csv` | `Company Name,Title,Description,Location,Started On,Finished On` |
| `Profile.csv` | `First Name,Last Name,Maiden Name,Address,Birth Date,Headline,Summary,Industry,Zip Code,Geo Location,Twitter Handles,Websites,Instant Messengers` |
| `Profile Summary.csv` | `Profile Summary` (colonne unique) |
| `Publications.csv` | `Name,Published On,Description,Publisher,Url` |
| `Reactions_<id>.csv` | `Date,Type,Link` |
| `Registration.csv` | `Registered At,Registration Ip,Subscription Types` |
| `Rich_Media.csv` | `Date/Time,Media Description,Media Link` (dates en texte : « You uploaded a … on April 12, 2026 at 8:03 AM (GMT) ») |
| `Saved_Items_<id>.csv` | `savedItem,CreatedTime` |
| `SavedJobAlerts.csv` | `ALERT_PARAMETERS,QUERY_CONTEXT,SAVED_SEARCH_ID` |
| `SearchQueries.csv` | `Time,Search Query` |
| `Security Challenges.csv` | `Challenge Date,IP Address,User Agent,Country,Challenge Type` |
| `Services Marketplace/Providers.csv` | `Creation Time,Marketplace Type,ProFinder Service Category,…,Media` |
| `Shares_<id>.csv` | `Date,ShareLink,ShareCommentary,SharedUrl,MediaUrl,Visibility` |
| `Skills.csv` | `Name` (colonne unique) |
| `Votes_<id>.csv` | `Date,Link,OptionText` |
| `Articles/Articles/<slug>.html` | fichier **HTML** (voir plus bas) |

Sous-dossiers présents : `Articles/Articles/`, `Jobs/`, `Services Marketplace/`.

Fichiers portant le suffixe `_<id>` : `Comments`, `InstantReposts`, `Member_Follows`,
`Reactions`, `Saved_Items`, `Shares`, `Votes`.

## Cas particuliers à reproduire

### Préambule de `Connections.csv`
Trois lignes avant l'en-tête réel :
```
Notes:
"When exporting your connection data, you may notice that some of the email addresses are missing. …"

First Name,Last Name,URL,Email Address,Company,Position,Connected On
```
(ligne `Notes:`, un paragraphe entre guillemets, une ligne vide, puis l'en-tête.)

### En-tête « messagerie »
`guide_messages.csv`, `learning_coach_messages.csv`, `learning_role_play_messages.csv`
et `messages.csv` partagent :
```
CONVERSATION ID,CONVERSATION TITLE,FROM,SENDER PROFILE URL,TO,RECIPIENT PROFILE URLS,DATE,SUBJECT,CONTENT,FOLDER
```
`messages.csv` ajoute `,ATTACHMENTS,IS MESSAGE DRAFT,IS CONVERSATION DRAFT` **et** met
**chaque nom de colonne entre guillemets** (`"CONVERSATION ID","CONVERSATION TITLE",…`).

### `LearningCoachMessages.csv`
Contient une seule ligne, sans en-tête : `No conversations found`.

### Structure d'un article HTML
```html
<html>
<head>
  <title>Titre de l'article</title>
  <style> /* CSS inline */ </style>
</head>
<body>
  <h1><a href="https://www.linkedin.com/pulse/<slug>">Titre de l'article</a></h1>
  <!-- paragraphes du corps -->
</body>
</html>
```
- Le **titre** est dans `<title>` et répété dans le `<h1>`.
- L'**URL canonique** est le `href` de l'ancre du `<h1>` (lien `…/pulse/…`).
- **Pas de date** dans l'export HTML.
- Le **nom de fichier** est un slug (titre en kebab-case) suffixé, et peut contenir des
  **accents** : ex. `progresser-en-tant-que-développeuse-ou-développeur-grâce-…-tartampion-7n5pe.html`.
