## ChessDuck 2.2.3 — résumé de revue plus lisible

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Résumé de la partie : si chaque joueur tient sur une ligne (nom, couleur et annotations), les annotations des deux joueurs sont alignées en colonne ; sinon, pour les deux joueurs, le nom et la couleur sont sur une ligne et les annotations dessous, alignées à gauche.

## ChessDuck 2.2.2 — noms des joueurs toujours entiers dans le résumé de revue

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Revue de partie : le nom de chaque camp (« L'ordinateur (Blancs) », pseudos longs) s'affiche toujours en entier ; si les compteurs d'annotations ne tiennent pas à côté, ils passent à la ligne.
- La phrase d'aide du résumé n'est plus jamais coupée ; l'historique des parties n'abrège plus « Victoire de … ».

## ChessDuck 2.2.1 — noms des pièces en français dans le clavier de Notation

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Notation : les touches K, Q, R, B, N affichent sous le nom anglais le nom français de la pièce (Roi, Dame, Tour, Fou, Cavalier).

## ChessDuck 2.2.0 — refonte interne : plus rapide, plus sobre, mieux testée

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).
Aucune fonctionnalité ni règle n'est modifiée : progression, historique, réglages et préférences d'export sont conservés.

- Démarrage plus léger : le moteur Stockfish et le catalogue des problèmes ne sont chargés qu'à la première ouverture de leur onglet ; chaque onglet garde son état.
- Problèmes : tirage, compteurs du catalogue et recherche interrogent une base indexée (nouvelle colonne « thème principal ») au lieu de charger 150 000 problèmes en mémoire ; mise à jour de la base en SQL natif.
- Échiquier : animations et secousses dessinées sans recomposer l'écran ; images des pièces et textes mis en cache.
- Export d'images : rendu, compression et enregistrement hors du fil principal, aperçus réutilisés, cache de partage purgé.
- Moteur : resynchronisation fiable après une recherche annulée, coups vérifiés ; aucune réponse d'une partie précédente ne peut s'afficher dans la nouvelle.
- Leçons précompilées (plus d'analyse de notation au démarrage) ; catalogues et historique en listes paresseuses.
- Pseudo enregistré après une courte pause de frappe (et toujours à la validation ou à la fermeture).
- Architecture : dépôts et injection de dépendances légère, ViewModels testés en isolation (34 tests automatisés).

## ChessDuck 2.1.2 — pièces bien visibles dans les images de solution

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Correctif : sur les petits échiquiers des images de solution, les pièces étaient dessinées semi-transparentes (plus foncées sur les cases sombres). Elles sont désormais toujours parfaitement opaques, comme dans l'image du problème.

## ChessDuck 2.1.1 — échiquiers plus grands dans les images partagées

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Problème : l'échiquier occupe presque toute la largeur (≈ 1 040 px en portrait au lieu de ≈ 790, ≈ 800 px en carré au lieu de ≈ 640).
- En-tête et pied de page compactés : l'objectif (« 2 coups à trouver ») passe dans le sur-titre, le thème dans la carte d'indice.
- Solution : marges réduites, grille choisie automatiquement pour obtenir les plus grands échiquiers, coup et réponse sur une seule ligne.

## ChessDuck 2.1.0 — images de partage : formats, fond clair, prêtes pour WhatsApp

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Deux formats au choix : **Portrait 4:5** (1080 × 1350, idéal WhatsApp et messageries) et **Carré 1:1** (1080 × 1080, publication Instagram).
- Dimensions taillées pour WhatsApp : l'image s'affiche en entier dans la conversation, sans recadrage ni réduction ; textes assez grands pour rester nets après compression.
- Fond **Sombre** ou **Clair**, décliné des thèmes Nuit et Jour de l'application.
- La solution tient toujours sur une seule image : grandes grilles et phrases complètes pour 1–2 coups, grille compacte (notation seule) au-delà.
- Aperçu en direct de chaque image dans « Exporter en image » ; réglages mémorisés.
- Anti-spoiler : l'aperçu de la solution reste masqué tant que le problème n'est pas résolu (« Toucher pour voir »).

## ChessDuck 2.0.3 — images de partage repensées

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

### Image du problème
- Énoncé-défi en titre, à la manière des recueils : « Les Blancs jouent et font mat en 2 », « Les Noirs jouent et gagnent ».
- Sous-titre qui invite à chercher (« Le mat est forcé. Saurez-vous le trouver ? ») et pastille d'objectif (« 2 coups à trouver »).
- Difficulté lisible d'un coup d'œil : tranche, classement et jauge de 4 points dorés.
- Échiquier mis en valeur : coins arrondis, ombre portée, dernier coup adverse surligné.
- Indice présenté dans une carte discrète, à lire seulement si besoin.
- Marque réduite à un pied de page sobre (mascotte, nom, référence Lichess) : aucune URL, aucun appel à installer.

### Image de la solution
- Même en-tête que l'énoncé : les deux images forment une paire.
- Une carte numérotée par coup : flèches, description, réponse adverse et pièce prise.
- Conclusion explicite (« Échec et mat ! » ou « Avantage décisif ») et roi maté mis en lumière.

## ChessDuck 2.0.2 — textes au-dessus de l'échiquier toujours complets

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Académie : le titre de la leçon s'affiche en entier (retour à la ligne au lieu de « … »).
- Problèmes : la ligne « Trait aux Blancs/Noirs · thème · niveau · #id » passe à la ligne au lieu d'être coupée.
- Jeu Libre et Revue : intitulé de la partie, bilan, date et résultat affichés en entier.
- Barres des joueurs et bulles du mode Enfant : le texte passe à la ligne au lieu d'être tronqué.
- L'échiquier s'ajuste automatiquement à la hauteur de ces textes.

## ChessDuck 2.0.0 — refonte de l'interface

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

### Design system
- Palette sémantique « Ardoise & Or » : contrastes WCAG AA (≥ 4,5:1) vérifiés par test en modes Nuit, Jour et Enfant.
- Typographie Material 3 avec chiffres tabulaires ; aucun texte sous 11 sp, respect de la taille de police du système.
- Icônes vectorielles Material à la place des emojis dans la navigation et les boutons ; cibles tactiles de 48 dp ; descriptions TalkBack sur tous les contrôles, l'échiquier et la jauge.
- Navigation Material 3 (barre en portrait, rail sur tablette), transitions entre onglets, bouton Retour Android géré, affichage bord à bord.

### Paramètres
- Thème Système / Jour / Nuit, couleurs dynamiques Material You (optionnelles), coordonnées de l'échiquier masquables, confirmation de l'abandon et de la nouvelle partie, interrupteurs pour le son et les vibrations.

### Échiquier
- Coordonnées toujours lisibles, même sous une pièce ; plus de badge ✕ sur les prises.
- Secousse et message quand on touche une pièce qui ne peut pas bouger.
- Jauge d'évaluation avec encoche d'égalité et valeur (+1.4, M2).

### Modules
- Notation : barre d'actions compacte, clavier FIDE en touches de 46–48 dp, effacement sûr (appui long sur ⌫), série et record affichés.
- Académie : leçon précédente / suivante, choix Guidé / Défi, coups cliquables, catalogue avec recherche et filtres.
- Problèmes : thème masqué en mode libre jusqu'à la résolution (bouton « Révéler le thème »), compte à rebours de la réfutation, catalogue en accordéons avec recherche par thème.
- Jeu Libre : joueurs et pièces prises autour de l'échiquier, liste des coups cliquable, « Annuler mon coup », confirmations, « Analyser la partie » en fin de partie, résumé de revue et tableau des coups sur tablette.
- Mode enfant : bouton Quitter déplacé en haut, boutons plus contrastés.

### Installation
Téléchargez `ChessDuck-2.0.0.apk` ci-dessous, ouvrez-le sur l'appareil et autorisez l'installation depuis cette source. Il s'installe par-dessus la version précédente sans perte de données.

© Ivan Herboch, 2026 — moteur Stockfish 11 (GPLv3), problèmes Lichess (CC0), pièces Cburnett (CC BY-SA 3.0), icônes Material (Apache 2.0), police Fredoka (OFL).
