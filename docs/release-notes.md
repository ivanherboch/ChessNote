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
