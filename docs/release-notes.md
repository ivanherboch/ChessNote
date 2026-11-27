## ChessDuck 4.4.4 — filtre de longueur plus clair

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Catalogue des problèmes : avec une longueur choisie (1 coup, court, long), les nombres de chaque thème et de chaque famille ne comptent plus que les problèmes de cette longueur. Avec « Long », par exemple, « Mat en 1 » affiche 0 au lieu de 2 270. Une phrase sous les puces rappelle ce que veut dire la longueur choisie.
- Rappel : la longueur s'applique au prochain problème tiré (bouton « Tirer un problème », ou choix d'un thème) ; le problème déjà affiché ne change pas.

## ChessDuck 4.4.3 — compteurs justes avec les thèmes secondaires

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Catalogue, « Inclure les thèmes secondaires » coché : « Tous les thèmes » affiche bien 150 000 problèmes (et non 419 697, qui était le nombre de liens problème → thème), et chaque famille compte ses problèmes une seule fois même quand ils ont plusieurs de ses thèmes. Les compteurs par thème, eux, étaient déjà justes.

## ChessDuck 4.4.2 — Problèmes : échiquier plus grand

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Le bandeau des coups sous l'échiquier est retiré de l'onglet Problèmes : l'échiquier reprend cette hauteur. Les flèches ‹ et › permettent toujours de revoir les coups joués et la solution.

## ChessDuck 4.4.1 — finitions des Problèmes

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- « À refaire » suit la répétition espacée de l'Académie. Un problème manqué revient le lendemain. Le résoudre sans erreur l'espace à 7, 30 puis 90 jours avant de le retirer. Une erreur garde l'intervalle en cours, une solution vue le remet à un jour.
- Tirage aléatoire plus équitable : il choisit au hasard parmi les problèmes trouvés, au lieu de prendre toujours le premier (qui favorisait ceux placés après une longue suite de problèmes résolus).
- « Continuer contre l'Ordinateur » depuis un problème : le Jeu Libre parle maintenant de « Position du problème » et de « Partie d'entraînement depuis un problème », et non plus d'une leçon.
- Grande police : l'indice et la réfutation se lisent en entier sous l'échiquier, sans « Lire la suite ». Le trait et le signet restent sur une ligne, les détails du problème passent dessous.

## ChessDuck 4.4.0 — Problèmes : « Mon niveau », À refaire, Favoris et thèmes secondaires

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- « Mon niveau » : un ELO tactique propre aux Problèmes. Il part de votre ELO du Jeu Libre et évolue après chaque problème (résolu net, erreur, abandon), une seule fois par problème. La nouvelle puce « Mon niveau · 1250 » de la difficulté tire des problèmes autour de cet ELO. « Tous » et les 4 difficultés restent inchangés.
- Problèmes joués, en trois onglets :
  - Récents : les 50 derniers ;
  - À refaire : un problème manqué (solution vue, flèche de l'indice) ou résolu après une erreur revient ici le lendemain ; le résoudre sans erreur l'en retire ;
  - Favoris : un signet, à côté du numéro du problème, l'y ajoute.
- Catalogue :
  - bilan tactique (ELO tactique, précision, série de résolutions nettes en cours, thèmes les plus manqués) ;
  - filtre de longueur de la solution (1 coup, court, long) ;
  - nouvel interrupteur « Inclure les thèmes secondaires » : un thème choisi couvre alors aussi les problèmes où il n'est pas l'idée principale (par exemple 25 666 sacrifices au lieu de quelques centaines). Par défaut, chaque problème reste rangé dans son seul thème principal.
- Base de données v5 : les 150 000 problèmes et toute votre progression sont conservés à la mise à jour. Les problèmes déjà résolus restent résolus.

## ChessDuck 4.3.0 — Problèmes : Solution, bandeau des coups et passerelles

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Après une erreur, un bouton « Solution » déroule toute la ligne, coup après coup, navigable avec ‹ et ›. Le problème ne compte alors pas comme résolu et reviendra au tirage.
- Problème fini (résolu ou solution vue) : « Recommencer » le rejoue depuis le coup d'introduction, et « Problème suivant » devient le bouton principal. Le dé du haut ne fait plus doublon.
- Le bouton de l'indice 2/2 dit ce qu'il coûte : « Montrer le coup (non compté) ».
- Bandeau des coups sous l'échiquier : les coups déjà joués (jamais la suite de la solution) ; toucher un coup y revient.
- Le message d'accueil ne répète plus le trait, déjà écrit au-dessus de l'échiquier.
- Catalogue :
  - un bouton « Tirer un problème » apparaît quand seule la difficulté change ;
  - sur l'écran « aucun problème », changer de difficulté relance aussitôt le tirage ;
  - quand tous les problèmes non résolus d'une sélection sont épuisés, le tirage le dit au lieu de reproposer en silence des problèmes déjà résolus.
- Nouvelles passerelles, une fois le problème fini :
  - « Continuer contre l'Ordinateur » ouvre le Jeu Libre depuis la position finale (sauf mat), avec votre camp ;
  - « Voir la leçon » ouvre dans l'Académie la leçon du thème (de préférence une leçon pas encore maîtrisée, proche de votre palier).

## ChessDuck 4.2.15 — Problèmes : fiabilité, anti-spoiler et tirage instantané

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Tirage aléatoire instantané : moins d'une milliseconde en mode Libre, contre environ 300 ms avant (mesuré sur la base de 150 000 problèmes).
- Résolution plus sûre :
  - on ne joue qu'au bout de la ligne déjà jouée ; en revoyant un coup précédent, la carte le dit (« Touchez › pour revenir au problème ») ;
  - un mauvais coup n'efface plus la suite déjà trouvée ;
  - avant le coup d'introduction, le trait affiché est le vrai (celui de l'adversaire).
- Problème terminé avec la flèche de l'indice : ton doré, sans vibration de réussite ; s'il était déjà résolu auparavant, le message le précise.
- Anti-spoiler :
  - le thème n'est affiché d'office que si votre filtre est précisément ce thème ;
  - les suggestions de recherche ne montrent plus le thème, seulement l'identifiant et la difficulté ;
  - l'aperçu de la solution n'est calculé qu'une fois démasqué.
- Recherche du catalogue : un mot (« fourchette ») ouvre le thème au lieu d'être pris pour un identifiant ; la base n'est interrogée qu'une fois la frappe posée.
- Réfutation des mauvais coups :
  - la prise en passant est bien nommée ;
  - un coup qui gagnait aussi, ou matait plus lentement, est expliqué comme tel ;
  - si l'Ordinateur ne répond pas à temps, aucune réponse n'est inventée.
- Export en image :
  - les solutions de 7 à 10 coups tiennent dans l'image (jusqu'à 5 colonnes, en 4:5 comme en 1:1) ;
  - la réponse adverse n'est plus jamais omise ;
  - un seul badge « x » par case ;
  - nouvelle option « Indice : Inclure / Sans » pour l'énoncé.

## ChessDuck 4.2.14 — carte « Leçons » plus claire

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Accueil de l'Académie, carte « Leçons » :
  - chaque bouton dit combien de leçons il ouvre : « Palier 2 (33) » (les leçons de votre palier) et « Toutes (211) » (le catalogue complet, avec recherche et filtres) ;
  - la progression s'écrit « 7 sur 211 maîtrisées ».

## ChessDuck 4.2.13 — une partie terminée n'est enregistrée qu'une fois

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Jeu Libre : une partie est close dès sa fin (mat, pat, nulle, abandon), avant même l'affichage du résultat. Un double toucher sur « Abandonner », ou « Nouvelle partie » touché à l'instant de la fin, ne peut plus l'enregistrer plusieurs fois dans l'historique, ni compter l'ELO deux fois.

## ChessDuck 4.2.12 — raccourci « Palier » sur l'accueil de l'Académie

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Carte « Leçons » de l'accueil : le bouton « Palier N » revient à côté de « Catalogue ». Il ouvre le catalogue filtré sur les leçons de votre palier. En grande police, les deux boutons passent sous le titre.
- Le filtre complet reste dans le catalogue (bouton « Filtres », rangée « Palier »).

