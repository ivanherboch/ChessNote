# ChessDuck — contexte pour une nouvelle session

Ce document résume tout ce qu'il faut savoir pour continuer à faire évoluer **ChessDuck** dans une nouvelle session.
Lis-le en entier avant toute modification, puis applique les conventions décrites ici.

---

## 1. Le projet en bref

- **Application Android francophone d'échecs**, 100 % hors-ligne : Kotlin, Jetpack Compose, Material 3, Room, DataStore,
  Coroutines/Flow, Stockfish 11 embarqué (exécutable natif `libstockfish.so`).
- **Dépôt** : `ivanherboch/ChessNote` (le dépôt garde ce nom ; l'application, elle, s'appelle ChessDuck).
- **Branche de travail** : `claude/chessduck-android-app-wcikw1` (c'est aussi la branche par défaut du dépôt).
  Toujours commiter et pousser sur cette branche. **Ne jamais créer de pull request** sauf demande explicite.
- **Version actuelle** : `2.2.0` (`versionCode = 21`), publiée en release GitHub `v2.2.0`.
- **Langue** : toute l'interface, les commentaires, la KDoc, les messages de commit et les notes de version sont **en français**.
- Le propriétaire est l'auteur de l'application : mention « © Ivan Herboch, 2026 » (À propos, images partagées).

### Modules (onglets)

| Onglet | Contenu |
|---|---|
| Notation | Exercices de notation anglaise FIDE (SAN) sur positions réelles ; niveaux Tous / Bases / Prises / Roques ; clavier virtuel ; indices 1/2 et 2/2 ; diagnostic pédagogique ; séries et record ; Mémo Anglais |
| Académie | 113 leçons (37 ouvertures, 38 milieux de partie, 38 finales) en mode Guidé (lecture commentée) et mode Défi (le joueur joue les coups d'un camp) ; maîtrise enregistrée en base ; catalogue filtrable et recherchable |
| Problèmes | 150 000 problèmes Lichess (CC0), 50 thèmes officiels regroupés en 6 familles, **un seul thème principal par problème** (`PuzzleThemes.primary`) ; anti-spoiler (thème masqué en mode libre, bouton « Révéler le thème ») ; réfutation temporisée (`REFUTE_HOLD = 4000 ms`) avec « Réessayer » ; indice en 2 temps ; 5 derniers problèmes ; recherche par identifiant ; export PNG (énoncé + solution) |
| Jeu Libre | Contre l'ordinateur (Stockfish, 600–2400 ELO, fautes « humaines » sous 1350) ou à 2 joueurs ; ELO FIDE (K = 32) ; jauge d'évaluation ; annuler son coup ; historique ; revue commentée en langage naturel avec annotations `!!` `!` `?!` `?` `??`, mats manqués en 1 et 2 |
| Mode enfant | Activé dans les Paramètres : écran clair et coloré (police Fredoka), 2 joueurs, cavalier en forme de girafe, leçons animées du déplacement de chaque pièce, confirmation avant de quitter ; la partie enfant est effacée à la sortie mais la partie du Jeu Libre est conservée |

Téléphone : portrait. Tablette (plus petite largeur ≥ 600 dp) : paysage, deux colonnes (échiquier à gauche, commandes à droite).

---

## 2. Architecture (après le refactoring P1 → P3 de la v2.2.0)

Paquet racine : `app/src/main/java/com/ivanherboch/chessduck/`

- `ChessDuckApp.kt` — `AppContainer` (injection manuelle : `db`, `prefs`, dépôts, `engine`, `analysis`, `feedback`)
  et `DuckViewModels.Factory` (tous les ViewModels sont créés par cette fabrique, avec `SavedStateHandle` si besoin).
- `MainActivity.kt` — n'observe que `AppChrome` (thème, palette, coordonnées, sons, vibrations, mode enfant) ;
  ViewModels créés **paresseusement** à la première visite d'un onglet ; `rememberSaveableStateHolder` par onglet ;
  `LocalIsTwoPane` (règle unique `isTwoPane(w, h) = w > h × 1,15`) fournie à la racine.
- `chess/` — moteur de règles pur Kotlin : `Chess.kt` (`Position` immuable, `Line` en lecture seule hors `push`,
  SAN/FEN/UCI, `hasAttacker`, `key` en cache, `sanBody`), `PieceFr.kt` (`PieceLexiconFr` : noms, genres, articles, possessifs).
- `content/` — `PuzzleThemes.kt` (50 thèmes, `PRIORITY`, `primary`, `primaryIndex`, `primarySql`), `Lessons.kt` +
  `LessonsOpenings/Middlegames/Endgames.kt` (**générés**, coups en UCI, voir §5).
- `data/` — `Db.kt` (Room **v3** : entités `Puzzle` avec colonne indexée `primaryTheme`, `PuzzleProgress`, `LessonProgress`,
  `GameRecord` ; migrations 1→2→3 ; `syncPuzzles` par `ATTACH` + `INSERT OR IGNORE`), `Prefs.kt` (DataStore « chessduck »,
  migration depuis les SharedPreferences `share_style`), `Repositories.kt` (interfaces `SettingsRepository`,
  `PuzzleRepository`, `GameRepository`, `LessonProgressRepository` et implémentations Room).
- `engine/Stockfish.kt` — interface `ChessEngine` ; `Stockfish` (UCI, recherches sérialisées et annulables,
  resynchronisation `isready` après annulation, options renvoyées seulement si elles changent).
- `feature/` (même paquet `feature`, fichiers séparés) :
  - Notation : `NotationLogic.kt`, `NotationViewModel.kt`, `NotationScreen.kt`, `MemoSheet.kt`
  - Académie : `AcademyViewModel.kt`, `AcademyScreen.kt`, `LessonCatalogSheet.kt`
  - Problèmes : `PuzzlesViewModel.kt`, `PuzzlesScreen.kt`, `PuzzleCatalogSheet.kt`, `ExportSheet.kt`, `ShareCard.kt` (rendu),
    `ShareExporter.kt` (cache, FileProvider, MediaStore, partage)
  - Jeu Libre : `GameLogic.kt`, `PlayViewModel.kt` (jeton `gameGeneration` contre les courses), `PlayScreen.kt`,
    `HistorySheet.kt`, `GameSettingsSheet.kt`, `GameReviewViewModel.kt` (+ `Review`), `GameReviewScreen.kt`, `GameAnalysis.kt` (`GameAnalysisService` partagé), `ReviewComments.kt`
  - Mode enfant : `KidViewModel.kt`, `KidScreen.kt`, `KidDemo.kt` (leçons animées)
  - `SettingsSheet.kt` (Paramètres, pseudo avec debounce de 300 ms ; reçoit `SettingsRepository` + portée applicative)
- `ui/` — `Theme.kt` (jetons Nuit/Jour/Enfant, 5 palettes `DuckPalette` au choix dont une adaptée au daltonisme, contrastes WCAG AA, Fredoka, `LocalIsTwoPane`), `Common.kt`
  (atomes : `DuckButton`, `DuckIconButton`, `DuckChip`, `DuckTextField`, `DuckSegmentedRow`, `SectionTitle`, `InfoPill`…),
  `Sheets.kt` (`DuckSheet`, `DuckLazySheet`, `ConfirmDialog`, `EmptyState`, `LoadingState`), `ModuleLayout.kt`
  (`FeedbackCard`, `AlignedLabelRows`, `WholeOrNothing`, `ModuleLayout`), `MoveStrip.kt`, `Board.kt` (échiquier interactif),
  `BoardGeometry.kt` (géométrie partagée écran / export / mode enfant), `LinePlayback.kt`, `Captured.kt`, `Feedback.kt`,
  `icons/DuckIcons.kt` (**généré** : 40 icônes Material ; l'app n'embarque que `material-icons-core`).
- `util/` — `runCatchingSuspend` (relance `CancellationException`), `normalizeForSearch`.

### Règles d'architecture à respecter
- Les ViewModels héritent de `ViewModel`, reçoivent des **interfaces** (jamais `ChessDuckApp` ; les composables non plus : seul `MainActivity` le touche) et exposent des `StateFlow` en
  lecture seule (`_state` privé). Toute nouvelle dépendance passe par `AppContainer` + `DuckViewModels.Factory`.
- `stateIn(..., SharingStarted.WhileSubscribed(5_000), ...)` ; lire `prefs.settings.first()` quand l'écran peut ne pas être abonné.
- Pas de `runCatching` autour d'appels `suspend` : utiliser `runCatchingSuspend`.
- Aucune écriture d'état Compose pendant une passe de mesure (publier dans `onPlaced`).
- Les états d'interface sont `@Immutable` ; `Position`, `Move`, `Line`, `Puzzle`, `GameRecord` sont déclarés stables dans
  `app/compose-stability.conf`.
- Le thème principal d'un problème suit **une seule règle partout** (`PuzzleThemes.primary` / colonne `primaryTheme`).

---

## 3. Préférences et exigences du propriétaire (à respecter dans toute évolution)

- **Iso-fonctionnalité** : ne jamais casser un comportement existant sans qu'on le demande.
- **Aucun texte tronqué au-dessus de l'échiquier** (titres de leçon, ligne « Trait aux … · thème · niveau · #id »,
  en-têtes de partie et de revue) : les textes passent à la ligne, jamais de « … ».
- **Aucune boîte de texte qui défile sous l'échiquier** : `FeedbackCard` ajuste la taille du texte (jusqu'à 12 sp),
  `ModuleLayout` réduit l'échiquier si nécessaire (au plus 22 %, sans « respiration »), « Lire la suite » en dernier recours.
