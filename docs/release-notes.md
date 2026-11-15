## ChessDuck 2.5.0 — refonte de l'ergonomie

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Démarrage : écran d'accueil aux couleurs du thème, sans flash blanc. L'orientation suit l'appareil et le bouton Retour remonte les onglets visités.
- Échiquier : quatre matières (bois classique, ardoise, tournoi, améthyste), dernier coup et échec mieux marqués, choix de promotion près de la case. Le plateau se joue aussi avec TalkBack, case par case.
- Toucher une pièce qui ne peut pas bouger explique pourquoi.
- Notation : champ de saisie stable, un seul bouton « Passer à l'exercice suivant », Mémo anglais dans l'écran.
- Académie : modes Guidé et Défi ; haut-parleur toujours visible, qui explique comment activer la voix ; leçon en cours signalée dans le catalogue.
- Problèmes : le bouton du catalogue affiche le filtre actif (« Libre · 12 résolus », « Intermédiaire · Clouage ») au lieu de « 0 / 150 000 ».
- Recherche plus tolérante dans le catalogue : elle ouvre l'unique suggestion ou le thème reconnu. Familles de thèmes en accordéon, avec « Toute la famille ».
- Après une erreur dans un problème, un bouton pause garde la réfutation à l'écran le temps de la comprendre.
- Export en image : le tiroir reste ouvert pendant la préparation (« Préparation… ») et les erreurs sont signalées. Toucher l'aperçu l'agrandit.
- Images de partage : marges plus larges, coordonnées sur les petits échiquiers de la solution, plus de chevauchement avec la pièce prise.
- Jeu Libre : sur tablette, tableau complet des coups. Toucher « Ordinateur · 1200 » ouvre les réglages, avec confirmation si une partie est en cours. Coups numérotés comme sur une feuille de partie.
- Revue : elle s'ouvre en pause sur le résumé. Les commentaires vouvoient le joueur. L'historique affiche des cartes à toucher, avec l'état de l'analyse.
- Mode enfant : confirmation avant d'abandonner, cartes de pièces qui réagissent au toucher. Les messages de nulle restent lisibles et « pièce bloquée » reste affiché plus longtemps.
- Paramètres : section « Audio, voix et retours ». Interrupteurs de voix grisés tant que le téléphone cherche une voix. Nouveau bouton « Réinitialiser mon ELO ».

## ChessDuck 2.4.9 — le mode enfant explique les nulles

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Mode enfant : une partie nulle dit pourquoi elle s'arrête, avec des mots simples : pat (le roi n'est pas en échec mais ne peut plus bouger), plus assez de pièces pour mater, même position trois fois, ou cinquante coups sans prise ni pion avancé.

## ChessDuck 2.4.8 — un échiquier plus grand en Notation

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Notation : clavier un peu plus compact, échiquier environ 9 % plus grand.
- Les touches restent faciles à viser : 44 dp de haut pour les lettres, chiffres et symboles, 52 dp pour les pièces, 48 dp pour la barre de saisie, Effacer et Valider.

## ChessDuck 2.4.7 — fiches du mode enfant parlées au bon moment

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Mode enfant : les explications des pièces sont réécrites pour l'oral (plus de parenthèses, nombres en lettres).
- La voix suit l'animation : chaque façon de bouger est annoncée au moment où on la voit (la Dame tout droit, puis en diagonale ; le Pion d'une case, de deux cases, puis la prise). L'étape suivante attend la fin de la phrase. Sans voix, le rythme de l'animation ne change pas.
- La jauge d'évaluation et les noms des joueurs restent alignés avec l'échiquier, même avec une grande taille de police.
- Nettoyage du code : fonctions jamais utilisées supprimées, réglages de voix factorisés.

## ChessDuck 2.4.6 — une voix douce pour le mode enfant

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Mode enfant : la présentation d'une pièce peut être lue à voix haute quand l'enfant ouvre sa fiche. Réglage « Voix en mode enfant » dans Paramètres › Mode enfant, désactivé par défaut.
- Pensé pour les oreilles des parents : une seule lecture par ouverture, pas de bouton pour la répéter, la même pièce n'est pas relue avant 30 secondes, une autre pièce coupe la précédente, et la voix se tait dès que la fiche se ferme. Rien n'est lu pendant les parties.

## ChessDuck 2.4.5 — pause de la lecture vocale

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Académie : le bouton Pause met aussi la voix en pause. Avec Lecture, elle reprend au début de la phrase interrompue, jamais au milieu d'un mot, puis la leçon continue.

## ChessDuck 2.4.4 — une voix qui parle échecs

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Lecture vocale des leçons : les termes d'échecs et les noms d'ouvertures sont désormais bien prononcés (« pat », « mat », « gambit », « fianchetto », « zugzwang », Giuoco Piano, Kieseritzky, Najdorf, Winawer, Tarrasch, Caro-Kann, Grünfeld…).
- L'écran garde l'orthographe exacte : seule la voix reçoit la prononciation.

