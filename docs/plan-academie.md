# Plan d'action — refonte pédagogique de l'Académie

Objectif : que l'Académie permette de **maîtriser la théorie des échecs**, et pas seulement de la regarder.
Ce plan reprend la revue critique « en professeur » (audit Stockfish des 113 leçons) et la liste des
types de leçons manquants. Chaque phase est livrable seule, testée, et publiée dans sa propre version.

---

## 1. Point de départ (audit du 16/11/2026)

| | Ouvertures | Milieu de partie | Finales |
|---|---|---|---|
| Leçons | 37 | 38 | 38 |
| Coups joués par l'élève (moyenne / leçon) | 7 | 2 | 3 |
| Coups refusés en Défi alors qu'un autre coup est aussi bon | 169 / 262 (65 %) | 0 | 52 / 126 (41 %) |
| Leçons qui parlent d'un plan / d'un piège | 2 / 0 | 0 / 2 | 0 / 1 |
| Leçons de 4 demi-coups ou moins | 0 | 38 | 21 |
| Coups erronés (perte ≥ 0,8 pion ou gain gâché) | **0** | **0** | **0** |

Constats principaux :
- **Contenu juste, méthode incomplète.** L'élève comprend et voit l'exemple, mais ne s'entraîne pas
  sur des positions inédites et ne révise jamais.
- **Le Défi teste la mémoire immédiate** et refuse des coups corrects.
- **« Maîtrisée » ne mesure rien** : une seule réussite, même avec erreurs et indices.
- **Finales** : les mats élémentaires démarrent à 2–3 coups du mat ; la technique n'est jamais pratiquée
  contre une vraie défense.
- **Ouvertures** : lignes uniques sans plan, ni piège, ni réponse aux écarts.
- **Milieu de partie** : un seul problème par motif ; ni stratégie, ni méthode de réflexion, ni défense.
- **Piège technique** : l'identifiant d'une leçon est sa position dans la liste. Insérer une leçon
  réattribuerait la progression aux mauvaises leçons.

---

## 2. Règles transverses (toutes phases)

- Ne pas casser `chess/Chess.kt`, `engine/Stockfish.kt`, les clés DataStore existantes, `SpokenFr`.
- Schéma Room : toute évolution = nouvelle version + migration + base embarquée mise à jour + test de
  mise à jour depuis une ancienne installation.
- Leçons : modifier `scripts/lessons/*.txt`, puis `gen_lessons.py` + `LessonCompilerTool`. Textes en
  style professeur, « À retenir », sans notation ni parenthèses, au vouvoiement, lexique de prononciation
  complété pour la voix.
- **Audit Stockfish obligatoire** après chaque lot de contenu : 0 coup erroné.
- Accessibilité : chaque nouvel exercice jouable avec TalkBack, cibles ≥ 48 dp, contrastes WCAG AA.
- Code : KISS, DRY, pas de code mort ; `./gradlew :app:testDebugUnitTest` vert avant chaque livraison.
- Chaque phase : captures d'écran revues, notes de version, `docs/contexte-session.md` mis à jour.

---

## Phase 0 — Fondations (v2.6.0, ≈ 1 jour)

Indispensable avant tout le reste ; sans changement visible pour l'élève.

| # | Tâche | Détail |
|---|---|---|
| 0.1 | **Identifiants fixes des leçons** | Champ `id=` dans l'en-tête de chaque leçon des fichiers source, repris par `gen_lessons.py` et `LessonCompilerTool` ; `Lessons.all` utilise cet id au lieu de l'index. Les 113 ids actuels sont conservés tels quels. |
| 0.2 | **Métadonnées des leçons** | Champs `level=` (bases, débutant, novice, club, confirmé), `theme=` (thème Lichess associé, ex. `fork`) et `type=` (démonstration, finale jouable, ouverture…). |
| 0.3 | **Juge de coups partagé** (`MoveJudge`) | « Ce coup est-il aussi bon que celui attendu ? » via Stockfish (MultiPV, écart ≤ 0,3 pion, ou gain conservé). Servira au Défi, aux exercices et aux finales jouables. |
| 0.4 | **Base v4** | Progression enrichie (étoiles, tentatives, prochaine révision), résultats d'exercices, et colonne `startFen` des parties (pour « Jouer cette position »). Migration 3 → 4 et base embarquée. |
| 0.5 | **Outil d'audit** | Le script d'audit devient `LessonAuditTool` (actif avec `REVIEW_SF=/usr/games/stockfish`, comme `ReviewConsistencyTool`). |

**Critères d'acceptation**
- Test : les ids actuels (1 à 113) gardent le même titre.
- Test : ids uniques.
- Test de migration 3 → 4 : la progression existante est conservée.
- Tests unitaires de `MoveJudge` avec le faux moteur.

---

## Phase 1 — Un Défi formateur et une vraie maîtrise (v2.6.x, ≈ 1–2 jours)