## ChessDuck 4.2.11 — « résolu » sans la flèche de l'indice

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Problèmes : un problème terminé après la flèche de l'indice 2/2 ne compte plus comme résolu (compteur, coche des récents). Il reste à résoudre et pourra revenir au tirage. Comme dans les séries de l'Académie, un problème réussi après une erreur compte toujours.
- L'indice 2/2 le signale avant (« Ce problème ne comptera pas comme résolu »), et le message de fin le rappelle.

## ChessDuck 4.2.10 — Problèmes débogués

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Reculer d'un coup juste après un bon coup ne fait plus sauter l'échiquier en avant quand la réponse adverse arrive : elle s'ajoute à la suite, et « Coup suivant » la montre.
- Base vérifiée : les 150 000 problèmes ont des coups légaux, une solution qui se termine par un coup du joueur, aucune fin de partie prématurée et un thème connu.
- Nouveau test automatique : 60 sessions d'actions au hasard (bons et mauvais coups, indice, navigation, Réessayer, nouveau problème, filtres). Aucun blocage, et la solution jouée résout toujours le problème, qui est bien compté comme résolu.

## ChessDuck 4.2.9 — quitter une partie compte comme un abandon

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Jeu Libre : commencer une nouvelle partie, changer les réglages ou lancer une position de leçon pendant une partie en cours contre l'Ordinateur compte désormais comme un abandon. La partie est enregistrée dans l'historique (analysable) et, si elle était classée, l'ELO est mis à jour. Relancer une partie mal engagée ne permet plus d'éviter une défaite.
- La confirmation l'annonce clairement (« La partie en cours comptera comme un abandon… »).
- Avant le premier échange, ou à deux joueurs, rien ne change : la partie est simplement laissée de côté.

## ChessDuck 4.2.8 — Jeu Libre débogué

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Filet de sécurité : naviguer dans les coups (flèches ou liste) pendant qu'un coup est arrêté referme l'arrêt. Avant, « Jouer quand même » pouvait jouer ce coup dans une autre position, et « Pourquoi ? » dessinait ses flèches sur le mauvais échiquier.
- Nouveau test automatique : 80 sessions de 160 actions au hasard (coups, indice, annulation, navigation, abandon, filet, nouvelle partie, réglages, positions de leçon). Aucune partie ne reste bloquée, chaque fin de partie est conclue et enregistrée une seule fois, et l'ELO ne bouge jamais pour une partie hors classement.

