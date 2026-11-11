package com.ivanherboch.chessduck.content

// Fichier généré par scripts/gen_lessons.py — modifier scripts/lessons/*.txt.

internal val ENDGAME_LESSONS = """
# Le mat avec la dame | Enfermer le roi sur la bande, approcher son propre roi, puis mater au contact. | w
FEN: 5k2/8/4K3/8/8/8/8/1Q6 w - - 0 1
Qb7 : La dame coupe la 7e rangée : le roi noir est prisonnier de la dernière.
Kg8 : Il ne lui reste que deux cases.
Kf6 : Le roi blanc s'approche pour soutenir sa dame, en laissant toujours un coup au roi noir : pas de pat !
Kh8 : Seul coup.
Qg7# : La dame mate au contact, protégée par son roi.

# Le mat avec deux tours : l'escalier | Les deux tours se relaient pour repousser le roi rangée après rangée. | w
FEN: 8/8/2k5/7R/8/8/8/K5R1 w - - 0 1
Rg6+ : Une tour garde la 5e rangée ; l'autre donne échec sur la 6e : le roi doit reculer.
Kd7 : Le roi recule d'une rangée.
Rh7+ : À l'autre tour de monter d'une marche.
Ke8 : Le roi est repoussé sur la dernière rangée.
Rg8# : Dernière marche : échec et mat. Les tours avancent à tour de rôle, comme sur un escalier.

# Le mat avec une tour | Roi et tour coopèrent : quand les rois se font face, la tour mate sur la bande. | w
FEN: 3k4/8/4K3/8/8/8/8/7R w - - 0 1
Rc1 : Coup d'attente : la tour enlève la colonne c au roi noir, qui doit venir en face du roi blanc.
Ke8 : Seul coup : les rois sont face à face.
Rc8# : Les rois en opposition, la tour mate sur la dernière rangée.

# Le mat avec les deux fous | Les fous travaillent côte à côte pour enfermer le roi dans un coin. | w
FEN: 6k1/8/5K2/8/8/8/3BB3/8 w - - 0 1
Bb4 : Le fou contrôle f8 : le roi noir est confiné près du coin.
Kh7 : Il ne lui reste que g8, h7 et h8.
Kf7 : Le roi blanc s'installe en f7 et contrôle g6, g7 et g8.
Kh8 : Seul coup.
Bd2 : Coup d'attente précis : le fou change de diagonale et surveille h6, sans faire pat.
Kh7 : Seul coup.
Bd3+ : Le fou de cases blanches donne échec…
Kh8 : Le roi retourne dans le coin.
Bc3# : …et le fou de cases noires mate sur la grande diagonale.

# Attention au pat ! | Avec une dame d'avance, le seul danger est le pat : laissez toujours un coup au roi adverse. | w
FEN: 7k/8/5K2/8/8/8/8/6Q1 w - - 0 1
Kf7 : Surtout pas la dame en g6 : le roi noir n'aurait plus aucun coup sans être en échec. Pat, partie nulle ! Le roi s'approche en laissant la case h7.
Kh7 : Seul coup.
Qg7# : Mat. Avant chaque coup gagnant, demandez-vous : l'adversaire a-t-il encore un coup légal ?

# La règle du carré : le pion gagne la course | Tracez le carré du pion : si le roi adverse ne peut pas y entrer, le pion va à dame. | w
FEN: 8/8/8/8/P6k/8/8/7K w - - 0 1
a5 : Le carré du pion a4 allait de a4 à e8. Le roi noir, en h4, était dehors : on pousse !
Kg5 : Le roi court en diagonale…
a6 : Le carré rétrécit à chaque pas du pion.
Kf6 : …mais le roi reste toujours en dehors.
a7 : Plus qu'un pas.
Ke7 : Trop tard.
a8=Q : Promotion ! Inutile de compter coup par coup : le carré suffit.

# La règle du carré : le roi rattrape le pion | Si le roi entre dans le carré du pion, il arrive à temps pour l'arrêter. | b
FEN: 8/8/8/8/P4k2/8/8/7K b - - 0 1
Ke5 : Le carré du pion a4 va de a4 à e8. En jouant en e5, le roi y entre : le pion sera rattrapé.
a5 : Le pion avance, le carré rétrécit.
Kd6 : Le roi reste dans le carré en se rapprochant en diagonale.
a6 : Encore un pas.
Kc7 : Toujours dans le carré.
a7 : Dernier espoir des Blancs.
Kb7 : Le roi attaque le pion et contrôle la case de promotion.
a8=Q+ : La promotion ne sauve rien.
Kxa8 : Le roi capture la dame : partie nulle.

# Le roi en 6e rangée devant son pion | La position gagnante de base : roi devant le pion, sur la 6e rangée. | w
FEN: 4k3/8/4K3/4P3/8/8/8/8 w - - 0 1
Kd6 : Roi en 6e rangée devant son pion : c'est gagné, quel que soit le trait. Le roi s'écarte pour laisser passer le pion.
Kd8 : Le roi noir prend l'opposition, mais cela ne suffit pas ici.
e6 : Le pion avance sous la protection de son roi.
Ke8 : Le roi noir bloque la case de promotion.
e7 : Le pion atteint la 7e rangée SANS donner échec : signe de victoire.
Kf7 : Seul coup : le roi noir doit céder le passage.
Kd7 : Le roi blanc contrôle la case de promotion.
Kg6 : Les Noirs ne peuvent plus rien.
e8=Q+ : Promotion !

# Le roi d'abord, le pion ensuite | En finale roi et pion, on avance d'abord le roi pour conquérir les cases clés. | w
FEN: 4k3/8/8/8/8/8/4P3/4K3 w - - 0 1
Kd2 : Ne poussez pas le pion ! C'est le roi qui doit ouvrir la route.
Kd7 : Le roi noir vient à sa rencontre.
Kd3 : Le roi continue d'avancer devant son pion.
Kc6 : Les Noirs cherchent le meilleur poste de défense.
Ke4 : Le roi est deux cases devant son pion : les cases clés sont à portée.
Kd6 : Le roi noir tente de barrer le passage.
Kf5 : Contournement par le côté libre.
Ke7 : Les Noirs reviennent en face.
Ke5 : Opposition, roi devant le pion.
Kd8 : Les Noirs doivent céder.
Ke6 : Roi en 6e rangée devant son pion : la position gagnante connue.
Kc7 : Le roi noir ne peut plus contester.
e4 : Maintenant seulement, le pion avance en toute sécurité.

# Le contournement | Quand le roi adverse doit céder le passage, on avance son roi du côté libre. | w
FEN: 8/8/8/4k3/8/4K3/4P3/8 b - - 0 1
Kd5 : Les Noirs sont au trait : ils doivent abandonner l'opposition.
Kf4 : Contournement : le roi blanc passe du côté que le roi noir vient de quitter.
Ke6 : Le roi noir revient en face.
Ke4 : Les Blancs prennent l'opposition, roi DEVANT le pion.
Kd6 : Les Noirs doivent encore céder.
Kf5 : Nouveau contournement : le roi gagne une rangée.
Kd5 : Le roi noir reste à côté.
e4+ : Le pion peut enfin avancer, soutenu par son roi.
Kc4 : Les Noirs tentent une attaque par derrière.
e5 : Le pion est plus rapide.
Kc5 : Poursuite vaine.
e6 : Encore un pas.
Kc6 : Le roi noir est trop loin de la case de promotion.
Kf6 : Précision finale : le roi d'abord ! Il contrôle e7 et f7 ; pousser le pion tout de suite laisserait le roi noir revenir en d7 et annuler.

# L'opposition pour annuler | Le défenseur recule toujours tout droit et reprend l'opposition : le pion ne passe pas. | b
FEN: 8/8/4k3/8/3KP3/8/8/8 b - - 0 1
Kd6 : Opposition : le roi noir se place en face du roi blanc, une case entre eux. Le roi blanc ne passera pas.
e5+ : Le pion avance avec échec.
Ke6 : Le roi bloque le pion.
Ke4 : Le roi blanc soutient son pion.
Ke7 : Recul tout droit : la règle d'or du défenseur.
Kd5 : Le roi blanc avance…
Kd7 : …et le roi noir reprend aussitôt l'opposition.
e6+ : Nouvel échec du pion.
Ke7 : On bloque de nouveau.
Ke5 : Les Blancs protègent leur pion.
Ke8 : Moment critique : il faut reculer TOUT DROIT. Un pas de côté perdrait la partie.
Kd6 : Le roi blanc s'approche.
Kd8 : Opposition !
e7+ : Le pion arrive en 7e AVEC échec : signe de nulle.
Ke8 : Seul coup.
Ke6 : Les Blancs doivent protéger leur pion… et c'est pat : partie nulle.

# Le pion tour : nulle dans le coin | Avec un pion de la colonne a ou h, le roi défenseur dans le coin ne peut pas être délogé. | w
FEN: 8/8/8/8/8/pk6/8/K7 w - - 0 1
Kb1 : Le roi reste collé au coin de promotion : il fait la navette entre a1 et b1.
a2+ : Le pion avance avec échec.
Ka1 : Le roi se blottit sur la case de promotion.
Ka3 : Les Noirs doivent défendre leur pion… et c'est pat. Le pion tour est le moins dangereux des pions.

# Le trébuchet | Deux pions bloqués, deux rois : dans cette position, celui qui doit jouer perd. | w
FEN: 8/8/5K2/3p4/3P4/1k6/8/8 w - - 0 1
Ke6 : Le bon chemin : attaquer le pion d5 par l'arrière. Venir tout de suite en e5 serait une faute : après Kc4, ce serait aux Blancs de jouer !
Kc4 : Le roi noir attaque d4.
Ke5 : Le roi blanc défend d4 et attaque d5, et c'est aux Noirs de jouer : trébuchet !
Kc3 : Tout coup du roi noir abandonne le pion d5.
Kxd5 : Le pion tombe et la partie avec. Calculez toujours qui sera au trait dans la position clé.

# La percée de pions | Trois pions face à trois : deux sacrifices pour en faire passer un. | w
FEN: 6k1/ppp5/8/PPP5/8/8/8/6K1 w - - 0 1
b6 : Le pion du milieu se sacrifie !
axb6 : Si l'autre pion prenait, on jouerait a6 puis c6 avec la même idée.
c6 : Deuxième sacrifice : le pion b7 est dévié.
bxc6 : Forcé, sinon le pion c prend en b7.
a6 : Le pion a est passé : aucun pion noir ne peut plus l'arrêter, et le roi est trop loin.
c5 : Les pions noirs sont trop lents.
a7 : Plus qu'un pas.
c4 : Rien à faire.
a8=Q+ : Promotion avec échec. La percée : donner deux pions pour une dame.

# L'étude de Réti | En diagonale, le roi poursuit deux objectifs à la fois. | w
FEN: 7K/8/k1P5/7p/8/8/8/8 w - - 0 1
Kg7 : Le roi blanc semble trop loin de tout. Il part en diagonale : un trajet, deux objectifs.
h4 : Le pion noir file vers la promotion.
Kf6 : Toujours la diagonale : le roi se rapproche des deux pions à la fois.
Kb6 : Les Noirs perdent un temps pour attaquer le pion c6.
Ke5 : Double menace : rattraper le pion h par f4, ou soutenir le pion c par d6.
h3 : Les Noirs choisissent la course.
Kd6 : Le roi protège la marche de son pion.
h2 : Chacun pousse.
c7 : Le pion blanc arrive aussi.
h1=Q : Les Noirs font dame…
c8=Q : …les Blancs aussi : nulle ! Pour un roi, la diagonale est aussi courte que la ligne droite.

# Finale de pions : le zugzwang | Obliger l'adversaire à jouer quand tout coup lui fait perdre un pion. | b
FEN: 8/4k1p1/7p/4KP1P/8/8/8/8 w - - 3 52
e5f4 : Le roi blanc recule pour défendre son pion f5.
e7f6 : Le roi noir attaque le pion : le roi blanc est attaché à sa défense.
f4g4 : Seul coup pour garder le pion.
f6e5 : Zugzwang : les Blancs doivent jouer, et tout coup de roi abandonne le pion f5.

# Dame contre pion en 7e : l'escalier | La dame force le roi devant son pion, le temps de rapprocher son propre roi. | w
FEN: 8/8/8/8/1Q6/8/3pk3/7K w - - 0 1
Qe4+ : La dame s'approche par un échec…
Kd1 : …et le roi doit se placer devant son pion.
Kg2 : Le pion étant bloqué, le roi blanc gagne un pas.
Kc1 : Le roi ressort pour menacer la promotion.
Qc4+ : On recommence : échec…
Kb2 : Le roi s'écarte.
Qd3 : …puis attaque du pion.
Kc1 : Le roi revient le défendre.
Qc3+ : Échec : le roi est de nouveau forcé devant son pion.
Kd1 : Seul coup pour garder le pion.
Kf3 : Le roi blanc approche encore d'un pas, en évitant f2 qui ferait pat. On répète la manœuvre jusqu'à ce que le roi soutienne sa dame : le pion finit par tomber. Cette méthode gagne contre un pion central ou cavalier.

# Dame contre pion tour en 7e : le pat sauveur | Face au pion a ou h, le roi défenseur se cache dans le coin et menace le pat. | b
FEN: 8/8/8/8/1Q6/8/pk6/7K b - - 0 1
Ka1 : Le roi se réfugie dans le coin, devant son pion…
Qb3 : …et si la dame s'approche sans donner échec, c'est pat ! Les Blancs n'ont jamais le temps d'amener leur roi : partie nulle.

# La position de Lucena : construire le pont | La technique gagnante quand le roi est coincé devant son pion en 7e rangée. | w
FEN: 1K6/1P1k4/8/8/8/8/r7/2R5 w - - 0 1
Rd1+ : Le roi blanc est enfermé devant son pion. On commence par repousser le roi noir d'une colonne.
Ke7 : Le roi s'éloigne.
Rd4 : Le coup clé : la tour monte en 4e rangée pour préparer le « pont ».
Ra1 : Les Noirs ne peuvent qu'attendre.
Kc7 : Le roi sort de sa cachette.
Rc1+ : Les échecs commencent…
Kb6 : Le roi descend en zigzag.
Rb1+ : Encore un échec.
Kc6 : On menace de promouvoir.
Rc1+ : Toujours échec.
Kb5 : Le roi se rapproche de la tour noire.
Rb1+ : Dernier échec…
Rb4 : Le pont ! La tour s'interpose : plus d'échecs possibles, le pion va à dame.

# La défense de Philidor | La méthode de nulle essentielle : tour sur la 6e rangée, puis échecs par derrière. | b
FEN: 4k3/7R/r7/3KP3/8/8/8/8 w - - 0 1
Rb7 : Les Blancs attendent : ils voudraient amener leur roi sur la 6e rangée.
Rg6 : La tour reste sur la 6e rangée et l'interdit au roi blanc.
e6 : Le pion avance : le roi blanc ne pourra plus s'abriter derrière lui.
Rg1 : Le signal ! Dès que le pion atteint la 6e rangée, la tour file tout en bas.
Kd6 : Le roi menace mat.
Rd1+ : Échec par derrière : le roi n'a aucun abri.
Ke5 : Le roi cherche à s'échapper.
Re1+ : Les échecs continuent.
Kf6 : Dernière tentative.
Rf1+ : Le roi ne peut fuir les échecs sans perdre son pion : partie nulle.

# Finale de tours : l'enfilade du pion a7 | Une ruse classique quand la tour est devant son pion en 7e rangée. | w
FEN: R7/P4k2/8/8/8/8/r7/6K1 w - - 0 1
Rh8 : La tour libère la case de promotion. Le roi noir en f7 est mal placé…
Rxa7 : La tour prend le pion.
Rh7+ : Enfilade : échec sur la 7e rangée, la tour a7 est derrière le roi.
Ke6 : Le roi doit s'écarter.
Rxa7 : La tour est gagnée. Voilà pourquoi le roi défenseur doit rester en g7 ou h7.

# Deux pions liés en 6e contre la tour | Deux pions passés liés sur la 6e rangée battent une tour seule. | w
FEN: 8/8/1PP5/8/8/8/7K/r3k3 w - - 0 1
b7 : Le premier pion avance…
Rb1 : La tour se place derrière lui.
c7 : …le second suit : deux menaces de promotion.
Rxb7 : La tour en élimine un…
c8=Q : …l'autre devient dame.

# Deux pions liés en 7e contre la tour | Côte à côte à un pas de la promotion, ils sont plus forts que la tour. | b
FEN: 3R4/8/p1p5/8/1P3p2/Pk2pK2/3p4/8 w - - 6 44
f3f4 : Le roi blanc capture un pion et s'éloigne des deux pions passés.
e3e2 : Les deux pions sont côte à côte, à un pas de la promotion : la tour ne peut pas les arrêter tous les deux.
d8d2 : La tour prend l'un…
e2e1q : …l'autre se promeut.

# Finale de tours : l'enfilade sur la rangée | Roi et tour sur la même ligne : un échec suffit à gagner la tour. | b
FEN: R7/4K1Rp/2P5/2k5/4r3/6P1/5r2/8 w - - 5 44
e7d8 : Le roi blanc fuit l'échec… en se plaçant sur la même rangée que sa tour a8.
f2f8 : Enfilade : la tour donne échec sur la 8e rangée.
d8c7 : Le roi doit s'écarter…
f8a8 : …et la tour restée derrière est capturée.

# Finale de tours : le filet de mat | Même avec peu de pièces, un roi trop audacieux peut se faire mater. | b
FEN: 8/1p2rk2/p2R1p1p/2P4P/1P1R1KP1/1r6/8/8 w - - 1 44
f4f5 : Le roi blanc s'avance trop loin, au milieu des pièces noires.
b3f3 : Échec sur la colonne f : le roi ne peut pas fuir, il faut interposer.
d4f4 : La tour s'interpose.
e7e5 : Échec et mat ! La tour est protégée par le pion f6.

# Finale de tours : le sacrifice qui mate | Un pion avancé près du roi adverse crée des motifs de mat du couloir. | w
FEN: 6k1/p5p1/6P1/1p1p4/2p5/P2r4/4r3/2K2R1R b - - 3 37
d3a3 : Les Noirs gagnent un pion sans voir que leur roi est enfermé par le pion g6.
h1h8 : Sacrifice d'attraction : la tour s'offre en h8.
g8h8 : Le roi doit prendre.
f1f8 : Mat du couloir : le pion g6 contrôle h7 et f7.

# Le fou de la mauvaise couleur | Pion tour et fou qui ne contrôle pas la case de promotion : nulle. | b
FEN: 7k/8/6K1/7P/8/3B4/8/8 b - - 0 1
Kg8 : Le fou blanc circule sur les cases blanches ; la case de promotion h8 est noire. Le roi fait la navette.
h6 : Le pion avance.
Kh8 : Le roi rejoint le coin : personne ne peut l'en chasser.
h7 : Pat ! Malgré un fou et un pion de plus, la partie est nulle.

# Finale de fous : dévier le défenseur | En finale aussi, on gagne en éliminant la pièce qui protège. | w
FEN: 8/3B4/1pp2p2/3b2k1/1P1K1p2/8/5P2/8 b - - 3 46
f4f3 : Les Noirs avancent leur pion f. Leur fou d5 n'est défendu que par le pion c6.
b4b5 : Déviation : le pion attaque le défenseur. S'il prend, il abandonne le fou.
c6b5 : Le pion capture…
d4d5 : …et le roi prend le fou.

# Finale de fous : promouvoir avec échec | Un échec bien choisi fait gagner le temps nécessaire à la promotion. | b
FEN: 8/3bP3/5p1B/7P/8/2k5/1b3p2/3K1B2 w - - 2 49
h6g7 : Le fou blanc s'occupe du pion f6… et néglige le pion f2, à un pas de la promotion.
d7g4 : Échec ! Le roi n'a pas de bonne case : un fou doit s'interposer.
f1e2 : Le fou s'interpose et abandonne le contrôle de f1.
f2f1q : Le pion se promeut : échec et mat, car le fou e2 est cloué.

# Deux pions liés contre le fou | Le fou ne contrôle qu'une couleur : deux pions qui avancent ensemble lui échappent. | b
FEN: 8/2K5/2p5/2Pk2p1/5p2/8/5B2/8 w - - 0 45
c7d7 : Le roi blanc attaque le pion c6. Mais les pions f et g noirs sont libres.
g5g4 : Les pions liés avancent ensemble, en se protégeant l'un l'autre.
f2h4 : Le fou cherche à les bloquer.
g4g3 : Encore un pas : le fou ne peut pas tenir les deux cases à la fois.

# Finale de cavaliers : dévier le roi bloqueur | Un pion sacrifié avec échec écarte le roi de la case de promotion. | w
FEN: 3k4/3P4/2K1P3/5n1p/6pP/6P1/8/8 b - - 2 62
f5g3 : Le cavalier noir part grignoter un pion à l'aile roi et s'éloigne de l'action.
e6e7 : Le pion se sacrifie avec échec pour dévier le roi de la case d8.
d8e7 : Le roi doit prendre.
c6c7 : Le roi blanc contrôle d8 : le pion d7 va se promouvoir, le cavalier est trop loin.

# Finale de cavaliers : la fourchette de pion | Même en finale, méfiez-vous du plus modeste des soldats. | b
FEN: 8/p5pk/1p3p2/8/2nPp1PN/2P3K1/P5P1/8 w - - 0 36
g3f4 : Le roi blanc avance pour attaquer le pion e4, à côté de son cavalier h4.
g7g5 : Fourchette de pion avec échec : le pion g attaque le roi et le cavalier.
f4e4 : Le roi prend le pion e4.
g5h4 : Le pion capture le cavalier.

# Le cavalier fait écran | Couper la route de la tour adverse pour laisser passer son pion. | b
FEN: R7/8/8/1k5K/1n6/7P/p5P1/8 w - - 2 55
g2g4 : Les Blancs avancent leur pion g, comptant sur la tour a8 pour surveiller le pion a2.
b4a6 : Interception : le cavalier se place sur la colonne a et coupe la route de la tour.
a8f8 : La tour tente de revenir par la 8e rangée, trop tard.
a2a1q : Le pion se promeut.

# Finale de dames : le roi participe au mat | Sans autres pièces, le roi devient une pièce d'attaque. | w
FEN: 8/Q7/1p3Kpk/7p/5P2/4P1P1/7q/8 b - - 1 38
h5h4 : Les Noirs poussent leur pion h, mais leur roi est collé à la bande, face au roi blanc.
a7g7 : La dame donne échec, protégée par son roi.
h6h5 : Seule case.
g7g6 : Mat : la dame, soutenue par le roi f6, enferme le roi noir sur la bande.

# Finale de dames : l'enfilade en diagonale | Roi et dame sur la même diagonale : danger ! | b
FEN: Q7/p2pk3/4p3/1P6/8/4K1P1/P4P2/4q3 w - - 3 34
e3f3 : Le roi blanc sort de l'échec en f3, sur la grande diagonale de sa dame a8.
e1h1 : Enfilade sur la grande diagonale : échec au roi, avec la dame a8 derrière.
f3e3 : Le roi s'écarte.
h1a8 : La dame capture la dame.

# Dame contre tour : séparer la tour de son roi | La tour n'est en sécurité que près de son roi : un échec les sépare. | w
FEN: 8/8/8/8/8/2k5/1r6/K2Q4 w - - 0 1
Qf3+ : La tour b2 n'est défendue que par son roi. Un échec oblige celui-ci à choisir.
Kd4 : Le roi s'éloigne et abandonne la tour.
Kxb2 : Le roi capture la tour. Contre une tour, la dame cherche toujours la fourchette ou la séparation.

# Le sacrifice de déviation pour promouvoir | Écarter le roi adverse de la case de promotion, même au prix d'une tour. | w
FEN: 8/R3Pk2/8/5PRK/3q4/7P/P7/8 b - - 4 62
d4a7 : La dame capture la tour a7 et attaque le pion e7.
g5g7 : Déviation ! La tour se sacrifie avec échec pour écarter le roi de la case e8.
f7g7 : Le roi capture.
e7e8q : Le pion se promeut : les Blancs retrouvent une dame et gardent des pions d'avance.

# La sous-promotion en cavalier | Le cavalier est la seule pièce que la dame ne sait pas imiter. | b
FEN: 4R3/1p1P4/p7/P6p/5k2/4p2K/5rp1/8 w - - 2 52
d7d8q : Les Blancs font dame et menacent de donner des échecs décisifs.
g2g1n : Sous-promotion ! Le cavalier donne échec immédiatement ; une dame laissait l'initiative aux Blancs.
h3h4 : Le roi n'a qu'une case.
f2h2 : La tour mate sur la colonne h. Au moment de promouvoir, pensez au cavalier.

"""