- **Accessibilité** : cibles tactiles de 48 dp, descriptions TalkBack, contrastes WCAG AA vérifiés par `ContrastTest`,
  texte jamais sous 11 sp (13 sp dans les leçons du mode enfant).
- **Anti-spoiler** : thème masqué en mode libre ; aperçu de la solution masqué dans l'export tant que le problème n'est pas résolu.
- **Images partagées (export PNG)** : sobres, **aucune publicité** (pas d'URL, pas d'appel à installer) ; titre-défi
  (« Les Blancs jouent et font mat en 2 ») ; formats Portrait 4:5 (1080 × 1350, WhatsApp) et Carré 1:1 (1080 × 1080) ;
  fond Sombre ou Clair ; échiquiers les plus grands possible ; pièces toujours opaques ; pied de page discret
  (mascotte, « ChessDuck », référence Lichess, ©).
- **Mode enfant** : simple, coloré, échiquier fixe (`adaptive = false`), sortie confirmée.
- L'ordinateur s'appelle « Ordinateur » dans l'interface (Stockfish n'est cité que dans « À propos »).

---

## 4. Compiler, tester, publier

### Commandes
```bash
./gradlew :app:testDebugUnitTest      # 34 tests : règles, leçons, base Room, contrastes, ViewModels, captures d'écran
./gradlew :app:assembleRelease        # APK signé : app/build/outputs/apk/release/app-release.apk (R8 + réduction des ressources)
```
- Le dépôt Maven renvoie parfois **HTTP 429** : relancer la commande (boucle de 3 essais).
- `SHOT_DIR=/chemin` : dossier où les tests Robolectric/Roborazzi écrivent les captures (par défaut `app/build/screenshots`).
  Les captures servent à **vérifier visuellement** chaque changement d'interface (les regarder avant de livrer).
