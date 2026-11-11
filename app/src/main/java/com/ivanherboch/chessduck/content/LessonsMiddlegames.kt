package com.ivanherboch.chessduck.content

// Fichier généré par scripts/gen_lessons.py — modifier scripts/lessons/*.txt.

internal val MIDDLEGAME_LESSONS = """
# La fourchette de cavalier | Un seul cavalier, deux cibles : quand le roi est l'une d'elles, l'autre tombe. | b
FEN: 3r1rk1/pp3pp1/5q2/7p/3nPBQ1/2N5/PP3P1P/R3K2R w KQ - 0 18
g4g5 : Les Blancs proposent l'échange des dames… en oubliant le cavalier noir posté en d4.
d4f3 : Fourchette ! Le cavalier donne échec au roi et attaque en même temps la dame g5.
e1e2 : Le roi doit parer l'échec : impossible de sauver aussi la dame.
f3g5 : Le cavalier capture la dame. Roi et dame visés ensemble : c'est la fourchette royale.

# La fourchette de pion | Le plus petit soldat peut attaquer deux pièces à la fois. | b
FEN: r1bq1rk1/2p1nppp/p1pb4/3p2B1/3P4/5N2/PPP2PPP/RN1QR1K1 w - - 6 10
f3e5 : Le cavalier blanc avance en e5, juste à côté du fou g5 : deux pièces voisines sur la même rangée…
f7f6 : Fourchette de pion ! Le pion f attaque à la fois le cavalier e5 et le fou g5.
e5c6 : Le cavalier se vend le plus cher possible en prenant un pion et en attaquant la dame.
e7c6 : On reprend tranquillement : les Noirs ont gagné une pièce pour un pion, et le fou g5 reste attaqué.

# La double attaque de la dame | La dame rayonne dans huit directions : idéale pour viser deux cibles. | b
FEN: r1bqkb1r/pp2pp1p/n1p2p2/1B1P4/8/P7/1PP2PPP/R1BQK1NR w KQkq - 0 9
d5c6 : Les Blancs capturent en c6, mais leur fou b5 n'est défendu par personne.
d8a5 : Double attaque : échec au roi en diagonale et attaque du fou b5 le long de la rangée.
b2b4 : Les Blancs parent l'échec en interposant un pion qui attaque la dame.
a5b5 : La dame prend le fou. Cherchez toujours l'échec qui attaque une pièce non défendue.

# Le clouage | Une pièce clouée ne peut pas bouger sans exposer une pièce plus précieuse derrière elle. | b
FEN: r3kbnr/2B2ppp/p1p5/8/4P3/5Q2/PqP2PPP/RN2K2R w KQkq - 0 11
f3c3 : Les Blancs proposent l'échange des dames. Mais leur dame et leur roi sont sur la même diagonale…
f8b4 : Clouage ! Le fou attaque la dame, qui ne peut pas quitter la diagonale sans laisser son roi en échec.
c3b4 : La dame n'a rien de mieux que de capturer le fou.
b2b4 : La dame noire reprend avec échec : une dame gagnée contre un fou. Une pièce clouée est une cible idéale.

# L'enfilade | Un clouage à l'envers : la pièce de valeur est devant et doit s'écarter. | w
FEN: 1rb2rk1/4ppbp/5np1/2p1P3/1n1q4/2NP3P/1P2B1PN/R1BQ1R1K b - - 2 16
d4e5 : La dame noire prend le pion e5 et se place sur la même diagonale que sa tour b8.
c1f4 : Enfilade ! Le fou attaque la dame ; derrière elle, sur la même diagonale, se trouve la tour.
e5e6 : La dame, trop précieuse, doit s'écarter…
f4b8 : …et le fou capture la tour restée derrière. Méfiez-vous de vos pièces alignées.

# L'attaque à la découverte | Une pièce s'écarte et démasque l'attaque d'une autre : deux menaces en un coup. | w
FEN: r2k1b1r/pp3p1p/2n1BNq1/5pBQ/3p4/2P5/PP4PP/R5K1 b - - 0 19
f7e6 : Les Noirs capturent le fou e6. Mais le cavalier f6 masque le fou g5, braqué sur le roi d8.
f6h7 : Le cavalier s'écarte en prenant h7 : le fou g5 donne échec, et le pion qui défendait la dame g6 a disparu.
g6g5 : Pour parer l'échec, la dame noire capture le fou…
h5g5 : …mais la dame blanche la reprend avec échec. La découverte a coûté la dame aux Noirs.

# L'échec à la découverte | Quand la pièce démasquée donne échec, la pièce qui bouge a les mains libres. | w
FEN: r1bq1rk1/1p4bp/2pp1n2/2PPp3/P3Pp2/BQN2BP1/7P/R4RK1 b - - 0 21
c8h3 : Le fou noir attaque la tour f1. Mais la dame b3 vise le roi g8 : seul le pion d5 fait écran.
d5c6 : Échec à la découverte ! En capturant c6, le pion dégage la diagonale de la dame.
g8h8 : Le roi doit se mettre à l'abri : les Noirs n'ont pas le temps de s'occuper du pion.
c6b7 : Le pion poursuit sa route, gagne un second pion et attaque la tour a8 : il menace de se promouvoir.

# L'échec double | Deux pièces donnent échec en même temps : seul le roi peut répondre. | b
FEN: 1r4k1/5p1p/1qP1p3/8/2Q1PB2/2Pn2P1/5nBP/R5K1 w - - 2 26
f4b8 : Les Blancs capturent la tour b8. Mais la dame b6 vise le roi g1 à travers son propre cavalier f2.
f2h3 : Échec double ! Le cavalier donne échec depuis h3 et démasque la dame. Ni capture ni interposition possibles : le roi doit bouger.
g1f1 : Le roi fuit en f1.
b6f2 : Échec et mat : la dame est protégée par le cavalier d3. L'échec double est l'arme la plus forcée du jeu.

# La déviation | Détourner un défenseur de sa tâche pour faire tomber ce qu'il protégeait. | w
FEN: 2kr3r/pp1n1pp1/2pBp1p1/6q1/Q2PP3/6P1/PP3P2/R4BK1 b - - 4 17
a7a6 : Les Noirs chassent la dame avec leur pion a… et affaiblissent l'abri de leur roi.
a4c6 : Sacrifice de dame ! Le but : dévier le pion b7, seul gardien de la diagonale a6-c8.
b7c6 : La prise est forcée.
f1a6 : Échec et mat : le fou a6 donne échec et le fou d6 contrôle les cases de fuite. Le pion b7 a été dévié de son poste.

# L'attraction | Attirer une pièce adverse, souvent le roi, sur une case où elle sera vulnérable. | w
FEN: r2q1r1k/pp1b1pR1/1n5P/2p1p2P/3pP3/2NP1P2/PPP1N3/1K4QR b - - 2 19
d4c3 : Les Noirs se jettent sur le cavalier c3 sans voir le danger qui plane sur leur roi.
g7h7 : Sacrifice d'attraction : la tour s'offre en h7 pour attirer le roi sur cette case.
h8h7 : Le roi n'a pas le choix : il doit capturer.
g1g7 : La dame mate en g7, soutenue par le pion h6. Le roi a été attiré sur une case fatale.

# Le sacrifice | Donner du matériel n'est qu'un investissement, si l'on récupère davantage ensuite. | b
FEN: b2k3r/2p1qp2/BpPp4/3Pn3/PP2Pp2/2N2P2/3K3P/R4Q2 w - - 0 30
f1f2 : La dame blanche vient en f2, sur la même rangée que son roi.
h8h2 : Sacrifice ! La tour prend h2 et cloue la dame sur son roi : elle est obligée de capturer.
f2h2 : La dame accepte l'offrande et se retrouve sur une case empoisonnée.
e5f3 : Le but du sacrifice : une fourchette de cavalier sur le roi et la dame.
d2c2 : Le roi s'écarte.
f3h2 : Le cavalier ramasse la dame. Bilan : une dame et deux pions pour une tour.

# La pièce en prise | La tactique la plus simple : repérer la pièce que plus personne ne défend. | w
FEN: r1bqk2r/b1p1npp1/p1np3p/1p1B4/1P1PP3/P4N1P/2Q2PP1/RNB2RK1 b kq - 3 13
e7d5 : Les Noirs échangent en d5… mais c'était leur cavalier e7 qui protégeait le cavalier c6.
c2c6 : La dame capture le cavalier laissé sans défense, avec échec en prime.
c8d7 : Les Noirs parent l'échec en attaquant la dame.
c6d5 : La dame prend le second cavalier. Après chaque coup adverse, demandez-vous : qu'est-ce qui n'est plus défendu ?

# La pièce enfermée | Une pièce sans case de fuite est une pièce perdue, même une dame. | b
FEN: r1bq1rk1/ppp2pb1/3p4/4n1pQ/2P1P3/2NB2B1/PP3PPP/R3K2R w KQ - 0 13
e1c1 : Les Blancs font le grand roque. Mais leur dame, aventurée en h5, n'a presque plus de cases.
c8g4 : Le fou attaque la dame : toutes ses cases de fuite sont contrôlées. Elle est enfermée.
g3e5 : Les Blancs prennent un cavalier pour limiter les dégâts.
g4h5 : Le fou capture la dame. Une pièce envoyée loin de son camp doit toujours garder un chemin de retour.

# L'élimination du défenseur | Supprimez la pièce qui protège, et la pièce protégée tombe. | b
FEN: 1r1qk2r/2p3pp/p4p2/1p2P3/1nQ2BN1/4P3/PP1bKPPP/R6R w k - 0 18
c4d4 : Les Blancs proposent l'échange des dames en d4. Or leur fou f4 n'est défendu que par le pion e3.
d8d4 : On échange les dames pour obliger ce pion à reprendre…
e3d4 : Le pion capture et abandonne la défense du fou.
d2f4 : Le défenseur éliminé, le fou tombe. Repérez toujours QUI défend la pièce que vous visez.

# Le dégagement | Libérer une case ou une ligne occupée par l'une de ses propres pièces. | w
FEN: 1r3knr/3N1p1p/2B1p1p1/8/Pp3N2/3P2P1/1bPQKP1P/q7 b - - 2 23
f8g7 : Le roi noir fuit l'échec du cavalier en g7.
f4h5 : Dégagement ! Le cavalier quitte f4 avec échec pour ouvrir la diagonale de la dame vers g5.
g6h5 : Les Noirs capturent le cavalier.
d2g5 : La dame emprunte la diagonale dégagée : échec et mat. Parfois, la meilleure place d'une pièce… c'est ailleurs.

# L'interception | Couper la ligne de communication entre deux pièces adverses. | b
FEN: 3rr1k1/pppq1ppp/2n3n1/2B3Q1/2B5/P2P3P/1PP2PP1/R4RK1 w - - 9 19
a1d1 : Les Blancs centralisent leur tour. Leur fou c5 n'est protégé que par la dame g5, le long de la 5e rangée.
e8e5 : Interception ! La tour, soutenue par ses cavaliers, se place entre la dame et le fou et attaque les deux.
g5c1 : La dame doit se sauver.
e5c5 : La tour capture le fou, désormais sans défense.

# Le coup intermédiaire | Avant la reprise attendue, intercaler un coup plus fort, souvent un échec. | b
FEN: r4rk1/p5p1/2p3b1/1p1p4/3Pnp2/PQNBP3/1P3q2/2KR2R1 w - - 0 24
c3e4 : Les Blancs prennent le cavalier e4 et attaquent la dame.
f2e3 : Coup intermédiaire ! Avant de reprendre en e4, la dame capture un pion avec échec.
c1c2 : Le roi doit répondre à l'échec.
g6e4 : Maintenant seulement, le fou reprend le cavalier, en clouant au passage le fou d3. Un pion de gagné.

# Les rayons X | Une pièce agit à travers une autre, placée sur la même ligne. | b
FEN: r6k/2p3p1/1p2R2p/pb5Q/3q4/6N1/PPP4P/1K2R1r1 w - - 4 27
h5b5 : La dame blanche capture le fou b5 et abandonne la surveillance de sa première rangée.
d4d1 : Rayons X ! La dame se sacrifie en d1 : la tour noire g1 « voit » à travers la tour e1.
e1d1 : La tour blanche doit capturer la dame.
g1d1 : La tour g1 reprend en d1 : échec et mat sur la première rangée.

# Le coup tranquille | Ni échec ni prise : un coup calme qui crée une menace imparable. | b
FEN: rnQ2b1r/p1p1kppp/2q5/4p3/8/8/PP1P1PPP/RNB2RK1 w - - 3 12
f1e1 : Les Blancs amènent une tour sur la colonne du roi. Leur dame, infiltrée en c8, est très exposée.
b8d7 : Coup tranquille : un simple développement. Mais la tour a8 attaque maintenant la dame, qui n'a aucune case de fuite.
c8a8 : La dame prend la tour : c'est le moins mauvais.
c6a8 : La dame noire reprend. Dame contre tour : un coup calme a suffi.

# La parade précise | Garder son sang-froid : face à une dernière menace, un seul coup conserve le gain. | b
FEN: r2qk2r/pp2bpp1/3p2bp/3Bp3/3pP3/3P1PQ1/PPP3PP/R1B1K2R w KQkq - 1 14
d5b7 : Les Blancs gagnent un pion et attaquent la tour a8. Mais leur dame g3 est sur la diagonale de leur roi.
e7h4 : Le fou cloue la dame sur le roi : elle est perdue.
b7c6 : Les Blancs tentent un échec de la dernière chance.
e8f8 : La parade précise : le roi s'écarte simplement. La dame blanche reste clouée et sera capturée au coup suivant.

# Le mat du couloir | Un roi emmuré derrière ses propres pions succombe sur la dernière rangée. | w
FEN: rnb2k1r/pp1p1ppp/5b2/8/2q2P2/2B5/PP4PP/RN1QR1K1 b - - 0 13
f6c3 : Les Noirs prennent le fou c3. Mais leur roi, derrière ses pions, manque d'air.
d1d6 : La dame donne échec et prend le contrôle de la diagonale : le roi n'a qu'une case.
f8g8 : Le roi se réfugie en g8.
e1e8 : Mat du couloir : la tour mate sur la dernière rangée. Pensez à offrir une case de fuite à votre roi !

# Le mat à l'étouffée | Le cavalier mate un roi étouffé par ses propres pièces. | w
FEN: r1bq1r1k/1ppp2pp/p2b4/3QR1N1/8/1B6/Pp3PPP/R1B3K1 b - - 0 15
b2a1q : Les Noirs capturent la tour et font une deuxième dame. Peu importe : leur roi est en danger.
d5g8 : Sacrifice de dame ! Le roi ne peut pas la prendre, car le fou b3 la protège.
f8g8 : La tour est obligée de capturer… et enferme son propre roi.
g5f7 : Mat à l'étouffée : cerné par ses propres pièces, le roi succombe au seul cavalier.

# Le mat d'Anastasie | Cavalier et pièce lourde coordonnés contre un roi au bord de l'échiquier. | w
FEN: r2q1r1k/3bNppp/p1p5/1p1N4/7R/1P3Q2/P4PPP/b5K1 b - - 1 20
c6d5 : Les Noirs prennent le cavalier d5. Il reste le cavalier e7, qui contrôle g8 et g6.
h4h7 : La tour se sacrifie en h7 pour ouvrir la colonne h.
h8h7 : Le roi capture la tour.
f3h5 : Mat d'Anastasie : la dame mate sur la colonne h, le cavalier interdit g8 et g6, le pion g7 bloque son propre roi.

# Le mat des Arabes | Tour et cavalier unis contre un roi dans le coin : l'un des plus vieux mats connus. | b
FEN: 2kr3r/1pp5/4p1p1/R2pPn2/q2n1Pp1/8/P4B1P/3Q1RK1 w - - 0 21
d1a4 : Les Blancs capturent la dame. Mais cavaliers et tour noirs encerclent le roi g1.
d4f3 : Le cavalier donne échec en f3, protégé par le pion g4.
g1h1 : Le roi va dans le coin.
h8h2 : Mat des Arabes : la tour mate en h2, protégée par le cavalier qui contrôle aussi g1.

# Le mat de Boden | Deux fous sur des diagonales croisées, après un sacrifice qui ouvre la position. | b
FEN: r3k1nr/pp2b2p/2p3p1/q3Pp2/B2PpB2/2Pb3Q/PP1N2PP/2KR3R w kq - 6 15
a4b3 : Le fou blanc recule en b3. Le roi c1 est déjà sous le regard du fou d3.
a5c3 : Sacrifice de dame pour ouvrir la diagonale a3-c1 !
b2c3 : Le pion doit capturer.
e7a3 : Mat de Boden : les deux fous se croisent et le roi est gêné par ses propres pièces en d1 et d2.

# Le mat des deux fous | Deux fous sur des diagonales voisines balaient le coin du roi. | w
FEN: r2r2bk/pp4p1/3b1p1p/3q4/6PP/2BBQ3/1PP2P2/2K1R2R b - - 6 31
d8e8 : Les Noirs attaquent la dame e3. Mais leur roi h8 est enfermé par son propre fou g8.
e3h6 : Sacrifice de dame : il faut faire sauter le pion g7 pour ouvrir la grande diagonale.
g7h6 : La prise est forcée.
c3f6 : Mat des deux fous : le fou mate sur la grande diagonale pendant que l'autre surveille h7.

# Le mat en queue d'aronde | La dame mate en diagonale un roi dont les cases arrière sont bouchées par ses pions. | w
FEN: 2r4k/2q3pR/rp3p2/2nQ4/8/pPP2PP1/P1N3P1/2KR4 b - - 0 24
h8h7 : Le roi noir capture la tour h7 et sort de son abri.
d1h1 : La seconde tour donne échec sur la colonne h : le roi n'a qu'une case.
h7g6 : Le roi avance en g6.
d5h5 : Mat en queue d'aronde : la dame mate au contact, protégée par la tour ; les pions f6 et g7 bloquent la retraite.

# Le mat du crochet | Tour, cavalier et pion forment une chaîne où chacun protège l'autre. | w
FEN: 3r1r1k/2pR1pnp/pb3NpB/2p1P3/7R/5P2/PP3P1P/6K1 b - - 3 22
d8d7 : Les Noirs capturent la tour d7. Mais le cavalier f6 et la tour h4 visent déjà h7.
h6g7 : Le fou élimine le défenseur de h7 et force le roi à sortir.
h8g7 : Le roi reprend.
h4h7 : Mat du crochet : la tour mate en h7, protégée par le cavalier, lui-même soutenu par le pion e5.

# Le mat de l'Opéra | La tour mate sur la dernière rangée, protégée par un fou. | b
FEN: 2r5/k5b1/p5p1/8/4N3/1R1P1p2/1P1K1P2/8 w - - 4 43
e4d6 : Le cavalier attaque la tour c8. Mais le roi blanc, près du bord, est en danger.
g7h6 : Le fou donne échec et s'empare de la diagonale c1-h6.
d2d1 : Le roi recule sur la première rangée.
c8c1 : Mat de l'Opéra : la tour mate sur la dernière rangée, soutenue par le fou.

# Le mat de Légal (attaque sur f7) | Un piège d'ouverture célèbre : la dame est offerte pour mater avec les pièces mineures. | w
FEN: r2qkb1r/ppp2ppp/2np4/P3N3/2B3b1/2P5/1PP2PPP/R1BQK2R b KQkq - 0 8
g4d1 : Les Noirs se jettent sur la dame… C'est exactement le piège !
c4f7 : Le fou frappe en f7, le point faible du camp noir : seul le roi le défend.
e8e7 : Le roi n'a qu'une case.
c1g5 : Échec et mat ! Fous et cavalier se coordonnent parfaitement. Trois pièces mineures bien placées valent mieux qu'une dame gourmande.

# L'attaque à l'aile roi | Contre un roque dégarni, chaque pièce qui s'approche crée une menace. | b
FEN: 2kr3r/ppp5/2npp3/2b1p1p1/4Q3/P1PP2qP/1P4B1/RNB2R1K w - - 0 18
f1f3 : La tour blanche attaque la dame g3. Mais le roque blanc est en ruine et le fou c5 vise g1.
h8h3 : La tour se sacrifie sur le dernier pion protecteur, avec échec.
g2h3 : Le fou capture… et libère la colonne g.
g3g1 : La dame descend en g1, soutenue par le fou c5 : échec et mat.

# L'attaque à l'aile dame | Un roi roqué du grand côté est souvent moins bien protégé. | w
FEN: 2k2r1r/pppq2p1/1bn1p3/4p1Bp/Q7/3P2P1/PP2PPBP/2R2RK1 b - - 5 16
c6d4 : Le cavalier bondit en d4 et attaque la dame a4… en démasquant sa propre dame d7.
g2b7 : Le fou se sacrifie en b7 avec échec pour éloigner le roi de sa dame.
c8b7 : Le roi capture.
a4d7 : La dame d7 n'est plus défendue : les Blancs la capturent.

# Le roi exposé | Un roi sans pions devant lui attire les pièces lourdes sur les colonnes ouvertes. | w
FEN: r4rk1/3nqp2/1pn1p1p1/2ppP1P1/3P2P1/3Q4/PP3PK1/RN5R b - - 0 20
e7g5 : La dame noire croque le pion g5. Mais la colonne h est ouverte face à son roi.
d3h3 : La dame rejoint la tour sur la colonne h : la menace de mat en h8 est imparable.
g5g4 : Pour éviter le mat, les Noirs doivent donner leur dame.
h3g4 : Les Blancs ont gagné la dame. Doublez vos pièces lourdes sur la colonne ouverte qui mène au roi.

# Le pion avancé | À un pas de la promotion, un pion devient une arme tactique redoutable. | w
FEN: 8/p3P2R/1p1k4/2p2K1p/2P4r/1P6/P6p/8 b - - 0 52
h2h1q : Les Noirs font dame les premiers. Mais le pion e7 est lui aussi à un pas de la promotion…
e7e8n : Promotion en cavalier, avec échec ! Une dame n'aurait pas donné échec et laissait l'initiative aux Noirs.
d6c6 : Le roi n'a que cette case.
h7c7 : La tour mate, protégée par le cavalier tout neuf. Au moment de promouvoir, examinez chaque possibilité.

# La prise en passant | La règle spéciale des pions peut rouvrir une ligne d'attaque. | w
FEN: 1r3rk1/p4ppp/q3p3/3pP2P/P1p3Q1/1P2R3/2P3P1/R6K b - - 0 22
h7h6 : Les Noirs jouent h6 pour bloquer le pion h5.
e3g3 : La tour double sur la colonne g et menace mat en g7.
g7g5 : Les Noirs ferment la colonne en avançant leur pion de deux cases, juste à côté du pion h5.
h5g6 : Prise en passant ! Le pion capture comme si le pion noir n'avait avancé que d'une case. La colonne g se rouvre : l'attaque continue. (Ce droit ne dure qu'un coup.)

# Le roque au bon moment | Roquer peut être un coup tactique : mise à l'abri et défense en un seul temps. | b
FEN: r3k2r/pp2q1pp/2n2n2/1Q2p1B1/3p4/P7/1P1N1PPP/R3R1K1 w kq - 3 17
e1e5 : Les Blancs prennent e5 avec la tour : la dame e7 et le cavalier c6 sont tous deux cloués.
e7e5 : La dame capture la tour (le cavalier, cloué, ne le pouvait pas).
b5b7 : La dame blanche attaque à la fois la tour a8 et le cavalier c6.
e8g8 : Le roque sauve tout : le roi est à l'abri, la tour f8 protège la tour a8 et la dame attaque toujours le fou g5.

# Calculer un mat en trois coups | Échecs, prises, menaces : examiner d'abord les coups forcés. | w
FEN: 8/3qbrpk/3p1n1p/p3pN2/Pp2P3/6RQ/1r1B1PPP/3R2K1 b - - 1 26
f6e4 : Le cavalier noir prend e4 et abandonne la défense de son roi.
g3g7 : Premier coup forcé : la tour se sacrifie en g7 avec échec.
f7g7 : La tour reprend.
h3h6 : Deuxième coup forcé : la dame prend h6 avec échec.
h7g8 : Le roi recule.
h6g7 : La dame prend la tour, soutenue par le cavalier f5 : mat. Les suites forcées sont les plus faciles à calculer.

# Le piège d'ouverture | Un coup de développement naturel peut être une faute : vérifiez vos défenseurs. | w
FEN: r1bqkb1r/ppp2ppp/2np1n2/3Np1N1/2B5/3P4/PPP2PPP/R1BQK2R b KQkq - 2 7
c8g4 : Les Noirs développent leur fou en attaquant la dame. Naturel… mais fautif.
d5f6 : Le cavalier capture en f6 avec échec : le fou g4 perd son unique défenseur.
d8f6 : Les Noirs reprennent.
d1g4 : La dame prend le fou. En ouverture aussi, chaque pièce développée doit rester défendue.

"""