| # | Tâche | Détail |
|---|---|---|
| 1.1 | **Coups équivalents acceptés** | « Bon coup aussi ! La leçon montre plutôt… », puis la leçon reprend son coup. Ne compte pas comme une erreur. Supprime les 221 refus injustes. |
| 1.2 | **Erreur expliquée** | Réfutation adverse avec flèche rouge (mécanisme du mode Problèmes), puis retour à la position d'avant l'erreur. |
| 1.3 | **Défi complet** | Commentaire des coups adverses affiché ; « Reprendre au coup manqué » au lieu de tout recommencer. |
| 1.4 | **Guidé actif** | Arrêts « À vous : que jouez-vous ici ? » aux coups clés (marqueur dans les fichiers source) ; la Lecture continue reste possible. |
| 1.5 | **Étoiles** | ★★★ sans erreur ni indice, ★★ une erreur ou l'indice 1, ★ sinon ; affichées dans le catalogue, améliorables. |
| 1.6 | **Validation sur positions inédites** | Après une leçon de tactique : 5 problèmes du thème associé, tirés des 150 000. **Maîtrisée = 4/5 sans l'indice qui donne le coup.** |
| 1.7 | **« Jouer cette position »** | Ouvre le Jeu Libre sur la position de fin de leçon (partie enregistrée avec sa position de départ). |

**Critères d'acceptation**
- Tests de ViewModel : coup équivalent accepté, erreur réfutée puis position rétablie, étoiles calculées, validation à 4/5.
- Test d'interface : parcours complet Guidé → Défi → validation.

---

## Phase 2 — Finales jouées pour de vrai (v2.7.0, ≈ 2 jours)

Nouveau type de leçon **« Gagnez cette position » / « Tenez la nulle »**.

| # | Tâche | Détail |
|---|---|---|
| 2.1 | **Générateurs de positions** | Positions de départ variées et légales par famille : roi et dame contre roi, roi et tour contre roi, deux fous, fou et cavalier, roi et pion contre roi (cases clés), Lucena, Philidor, pion tour. |
| 2.2 | **Écran d'entraînement** | Stockfish défend à pleine force. Objectif affiché (mater en N coups, promouvoir en gardant le gain, tenir N coups). Indices par le moteur. Réussite = 3 positions sur 5. |
| 2.3 | **Mats élémentaires refaits** | Démonstrations complètes depuis le centre : réduire la boîte, le roi aide, attention au pat. |
| 2.4 | **Fiches « Conditions »** | Pour chaque position théorique : comment la reconnaître (roi coupé, pion pas trop avancé, bon coin…). |
| 2.5 | **Tout coup qui garde le gain est accepté** | Dans les leçons de finale (via `MoveJudge`). |

**Critères d'acceptation**
- Tests : positions générées légales et du bon type (gagnantes ou nulles selon la famille).
- Tests : détection de la réussite et de l'échec.
- Contrôle sur téléphone : temps de réponse du moteur.

---

## Phase 3 — Révisions, cursus et accueil (v2.8.0, ≈ 2 jours)

| # | Tâche | Détail |
|---|---|---|
| 3.1 | **Révisions espacées** | Leçon maîtrisée revue à 1, 7, 30 puis 90 jours (en Défi + 2 positions inédites) ; ratée → revient le lendemain. |
| 3.2 | **Accueil de l'Académie** | « Continuer », « Révisions du jour (n) », objectif du jour, série de jours bienveillante. |
| 3.3 | **Parcours par niveaux** | Les bases, Débutant (≈ 800), Novice (≈ 1200), Club (≈ 1600), Confirmé (≈ 1800) ; « prochaine leçon conseillée ». |
| 3.4 | **Examens** | 10 positions mélangées sans annonce du thème ; 80 % pour valider le niveau. |
| 3.5 | **Test de placement** | 12 positions au premier lancement, pour proposer le bon niveau (facultatif, passable). |

**Critères d'acceptation**
- Tests du calendrier de révision.
- Tests du passage de niveau.
- Test d'interface de l'accueil.

---

## Phase 4 — Nouveaux types d'exercices générés automatiquement (v2.9.0, ≈ 2–3 jours)

Aucune rédaction nécessaire : 150 000 problèmes + Stockfish.

| # | Type | Source |
|---|---|---|
| 4.1 | **Quelle est la menace ?** (toucher la pièce visée) | Problèmes Lichess vus du camp qui vient de commettre la faute. |
| 4.2 | **Le seul coup** (défense) | Thème Lichess « Coup défensif » (`defensiveMove`) et positions où Stockfish ne trouve qu'un coup qui tient. |
| 4.3 | **Puis-je prendre ?** (compte des échanges) | Positions avec prise possible, réponse oui/non jugée par Stockfish. |
| 4.4 | **Vision de l'échiquier** | Séries de 30 s : couleur d'une case, toucher une case, sauts de cavalier. Entièrement généré. |
| 4.5 | **Calcul à l'aveugle** | Position affichée, puis 2 à 4 coups annoncés à voix haute sans être montrés ; question finale. |
| 4.6 | **Mini-jeux** | Guerre des pions, dame contre huit pions, cavalier gourmand (contre Stockfish ; réutilisables en mode enfant). |

