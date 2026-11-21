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
- **Version actuelle** : `3.2.4` (`versionCode = 58`), publiée en release GitHub `v3.2.4`.
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
- `data/` — `Db.kt` (Room **v4** : entités `Puzzle` avec colonne indexée `primaryTheme`, `PuzzleProgress`, `LessonProgress`
  (étoiles, erreurs, indices, exercices, champs de révision), `GameRecord` (+ `startFen`), `AcademyAssessment` ;
  migrations 1→2→3→4 ; `syncPuzzles` par `ATTACH` + `INSERT OR IGNORE`), `Prefs.kt` (DataStore « chessduck »,
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