## ChessDuck 2.4.3 — numérotation par tour

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Bande des coups : chaque tour est regroupé dans une capsule avec son numéro en bleu, le coup des Blancs et la réponse des Noirs. Seul le coup affiché est surligné, plus son numéro.
- Titres des cartes en numérotation standard : « 12… Qe5+ · coup 12 sur 14 » au lieu de « Coup 24 / 28 », qui comptait les demi-coups.
- Académie : les leçons sont numérotées depuis leur début (1, 2, 3…), même quand elles viennent d'une vraie partie ; une leçon qui commence par un coup noir débute par « 1… ». La revue et les problèmes gardent les vrais numéros de la partie.

## ChessDuck 2.4.2 — revue : jauge et commentaires concordants

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Revue de partie : un « mat manqué » précise désormais où en est le mat après le coup joué, comme la jauge (« Après Qe5+, il lui faut encore 2 coups pour mater »). La jauge montre toujours la position affichée ; le commentaire juge le coup qui y a mené.
- Cohérence vérifiée sur 40 parties simulées avec Stockfish (3 470 coups commentés, 64 mats manqués) : aucune contradiction entre la jauge et les commentaires.

## ChessDuck 2.4.1 — des leçons réécrites comme par un professeur

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Académie : les 113 leçons ont été réécrites, de l'objectif à chaque commentaire. Chaque coup est expliqué par son idée et sa raison, et chaque leçon se termine par une règle « À retenir ».
- Les coups de l'adversaire sont eux aussi expliqués, au lieu de simples mentions comme « Seul coup » ou « Symétrie ».
- Des phrases complètes et claires, sans abréviations ni notation anglaise : idéal pour la lecture vocale.
- Les coups des leçons ne changent pas : votre progression est conservée.

## ChessDuck 2.4.0 — lecture vocale des leçons

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Académie : nouvelle lecture à voix haute des leçons en mode Guidé (objectif, puis chaque coup et son commentaire), pour garder les yeux sur l'échiquier. Les coups sont prononcés en français (« cavalier prend e5, échec »).
- La lecture automatique attend la fin de chaque commentaire avant de jouer le coup suivant ; un bouton haut-parleur permet d'arrêter ou de réécouter.
- À activer dans Paramètres › Sons et vibrations (désactivée par défaut). Elle utilise une voix française installée sur le téléphone, sans connexion ; aucune voix en mode Défi ni en mode enfant.
- Jeu Libre : à la fin d'une partie, la jauge d'évaluation affiche « # » (mat) ou l'égalité (nulle) au lieu d'une ancienne évaluation.

## ChessDuck 2.3.2 — jauge lisible, images aux couleurs de votre palette

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Jauge d'évaluation : le score s'affiche toujours en entier, même avec une grande taille de police (au-delà de 10 pions, il est arrondi à l'unité : « +12 »).
- Images de problèmes et de solutions : elles reprennent la palette choisie dans les Paramètres (fond, accents, flèches et pastilles), en version sombre comme claire.

## ChessDuck 2.3.1 — fiabilité et fluidité

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Jeu Libre : un clic sur un coup éloigné dans la liste affiche directement la position, sans animation trompeuse ; avancer ou reculer d'un coup reste animé.
- Images de partage générées plus rapidement (pièces chargées une seule fois).
- Mémoire mieux maîtrisée : seules les analyses des 20 dernières parties sont gardées en mémoire, les autres sont relues depuis l'historique.
- Réorganisation interne du code (aucun changement de vos données, réglages ou progression).

## ChessDuck 2.3.0 — palettes de couleurs

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Paramètres : 5 palettes de couleurs au choix, avec aperçu, en mode Jour comme en mode Nuit : Ardoise & Or (charte ChessDuck), Améthyste, Noyer, Graphite & Rose et Bleu-Orange, adaptée au daltonisme (réussite en bleu, erreur en vermillon). Elles remplacent l'option « Couleurs dynamiques ».
- Toutes les palettes respectent les contrastes WCAG AA ; l'échiquier en bois reste identique.
- Le mode enfant est désormais le premier réglage du tiroir Paramètres ; le choix de la palette se trouve sous les pseudos.

## ChessDuck 2.2.6 — correctif des problèmes récents

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Problèmes récents : un problème joué avec un thème précis sélectionné (ex. « Mat en 1 ») ou dont le thème a été révélé affiche désormais son thème, au lieu de « thème masqué ». Les problèmes jamais résolus ni révélés restent masqués (anti-spoiler).

## ChessDuck 2.2.5 — nouvelle mascotte partout dans l'application

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Le nouveau canard-cavalier de l'icône remplace l'ancienne mascotte dans l'en-tête de l'application, l'écran de sortie du mode enfant et le pied de page des images partagées.

## ChessDuck 2.2.4 — nouvelle icône

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Nouvelle icône : canard-cavalier jaune en relief sur un damier bleu-violet, adaptée à toutes les formes d'icônes Android (carré arrondi, cercle, goutte) et aux icônes à thème (Android 13+).

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