## ChessDuck 4.2.7 — Académie déboguée

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Quitter une révision ou « Mon répertoire » juste après le dernier coup ne compte plus la leçon comme pratiquée (étoiles, étape « Leçon » de la séance du jour) : la ligne rejouée s'arrête aussitôt.
- Séance du jour : le bandeau « Étape terminée ✓ · Suite » ne resurgit plus à tort dans une autre leçon après le retour à l'accueil.
- Séries de problèmes au bon niveau : quand un thème a trop peu de problèmes au palier (le roque au palier 2 n'en a que 2), la recherche s'élargit peu à peu autour du palier au lieu de piocher dans toute la base (surtout des problèmes vers 2000, impossibles à réussir à 4 sur 5).
- Nouveau test automatique qui parcourt les 211 leçons comme un élève (Comprendre jusqu'au bout, puis Pratiquer, ligne principale et chaque variante) : aucune ne bloque ni ne se termine sans rien demander.

## ChessDuck 4.2.6 — filet de sécurité débogué

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Bugs corrigés dans le filet de sécurité :
  - un coup rejoué après être revenu en arrière dans l'historique n'était pas vérifié : il l'est désormais ;
  - pendant la vérification, les flèches de navigation sont bloquées (plus d'état incohérent) ;
  - « … était attaquée, mais pas en danger » n'apparaît plus quand la pièce était réellement menacée ;
  - un coup qui fait pat alors que vous gagnez est maintenant arrêté ;
  - un simple conseil (« vous auriez dû développer votre cavalier », « pensez à roquer ») ne suffit plus à arrêter un coup ;
  - les coups ne prennent plus de majuscule à tort en début d'explication (« B4 », « F5 » → « b4 », « f5 »).
- Nouveau cas reconnu : un coup qui laisse à l'adversaire un échec qui fait basculer la partie (« Après Nd7, l'échec f6+ fait basculer la partie… »).
- Explication plus juste quand une pièce va tomber : « attaquée par un pion, qui vaut moins qu'elle », « plus personne ne la défend », ou « attaquée plus de fois qu'elle n'est défendue », selon le vrai motif.
- Mesuré sur des positions réelles avec l'Ordinateur : 121 gaffes sur 133 arrêtées (106 avant), aucun arrêt à tort sur 150 bons coups, et 9 arrêts tous justifiés sur 347 coups de débutants.

## ChessDuck 4.2.5 — le filet de sécurité ne s'arrête que pour une vraie gaffe

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Le filet ne vous arrête plus que pour une gaffe concrète qu'il sait expliquer : pièce laissée en prise, pièce qui va tomber, mat permis, fourchette… Plus d'arrêt avec un simple « X était plus fort que Y ».
- Pas d'arrêt non plus si vous restez nettement gagnant après votre coup.
- Nouveau cas reconnu (aussi dans la revue de partie) : une pièce qui n'est plus assez défendue après la réponse adverse (« après Bd7, il est attaqué plus de fois qu'il n'est défendu et va tomber »).

## ChessDuck 4.2.4 — coup conseillé expliqué en clair

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Filet de sécurité : le coup conseillé dit ce qu'il fait vraiment, au lieu d'un « qui ne perd rien » parfois trompeur :
  - échange de dames (« vous prenez sa dame, l'adversaire reprend la vôtre, et vous ne perdez rien ») ou autre échange égal ;
  - gain de matériel (la pièce prise vaut plus que celle qui prend) ou prise gratuite ;
  - « qui ne perd rien » seulement pour un coup sans prise.

## ChessDuck 4.2.3 — filet de sécurité fluide

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Avec le filet de sécurité, votre coup s'affiche aussitôt, comme sans filet : la vérification (plus courte) se fait pendant la réflexion de l'Ordinateur et ne s'y ajoute plus.
- Une gaffe est retirée de l'échiquier (la pièce revient) au moment de l'arrêt ; un bon coup enchaîne directement sur la réponse de l'Ordinateur.

## ChessDuck 4.2.2 — Séance du jour bien visible, filet de sécurité plus juste

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Accueil de l'Académie : la première carte s'appelle désormais « Séance du jour » (bord vert), avec la série de jours, le palier, les étapes numérotées en pastilles (l'étape en cours surlignée, ✓ une fois faite) et un gros bouton « Commencer la séance : … » puis « Suite de la séance : … ».
- Filet de sécurité :
  - quand la pièce que vous déplacez était attaquée mais pas en danger (défendue, un échange ne vous ferait rien perdre), il le dit, puis nomme le vrai problème (« Le vrai problème : Qe2 laisse votre cavalier en f3… ») ;
  - « Pourquoi ? » donne aussi le coup conseillé (flèche verte), qui ne perd rien ;
  - deuxième arrêt dans la même position : l'explication et le coup conseillé s'affichent d'emblée, pour ne plus tourner en rond quand presque tous les coups perdent du matériel.

## ChessDuck 4.2.0 — Séance du jour et filet de sécurité

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Séance du jour (accueil de l'Académie, carte « Mon parcours ») : une dizaine de minutes en un seul bouton.
  - Les étapes du jour : révisions dues, la leçon en cours (Comprendre puis Pratiquer), puis 5 exercices de votre point faible n° 1 ; chacune est cochée une fois faite.
  - Le bouton lance toujours l'étape suivante ; à la fin d'une étape, un bandeau propose « Continuer » vers la suivante.
  - Série de jours consécutifs (🔥) : elle avance chaque jour où la séance est terminée.
- Filet de sécurité dans le Jeu Libre (Paramètres de partie) :
  - l'Ordinateur arrête une gaffe nette avant qu'elle soit jouée ; « Pourquoi ? » l'explique comme dans la revue, avec la réponse adverse en rouge ;
  - « Changer de coup » ou « Jouer quand même » ; dès qu'il vous a arrêté, la partie ne compte plus pour l'ELO ;
  - réglage mémorisé, bouclier vert à côté de l'adversaire quand il est actif ;
  - les gaffes arrêtées comptent dans « À travailler » de l'Académie, même sans analyse de la partie.

## ChessDuck 4.1.4 — l'Atelier avant les Leçons

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Accueil de l'Académie : la carte « Atelier » passe avant la carte « Leçons ».
- « À travailler » : un point travaillé qui n'est pas réapparu dans vos parties l'indique en clair (« Plus revu depuis votre entraînement ») au lieu de « 0 depuis votre entraînement ».

## ChessDuck 4.1.3 — fin de « Comprendre » bien signalée

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- À la fin de la démonstration, la carte devient verte avec « Comprendre terminé ✓ ».
- Le bouton principal propose aussitôt la suite (« Question de fin » s'il y en a une, sinon « Passer à la pratique »), même pendant que la voix lit le dernier commentaire.
- L'onglet « Comprendre » d'une leçon déjà suivie jusqu'au bout porte une coche, aussi en Pratiquer et lors des visites suivantes ; la leçon passe en « En cours » dans son en-tête.

## ChessDuck 4.1.2 — l'accueil de l'Académie tient sur un écran

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- L'accueil de l'Académie tient sans défilement sur un téléphone, même avec les révisions du jour et 3 points à travailler :
  - « Mon parcours » plus compact : palier et « Changer de palier » sur une ligne, progression et nombre de leçons maîtrisées côte à côte, l'action principale et « Révisions » sur la même rangée ;
  - « À travailler » : chaque point est une ligne qu'on touche (titre, raison courte, flèche) ;
  - « Leçons » sur une ligne avec le bouton « Catalogue » (les filtres par palier et catégorie restent dans le catalogue) ;
  - « Atelier » : les 4 tuiles sur une seule rangée.
- Grande police : les tuiles passent à deux par rangée et « Changer de palier » sous le titre, sans aucun texte coupé.
- Leçon : le bouton de retour à l'accueil (à gauche du nom de la leçon) est une flèche coudée, bien distincte de la flèche « Leçon suivante ».

## ChessDuck 4.1.0 — « À travailler » qui suit vos progrès

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Les parties récentes comptent plus que les anciennes : le bilan suit vos progrès au lieu de rester figé sur vos 20 dernières parties.
- Un point travaillé avec succès (série d'exercices réussie, leçon maîtrisée) recule : ses erreurs d'avant comptent beaucoup moins, et il disparaît 3 jours s'il ne réapparaît pas dans vos parties.
- Plusieurs leçons par point faible : quand la leçon proposée est maîtrisée, une autre leçon du même thème prend le relais (pièces en prise, prises gagnantes, mats, défense, roque et sécurité du roi…).
- Deux nouveaux points faibles détectés dans vos parties : « Fourchette subie » et « Fourchette manquée », avec les 6 leçons sur la fourchette. La revue de partie les explique aussi.
- L'explication précise le nombre de parties analysées, la dernière occurrence et, après un entraînement, le nombre d'erreurs depuis.
- « Travailler ce point dans l'Académie », depuis la revue de partie, propose lui aussi la leçon du moment.

## ChessDuck 4.0.4 — noms des pièces en entier (mode enfant)

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Mode enfant : le nom de chaque pièce est écrit sous sa carte colorée, sur toute sa largeur. « Cavalier » ne déborde plus sur les petits écrans.

## ChessDuck 4.0.3 — Calcul à l'aveugle plus clair, jamais bloqué

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Consigne explicite : les pièces restent à leur place ; imaginez les deux coups annoncés, puis jouez le coup suivant en touchant la pièce, puis sa case d'arrivée.
- « Revoir l'échiquier » devient « Voir la position » : la position après les coups annoncés s'affiche avec la marche à suivre (la réponse ne compte alors plus).
- Nouveau bouton « Solution » : la flèche verte montre le bon coup, la question compte comme manquée et « Question suivante » apparaît. On n'est plus jamais bloqué sur une question.

## ChessDuck 4.0.2 — barres de progression nettes

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Barres de progression (palier de l'Académie, analyse d'une partie, compte à rebours d'une réfutation) : plus de petit point au bout ni d'écart entre la partie remplie et le reste.

## ChessDuck 4.0.1 — un échiquier plus grand dans les séances

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Finales, séries de problèmes et réflexes : l'échiquier occupe presque toute la largeur du téléphone (environ 96 % au lieu de 80 % sur un écran courant).
- Plus d'échiquier qui s'affiche petit puis grandit quelques secondes plus tard :
  - la ligne au-dessus de l'échiquier (« Ordinateur · vous jouez les Blancs », pastilles) garde toute la largeur de l'écran et ne passe plus à la ligne quand l'échiquier rétrécit ;
  - la place réservée aux textes du bas repart de zéro en changeant d'écran, au lieu de garder quelques secondes celle de l'écran précédent.
- Finales : titre de la carte sur une ligne (la position en cours est donnée par les pastilles), consigne et méthode sans ligne vide.

## ChessDuck 4.0.0 — l'Académie simplifiée

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

Cette version termine la refonte de l'Académie : leçons en deux temps (Comprendre, puis Pratiquer), un vrai accueil avec l'Atelier, et des parcours récurrents plus courts.

- Révisions du jour rapides : une seule épreuve par leçon due.
  - Tactique et méthode : 1 problème inédit du thème.
  - Finale : 1 position contre l'Ordinateur.
  - Ouverture : la ligne à rejouer (un coup aussi bon est accepté, flèche verte à l'appui).
  - Bases, stratégie et autres leçons : les coups clés à rejouer.
  - Le calendrier se met à jour dès la fin de l'épreuve ; « Révision suivante » puis « Terminer les révisions ».
- « Mon répertoire » choisi par vous :
  - un signet sur chaque leçon d'ouverture (et un bouton dans sa fiche) l'ajoute ou la retire ;
  - dans l'Atelier, « Mon répertoire » liste vos ouvertures par camp, avec « Choisir mes ouvertures » ;
  - la révision ne tire plus au hasard parmi les 53 ouvertures : vos ouvertures choisies, sinon celles déjà travaillées, sinon une invitation à les choisir.
- Fiche de la leçon en 3 sections : L'essentiel (objectif, « À retenir » mis en avant, conditions), Idées et pièges, Moments clés.
- Calcul à l'aveugle : les coups annoncés sont lus en français et restent écrits quand la voix est coupée.
- Captures et vérifications complètes sur téléphone (y compris grande police) et tablette (portrait et paysage).

## ChessDuck 3.11.0 — un vrai accueil pour l'Académie

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- L'onglet Académie s'ouvre sur un accueil, sans échiquier :
  - « Mon parcours » : votre palier, sa progression, « Changer de palier » (et le test de placement s'il reste à faire), puis une seule action principale, « Continuer » la leçon commencée ou « Prochaine leçon » ;
  - « Révisions du jour (N) » dès qu'il y en a ;
  - « À travailler » : vos points faibles du moment, seulement s'il y en a, avec « Travailler ce point » ;
  - « Leçons (211) » : le catalogue, avec des raccourcis vers votre palier et chaque catégorie ;
  - « Atelier » : 4 tuiles, Finales contre l'Ordinateur, Réflexes & Calcul, Mon répertoire, Tests de niveau.
- Le bouton « Entraînements · N » et son long tiroir disparaissent : l'en-tête est le même dans les 4 onglets (mascotte, titre, Paramètres).
- Navigation claire : la flèche ← (et le retour Android) d'une séance ramène à l'écran qui l'a lancée (leçon, accueil ou Atelier) ; une leçon revient à l'accueil.
- Le pont « Travailler ce point dans l'Académie » depuis la revue de partie ouvre toujours la séance directement.
- Catalogue allégé : numéro, titre, résumé et un seul état (À revoir, Maîtrisée avec ses étoiles, En cours ou Conseillée).
- Sur tablette, l'accueil se présente en deux colonnes.
- Bouton « Fiche » de la leçon : même hauteur que les onglets Comprendre et Pratiquer.

## ChessDuck 3.10.0 — des leçons plus lisibles : Comprendre, puis Pratiquer

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Chaque leçon a deux onglets clairs : « Comprendre » et « Pratiquer », à la place de Guidé, Défi et Deviner.
- En-tête allégé au-dessus de l'échiquier :
  - titre, catégorie, palier et un seul statut (À faire, En cours ou Maîtrisée), avec les étoiles ;
  - un bouton « Fiche » lisible à la place de la petite icône, le menu Variantes si l'ouverture en a, et le retournement de l'échiquier ;
  - les icônes « Moments clés » et « Jouer avec le camp de votre choix » quittent le haut de l'écran.
- Comprendre : la leçon se déroule sans arrêt bloquant, avec ses flèches et repères, à lire ou à écouter.
  - Barre du bas fixe : voix, retour au début, Lecture/Pause, aller à la fin.
  - À la fin : « Question de fin » s'il y en a une, puis « Passer à la pratique ».
  - « S'entraîner » (onglet Problèmes) n'est plus proposé quand Pratiquer est déjà une série de problèmes du thème.
- Pratiquer lance directement le bon exercice selon la leçon :
  - tactique et méthode : 5 problèmes inédits du thème (4 sur 5 pour maîtriser la leçon), avec « Revoir les coups » pour revenir à Comprendre ;
  - finales avec positions générées : 5 positions contre l'Ordinateur (3 sur 5 pour maîtriser la leçon) ;
  - ouvertures : la ligne et ses variantes à rejouer ;
  - bases, stratégie et autres finales : seulement les coups clés de la leçon, les autres se jouent seuls ;
  - parties modèles : les coups clés à deviner par défaut, avec une puce « Coups clés » / « Tous les coups ».
- Plus d'étape « À valider » : une leçon réussie dans Pratiquer est maîtrisée.
- Un coup aussi bon que celui de la leçon est félicité (« Bon coup aussi ! ») et la flèche verte montre aussitôt le coup du répertoire, sans pénalité.
- Les cœurs disparaissent : un problème résolu sans la flèche de l'indice 2/2 compte, même après une erreur.
- Correctif : la flèche de l'indice 2/2 montrée sur un coup intermédiaire d'un problème est désormais bien prise en compte (le problème ne compte pas).
- Après une erreur, un seul bouton partout : « Réessayer » (un toucher sur l'échiquier reprend aussi tout de suite).

## ChessDuck 3.9.0 — des cœurs pour les entraînements

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Séries d'exercices de l'Académie (validation d'une leçon, « Le seul coup », révisions) : vous avez autant de cœurs que de problèmes, soit 5 cœurs pour 5 problèmes.
  - Une erreur coûte un cœur, et le problème compte encore si vous le résolvez ensuite.
  - Sans cœur restant, une erreur fait manquer le problème, comme avant.
  - Les cœurs restants s'affichent dans le titre de la série.
  - La flèche de l'indice 2/2 et le bouton « Solution » font toujours manquer le problème.
- Le test de placement et l'examen du palier restent sans cœurs : ils mesurent votre niveau.

## ChessDuck 3.8.3 — le deuxième indice en mode Guidé

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Mode Guidé, questions « À vous » : l'indice se donne en deux temps, comme dans le Défi, les exercices, les finales et les Problèmes. Le premier surligne la pièce à jouer, le second montre le coup par une flèche verte. Le bouton restait grisé après le premier indice.

## ChessDuck 3.8.2 — plus d'échiquier gelé

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Échiquier gelé corrigé (finales, Défi, exercices, Problèmes, Jeu Libre).
  - Quand l'échiquier changeait de taille, par exemple pour laisser de la place à un message plus long, les touchers continuaient d'utiliser l'ancienne taille des cases. On touchait sa pièce, l'application « voyait » une case voisine vide, et rien ne se passait.
  - Le même défaut expliquait que les points des destinations n'apparaissent qu'en touchant ailleurs.
  - Le seul remède était de quitter l'Académie puis d'y revenir. Ce n'est plus nécessaire.
- Défi et mode Guidé : si l'Ordinateur ne peut pas juger un coup à temps (démarrage sur un téléphone lent, autre analyse en cours), le coup n'est plus refusé ni compté comme une erreur : il suffit de le rejouer. Il dispose aussi de plus de temps pour juger (5 secondes au lieu de 1,5).
- « Deviner le coup » (parties modèles) : dans ce même cas, plus de 0 point injuste.

## ChessDuck 3.8.1 — l'Ordinateur prêt dès l'Académie

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- L'Ordinateur démarre de nouveau dès l'ouverture de l'Académie, en arrière-plan. Le premier entraînement (finale, réflexes, exercices, Défi) ne fait plus attendre son lancement, et l'échiquier répond tout de suite.

## ChessDuck 3.8.0 — plus léger, plus rapide, plus fiable

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- APK deux fois plus léger : 15,8 Mo au lieu de 34,5 Mo (la base des 150 000 problèmes est maintenant compressée).
- Démarrage : l'Ordinateur ne se lance plus à l'ouverture de l'application, mais au premier coup à juger (Défi, question « À vous », finale, exercices, Jeu Libre).
- « Jouer cette position » : à la première ouverture du Jeu Libre, la position de la leçon n'est plus remplacée par une partie normale.
- Partie accompagnée : une vérification de coup en cours est abandonnée proprement en cas d'annulation, d'abandon ou de nouvelle partie.
- Problèmes :
  - Le catalogue compte les problèmes par thème environ dix fois plus vite.
  - « S'entraîner sur ce thème » ne tire plus deux problèmes d'affilée.
  - Une résolution est toujours enregistrée, même si l'on passe aussitôt au problème suivant.
- Fluidité : les positions de finale et les exercices de réflexes sont préparés hors du fil principal, les images des pièces sont chargées une seule fois, et les parties déjà analysées ne sont plus rejouées pour le bilan des points faibles.
- Robustesse :
  - L'Ordinateur ne peut plus bloquer l'application s'il cesse de répondre.
  - Le moteur, la voix et les sons sont libérés en quittant l'application.
  - Une image ratée n'est plus laissée dans la galerie.
  - Une voix de synthèse défaillante ne fait plus planter l'application.

## ChessDuck 3.7.0 — audit de l'Académie, lot 4 : la partie accompagnée et des repères sur l'échiquier

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Partie accompagnée : dans « Jouer cette position », l'Ordinateur vérifie chacun de vos coups. Avant une grosse erreur (2 pions ou plus), il arrête le coup, deux fois au plus par partie.
  - Il vous pose la question réflexe qui convient (quelle prise, quel échec, quel mat laissez-vous ?), sans dévoiler la réponse.
  - Vous changez de coup, ou vous le jouez quand même.
  - Le bouton à la toque (en haut) active ou coupe ce mode.
- Repères sur l'échiquier en mode Guidé : 80 schémas au lieu de 20. Ils montrent les concepts d'un coup d'œil, sans alourdir le texte :
  - les déplacements des pièces, la prise en passant et la promotion ;
  - les cases clés, le carré du pion, les murs du mat, l'opposition, le coin de la mauvaise couleur ;
  - les avant-postes, les cases faibles, les pions doublés, isolés ou pendants, les colonnes ouvertes et la septième rangée.

## ChessDuck 3.6.0 — audit de l'Académie, lot 3 : plus de pratique, un suivi plus juste

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Les bases :
  - « L'échiquier et la notation » vous fait jouer deux coups notés, puis mène directement à la Vision des cases (onglet Notation).
  - « La guerre des pions » se joue maintenant contre l'Ordinateur : trois pions contre trois, trouvez la percée et faites passer un pion à dame. C'est aussi une nouvelle finale dans le tiroir « Entraînements ».
- Stratégie dès le palier 2 : « La pire pièce » et « Les pions doublés » passent au palier 2 ; « Bon fou et mauvais fou », « La colonne ouverte » et « La tour en septième rangée » passent au palier 3.
- Tactique : 38 leçons à un seul arrêt « À vous » en ont maintenant un deuxième. Vous jouez aussi la suite de la combinaison, pas seulement son premier coup.
- Parties modèles :
  - Chaque partie a 5 à 7 moments clés, choisis avec l'Ordinateur (les coups où le bon choix compte vraiment).
  - En Guidé, la partie s'y arrête pour vous demander le coup.
  - En Deviner, l'étoile permet de ne deviner que ces moments ; les autres coups se jouent seuls.
- Révisions adaptatives : réussie sans erreur ni indice, une révision allonge l'intervalle ; réussie avec une hésitation, elle garde le même ; ratée, la leçon revient le lendemain.
- Points faibles : les erreurs et les indices du Défi s'additionnent d'une tentative à l'autre au lieu d'être effacés au dernier essai.
- Un Défi interrompu par Android (application fermée en arrière-plan) reprend au coup atteint, avec ses erreurs, au lieu de repartir du début. La variante d'ouverture choisie est aussi gardée.
- Tiroir « Entraînements » : une barre montre les leçons maîtrisées du palier en cours et l'état de son examen.
- « Puis-je prendre ? » : l'explication donne aussi la réponse de l'Ordinateur à la prise (reprise, mat, prise d'une autre pièce, échec).
- « Jouer cette position » : l'Ordinateur joue au niveau de votre palier (de 600 à 2100 ELO), et non plus au réglage du Jeu Libre.

## ChessDuck 3.5.0 — audit de l'Académie, lot 2 : un Défi qui teste vraiment, des finales mieux arbitrées

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Défi : un coup aussi bon que celui de la leçon est toujours félicité, sans erreur comptée, mais la flèche verte ne montre plus le coup attendu. Il faut le retrouver de mémoire, ou demander un indice. Les étoiles reflètent ainsi ce que vous avez retenu.
- Ouvertures : le menu « Variantes » marche aussi en Défi. Chaque variante se rejoue depuis le début, et une variante réussie (au plus une erreur) reçoit une coche verte dans le menu. La maîtrise de la leçon reste fondée sur la ligne principale.
- Finales contre l'Ordinateur :
  - La triple répétition et la règle des 50 coups sont reconnues. Un échec perpétuel tient donc la nulle tout de suite ; dans une finale à gagner, répéter la position fait échouer.
  - Il y a plus de temps pour mater : 16 coups avec roi et dame contre roi (au lieu de 12), 12 coups avec les deux tours (au lieu de 8).
  - Dans les nulles à tenir, une gaffe est reconnue plus tôt : à partir d'un avantage adverse de 1,5 pion, et d'une baisse de 1,2 pion.
  - Défense Vancura, Philidor, et dame contre pion fou : l'Ordinateur embarqué croit parfois ces nulles perdues. Un coup n'y est plus refusé sur sa seule évaluation : seule une nette baisse par rapport à la position d'avant le condamne.

## ChessDuck 3.4.0 — audit de l'Académie, lot 1

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Accueil : l'application s'ouvre sur l'Académie. Un nouvel élève commence à la première leçon (« L'échiquier et la notation ») et au palier 1 ; le palier se choisit à la main dans le tiroir « Entraînements ».
- Cursus réordonné : au palier 2, les initiations (pièce gratuite, mat en un coup, fourchette de dame) et les méthodes (échecs, prises, menaces ; contrôle anti-gaffe ; compter attaquants et défenseurs) viennent avant les tactiques combinées ; « Le mat du berger » suit « Les trois principes ».
- Mode Guidé : les coups des questions « À vous » encore à résoudre, et la suite, sont masqués dans la bande des coups (et le tableau sur tablette) : plus de réponse en clair.
- Questions de fin de leçon : les 74 QCM ont de nouvelles mauvaises réponses, plausibles et de longueur comparable (la bonne n'était plus devinable à sa longueur). Une mauvaise réponse ne dévoile plus la bonne : on réessaie.
- Exercices au niveau débutant : les paliers 1 et 2 puisent dans les problèmes 400 à 1199 (et non plus 400 à 1100, presque vide pour certains thèmes comme le roque). L'examen proposé est celui de votre palier.
- Révisions : passer à la révision suivante ne rouvre plus le tiroir en pleine séance ; une finale révisée (une position) affiche « Position réussie » et la date de la prochaine révision.
- Bouton Retour d'Android : pendant une séance (finale, réflexes, série, question, révision, répertoire), il quitte la séance au lieu de quitter l'Académie.
- Leçons : « Le roque au bon moment » demande aussi le roque ; « Méthode : le contrôle anti-gaffe » se valide sur des problèmes de défense ; « Travailler ce point » ouvre la leçon en mode Guidé.
- « Quelle est la menace ? » n'est plus posée quand votre roi est en échec.
- Finales : « vous jouez les Blancs / Noirs » est indiqué ; « Reprendre le coup » disparaît après un rejeu réussi.
- En-tête : le titre « ChessDuck » se réduit au lieu de toucher le bouton « Entraînements » ; en très grande police, les onglets se réduisent au lieu d'être coupés (« Académi »).
- Vibrations : en revenant sur l'onglet Académie, la dernière vibration de réussite ou d'erreur n'est plus rejouée (d'où une vibration plus forte que sur les autres onglets).

## ChessDuck 3.3.5 — un échiquier qui ne se bloque plus

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- L'Ordinateur démarre dès l'ouverture de l'Académie : le premier coup jugé (Défi, finale) n'attend plus son lancement, pendant lequel l'échiquier semblait bloqué.
- Après un mauvais coup, pendant que la réfutation est affichée, un simple toucher sur l'échiquier remet la position : on rejoue tout de suite, sans attendre la fin du compte à rebours ni chercher le bouton. Vaut pour le Défi, les séries de problèmes et l'onglet Problèmes.

## ChessDuck 3.3.4 — des finales enfin jouables

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Finales contre l'Ordinateur : vos bons coups ne sont plus refusés.
  - L'Ordinateur analyse la position pendant que vous réfléchissez : il est prêt quand vous jouez, et l'indice s'affiche tout de suite.
  - Un coup n'est jugé perdant que s'il fait nettement baisser l'évaluation de la position d'avant (et plus seulement sous un seuil fixe, que le moteur du téléphone ne voyait pas toujours).
  - Si l'Ordinateur tarde à répondre (démarrage, téléphone occupé), la position n'est plus perdue : votre coup est annulé et vous le rejouez.
  - Le pion perdu ne fait plus échouer la position s'il vous reste une tour (ou une dame) contre le roi seul.
- L'échiquier ne reste plus bloqué de longues secondes : les attentes du moteur, qui s'accumulaient, ont disparu.
- Vérification : avec Stockfish, les meilleurs coups ont réussi les 55 positions testées (11 finales × 5).

## ChessDuck 3.3.3 — un parcours plus lisible

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Une leçon choisie dans le catalogue, ou depuis « Reprendre » et « Conseillée », s'ouvre toujours en mode Guidé (elle gardait auparavant le mode de la leçon précédente, souvent Défi). Les flèches ‹ › gardent le mode en cours, pour enchaîner les Défis.
- Les entraînements lancés depuis le tiroir (réflexes, finales, tests, révisions, répertoire) ramènent au tiroir quand on les quitte, au lieu d'afficher la dernière leçon ouverte. Le bouton de retour s'appelle alors « Revenir aux entraînements ».

## ChessDuck 3.3.2 — plus de texte coupé au-dessus de l'échiquier

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- La ligne d'information au-dessus de l'échiquier (calcul à l'aveugle, séries, réflexes, finales) n'est plus rognée : sa hauteur est calculée à la largeur de l'échiquier, et l'échiquier cède la place nécessaire si le texte passe à la ligne.
- Calcul à l'aveugle : « Après ces coups : trait aux Blancs », plus court.
- Entraînements : les finales contre l'Ordinateur ne sont plus dans une section dépliable ; elles sont toutes visibles, en fin de liste, groupées « À gagner » puis « À tenir (nulle) ».

## ChessDuck 3.3.1 — des finales qui expliquent

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Finales contre l'Ordinateur : chaque finale a sa méthode en une phrase (par exemple, roi et pion : « Le roi passe devant son pion et gagne une case clé, deux rangées devant lui… »). Elle s'affiche au départ de chaque position.
- Après un coup qui laisse échapper le gain (ou perd la nulle), l'explication dit quelle réponse de l'Ordinateur le punit, quel coup il fallait jouer, et rappelle la méthode.
- « Reprendre le coup » remplace « Rejouer » : la position revient juste avant l'erreur, avec le bon coup en flèche verte, pour s'entraîner (le score de la série ne change pas).

## ChessDuck 3.3.0 — des idées et des pièges pour chaque leçon

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Les 158 leçons qui n'en avaient pas reçoivent leurs sections « Les idées » et « Les pièges » : les bases (16), la tactique et la méthode (61), la stratégie (18), les finales (55) et les parties modèles (8). Les 211 leçons ont désormais une fiche complète.
- Chaque leçon a deux ou trois idées clés et deux pièges : « Pour vous » (ce qu'on peut exploiter chez l'adversaire) et « Contre vous » (l'erreur à ne pas commettre).
- Ces sections s'affichent dans la fiche de la leçon (icône « i ») et sont lues par la voix à l'ouverture de la leçon, comme pour les ouvertures.

## ChessDuck 3.2.6 — des réflexes qui ont du sens

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- « Quelle est la menace ? » : la menace se lit désormais sur la position affichée. Avant, elle était tirée de la suite de la solution : dans une enfilade, l'exercice désignait la pièce de derrière (une tour) alors que c'est celle de devant (la dame) qui est attaquée.
  - Une pièce est menacée si l'adversaire peut la prendre en gagnant du matériel : elle n'est pas protégée, ou elle vaut plus que la pièce qui la prend. L'explication le précise.
  - Fourchette : chacune des pièces attaquées est une bonne réponse, et toutes sont montrées.
  - L'Ordinateur confirme chaque exercice : si l'adversaire a mieux à faire (un mat, une attaque plus forte), l'exercice est écarté.
- « Calcul à l'aveugle » : quand le coup attendu mate, tout autre mat est accepté (par exemple Rxh7# au lieu de Qxh7#).
- Vérification sur un échantillon de la base (1 200 exercices générés), avec Stockfish : les exercices de prise étaient déjà filtrés par l'Ordinateur ; les menaces et le calcul à l'aveugle sont corrigés comme ci-dessus.

## ChessDuck 3.2.5 — la leçon reprend après « À vous »

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Mode Guidé : après le bon coup à un arrêt « À vous : que jouez-vous ici ? », la leçon reprend d'elle-même. Elle laisse le temps de lire (ou d'écouter) « Bravo » et le commentaire du coup, puis enchaîne la réponse adverse et la suite, jusqu'au prochain arrêt. Le bouton « Pause » l'arrête à tout moment.

## ChessDuck 3.2.4 — positions de départ corrigées

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Trois leçons partaient d'une position impossible : le roi du camp qui n'avait pas le trait était déjà en échec.
  - « L'échec et mat » : la dame a1 attaquait déjà le roi h8 par la grande diagonale (d'où un « Kg6+ » incompréhensible). La dame part maintenant de b1 : 1. Kg6 Kg8 2. Qb8#.
  - « Le mat avec la dame » : la dame d1 et le roi d5 étaient sur la même colonne. Le roi noir part maintenant de c5.
  - « Le pion passé protégé » : le pion d5 attaquait le roi c6. Le roi noir part maintenant de d6.
- Un nouveau test vérifie toutes les positions de toutes les leçons (lignes principales et variantes) : aucun camp sans le trait ne peut être en échec.

## ChessDuck 3.2.3 — une fiche pour chaque leçon

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- L'icône « i » (fiche de la leçon) est disponible sur toutes les leçons, plus seulement sur les ouvertures et quelques finales. La fiche réunit l'objectif, la règle « À retenir », le thème tactique (son but et un conseil), les moments clés (les coups à trouver aux arrêts « À vous », avec leur explication) et, quand la leçon en a, les conditions, les idées et les pièges.
- Parties modèles : le mode « Devinez le coup » s'appelle « Deviner » dans la rangée des modes, pour qu'elle tienne sur une ligne avec la fiche et « Jouer cette position » ; en très grande police, les modes occupent toute une rangée et les icônes passent dessous, pour qu'aucun libellé ne soit coupé.

## ChessDuck 3.2.2 — des exercices qui disent quoi chercher

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Validation et révision sur problèmes : l'énoncé ne dit plus « les Blancs appliquent l'idée de la leçon ». Il nomme ce qu'il faut chercher (par exemple « À chercher : fourchette (attaquer deux cibles à la fois avec une seule pièce) ») et rappelle la règle « À retenir » de la leçon.
- Mode Guidé : quand la voix lit (l'objectif à l'ouverture de la leçon, ou un commentaire), le bouton bleu affiche « Pause » et la suspend ; « Lecture » reprend la phrase là où elle s'était arrêtée, puis déroule les coups.

## ChessDuck 3.2.1 — le Défi n'est plus sauté

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Après la question de fin de leçon (« Quel est le plan ? »), le bouton principal est « Passer au Défi » tant que le Défi de la leçon n'est pas réussi ; « Leçon suivante » n'apparaît qu'ensuite.
- Même parcours pour les finales lancées depuis le mode Guidé (« Gagnez-la », « Tenez la nulle ») : à la fin de la série, « Passer au Défi ».

## ChessDuck 3.2.0 — une Académie alignée sur le reste de l'application

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Mauvais coups : la même réfutation que dans les Problèmes, partout dans l'Académie. Le coup est joué, l'Ordinateur répond (flèche rouge) en nommant la conséquence (mat, pièce perdue, avantage rendu), un compte à rebours annule le coup, la pause le garde affiché, et la pièce revient en glissant.
- Séries de problèmes (validation, révision, « Le seul coup », placement, examen) : on réessaie après une erreur au lieu de voir tout de suite la solution. Un problème résolu après une erreur, ou avec la flèche de l'indice, ne compte pas : les seuils (4 sur 5, 8 sur 10) restent exigeants. « Solution » après une erreur, et ‹ › pour revoir les coups.
- Finales contre l'Ordinateur :
  - indice en deux temps (la pièce, puis la flèche) ;
  - sur un coup perdant, l'Ordinateur joue sa réponse (flèche rouge) ;
  - la jauge d'évaluation apparaît une fois la position terminée ;
  - « Rejouer » pour s'entraîner sur une position manquée, sans changer le score ;
  - bandeau de l'Ordinateur au-dessus de l'échiquier (réflexion, trait).
- En-tête de séance commun (Académie, Revue de partie, Vision des cases) : flèche de retour à gauche, titre jamais coupé, retournement de l'échiquier disponible pendant les séances. Les boutons « Arrêter » en double disparaissent.
- Pastilles d'avancement (réussi, manqué, en cours) dans les séries, les réflexes et les finales, sur la ligne au-dessus de l'échiquier.
- Couleurs unifiées : vert pour l'indice et la bonne case, rouge pour l'erreur et la menace, bleu pour les démonstrations de la leçon (les flèches du mode Guidé ne sont plus rouges).
- Préparation des exercices : un écran d'attente au lieu de l'échiquier de la leçon, et un message clair si aucun exercice n'est disponible.
- Fiche de la leçon (objectif, à retenir, conditions, idées, pièges) dans un tiroir, ouvert par l'icône « i » : la carte de départ ne montre plus que le résumé et la règle.
- Parties modèles : « Jouer cette position » à chaque coup (icône manette), avec le choix de jouer les Blancs ou les Noirs. « Devinez le coup » affiche le numéro du coup et montre brièvement la réfutation d'un coup à 0 point.
- Mode Guidé : indice pendant un arrêt « À vous » (la pièce à jouer) ; en fin de leçon, « Passer au Défi » remplace Lecture ; l'Ordinateur affiche « analyse votre coup… » pendant le jugement.
- Fin de Défi : « Revoir » relit la leçon en mode Guidé. La variante jouée s'affiche dans le titre du Défi.
- Haut-parleur : barré quand la voix est coupée ; un second toucher l'active, sans effacer le commentaire.
- Entraînements : les tests (placement, examen) remontent sous les priorités ; les 11 finales sont regroupées dans une section dépliable (Gagner / Tenir la nulle) ; le compteur du bouton inclut les priorités.
- Catalogue : raccourci « Aller à la leçon en cours », compteurs sur les filtres Statut et Palier, mention « Validation restante » (aussi sur la carte de la leçon : « À valider »).
- Tablette : en mode Guidé, le tableau complet des coups s'affiche à droite.
- Retours haptiques en finale et dans les réflexes, et au toucher d'une case.

## ChessDuck 3.1.2 — leçons numérotées dans l'ordre

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Les leçons sont numérotées dans l'ordre du catalogue : la n° 1 est « L'échiquier et la notation », la première de la liste. La progression déjà enregistrée est conservée.
- La recherche par numéro dans le catalogue suit cette nouvelle numérotation.
- À la fin d'un Défi (ou d'une validation, ou de la question de fin de leçon), « Leçon suivante » ouvre la leçon suivante en mode Guidé. La flèche de l'en-tête garde le mode en cours.

## ChessDuck 3.1.1 — l'échiquier de l'Académie au plus grand

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Plus rien entre la leçon et l'échiquier : les puces « Conseillée », « Reprendre » et « Révisions du jour » passent dans le tiroir « Entraînements », en tête, sous « À faire maintenant ». Le bouton de l'en-tête affiche leur nombre (« Entraînements · 1 »).
- La leçon conseillée est aussi marquée « Conseillée » dans le catalogue.
- Pendant les révisions et le répertoire, la progression (« Révision 1 sur 3 ») s'affiche dans l'en-tête de séance, avec la croix pour quitter.
- La carte de la leçon tient sur deux lignes compactes : le titre, puis la catégorie, le palier, la progression et les étoiles en petit.
- En mode Défi, l'échiquier occupe désormais toute la largeur d'un téléphone.

## ChessDuck 3.1.0 — une Académie plus lisible, un échiquier plus grand

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Échiquier plus grand sur téléphone :
  - le bouton « Entraînements » passe dans l'en-tête de l'application, à côté de Paramètres ; avec une très grande police, il devient une icône ;
  - la puce « Palier », qui doublait ce bouton, disparaît ;
  - le bandeau (révisions du jour, leçon à reprendre, leçon conseillée) tient sur une ligne et ne s'affiche que s'il a quelque chose à proposer ;
  - les variantes d'une ouverture se choisissent dans un menu « Variantes », à côté de Guidé / Défi.
- Pendant une séance (validation sur 5 problèmes, révision, finale de validation, question de fin de leçon, entraînement), un en-tête dit clairement ce que montre l'échiquier. La croix permet de revenir à la leçon ; les modes Guidé / Défi et le retournement de l'échiquier, inopérants pendant la séance, n'y sont plus affichés.
- Tiroir « Entraînements » :
  - l'exercice en cours est surligné (« En cours ») ;
  - les finales et les tests déjà validés sont cochés ;
  - vos 3 priorités ont leurs propres cartes ;
  - le bouton de l'en-tête est surligné quand une séance est en cours.
- Pendant une révision ou un répertoire, la progression n'est plus une fausse puce cliquable ; seul « Quitter » est un bouton.
- QCM : après la réponse, votre choix reste affiché, en vert s'il est juste, en rouge sinon, avec la bonne réponse.
- Finales jouables : le bouton Indice est désactivé pendant que l'Ordinateur cherche (« Recherche… ») et ne lance jamais deux recherches à la fois.
- Calcul à l'aveugle :
  - le bouton Arrêter reste dans la zone du pouce ;
  - la case de départ touchée est écrite dans la carte ;
  - si la voix est coupée, « Réécouter » explique comment l'activer.
- Catalogue des leçons :
  - les filtres se replient derrière un bouton « Filtres » : les leçons apparaissent dès l'ouverture ;
  - le palier, le statut et la catégorie sont gardés d'une ouverture à l'autre ;
  - choisir une catégorie ne change plus la leçon ouverte.
- Mode Guidé et Défi :
  - un second toucher sur le haut-parleur referme l'aide sur la voix ;
  - un Défi réussi garde toute la place pour son bilan ;
  - « Jouer cette position » n'apparaît plus sur une position de mat ou de pat.
- Les paliers s'écrivent « 2 · Débutant » ; TalkBack les lit naturellement.

## ChessDuck 3.0.0 — l'Académie complète : 211 leçons et un parcours personnalisé

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Académie : 211 leçons sur 5 paliers (contre 114). Chacune est vérifiée par l'Ordinateur : aucun coup perdant n'est enseigné.
  - Nouvelle catégorie « Les bases » (palier 1) : l'échiquier et la notation, la marche de chaque pièce, la valeur des pièces, l'échec, le mat, le pat, les nulles, le roque, la prise en passant, la promotion et la guerre des pions.
  - Méthode de réflexion : échecs, prises et menaces, le contrôle anti-gaffe, compter attaquants et défenseurs, les coups candidats, ce que veut l'adversaire ; trois exercices d'initiation.
  - Les 37 ouvertures sont enrichies : trois idées clés, un piège pour vous et un contre vous, deux déviations fréquentes de l'adversaire et un QCM sur le plan. On y explique aussi pourquoi les Noirs acceptent un déséquilibre dans l'Alekhine, la Dragon, la Pirc et la scandinave.
  - 16 nouvelles ouvertures : catalane, Réti, siciliennes Taimanov, Kan, Rossolimo, Grand Prix et gambit Smith-Morra, Caro-Kann d'avance et attaque Panov, françaises d'échange et Rubinstein, italienne lente, attaque Marshall, gambits Budapest et Benko, Trompowsky.
  - Tactique : contre-exemples (le coup naturel puni), pion empoisonné, surcharge, moulin, desperado, échec perpétuel, pat sauveur, sacrifice grec, double sacrifice des fous, et les mats de Greco, Damiano, Lolli, Pillsbury et Morphy.
  - Nouvelle catégorie « Stratégie » (18 leçons) : centre de pions, chaînes, pion isolé, pions pendants, doublés, arriérés, majorité, avant-poste, bon et mauvais fou, paire de fous, colonne ouverte, tour en septième, doublement des tours, attaque de minorité, tempête de pions, sacrifice de qualité, prophylaxie et pire pièce. Chaque leçon se termine par un QCM « Qui est mieux ? » ou « Quel est le plan ? ».
  - 17 finales : étude de Saavedra, défense Vancura, défense du côté long, règle de Tarrasch, dame contre pion en septième, deux tours contre dame, fous de couleurs opposées, pions passés éloignés et protégés, zugzwang mutuel, tour contre fou, cavalier contre pion, opposition… Trois nouvelles finales se jouent contre l'Ordinateur : dame contre pion, dame contre pion fou (le pat) et défense Vancura.
  - 7 parties modèles commentées : l'Immortelle d'Anderssen, Rubinstein, Lasker, Nimzowitsch, Alekhine, Botvinnik et Capablanca. Les coups historiques imprécis sont signalés.
- Revue de partie : après une gaffe (pièce en prise, mat manqué ou permis, roque ou développement en retard), le bouton « Travailler ce point dans l'Académie » ouvre la leçon et 5 exercices adaptés.
- Bilan : le tiroir « Entraînements » présente vos 3 priorités du moment, tirées de vos parties analysées et de vos difficultés dans l'Académie.
- Notation : nouvel exercice facultatif « Vision des cases » (couleur d'une case, échiquier masqué ; toucher une case ; chemin du cavalier), sans chronomètre et utilisable avec TalkBack.
- Exercice avancé « Calcul à l'aveugle » (palier 5) : deux coups sont annoncés à voix haute et écrits, puis vous trouvez le coup suivant sans les voir joués. Un bouton réaffiche l'échiquier.
- Les libellés « Guidé » et « Devinez le coup » passent sur deux lignes au lieu d'être coupés avec une grande taille de police.

## ChessDuck 2.7.4 — un bandeau lisible avec une grande police

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Académie : les puces du bandeau (« Conseillée : … », « Continuer : … », « Révisions du jour », « Entraînements ») passent à la ligne au lieu de défiler les unes sous les autres. Avec une grande taille de police, la puce « Conseillée » ne chevauche plus « Entraînements », qui reste entièrement visible. Un titre plus large que l'écran est abrégé par des points de suspension.

## ChessDuck 2.7.3 — des entraînements clairement annoncés

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Le bouton au chapeau de l'Académie s'appelle désormais « Entraînements ».
- Pendant un entraînement indépendant de la leçon (réflexes, finale lancée depuis le tiroir, « Le seul coup », test de placement, examen), l'en-tête annonce « Entraînement : … » à la place de la leçon en cours et des modes Guidé et Défi. Un bouton « Revenir à la leçon » permet d'en sortir. L'échiquier, un peu plus grand, ne se confond plus avec la leçon.

## ChessDuck 2.7.2 — un bon coup différent n'est plus remplacé en silence

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Défi et arrêts « À vous » : un coup aussi bon que celui de la leçon, mais différent (par exemple Rh5+ au lieu de Ra5+ dans le mat avec deux tours), est félicité, sans erreur ni étoile perdue. Puis la position revient : une flèche verte montre le coup de la leçon, et c'est vous qui le jouez. L'application ne joue plus le coup de la leçon à votre place.

## ChessDuck 2.7.1 — le Défi suit la méthode de la leçon

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Défi corrigé : quand la leçon mène au mat, un autre coup n'est accepté que s'il mate au moins aussi vite. Par exemple, dans le mat avec deux tours, Rh2 garde le mat mais ne suit pas l'escalier. Le coup est montré, compté comme une erreur (« garde le mat, mais plus lentement »), puis la position revient sans que la leçon joue son propre coup.
- Même règle en tactique : gagner une dame au lieu de mater n'est plus considéré comme « aussi bon ».
- Même règle dans les arrêts « À vous » du mode Guidé.

## ChessDuck 2.7.0 — s'entraîner pour de vrai (Académie, étape 2)

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- **Finales jouées contre l'Ordinateur** : « Gagnez » ou « Tenez la nulle » sur 8 familles (roi et dame, deux tours, roi et tour, deux fous, roi et pion, Lucena, Philidor, pion tour). Les positions sont tirées au hasard pour apprendre la méthode, pas une position. L'Ordinateur défend à pleine force, et la partie s'arrête si le gain s'envole, en cas de pat ou au-delà du nombre de coups.
- **Mats élémentaires refaits depuis le centre** : la dame, les deux tours, la tour et les deux fous repoussent le roi du milieu de l'échiquier jusqu'au mat. Les finales théoriques affichent leurs **conditions** (Lucena, Philidor, roi en sixième, opposition, pion tour).
- **Validation des finales** : 3 positions réussies sur 5 contre l'Ordinateur, après un Défi à au moins 2 étoiles.
- **Réflexes** : « Quelle est la menace ? » (touchez la pièce menacée), « Le seul coup » (trouvez l'unique défense) et « Puis-je prendre ? » (oui ou non, avec l'explication du bilan matériel).
- **Révisions espacées** : une leçon maîtrisée revient après 1, 7, 30 puis 90 jours ; une révision ratée la fait revenir le lendemain. Le bandeau de l'Académie affiche les « Révisions du jour », la leçon à « Continuer » et la leçon « Conseillée » pour votre palier.
- **Cursus en 5 paliers** (Les bases, Débutant, Novice, Club, Confirmé), avec toutes les leçons classées. Le catalogue se filtre par palier et par « À revoir », et indique la date de la prochaine révision.
- **Tests** : test de placement facultatif (12 positions) et examen de palier (10 positions mélangées, réussite à 8), tous deux dans le tiroir « Entraînements ».
- **Ouvertures** : variantes (« Si 3… Nf6 »), fiche des idées et des pièges, QCM final « Quel est le plan ? » (pilote : la partie italienne), et « Réviser mon répertoire » avec les Blancs ou les Noirs.
- **Partie modèle** : « La partie de l'Opéra » de Morphy (Paris, 1858), commentée coup par coup, et le mode « Devinez le coup » (3 points pour le coup du maître, 1 ou 2 pour un coup presque aussi bon).
- **Défi corrigé** : un coup différent de celui de la leçon s'affiche d'abord là où vous l'avez joué, pendant que l'Ordinateur le juge. S'il est aussi bon, la leçon reprend ensuite son propre coup ; sinon, la réfutation part de votre coup.
- **Voix** : la fiche complète de la leçon est lue à l'ouverture, ainsi que les variantes ; la voix se tait pendant les exercices. Morphy, Brunswick et Isouard sont bien prononcés.

## ChessDuck 2.6.0 — une Académie qui fait vraiment apprendre (étape 1)

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- **Guidé actif** : aux moments clés, la leçon s'arrête et vous demande « À vous : que jouez-vous ici ? ». Proposez votre coup sur l'échiquier, ou touchez Lecture pour découvrir celui de la leçon. La lecture vocale pose aussi la question.
- **La règle d'abord** : l'objectif de chaque leçon affiche dès le départ la phrase « À retenir » et, pour Lucena et Philidor, les conditions qui permettent de reconnaître la position. Des repères colorés (cases et flèches) éclairent certaines positions.
- **Défi plus juste** : un coup aussi bon que celui de la leçon est accepté (« Bon coup aussi ! La leçon poursuit avec… »), au lieu d'être refusé. Un mauvais coup est réfuté par la réponse de l'Ordinateur (flèche rouge), puis « Reprendre au coup manqué » vous ramène juste avant l'erreur.
- **Défi mieux commenté** : les coups adverses sont expliqués, et le dernier commentaire reste affiché à la fin avec sa règle « À retenir ». Une fois le Défi réussi, la leçon se relit coup par coup, et revenir au Guidé garde la position.
- **Étoiles** : ★★★ sans erreur ni indice, ★★ avec au plus une erreur ou le premier indice, ★ sinon. Elles s'affichent dans l'en-tête et dans le catalogue.
- **Maîtrise validée** : pour les leçons de tactique et de finale liées à un thème, la maîtrise se gagne sur 5 problèmes inédits du même thème, tirés des 150 000 problèmes embarqués (4 réussis sur 5, sans l'indice qui montre le coup), après un Défi à au moins 2 étoiles.
- **Passerelles** : en fin de leçon, « S'entraîner » ouvre l'onglet Problèmes sur le thème de la leçon, et « Jouer cette position » lance une partie d'entraînement contre l'Ordinateur depuis la position atteinte. Cette partie ne change pas l'ELO, et sa revue repart de la bonne position.
- **Leçons corrigées** : « Calculer un mat en trois coups » (le pion g7, pas un cavalier), « L'échec double » (le second cavalier noir présenté dès le début), « Le contournement » (trait expliqué, ligne menée jusqu'à la promotion) et « Le pat sauveur » (vraie défense contre les échecs de la dame).
- **Voix** : Réti, Philidor, Anastasie, Nimzo et Scandinave sont mieux prononcés.
- **Sous le capot** : identifiants de leçons figés (votre progression est conservée), base de données v4 avec migration automatique, et vérification automatique des 113 leçons (coups vérifiés par l'Ordinateur, cohérence entre le texte et l'échiquier).

## ChessDuck 2.5.2 — échecs et prises visibles dans les images

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Images exportées : la case du roi en échec ou maté est rougie, sur l'énoncé comme sur chaque étape de la solution (après votre coup et après la réponse adverse).
- Nouveau badge « x » sur la case d'arrivée de chaque prise (votre coup et la réponse adverse), ajouté à la légende : « x prise ».

## ChessDuck 2.5.1 — couleurs d'échiquier partout

APK Android signé, 100 % hors-ligne (Android 8.0 et plus ; arm64-v8a, armeabi-v7a, x86_64).

- Images exportées (problème et solution) : les cases reprennent les couleurs d'échiquier choisies dans les Paramètres.
- Lettres des colonnes de nouveau en bas à droite des cases, sur tous les échiquiers.
- Paramètres réorganisés :
  - « Mode d'affichage » ;
  - « Aides pendant le jeu » ;
  - « Audio, voix et retours » ;
  - « Joueurs » ;
  - « Couleurs de l'application », suivie de « Couleurs de l'échiquier » ;
  - « À propos » ;
  - « Réinitialisation » tout en bas.
- Couleurs de l'échiquier : les quatre choix ont la même hauteur.
- Aperçus des palettes : chacun montre ses propres couleurs. Les pastilles reprises d'« Ardoise & Or » et les cases d'échiquier ont disparu.

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