**Critères d'acceptation**
- Tests des générateurs : bonne réponse unique et vérifiée.
- Tests d'interface pour chaque type, TalkBack compris.

---

## Phase 5 — Contenu rédigé, par lots (v3.0 → v3.x, plusieurs sessions)

Chaque lot : rédaction → compilation → audit Stockfish (0 erreur) → captures → version.

| Lot | Contenu | Volume |
|---|---|---|
| A | **Les bases** (marche des pièces, valeur, échec, mat, roque, prise en passant, promotion, pat, nulles, notation) + **Méthode de réflexion** (échecs-prises-menaces, coups candidats, vérification anti-gaffe) | ≈ 23 leçons |
| B | **Ouvertures : fiches « Plans et pièges »** pour les 37 ouvertures (3 idées, piège pour et contre soi) + **arbres de variantes** (2–3 écarts courants) + **entraînement du répertoire** en répétition espacée | 37 fiches |
| C | **Tactique** : 2 exemples gradués de plus par motif + **contre-exemples** (erreur typique punie) | ≈ 76 exemples |
| D | **Stratégie** (nouvelle catégorie) avec exercices **« Qui est mieux, et pourquoi ? »** et **« Quel est le plan ? »** | ≈ 18 leçons |
| E | **Finales manquantes** (cases correspondantes, triangulation, Vancura, côté court / côté long, dame contre tour, fou et cavalier…) | ≈ 30 leçons |
| F | **Parties modèles** commentées + **« Devinez le coup »** (points selon le coup du maître et l'avis de Stockfish) | ≈ 10 parties |
| G | **Ouvertures nouvelles** (siciliennes Taimanov/Kan/Rossolimo, Catalane, Benoni, Réti…) | ≈ 25 leçons |

---

## Phase 6 — Personnalisation (v3.x, ≈ 1–2 jours)

| # | Tâche | Détail |
|---|---|---|
| 6.1 | **Leçon personnalisée depuis la Revue** | « Vous avez manqué 3 fourchettes cette semaine » → leçon + 5 exercices du motif. |
| 6.2 | **Thèmes faibles** | Taux de réussite par thème (Académie + Problèmes) ; les 3 plus faibles proposés à l'accueil. |
| 6.3 | **Récompenses utiles** | Badges de compétence (« Lucena sans faute »), célébrations, matières/palettes débloquées par niveau. |

---

## 3. Calendrier indicatif

| Version | Phase | Durée estimée |
|---|---|---|
| 2.6.0 | 0 — Fondations | 1 jour |
| 2.6.x | 1 — Défi formateur, maîtrise | 1–2 jours |
| 2.7.0 | 2 — Finales jouées | 2 jours |
| 2.8.0 | 3 — Révisions, cursus, accueil | 2 jours |
| 2.9.0 | 4 — Exercices générés | 2–3 jours |
| 3.0 → 3.x | 5 — Contenu par lots A → G | 1 lot par session |
| 3.x | 6 — Personnalisation | 1–2 jours |

Ordre justifié : les fondations protègent la progression ; la phase 1 corrige ce qui fausse
l'apprentissage aujourd'hui ; les phases 2 à 4 apportent la pratique ; le contenu vient ensuite
s'insérer dans un cadre déjà prêt (niveaux, validation, révisions).

---

## 4. Risques et parades

| Risque | Parade |
|---|---|
| Migration de la base (Room v4) | Test de mise à jour depuis une ancienne installation ; base embarquée régénérée par script. |
| Temps de calcul de Stockfish sur téléphone (juge de coups, finales jouables) | Recherches courtes (≤ 300 ms), calcul anticipé en arrière-plan comme pour l'indice du Jeu Libre. |
| Volume de rédaction | Lots indépendants, audit automatique, gabarit de leçon commun. |
| Taille de l'APK | Contenu textuel léger ; aucun nouvel asset lourd prévu. |
| Surcharge de l'écran Académie | Accueil unique « Continuer » ; les nouveaux types s'ouvrent depuis la leçon ou le parcours. |

---

## 5. Indicateurs de réussite

- 0 coup erroné à l'audit Stockfish, à chaque lot.
- 0 coup équivalent refusé en Défi.
- Chaque leçon de tactique validable sur positions inédites ; chaque finale théorique jouable contre le moteur.
- Couverture : Les bases, Méthode de réflexion, Stratégie et plans d'ouverture présents dans le catalogue.

---

## 6. Décisions à prendre avant de démarrer

1. Valider la base v4 (nouvelle version du schéma, avec migration).
2. Seuils : écart accepté pour un coup équivalent (0,3 pion proposé), maîtrise à 4/5, examen à 80 %.
3. Test de placement au premier lancement : oui / non.
4. Notifications de révision (désactivées par défaut) : oui / non.
5. Ordre des lots de contenu (A → G proposé).
