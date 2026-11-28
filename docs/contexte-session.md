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
- **Version actuelle** : `4.7.0` (`versionCode = 118`), publiée en release GitHub `v4.7.0`.
- **Langue** : toute l'interface, les commentaires, la KDoc, les messages de commit et les notes de version sont **en français**.
- Le propriétaire est l'auteur de l'application : mention « © Ivan Herboch, 2026 » (À propos, images partagées).

### Modules (onglets)

| Onglet | Contenu |
|---|---|
| Notation | Exercices de notation anglaise FIDE (SAN) sur positions réelles ; niveaux Tous / Bases / Prises / Roques ; clavier virtuel ; indices 1/2 et 2/2 ; diagnostic pédagogique ; séries et record ; Mémo Anglais |
| Académie | 211 leçons en 6 catégories (16 bases, 53 ouvertures, 61 milieux de partie, 18 stratégie, 55 finales, 8 parties modèles) : Comprendre (démonstration commentée, voix) puis Pratiquer (coups, problèmes du thème ou finale contre l'Ordinateur) ; révisions espacées, séance du jour, Atelier (finales, réflexes, répertoire, tests) ; catalogue filtrable et recherchable |
| Problèmes | 150 000 problèmes Lichess (CC0), 50 thèmes officiels regroupés en 6 familles, **un seul thème principal par problème** (`PuzzleThemes.primary`) ; anti-spoiler (thème masqué en mode libre, bouton « Révéler le thème ») ; réfutation sereine (`REFUTE_HOLD = 8000 ms`, jauge or) avec « Réessayer » ; indice en 2 temps ; « Mon niveau » (ELO tactique), longueur, relais par thème secondaire ; Problèmes joués (50 récents, À refaire, Favoris) ; recherche par identifiant ; export PNG (énoncé + solution) |
| Jeu Libre | Contre l'ordinateur (Stockfish, 600–2400 ELO, fautes « humaines » sous 1350) ou à 2 joueurs ; partie libre par défaut (filet de sécurité, sans ELO) ou classée (ELO FIDE, K = 32, sortie du classement par une aide) ; partie en cours gardée si Android ferme l'application ; jauge d'évaluation ; annuler son coup ; historique ; revue commentée en langage naturel avec annotations `!!` `!` `?!` `?` `??`, mats manqués en 1 et 2 |
| Mode enfant | Activé dans les Paramètres : écran clair et coloré (police Fredoka), 2 joueurs, cavalier en forme de girafe, leçons animées du déplacement de chaque pièce, confirmation avant de quitter ; la partie enfant est effacée à la sortie mais la partie du Jeu Libre est conservée |

Téléphone : portrait, ou paysage compact (`LocalCompactHeight` : rail de navigation avec Paramètres, sans en-tête). Tablette : deux colonnes en paysage (échiquier à gauche, commandes à droite).

**Sauvegarde** : `data/UserBackup.kt` (JSON, Paramètres > « Sauvegarde hors-ligne ») ; `chessduck.db` exclue de la
sauvegarde Android (`res/xml/backup_rules.xml`, `data_extraction_rules.xml`).

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
- `data/` — `Db.kt` (Room **v5** : entités `Puzzle` (index couvrants `rating,id` et `primaryTheme,rating,id`), `PuzzleThemeLink` (`puzzle_themes`), `PuzzleProgress` (statut, essais, À refaire, favori), `LessonProgress`
  (étoiles, erreurs, indices, exercices, champs de révision), `GameRecord` (+ `startFen`), `AcademyAssessment` ;
  migrations 1→2→3→4→5 ; `syncPuzzles` par `ATTACH` + `INSERT OR IGNORE`), `Prefs.kt` (DataStore « chessduck »,
  migrations depuis les SharedPreferences `share_style` et `PlayDefaultsMigration` ; `snapshot`/`restore` pour la sauvegarde),
  `UserBackup.kt` (export/import JSON de la progression), `Repositories.kt` (interfaces `SettingsRepository`,
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
  `BoardGeometry.kt` (géométrie partagée écran / export / mode enfant), `LinePlayback.kt`, `Captured.kt`, `Feedback.kt`, `Narrator.kt` (synthèse vocale hors-ligne de l'Académie ; `SpokenFr` prononce la notation),
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
./gradlew :app:testDebugUnitTest      # 89 tests : règles, leçons, base Room, contrastes, ViewModels, captures d'écran
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
| `scripts/migrate_asset_v4.py` | Migre sur place `assets/chessduck.db` du v3 au v4 (tables de l'utilisateur recréées d'après `4.json`) |
| `LessonAuditTool` | Toujours actif : identifiants uniques, « À retenir » partout, thèmes connus, arrêts du Guidé sur un coup du joueur, cohérence texte/échiquier (« le cavalier g7 »…). Avec `REVIEW_SF=/usr/games/stockfish` : chaque coup du joueur vérifié par le moteur (rapport `app/build/lesson-audit.txt`) |
| `IconGeneratorTool` | `ICON_GEN="rounded.Flag …"` : régénère `ui/icons/DuckIcons.kt` ; ajouter une icône étendue = l'ajouter ici (pas de dépendance `material-icons-extended` dans l'app) ; `DuckIconsTest` vérifie l'égalité avec Material |
| `scripts/build_stockfish.sh` | Recompile Stockfish 11 (NDK requis) |

Base embarquée : `app/src/main/assets/chessduck.db` (v4, 150 000 problèmes, `PRAGMA user_version = 4`,
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
- **2.3.x–2.4.x** : lecture vocale des leçons (Guidé) et des fiches du mode enfant, leçons réécrites pour l'oral,
  lexique de prononciation, cohérence jauge / commentaires de revue, bandeau de coups par tour, nulles expliquées.
- **2.5.0** : refonte ergonomique en 4 étapes (shell et design system, échiquier accessible, écrans adultes,
  mode enfant et paramètres). Interface adulte et commentaires de revue au vouvoiement ; mode enfant au tutoiement.
- **2.7.0** : refonte de l'Académie, étape 2 sur 3 : finales jouables, réflexes, révisions espacées, paliers,
  placement et examens, variantes, QCM, répertoire, partie modèle et « Devinez le coup ».
- **3.4.0** (audit, lot 1) : `MoveStrip`/`MoveTable(hiddenFrom)` ; `levelRange(1,2) = 400..1199` ; `nextReview` sans `backToDrawer`, `EndgameTrainer.note`, seuil `min(PASS, total)` ; `BackHandler` de séance ; titre et onglets en `TextAutoSize` ; ordres P2 réécrits (initiations et méthodes d'abord), mat du berger après les trois principes ; onglet de départ Académie, défauts `lessonId = 115`, `academyLevel = 1` (FakeSettings garde 1/2 ; tests d'écran : `openLesson`) ; `setLevel` + puces de palier ; examen du palier en cours ; QCM : `retryQuiz`, 74 distracteurs réécrits ; `threatFrom` écarte l'échec ; `replayOk` ; vibrations non rejouées (`seenPulses`).
- **3.5.0** (audit, lot 2) : Défi `EQUIVALENT` sans `suggest` (flèche réservée à l'indice) ; `VariantMenu` en Défi (`setVariant` relance `startChallenge`), `Settings.variantsDone` (« id:variante », `markVariantDone`), une variante ne touche pas la maîtrise ; `EndgameSession.history` + `drawRule` (triple répétition, 50 coups) ; KQK 16 coups, KRRK 12 ; nulle : `DRAW_CP = 150`, `DRAW_DROP_CP = 120` ; `MISJUDGED` (Vancura, Philidor, dame contre pion fou) : sans référence, jamais refusé.
- **3.6.0** (audit, lot 3) : `EndgameFamily.PAWN_WAR` (leçon 130 `drill=PAWN_WAR`, roi noir sur les 2 colonnes éloignées, vérifié SF 10/10) ; leçon 115 : 2 arrêts + passerelle `onVision` (`NOTATION_LESSON`) ; stratégie 171/158 → P2, 164/165/162 → P3 ; 38 tactiques : 2e arrêt (p+2) ; parties modèles : 53 arrêts (moments clés, SF multipv) + `keyMoments`/`setKeyMoments` (étoile, `heroToMove` saute les coups hors arrêts) ; `SrsScheduler.reviewed(fluent)` ; erreurs/indices cumulés ; `resumeChallenge` (SavedStateHandle : variante, erreurs, indices) ; `AcademyHome.levelMastered/levelTotal` ; `ReflexDrills.replyText` ; `positionElo(level)`.
- **3.7.0** (audit, lot 4 ; ID1, notation française, écarté à la demande) : partie accompagnée (`PlayState.coached/coachLeft/checking/warning`, `CoachWarning`, `coach()` : perte ≥ `BLUNDER_CP = 200` et partie pas déjà perdue, `COACH_STOPS = 2`, `coachText`, `toggleCoach`/`playAnyway`/`dismissWarning`), activée pour `startFromPosition` ; 60 `SHOW` ajoutés (bases, stratégie, finales : 80 au total).
- **3.8.0** (plan de dette technique, groupes 1 à 3) : base compressée dans l'APK (15,8 Mo), `DebugProbesKt.bin` exclu ; `warmEngine()` au premier besoin ; `AppContainer.release()` en fin d'activité ; Stockfish : `failStart`, `SEARCH_GRACE_MS` ; `coachJob`/`stopCoach` ; `PlayViewModel.init` passe par `applyJob` ; `EndgameTrainer`/`ReflexTrainer(work)` ; `SquareVision.positionFor` ; cache `WeakTopics` ; `pieceImages` partagées ; `solvedCount`/`solvedAmong` ; `themeCounts` par index couvrant ; `RefutationLogic.intro/play/held` ; `Position.passTurn` ; Académie découpée : `AcademyModels`, `AcademyPractice` (`PracticeSeries`, `PracticeHost`), `AcademyOverview`, `AcademyTrainings`, `AcademySessionPanels`, `AcademyLessonSheets`, `AcademyLessonHeader`, `AcademySessionBoards` ; `MainActivity` : `TabBridges`.
- **3.8.1** : `warmEngine()` rappelé dans `AcademyViewModel.init` (l'attente du premier coup après la 3.8.0 gênait les entraînements) ; les points d'entrée des séances le rappellent sans effet.
- **3.8.2** : `ChessBoard.pointerInput` lit `size` à chaque toucher (la taille de case calculée au démarrage du gestionnaire devenait fausse quand l'échiquier changeait de taille sans recomposition : réserve de `ModuleLayout`) ; `MoveJudge.JUDGE_WAIT = 5 s` ; `Verdict.UNAVAILABLE` (Défi, Guidé) et `loss == null` (Deviner) : à rejouer, sans erreur ni 0 point ; tests `BoardResizeTest` (échoue sans le correctif) et `EndgameRealEngineTest` (vrai Stockfish de la machine branché dans `AppContainer.engineLazy`, finales Blancs/Noirs et réflexes ; ignoré sans /usr/games/stockfish).
- **3.8.3** : `guideHint` en deux temps (pièce, puis flèche ; bouton actif jusqu'à 2/2) ; flèche verte aussi à l'arrêt « À vous » du Guidé ; test `guidedHintHasTwoStages`.
- **3.9.0** : cœurs des séries (`Practice.hearts/heartsMax/penalized`, `PracticeSeries.HEARTS_KINDS` = LESSON, ONLY_MOVE, REVIEW ; autant de cœurs que de problèmes) : une erreur coûte un cœur, le problème compte si `!penalized && hintStage < 2` ; `heartsText` ; titre de la série « N cœurs sur M ».
- **3.10.0** : refonte UX Académie, étape 1 sur 3 (plan `plan_simplification_academie_claude.md`) : `Comprendre | Pratiquer` (`LessonModeBar`), `LessonSelector` à statut unique (`lessonStatus`), Comprendre sans arrêts (`asking`/`guideMove`/`guideHint`/`hiddenFrom` supprimés), Pratiquer routé par `practiceMode()`, `EQUIVALENT` avec `suggest`, cœurs supprimés (`Practice.arrowUsed`), bouton « Réessayer » partout.
- **3.11.0** : refonte UX Académie, étape 2 sur 3 : `AcademyRoute` (HOME, LESSON, WORKSHOP_*), `AcademyHomeScreen` + `WorkshopScreen`, tiroir `TrainingsSheet`/`AcademyTrainingsButton`/`fromDrawer`/`drawerReturn`/`suggestions`/`trainingActive` supprimés, catalogue à un seul état, Fiche à 40 dp.
- **4.0.0** : refonte UX Académie, étape 3 sur 3 : révisions à une épreuve (`openReview` selon `practiceMode`), `Settings.repertoireIds` (clé `academy_repertoire_ids`, `toggleRepertoire`), `repertoirePool`, signet d'ouverture (icônes `Bookmark`/`BookmarkBorder`), `LessonBriefSheet` en 3 sections, test vocal du Calcul à l'aveugle ; `Validation` et `validation()` supprimés.
- **4.0.1** : `ModuleLayout` (portrait) mesure `overBoard` à toute la largeur moins 16 dp (plus de rétroaction largeur de l'échiquier → lignes) et accepte `reserveKey` (réserve remise à zéro, sans animation, au changement d'écran) ; `AcademyScreen` : `bottomMin` 150 dp en séance, `reserveKey` = leçon, mode, variante, série, finale, réflexe ; titre de finale sans « position i sur n » ; test `endgameBoardFillsThePhoneWidth` (≥ 92 % de la largeur, taille stable, w393dp).
- **4.0.2** : `LinearProgressIndicator` (accueil, revue, réfutation) avec `gapSize = 0.dp, drawStopIndicator = {}` : plus de point d'arrêt Material 3.
- **4.0.3** : Calcul à l'aveugle : `ReflexKind.BLIND.question` réécrite, `ReflexTrainer.showBoard` (message de marche à suivre, une seule fois), `ReflexTrainer.giveUp` (solution, question manquée) ; `ReflexPanel` : « Voir la position » puis « Solution ».
- **4.0.4** : `KidScreen.PieceCard` : nom sous la carte (hors coins arrondis et bordure), `AutoText` jusqu'à `MIN_TEXT`, écart 3 dp entre cartes.
- **4.1.0** : `WeakTopics.compute(games, progress, worked, now)` : poids `DECAY` (0,93) par partie, erreurs d'avant le dernier travail × `WORKED_WEIGHT` (0,25), disparition `SNOOZE_DAYS` (3) si pas réapparu ; `ReviewTopic.themes` + `pool`/`lessonFor` (rotation des leçons) ; `ReviewTopic.FORK_ALLOWED` (leçon 38) et `MISSED_FORK` (138) via `ReviewComments.isFork` ; `Settings.lessonWorkedAt` (DataStore `academy_lesson_worked_at`, `markLessonWorked`, posé par une pratique réussie ou un Pratiquer ≥ 2 étoiles) ; `AcademyViewModel.trainReviewTopic` ; `Priority.score` en Double.
- **4.1.1–4.1.2** : `AcademyHomeScreen` compact (sans défilement sur 360 dp, test `academyHomeFitsWithoutScrolling`) : lignes de priorité cliquables, `LessonsCard` sur une ligne (chips retirées), tuiles de l'Atelier en une rangée (2 par rangée si `fontScale > 1,3`), raisons de `WeakTopics` raccourcies ; `LessonSelector` : retour à l'accueil avec l'icône `AutoMirrored.Rounded.KeyboardReturn` (flèche coudée).
- **4.1.3** : `Settings.understoodIds` (DataStore `academy_understood_ids`, `markUnderstood`) posé par `goTo` à la fin de la ligne principale dans Comprendre ; `AcademyViewModel.understood` ; `lessonStatus(p, understood)` ; `LessonModeBar` : icône `CheckCircle` sur le segment Comprendre (`DuckSegmentedRow.icons` accepte des null) ; carte de fin « Comprendre terminé ✓ » (Tone.GOOD), bouton de suite visible même pendant la voix.
- **4.1.4** : accueil : `WorkshopTiles` avant `LessonsCard` ; raison « Plus revu depuis votre entraînement » quand aucune erreur depuis.
- **4.2.0** : Séance du jour : `data/DailyLog` (DataStore `academy_daily`, `markDailyStep`), `DailyStep`/`DailyPlan` (`AcademyModels`), `AcademyViewModel.daily`, `startDaily`, `markDaily` (fin des révisions, Pratiquer, série de leçon via `PracticeHost.lessonSeriesDone`, finale), `AcademyState.dailyRunning/dailyPrompt`, `DailyBanner` (AcademyScreen). Filet de sécurité : `Settings.safetyNet` (`play_safety_net`), `PlayState.netUsed` (partie hors ELO), `CoachWarning.why/reply/showWhy`, `ReviewComments.netReason`, `PlayViewModel.setSafetyNet/showWhy`, interrupteur dans `GameSettingsSheet`, icône `Rounded.Shield` ; `NetEvent` (`play_net_events`, 40 au plus) pris en compte par `WeakTopics.compute(events)`.
- **4.2.1–4.2.2** : carte « Séance du jour » (`JourneyCard`, `DailyPlan.buttonLabel/stepDetail`, pastilles numérotées) ; filet : `ReviewComments.netReason` → `NetReason(text, topic, best)` (pièce attaquée « mais pas en danger », « Coup conseillé »), `CoachWarning.best` (flèche verte), deuxième arrêt au même demi-coup → explication d'emblée (`stopPly/stopLine`).
- **4.2.3** : `PlayViewModel.coach` affiche le coup aussitôt (ligne poussée, `checking`), vérifie en `CHECK_MS` (250 ms) par position, retire la gaffe (`undoAnim`) ou enchaîne `afterMove` ; `replySince` fait partir la pause minimale de la réponse de l'affichage du coup ; analyse de la position d'après gardée pour l'indice.
- **4.2.4** : `ReviewComments.bestNote` : le coup conseillé du filet est décrit (échange de dames, échange égal, gain de matériel, prise gratuite, coup tranquille).
- **4.2.5** : filet : arrêt seulement si `netReason` renvoie un point faible (`topic`) et si le joueur ne reste pas au-dessus de `STILL_WINNING_CP` (300) ; `ReviewComments` : cas 2 ter « pièce qui va tomber après la réponse » (`underAttack` : non défendue, attaquée par moins fort, ou plus d'attaquants que de défenseurs).
- **4.2.6** : `SafetyNet.decide` extrait (pat gagnant, `CHECK_REPLY_CP` = 300 pour l'échec gagnant permis) ; vérification aussi d'un coup rejoué depuis l'historique ; `goTo`/`navigate` bloqués pendant `checking` ; thèmes `CASTLING`/`DEVELOPMENT` exclus des arrêts (`SafetyNet.ADVICE`) ; texte du cas 2 ter selon le motif ; banc `SafetyNetEngineTool` (`SAFETY_AUDIT=1`) : 121/133 gaffes arrêtées, 0 faux arrêt (B), 9 arrêts justes sur 347 coups débutants (C).
- **4.2.7** : `closeReviews`/`closeRepertoire` → `stopSessionLine()` (annule lecture/jugement, `challenge = false`) ; `dailyPrompt` effacé par `open()` hors séance, `goHome`, `backToCaller`/`openRoute` vers HOME ; `PracticeSeries.widen` (tranche ±300, ±600, puis 0..4000) ; test `AcademyWalkthroughTest` (toutes les leçons, variantes, sortie de révision, bandeau).
- **4.2.8** : `PlayViewModel.navigate` efface `warning` ; `playAnyway` vérifie la légalité dans la position affichée ; test `PlayFuzzTest` (moteur bruité, 80 graines × 160 actions : blocage, fin non conclue, enregistrement, ELO hors classement).
- **4.2.9** : `PlayViewModel.abandonInProgress()` (appelé par `newGame`, `applySettings`, `startFromPosition`) : partie en cours contre l'Ordinateur (`inProgress`) → `finish` comme un abandon (enregistrée, ELO si classée) ; textes de confirmation `quitText` dans `PlayScreen`.
- **4.2.10** : `PuzzlesViewModel.onMove` : la réponse adverse n'avance le curseur que s'il est resté sur le coup joué ; audit de la base (150 000 problèmes : légalité, parité, fins prématurées, thèmes) ; tests `PuzzlesFuzzTest`.
- **4.2.11** : `PuzzleState.arrowUsed` (indice 2/2) : pas de `markSolved` à la fin, message dédié ; même règle que `PracticeSeries` (erreur tolérée, flèche non).
- **4.2.12** : `LessonsCard(masteredCount, level, onCatalog)` : bouton « Palier N » (catalogue filtré sur le palier), boutons sous le titre si `fontScale > 1.3`.
- **4.2.13** : `PlayViewModel.finishedGeneration` + résultat provisoire « Partie terminée. » posé aussitôt dans `finish` (plus de double enregistrement ni de double ELO) ; `rnd` injectable ; `PlayFuzzTest` à graine fixe (`FUZZ_FROM`/`FUZZ_TO`/`FUZZ_DEBUG`).
- **4.2.14** : `LessonsCard` : titre « Leçons », « N sur 211 maîtrisées », boutons « Palier N (n) » et « Toutes (211) » (description TalkBack contenant « Ouvrir le catalogue des leçons », utilisée par les tests).
- **4.2.15** : refonte des Problèmes, étape 1 sur 3 (plan `plan_problemes_3_etapes.md`) : fiabilité, anti-spoiler, réfutation, export, tirage par rowid (voir le Relais ci-dessous).
- **4.3.0** : refonte des Problèmes, étape 2 sur 3 : Solution et Recommencer, bandeau des coups, catalogue (tirage, sélection épuisée), passerelles vers le Jeu Libre et l'Académie (voir le Relais ci-dessous).
- **4.4.0** : refonte des Problèmes, étape 3 sur 3 : Room v5 (suivi détaillé, index couvrants, `puzzle_themes`), « Mon niveau », À refaire et Favoris, thèmes secondaires, bilan et longueur (voir le Relais ci-dessous).
- **4.4.1** : points hors périmètre de la refonte des Problèmes traités : `PuzzleProgress.after` suit `SrsScheduler.INTERVAL_DAYS` (abandon → 1 j, erreur → intervalle gardé, résolution nette → intervalle suivant puis retrait) ; `probeAll`/`probeInThemes` renvoient jusqu'à `PROBE_PICK` (200) candidats tirés au hasard ; `PlayState.origin` (`PositionOrigin` LESSON/PUZZLE, `TabBridges.playPosition` en Triple) ; grande police dans `PuzzlesScreen` (`bottomMin` 240 dp, ligne d'information sur deux lignes, indice 1/2 raccourci).
- **4.4.2** : `MoveStrip` retiré de `PuzzlesScreen` (demande du propriétaire : échiquier trop petit) ; `PuzzlesViewModel.goTo` reste (tests, navigation possible).
- **4.4.3** : thèmes secondaires : total « Tous les thèmes » tiré des compteurs stricts (problèmes distincts), familles via `PuzzleRepository.linkedCount` (COUNT DISTINCT sur `puzzle_themes`).
- **4.4.4** : compteurs du catalogue avec filtre de longueur : `PuzzleDao.metaWithLength` + `countByLength` (un passage, thèmes/familles/total avec résolus, ~1,6 s sur la vraie base en Robolectric), `PuzzleRepository.lengthCounts`, cache `lengthCache` dans le ViewModel ; phrase explicative sous les puces de longueur.
- **4.4.5** : longueur par nombre réel de demi-coups (`PuzzleThemes.lengthPlies`, paramètres DAO `minPly`/`maxPly` sur `length(moves) - length(replace(moves, ' ', '')) + 1`) au lieu des étiquettes Lichess (26 incohérentes, dont #Cex55).
- **4.4.6** : interrupteur « Inclure les thèmes secondaires » supprimé (préférence `puzzle_secondary`, `setSecondary`, `linkedCount`/`countLinkedTotal`) ; `drawRandom` tire d'abord un problème non résolu du thème principal, puis par relais un non résolu où le thème est secondaire (`randomId(..., onlyUnsolved = true)`, `PuzzleState.secondary` + message « apparaît aussi »), puis un résolu ; compteurs toujours stricts.
- **4.4.7** : boutons d'action communs dans `ui/ActionButtons.kt` (`NextButton`, `RetryButton`, `RestartSeriesButton`, `SolutionButton`, `ScoredHintButton`) et textes d'indice `HintText` (PIECE, ARROW, `scored`, `plain`), utilisés par Problèmes, Pratiquer, leçon, finales, réflexes et Jeu Libre. Tout nouveau bouton de ce type doit passer par eux. `DuckButton` efface son icône si elle couperait un mot du libellé (`IconAndLabel`, mesure de la largeur intrinsèque minimale).
- **4.4.8** : audit v4.4.7, étape 1. `PlayViewModel` reçoit `SavedStateHandle` (clés `play_*`, `save()`/`restore()` ; partie terminée ou vide : sauvegarde effacée ; coup en vérification non gardé). `PuzzleDao.themeCountsWithLength` (agrégation SQL) remplace `metaWithLength`/`countByLength`/`lengthCounts` ; `PuzzleRepository.themeCounts(range, length)` ; `linkedTotals`/`solvedMeta` et le paramètre `secondary` des compteurs supprimés. `PuzzleDao.recordAttempt`/`setBookmark` en `@Transaction`. `Stockfish.close()` : hors verrou, seul `process` (@Volatile) est détruit. `ReflexTrainer.queue` : `List<ReflexItem>` immuable (`sealed interface ReflexItem`). `Position.insufficientMaterial` : fous tous de même couleur.
- **4.4.9** : audit v4.4.7, étape 2. `AcademyState.session : SessionContext` (`endgameLesson`, `endgameReview`, `topicSeries`, `reviewFluent`, `missed`) remplace les `var` du ViewModel (`ctx`, `setSession`) ; `Practice.missed` remplace `practiceMissed`. `advanceOpponent()` : retour sur le coup du joueur + dernier commentaire seulement ; `finishChallenge()` : dernier paragraphe + « À retenir » + score/maîtrise. `SrsScheduler.started(p, understood)` : règle unique « En cours » (en-tête, catalogue, `continueId`, `continueLesson(..., understood)`). `PracticePanel(fromCaller)`. Leçon 115 : « Coup suivant des Blancs : … ».
- **4.5.0** : audit v4.4.7, étape 3. `Settings` : `ratedGame = false`, `safetyNet = true`, `aiElo = 600` (installation vierge) ; `setRatedGame` (coupe le filet), `setSafetyNet(true)` repasse en libre. `FakeSettings` et `@Before` Robolectric : élève engagé (classée, 1200, sans filet). `PlayState.rated`/`unratedBy`/`notice` (sauvegardés) ; `unrate()` sur indice 2/2, annulation, filet ; `setRatedGame` ; partie hors ELO enregistrée avec `eloAfter = 0` (« Hors ELO » dans `HistorySheet`). `GameSettingsSheet` : section « Enjeu ». `REFUTE_HOLD = 8000`, jauge or. `ExportSheet(themeVisible)`, « À refaire » sans thème non révélé, pastille d'échéance. `VisionSession.filter` + `setVisionFilter` + puces. Hors périmètre traité : message du relais quand le problème passé était le dernier (`skippedLast`), bilan de leçon sur les 2 derniers commentaires, KDoc de `Stockfish.close()` (redémarrage voulu).
- **4.5.1** : `PlayDefaultsMigration` (DataStore, une fois, tant que `play_rated_game` est absent) : magasin vide → libre/filet/600 ; sinon → classée sauf filet stocké, `ai_elo` 1200 si absent. Corrige la 4.5.0, qui appliquait les nouveaux défauts aux utilisateurs existants n'ayant jamais touché ces réglages.
- **4.6.0** : audit v4.4.7, étape 4. `LocalCompactHeight` (deux colonnes et hauteur < 480 dp, `isCompactHeight`) : pas d'en-tête global, Paramètres en bas du `NavigationRail` défilable ; Notation : rangée haute 48, pièces 40, `KEY_ROW_COMPACT = 34`. Grande police (> 1,3) : touches de pièces sans ligne anglaise, `FlowRow` des pastilles, niveau court, « Réponse… ». `MoveStrip` : bande de 48 dp (fond dessiné en retrait). `BoardTheme.TOURNAMENT_SELECT` (bleu) hors `colorBlind` ; `DuckColors.colorBlind` (Bleu-Orange) ; `annotationColor(a, colorBlind)`. `DailyBanner` dans une `Column` au-dessus de `ModuleLayout` ; haut-parleur `VolumeUp` + `selected = speaking`. Hors périmètre traité : en-tête du Jeu Libre en grande police (libellés courts, description TalkBack complète).
- **4.7.0** : audit v4.4.7, étape 5. `UserBackup` (JSON : leçons, problèmes, parties, tests, préférences typées ; import vérifié puis restauration en transaction ; `BackupFormatException`) ; DAO `all*`/`clear*`/`insert*` ; `Prefs.snapshot`/`restore` ; Paramètres : « Sauvegarde hors-ligne » (CreateDocument/OpenDocument) et « Réinitialiser mon ELO tactique » (`puzzleElo = 0`, suivi par `PuzzlesViewModel`). `backup_rules.xml` + `data_extraction_rules.xml` excluent `chessduck.db`. Signature : `CHESSDUCK_*` (environnement ou `keystore.properties`), repli sur `keystore/chessduck-release.jks`. `.github/workflows/ci.yml` (tests à chaque push et PR). README et §1-§2 alignés (211 leçons, Room v5).
- **3.3.5** : `AcademyViewModel.init` lance une recherche courte (démarrage du moteur) ; réfutation affichée : `onSquareTap` de l'échiquier = `resumeAtMiss` / `retryPractice` / `PuzzlesViewModel.retry`. Test d'écran `challengeCaptureByTaps` (leçon 9 en Défi, prise Qxd2 au toucher).
- **3.3.4** : `EndgameTrainer` : analyse préparée (`prepare`/`prepared`, Deferred par FEN) pendant la réflexion de l'élève ; coup perdant si sous le seuil ET baisse ≥ `DROP_CP` (150) par rapport à la position d'avant ; `ENGINE_WAIT` 10 s, moteur muet = coup annulé ; pion perdu avec tour/dame contre roi seul = réussi. Outil `EndgameSfTool` (REVIEW_SF, FAMILY, TOTAL ; `--rerun`). Moteur du téléphone : Stockfish 11.
- **3.3.3** : `open(..., guided = true)` depuis le catalogue et les suggestions ; `AcademyState.fromDrawer`/`drawerReturn` : `markFromDrawer()` (boutons de séance du tiroir) et `backToDrawer()` dans les `close*`, l'écran rouvre le tiroir.
- **3.3.2** : `ModuleLayout` mesure `overBoard` à la largeur de l'échiquier (portrait et paysage) et réduit l'échiquier si besoin : plus de texte rogné.
- **3.3.1** : `EndgameFamily.method` (méthode en une phrase) ; `EndgameSession.better`/`before`/`beforeMoves` : après un coup perdant, meilleur coup cherché sur la position d'avant, et `retry()` y revient (flèche verte).
- **3.3.0** : `IDEA:`/`TRAP:` rédigés pour les 158 leçons qui n'en avaient pas (basics, middlegames, strategy, endgames, model_games) : 2-3 idées, 2 pièges (« Pour vous » / « Contre vous ») ; les 211 leçons en ont.
- **3.2.6** : `ReflexDrills.threatFrom` lit la menace sur la position affichée (`threatsIn` : prises adverses gagnantes si l'adversaire rejouait, `opponentToMove`), `ThreatItem.threats` (fourchette : plusieurs cibles), confirmation par l'Ordinateur (`threatConfirmed`) ; calcul à l'aveugle : tout mat accepté. Outil `ReflexAuditTool` (REFLEX_AUDIT=entrée.tsv) + script Stockfish pour auditer un échantillon.
- **3.2.5** : `guideMove` (verdict EXACT) relance la lecture automatique (`startAutoplay`, extrait de `togglePlay`, sans reprise d'une phrase suspendue).
- **3.2.4** : FEN de départ corrigées (ids 124, 76, 182 : camp sans le trait en échec) ; `LessonsTest.lessonStartPositionsAreLegal` contrôle toutes les positions de toutes les lignes.
- **3.2.3** : `LessonBriefSheet` pour toutes les leçons (icône « i » toujours présente) : objectif, à retenir, thème (`PuzzleThemes` : but et conseil), moments clés (`pauseSteps` : coup et commentaire), puis conditions, idées, pièges s'il y en a.
- **3.2.2** : énoncé des séries LESSON/REVIEW (`showPractice`) : thème du problème (`PuzzleThemes.byKey[l.theme]` : nom et but) et rappel « À retenir » de la leçon. Bouton Lecture/Pause : « Pause » aussi quand la voix parle (`speaking`), qui appelle `vm.pause()` (voix suspendue, reprise par `togglePlay`).
- **3.2.1** : parcours Guidé → Défi garanti : `QuizPanel` et `EndgamePanel` reçoivent `needsChallenge` (leçon non maîtrisée, moins de 2 étoiles, hors révision/répertoire) et proposent « Passer au Défi » au lieu de « Leçon suivante ».
- **3.2.0** : plan UI/UX Académie (P0-P2). Réfutation partagée `ui/Refutation.kt` (`Refutation(position, last, arrow: Move?)`, `REFUTE_HOLD`, `RefuteCountdown`, `refuteArrow`) + `feature/RefutationLogic.kt` (réponse et texte communs Problèmes/Académie). `Practice` aligné sur les Problèmes (`refute`, `hadError`, `progress`, `retryPractice`, `practiceSolution`, `practiceBack/Forward`). `EndgameSession` : `hintStage`, `evalWhite`, `punish`, `start`/`replay` (`retry()`), `pulse` ; `ReflexSession.pulse`. `ui/SessionHeader.kt` (Académie, Revue, Vision). `AcademyState.sessionFlipped` (`flip()` en séance). `AcademyBoardInfo` + `StepDots` en `overBoard` (rien pour la leçon). `LessonBriefSheet`, `ChooseColorSheet`, `guideHint`, `enableNarration`, `FeedbackCard(busy)`. Convention de couleurs : vert indice/bonne case, rouge erreur/menace, bleu démonstration, `select` sélection, or repère.
- **3.1.2** : numéro affiché d'une leçon = sa place dans `Lessons.all` (`Lessons.number(id)`), l'id reste la clé de progression ; « Leçon suivante » en fin de Défi / validation / quiz ouvre la suivante en Guidé (`nextLesson(guided = true)`).
- **3.1.1** : plus de bandeau au-dessus de l'échiquier de l'Académie : suggestions dans le tiroir (« À faire maintenant »,
  `AcademyViewModel.suggestions` affiché sur le bouton), révision et répertoire dans `sessionTitle`, carte de leçon sur
  deux lignes compactes.
- **3.1.0** : ergonomie de l'Académie : « Entraînements » dans l'en-tête (`AcademyTrainingsButton`, onglet 1), bandeau
  d'une ligne masqué s'il est vide, en-tête de séance (`sessionTitle` → `SessionTitle`), menu des variantes, tiroir
  avec séance surlignée et cartes de priorités, catalogue à filtres repliables et persistants (`setFilter` n'ouvre plus
  de leçon), `levelName` « 2 · Débutant » / `levelSpoken` « 2, Débutant ».
- **3.0.0** : refonte de l'Académie, étape 3 sur 3 : 211 leçons (bases, méthode, stratégie, finales, 8 parties modèles,
  53 ouvertures), pont Revue → Académie, bilan des 3 priorités, Vision des cases, Calcul à l'aveugle.
- **2.7.1 → 2.7.4** : correctifs de l'Académie (coup du joueur montré pendant le jugement, mat plus lent refusé,
  coup équivalent ramené avec flèche verte, bouton « Entraînements » et en-tête d'entraînement, bandeau de puces
  qui passe à la ligne au lieu de défiler sous « Entraînements » avec une grande police).
- **2.6.0** : refonte de l'Académie, étape 1 sur 3 (plan `plan_academie_claude_etapes.md`) : identifiants fixes,
  Room v4, juge de coups, Guidé actif, Défi intelligent avec étoiles, validation sur 5 problèmes, passerelles.

### Relais refonte UX Académie (refonte terminée, v4.0.0)
- Révisions : `openReview(index)` lance une seule épreuve selon `practiceMode(l)` : PUZZLES → `startSeries(REVIEW, 1 problème)`, ENDGAME → `startEndgame(total = 1)` + `endgameReview`, MOVES → ligne (ouvertures) ou coups clés (`keyMovesOnly` vaut aussi en révision) ; `reviewChallengeDone` appelle directement `completeReview`. En-têtes « Révision i sur n · 1 problème / 1 position ».
- Répertoire : `Settings.repertoireIds` (`Prefs`, DataStore `academy_repertoire_ids`), `SettingsRepository.toggleRepertoire`, `AcademyViewModel.repertoireIds`, `toggleRepertoire`, `repertoirePool(white, chosen, worked)` (choisies, sinon travaillées, sinon vide → `startRepertoire` ne fait rien).
- UI : signet dans `LessonModeBar` (ouvertures, deux rangées), bouton « Dans mon répertoire » dans `LessonBriefSheet(l, onClose, inRepertoire, onToggleRepertoire)`, `RepertoireSide` dans `WorkshopScreen` (« Réviser », « Choisir mes ouvertures »).
- `LessonBriefSheet` : sections « L'essentiel », « Idées et pièges », « Moments clés ».

### Relais refonte UX Académie (état après l'étape 2, v3.11.0)
- `AcademyModels.kt` : `enum class AcademyRoute { HOME, LESSON, WORKSHOP_ENDGAMES, WORKSHOP_REFLEX, WORKSHOP_REPERTOIRE, WORKSHOP_TESTS }` ; `AcademyState.route` (HOME par défaut, sauvegardée sous `academy_route`) et `sessionFrom` (écran d'origine d'une séance) ; `backLabel(s)`.
- `AcademyViewModel` : `openRoute(r)`, `goHome()` (arrête tout, voix comprise), `back()` (retour Android et ←), `enterSession()` dans les `start*` publics (sauf `startEndgame(fromLesson = true)`), `backToCaller()` dans les `close*` (remplace `backToDrawer`). `open(save/session)` passe en LESSON ; la restauration au lancement garde l'accueil.
- `AcademyOverview.home.continueId` : la dernière leçon ouverte si commencée sans être maîtrisée, sinon une autre commencée ; `suggestions` supprimé.
- `AcademyHomeScreen.kt` : `AcademyHomeScreen(vm, onCatalog(level, category))` (JourneyCard, PrioritiesCard, LessonsCard, WorkshopTiles, LevelSheet ; deux colonnes si `LocalIsTwoPane`) et `WorkshopScreen(vm, route)`. `AcademyTrainings.kt` supprimé.
- `AcademyScreen` : `when (s.route)` → accueil, Atelier ou `ModuleLayout` de la leçon ; `BackHandler(route != HOME) { vm.back() }`. `LessonSelector(…, onHome, onNext)` : ← Accueil à gauche.
- `LessonCatalog` : un seul état par carte (À revoir / Maîtrisée + étoiles / En cours / Conseillée).
- Reste pour l'étape 3 : révisions du jour (`startReviews` rejoue encore la ligne puis 2 problèmes), répertoire (`startRepertoire` tire 5 ouvertures du camp), `LessonBriefSheet`.

### Relais refonte UX Académie (état après l'étape 1, v3.10.0)
- `AcademyLessonHeader.kt` : `LessonSelector(l, mastered, stars, status, …)` + `lessonStatus(LessonProgress?)` (« À faire » / « En cours » / « Maîtrisée ») ; `LessonModeBar(vm, s, l, variantsDone, onBrief)` = segments « Comprendre » | « Pratiquer », `VariantMenu`, bouton « Fiche », ⇅.
- `AcademyState` : `challenge` = Pratiquer ; `keyMoments = true` par défaut ; `lessonPractice` (série ou finale lancée depuis la leçon : en-tête de leçon gardé, `sessionTitle` nul) ; `quizPassed`. Champs `answered`/`ASK` supprimés.
- `AcademyViewModel` : `PracticeMode { PUZZLES, ENDGAME, MOVES }`, `practiceMode(l)`, `startPractising()` (appelé par `open`/`setChallenge(true)`), `keyMovesOnly(l)` (modèles selon `keyMoments`, bases, stratégie, finales sans générateur), `backToLesson()` dans `closePractice`/`closeEndgame` ; maîtrise : Pratiquer ≥ 2 étoiles, série ≥ 4/5 (`lessonPracticeDone`), finale ≥ 3/5 (`endgameFinished`).
- `Practice` : `hearts`/`heartsMax`/`penalized`/`HEARTS_KINDS` supprimés ; compte si `hintStage < 2 && !arrowUsed`.
- `AcademyScreen` : barre du bas Comprendre = 🔊 ⏮ [Lecture/Pause | « Question de fin » | « Passer à la pratique »] ⏭ ; `LessonBridges(showTrain = practiceMode != PUZZLES)` ; `QuizPanel(toPractice)`, `PracticePanel(inLesson)`, `EndgamePanel(inLesson)` (« Revoir les coups »).
- Reste en place pour l'étape 2 : tiroir `Entraînements · N` (`AcademyTrainings`), `AcademyOverview`, `LessonBriefSheet` ; révisions et répertoire passent encore par `startChallenge`.

### Relais refonte Académie (état après l'étape 2, v2.7.0)
- Sources (étape 2) : en-tête `… | id=N level=2..5 order=level*1000+id theme= kind=line|model_game drill=FAMILLE` ;
  lignes `IDEA:`, `TRAP:`, `QUIZ: question | bonne | mauvaise(s) | explication`, `VARIANT n | Titre` suivi de `> SAN : texte`.
  Fichier `scripts/lessons/model_games.txt` (catégorie `MODEL_GAMES`, id 114 : l'Opéra). Généré : `LessonsModelGames.kt`.
- `Lesson` : + `drill`, `ideas`, `traps`, `quiz: LessonQuiz?`, `variants: List<LessonVariant(from, title, uci, texts)>`,
  `lineFor(v)`, `textsFor(v)` ; `LessonKind.MODEL_GAME` ; `objectiveText(l)` (fiche lue et affichée à l'ouverture).
- `EndgamePractice.kt` : `EndgameFamily` (KQK, KRRK, KRK, KBBK, KPK_WIN, LUCENA, PHILIDOR_DRAW, ROOK_PAWN_DRAW),
  `EndgamePositions.generate(f, rnd)`, `EndgameTrainer` (5 positions, `PASS = 3`, `WIN_CP = 250`).
- `ReflexDrills.kt` : `threatFrom(puzzle)`, `captureFrom(pos, rnd)`, `ReflexTrainer`, `ReflexKind` ; « Le seul coup » =
  série `PracticeKind.ONLY_MOVE` (thème `defensiveMove`). `ChessBoard(onSquareTap = …)` pour toucher une case.
- `SrsScheduler.kt` (horloge injectable) : `mastered`, `reviewed`, `isDue`, `due`, `daysUntil`, `recommended`, `continueLesson`.
- `AcademyAssessments.kt` : `levelRange`, `placementSpecs`/`placementLevel`, `examSpecs`/`examPassed` (8/10) ;
  `PracticeKind` LESSON/REVIEW/ONLY_MOVE/PLACEMENT/EXAM ; résultats dans `academy_assessments` (`placement`, `exam`).
- `AcademyViewModel` : `home: StateFlow<AcademyHome>`, `endgame`, `reflex`, `setVariant`, `startQuiz/answerQuiz`,
  `skipGuess`, `startReviews/nextReview`, `startRepertoire/nextRepertoire`, `startPlacement`, `startExam`, `validation(l)`.
  Écran : `AcademyPanels.kt` (bandeau, tiroir Entraînements, panneaux finale/réflexes/série/QCM, variantes).

### Relais refonte Académie (étape 3 terminée, v3.0.0)
- 211 leçons, identifiants 1–211 sans trou : bases 115–130 (`basics.txt`, palier 1, catégorie `BASICS`), méthode et
  initiation 131–138, tactique 139–153 (`middlegames.txt`), stratégie 154–171 (`strategy.txt`, catégorie `STRATEGY`),
  finales 172–187 et 211, parties modèles 188–194 (`model_games.txt`), ouvertures nouvelles 195–210 (`openings.txt`).
- Les 37 ouvertures d'origine ont toutes `IDEA` ×3, `TRAP` ×2, 2 `VARIANT` et un `QUIZ`.
- Finales jouables ajoutées : `KQKP`, `QUEEN_BISHOP_PAWN_DRAW`, `VANCURA_DRAW` (`EndgamePractice.kt`, règle `pawnGone`).
- Audit : dans une partie modèle, un coup historique imprécis n'est accepté que si son commentaire contient
  « Ordinateur ». Positions des leçons PUZZLE ajoutées à `scripts/lessons/puzzles.json` depuis la base embarquée.
- `WeakTopics.kt` : `ReviewTopic` (leçon + thème), `ReviewComments.topic(g, line, i, analysis)`, `WeakTopics.compute` →
  `AcademyViewModel.priorities` (paramètre `games`) et `trainTopic(lessonId, theme)` ; bouton dans `ReviewLayout`.
- `SquareVision.kt` (`VisionKind`, `VisionSession`) : `NotationViewModel.vision`, menu du niveau → « Vision des cases ».
- Calcul à l'aveugle : `ReflexKind.BLIND`, `BlindItem`, `ReflexTrainer(speak = …)`, `announce()`, `showBoard()`.
- `SrsScheduler.recommended` : les bases (palier 1) ne sont conseillées qu'au palier 1.

### Relais refonte Académie (état après l'étape 1, v2.6.0)
- Sources : en-tête `# Titre | Résumé | w/b/PUZZLE id | id=N level= order= theme= kind=line` ; `?SAN : …` / `-? …` =
  arrêt « À vous » ; lignes `TAKEAWAY:`, `CONDITIONS:`, `SHOW n: marks=e4,d5 arrows=f3e5`. Ids figés 1–37 / 38–75 / 76–113.
- `content/Lessons.kt` : `Lesson(id, category, …, level, order, theme, kind: LessonKind, pauseSteps, takeaway, conditions,
  stepVisuals: Map<Int, StepVisuals>)` ; `LessonCategory` = BASICS, OPENING, MIDDLEGAME, STRATEGY, ENDGAME ;
  `Lessons.categories` = catégories non vides ; tri par catégorie puis `order`.
- `feature/MoveJudge.kt` : `judge(position, played, expected, endgame)` → `Verdict` EXACT / EQUIVALENT / WORSE /
  UNAVAILABLE ; règle isolée `MoveJudge.acceptable(mine, target, endgame)`.
- `AcademyViewModel(prefs, lessons, narrator, engine, puzzles, saved, random)` : `asking()`, `guideMove`, `challengeMove`,
  `resumeAtMiss`, `holdRefutation`, `startPractice` / `practiceMove` / `practiceHint` / `nextPractice` / `closePractice` ;
  `AcademyState.practice: Practice?`, `stars`, `errors`, `maxHint`, `refute`, `pulse`. Constante `ASK`.
- Room v4 : `LessonProgress(lessonId, masteredAt>0 = maîtrisée, stars, errors, hintsUsed, practicePassed, practiceTotal,
  srsStage, nextReviewAt, lastReviewedAt)`, table `academy_assessments` (`AcademyAssessment`), `GameRecord.startFen` ;
  `LessonProgressRepository` : `masteredLessons`, `progress()`, `progress(id)`, `save`, `saveAssessment`, `assessments`.
- Réglages : `Settings.academyLevel` (1–5, défaut 2), `placementDone` ; passerelles : `PuzzlesViewModel.train(theme, band)`,
  `PlayViewModel.startFromPosition(fen, playerWhite)` (partie d'entraînement sans ELO), câblées dans `MainActivity`.

Les détails de chaque version sont dans `docs/release-notes.md` ; les exemples d'images partagées dans `docs/screenshots/`.

---

### Relais refonte Problèmes (état après l'étape 1, v4.2.15)
- Symboles : `PuzzleState.alreadySolved`, `themeVisible` (filtre == `puzzle.primaryTheme`) ; `PuzzlesViewModel` : `filterTouched`, `loadJob` porte aussi le chargement de départ, `drawRandom()`, `looksLikeId()`, `SEARCH_DEBOUNCE_MS` (150), préchauffage du moteur dans `init`, `onMove` renvoie 3 (fin avec la flèche), `canPlay` exige `!solved && cursor == progress`, `refute` ne tronque plus `progress`, `markSolved` en `NonCancellable`, rafraîchissement après `syncCatalogue()`. `PuzzlesScreen` : `reserveKey = p.id`, trait réel en position 0, message « Vous revoyez… », conseil en INFO et GOOD. `PuzzleCatalogSheet` : suggestions sans thème, priorité au thème si la saisie n'est pas un identifiant. `RefutationLogic.reply` : `capturedType` (prise en passant), mats et scores décisifs avant la prise, `reply = null` si le moteur ne répond pas. `ShareCard` : jusqu'à 5 colonnes au-delà de 6 coups, `replyInline` (réponse sur une 2e ligne sinon), badge « x » dédoublonné, pastille ajustée, `render(includeTip)`, `lastSolutionGrid` (tests). `ExportSheet` : option « Indice », `onReveal` (aperçu de la solution calculé seulement une fois démasqué ou résolu). `PuzzleDao.maxRowId`, `probeAll`, `probeInThemes` ; `RoomPuzzleRepository.randomId` : 4 fenêtres de 3 000 rowid, puis repli exact.
- DataStore et Room : aucune clé nouvelle, Room toujours en version 4 (requêtes DAO seulement).
- Mesures (base réelle, Robolectric) : tirage Libre 0,54 ms (ancien 302 ms), fourchette 0,53 ms, roque (thème rare, repli) 4,2 ms.
- Repéré hors périmètre (non corrigé) : le message d'accueil répète « Trait aux … » (tâche 2.2) ; le tirage par fenêtre favorise légèrement les problèmes qui suivent un long trou de rowid résolus.
- Reste pour l'étape 2 : boutons Solution et Recommencer, `MoveStrip`, bouton Dé en double une fois résolu, « Tirer un problème » quand seule la difficulté change, alerte quand tout est résolu dans le filtre, ponts vers le Jeu Libre et l'Académie.

### Relais refonte Problèmes (état après l'étape 2, v4.3.0)
- Symboles : `PuzzleState.hadError`, `revealed`, `allSolvedInFilter`, `over` (résolu ou dévoilé) ; `themeVisible` inclut `revealed`. `PuzzlesViewModel.solution()` (après une erreur : ligne complète déroulée toutes les 700 ms, non comptée), `restart()` (si `over`), `goTo(n)` (bandeau, borné à `progress`), `show(p, allSolved)` (message d'accueil sans le trait), `drawRandom` détecte un problème déjà résolu tiré (filtre épuisé) ; `canPlay` exige aussi `!revealed`. `PuzzlesScreen(vm, onPlayPosition, onOpenLesson)` : `MoveStrip` en `underBoard` (coups joués seulement), boutons Recommencer / Problème suivant / Solution, `HintButton(nextLabel)` (« Montrer le coup (non compté) », court si Solution est affichée ou en grande police), `puzzleBridges()` dans l'action de la carte. `PuzzleCatalogSheet` : `bandAtOpen`, bouton « Tirer un problème », relance si `empty`, note `allSolvedInFilter`. `AcademyViewModel.openLessonForTheme(theme)` et `lessonForTheme(theme, progress, level)`. `MainActivity` : `TabBridges.lessonTheme`, câblage onglets 1 ↔ 2 → 3. `ui/Common.kt` : paramètre `nextLabel` de `HintButton` (sans effet ailleurs).
- DataStore et Room : aucune clé nouvelle, Room toujours en version 4.
- Tests : `PuzzlesFuzzTest` (Solution, Recommencer, bandeau dans le fuzzing ; solution non comptée et navigable ; sélection épuisée), `ViewModelsTest.academyOpensTheLessonOfAPuzzleTheme`, `AppRobolectricTest.puzzleSolutionAndBridges` (de bout en bout : Solution → Voir la leçon → Recommencer → Continuer contre l'Ordinateur) et `puzzleAfterAnErrorWithLargeFont` (police 1,6), `TabletRobolectricTest.tabletLandscape` (capture `t12_problemes_solution`).
- Repéré hors périmètre (non corrigé) : depuis un problème, le Jeu Libre annonce « Position de la leçon contre l'ordinateur » et « Partie d'entraînement depuis une leçon » (textes de `PlayScreen`/`PlayViewModel`) ; en grande police, le texte de l'indice 1/2 se replie derrière « Lire la suite » (comportement normal de `FeedbackCard`).
- Reste pour l'étape 3 : Room v5 (`puzzle_progress` étendu, index couvrants, `puzzle_themes`), « Mon niveau » (ELO tactique), Récents 50 / À refaire / Favoris, thèmes secondaires, bilan tactique et filtre par longueur.

### Relais refonte Problèmes (refonte terminée, v4.4.0)
- Room v5 (`MIGRATION_4_5`, schéma exporté `5.json`) :
  - `PuzzleProgress` gagne `status` (`PuzzleOutcome` : NONE, CLEAN, WITH_ERROR, GAVE_UP), `attempts`, `nextReviewAt`, `lastTriedAt` et `bookmarked`. Une ligne n'est « résolue » que si `solvedAt > 0`, et toutes les requêtes filtrent ainsi. `PuzzleProgress.after()` porte la règle : résolution nette = rien à refaire ; erreur ou abandon = à refaire 1 jour plus tard (`REDO_DELAY`).
  - Index couvrants `index_puzzles_rating_id` et `index_puzzles_primaryTheme_rating_id` ; `themeCounts` est `INDEXED BY` le second.
  - Entité `PuzzleThemeLink` (table `puzzle_themes`, clé primaire (thème, classement, id), créée WITHOUT ROWID par `PUZZLE_THEMES_SQL`), remplie par `themeLinksSql()` (CTE récursive, 50 thèmes officiels), y compris dans `syncPuzzles` : 419 697 liens.
  - Base livrée migrée sur place par `scripts/migrate_asset_v5.py`. Empreinte des 150 000 problèmes inchangée ; 42,6 Mo bruts (30,3 avant) ; environ +4 Mo d'APK, mesuré avant de choisir WITHOUT ROWID plutôt qu'une table avec index séparé (+9 Mo).
- DAO : `probeAll`/`probeInThemes`/`countAll`/… prennent les motifs de longueur `lenA`/`lenB` ; `countLinked`, `idLinkedAt`, `linkedTotals`, `solvedMeta` ; `progressOf`, `bookmarked`, `bookmarks`, `toRedo`, `attempts`, `linkCount`.
- `PuzzleRepository` :
  - `randomId(…, secondary, length)` et `themeCounts(range, secondary)` ;
  - `recordAttempt`, avec `markSolved` qui délègue en résolution nette (séries de l'Académie) ;
  - `setBookmark`, `bookmarked`, `bookmarks`, `toRedo`, `attempts`.
- DataStore : `puzzle_elo` (0 = pas encore calculé, il part alors de l'ELO du Jeu Libre), `puzzle_length` (`puzzle_secondary` supprimé en 4.4.6) ; historique des récents porté à 50 (`RECENT_PUZZLES`).
- `PuzzleThemes` : `MY_LEVEL` (= 5, valeur de `puzzleBand`), `levelRange(elo)` (± 100), `bandLabel`, `LENGTHS`, `lengthPatterns`.
- `PuzzlesViewModel` :
  - `PuzzleState.puzzleElo`, `secondary` (problème tiré par relais sur un thème secondaire, 4.4.6), `length`, `range` et `filterMatches` (le thème visible tient compte du relais) ;
  - ELO : `tacticElo()` (Elo, K = 32, borné 400–3000), `rate()` une fois par problème et par session, `conclude()` (essai enregistré en NonCancellable) ;
  - `setLength`, `toggleBookmark` ; tirage en trois temps (principal non résolu, secondaire non résolu, puis résolu) ;
  - flux `bookmarked`, `bookmarks`, `toRedo`, `stats` (`TacticStats.of`).
- `PuzzlesScreen` : signet sur la ligne d'information, tiroir « Problèmes joués » (Récents, À refaire, Favoris : `PlayedRow`). `PuzzleCatalogSheet` : `TacticCard`, puce « Mon niveau · ELO », longueur, « Tirer un problème » dès que difficulté ou longueur changent (interrupteur des thèmes secondaires supprimé en 4.4.6).
- Tests :
  - `migration4To5KeepsEverything` : base v4 complète, 150 000 problèmes, progression, leçon, partie et test conservés, liens créés, index remplacés ; base livrée v5 vérifiée ;
  - `upgradeImportsNewPuzzlesAndKeepsProgress` : liens après import ;
  - `PuzzlesFuzzTest` : ELO, « À refaire », favoris, secondaires, longueur, « Mon niveau », bilan ;
  - audit des 150 000 problèmes repassé, vert.
- Repéré hors périmètre aux étapes 1 à 3 : tout traité en 4.4.1 (répétition espacée de « À refaire », biais du tirage par fenêtre, origine « problème » dans le Jeu Libre, indice lisible en grande police).

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

---

## Phase B — rotation du certificat de signature

Aujourd'hui (Phase A), la clé `keystore/chessduck-release.jks` et son mot de passe sont dans le dépôt : n'importe qui
pourrait signer un APK que les téléphones accepteraient comme une mise à jour de ChessDuck. Changer de clé fait échouer
la mise à jour (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) : il faut désinstaller, ce qui efface la progression. D'où l'ordre :

1. **Fait (4.7.0)** : la sauvegarde hors-ligne existe ; la signature lit d'abord `CHESSDUCK_STORE_FILE`,
   `CHESSDUCK_STORE_PASSWORD`, `CHESSDUCK_KEY_ALIAS`, `CHESSDUCK_KEY_PASSWORD` (environnement ou `keystore.properties`,
   ignoré par Git), sinon la clé de démonstration.
2. **Annoncer** dans les notes de version, une ou deux versions à l'avance : « exportez votre progression ».
3. **Créer la clé privée**, hors du dépôt :
   `keytool -genkeypair -v -keystore chessduck-private.jks -alias chessduck -keyalg RSA -keysize 4096 -validity 10000`.
4. **La donner à GitHub Actions** : secrets `CHESSDUCK_KEYSTORE_BASE64` (`base64 -w0 chessduck-private.jks`),
   `CHESSDUCK_STORE_PASSWORD`, `CHESSDUCK_KEY_ALIAS`, `CHESSDUCK_KEY_PASSWORD` ; un workflow de construction décode le
   fichier dans `$RUNNER_TEMP`, exporte `CHESSDUCK_STORE_FILE`, puis lance `./gradlew :app:assembleRelease`.
5. **Publier la première version signée par la nouvelle clé** en expliquant : exporter la progression, désinstaller,
   installer, importer la sauvegarde.
6. **Ensuite** seulement : retirer `keystore/chessduck-release.jks` et le repli de `app/build.gradle.kts`, et changer le
   mot de passe s'il avait servi ailleurs. L'historique Git garde l'ancienne clé : elle doit être considérée comme publique.

Alternative : Google Play App Signing, qui gère la clé de diffusion (seule la clé d'envoi reste à protéger).