- Roborazzi est en mode enregistrement par défaut ; `-Proborazzi.test.record=false` pour le mode vérification.
- Configuration cache Gradle activé ; dépendances dans `gradle/libs.versions.toml`.

### Environnement
- JDK 17+, SDK Android 35 (`build-tools 35.0.0`), `local.properties` avec `sdk.dir=…` (dans la session précédente :
  `/opt/android-sdk`). Si le SDK manque dans le nouveau conteneur, l'installer avant de compiler.
- Données de test facultatives (tests ignorés si absentes) :
  - `PUZZLE_DUMP` (défaut `/opt/work/puzzles_dump.txt`) : export `id|fen|moves` des 150 000 problèmes, régénérable avec
    `sqlite3`/Python depuis `app/src/main/assets/chessduck.db` ;
  - `OLD_DB` (défaut `/opt/work/old50k.db`) : ancienne base v1 de 50 000 problèmes pour tester la mise à jour.

### Publier une nouvelle version
1. Incrémenter `versionCode` et `versionName` dans `app/build.gradle.kts` (2.2.0 → 2.2.1 ou 2.3.0…).
2. `./gradlew :app:testDebugUnitTest :app:assembleRelease` (tout doit passer).
3. Remplacer l'APK : `git rm releases/ChessDuck-<ancienne>.apk` puis copier l'APK vers `releases/ChessDuck-<nouvelle>.apk`
   (un seul APK dans `releases/`).
4. Ajouter une section **en tête** de `docs/release-notes.md` (« ## ChessDuck X.Y.Z — titre », puis liste des changements).
5. Commiter et pousser : le workflow `.github/workflows/release.yml` crée la release `vX.Y.Z` (notes = `docs/release-notes.md`).
6. Vérifier la release, puis donner le lien :
   `https://github.com/ivanherboch/ChessNote/releases/download/vX.Y.Z/ChessDuck-X.Y.Z.apk`

### Commits
- Messages en français, détaillés, terminés par les lignes d'attribution demandées par la session.
- Pousser avec `git push -u origin claude/chessduck-android-app-wcikw1` (réessayer en cas d'erreur réseau).
- Ne pas laisser de fichiers non suivis (le hook de fin de session l'exige) ; `__pycache__/` et `*.pyc` sont ignorés.

---

## 5. Outils et données générées

| Outil | Rôle |
|---|---|
| `scripts/gen_lessons.py` + `LessonCompilerTool` | Leçons : modifier `scripts/lessons/*.txt`, puis `python3 scripts/gen_lessons.py` (étape 1 → `app/build/lessons/lessons.tsv`) et `LESSON_COMPILE=app/build/lessons/lessons.tsv ./gradlew :app:testDebugUnitTest --tests '*LessonCompilerTool*'` (étape 2 → fichiers Kotlin typés, coups validés en UCI) |
| `LessonSnapshotTool` | `LESSON_SNAPSHOT=/tmp/x.txt` : instantané des 113 leçons pour comparer avant/après |
| `LessonToolTest` | Outil d'auteur pour brouillons de leçons avec un Stockfish hôte (`LESSON_TOOL_IN`, `LESSON_TOOL_SF`) |
| `scripts/build_puzzle_db.py` | Reconstruit la base des problèmes depuis le CSV Lichess (non versionné) au schéma Room v3 |
| `scripts/migrate_asset_v3.py` | Migre sur place `assets/chessduck.db` au schéma v3 (sans le CSV) |
| `IconGeneratorTool` | `ICON_GEN="rounded.Flag …"` : régénère `ui/icons/DuckIcons.kt` ; ajouter une icône étendue = l'ajouter ici (pas de dépendance `material-icons-extended` dans l'app) ; `DuckIconsTest` vérifie l'égalité avec Material |
| `scripts/build_stockfish.sh` | Recompile Stockfish 11 (NDK requis) |

Base embarquée : `app/src/main/assets/chessduck.db` (v3, 150 000 problèmes, `PRAGMA user_version = 3`,
`room_master_table` renseignée). Schémas Room exportés dans `app/schemas/`.
Toute évolution du schéma : nouvelle version Room + migration + mise à jour de la base embarquée + tests.

---

## 6. Historique des versions (résumé)

- **1.x** : modules de base, 150 000 problèmes avec au moins 300 par difficulté × thème, thème principal unique,
  familles de thèmes, mode enfant, revue commentée, partage PNG, releases GitHub automatiques.
- **2.0.0** : refonte UI/UX Material 3 (design system, accessibilité, tablette).
- **2.0.1–2.0.2** : plus de défilement dans les cartes sous l'échiquier ; textes au-dessus de l'échiquier jamais tronqués.
- **2.0.3–2.1.2** : images de partage repensées (défi, formats 4:5 / 1:1, fond clair/sombre, adaptées à WhatsApp,
  échiquiers agrandis, pièces opaques).
- **2.2.0** : refactoring complet (plan P1 → P3) sans changement fonctionnel : performances, Room v3 indexée,
  architecture en dépôts, tests de ViewModels, tests d'interface sans attentes fixes.

Les détails de chaque version sont dans `docs/release-notes.md` ; les exemples d'images partagées dans `docs/screenshots/`.

---

## 7. Limites connues et pistes

- Le masquage « spoiler » d'une image n'existe pas dans WhatsApp : l'image de la solution reste visible dès réception.
- Une solution de 5–6 coups tient sur une image, mais avec de petits échiquiers (≈ 310 px).
- L'APK pèse ≈ 34 Mo (base de 150 000 problèmes avec index).
- La migration du réglage d'export (SharedPreferences → DataStore) n'a pas de test automatique (DataStore est un singleton
  partagé par les tests Robolectric).
- Les exercices de Notation « promotion » et « mat en 1 » sont tirés des problèmes dont c'est le thème principal.

## 8. Façon de travailler attendue

1. Lire le code concerné avant de modifier ; garder le style et la KDoc en français.
2. Après chaque changement : tests unitaires, puis contrôle visuel des captures d'écran concernées.
3. Build release avant de livrer ; publier une nouvelle version si l'utilisateur doit pouvoir tester.
4. Répondre à l'utilisateur en français, de façon concise : ce qui a changé, le lien de téléchargement, les limites éventuelles.
