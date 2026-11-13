package com.ivanherboch.chessduck.content

/** Un thème officiel Lichess, traduit, avec un objectif pédagogique sans divulgâcher la solution. */
data class ThemeInfo(val key: String, val name: String, val goal: String, val group: String, val tip: String = "")

object PuzzleThemes {
    const val G_PHASE = "Phases de la partie"
    const val G_TACTIC = "Motifs tactiques"
    const val G_ATTACK = "Attaque & défense"
    const val G_SPECIAL = "Coups spéciaux"
    const val G_MATE = "Mats"
    const val G_ENDGAME = "Finales"

    val all = listOf(
        ThemeInfo("opening", "Ouverture", "Punir une imprécision dès les premiers coups.", G_PHASE, "Cherchez la pièce adverse mal défendue ou le roi encore au centre : en début de partie, un retard de développement se paie cher."),
        ThemeInfo("middlegame", "Milieu de partie", "Trouver la tactique quand toutes les pièces sont en jeu.", G_PHASE, "Passez en revue, dans l'ordre, les échecs, les prises puis les menaces : le coup le plus forçant est souvent le bon."),
        ThemeInfo("endgame", "Finale", "Être précis quand il reste peu de pièces.", G_PHASE, "Avec peu de pièces, chaque temps compte : activez votre roi et vos pions passés avant tout."),

        ThemeInfo("fork", "Fourchette", "Attaquer deux cibles à la fois avec une seule pièce.", G_TACTIC, "Repérez deux cibles adverses non défendues (ou le roi et une pièce) qu'une seule de vos pièces peut attaquer en même temps."),
        ThemeInfo("pin", "Clouage", "Exploiter une pièce qui ne peut pas bouger sans exposer plus précieux.", G_TACTIC, "Cherchez deux pièces adverses alignées sur une colonne, une rangée ou une diagonale : celle de devant ne peut pas bouger librement."),
        ThemeInfo("skewer", "Enfilade", "Attaquer une pièce de valeur pour gagner celle qui se cache derrière.", G_TACTIC, "Cherchez deux pièces adverses alignées, la plus précieuse devant : attaquez-la pour gagner celle qui se cache derrière."),
        ThemeInfo("discoveredAttack", "Attaque à la découverte", "Déplacer une pièce pour démasquer l'attaque d'une autre.", G_TACTIC, "Une de vos pièces masque l'action d'une autre : déplacez-la en créant une seconde menace."),
        ThemeInfo("discoveredCheck", "Échec à la découverte", "Démasquer un échec tout en créant une seconde menace.", G_TACTIC, "Une de vos pièces fait écran entre une pièce à longue portée et le roi adverse : en s'écartant, elle peut tout se permettre."),
        ThemeInfo("doubleCheck", "Échec double", "Donner échec avec deux pièces : seul le roi peut répondre.", G_TACTIC, "Trouvez le coup qui donne échec avec deux pièces à la fois : le roi adverse sera obligé de bouger."),
        ThemeInfo("deflection", "Déviation", "Détourner un défenseur de sa tâche essentielle.", G_TACTIC, "Identifiez le défenseur qui tient la position adverse, puis forcez-le à quitter son poste."),
        ThemeInfo("attraction", "Attraction", "Attirer une pièce adverse sur une case fatale.", G_TACTIC, "Demandez-vous sur quelle case une pièce adverse (souvent le roi) serait vulnérable, puis forcez-la à y venir."),
        ThemeInfo("sacrifice", "Sacrifice", "Donner du matériel pour obtenir bien davantage ensuite.", G_TACTIC, "N'écartez pas les coups qui donnent du matériel : comptez ce que vous récupérez à la fin de la suite forcée."),
        ThemeInfo("hangingPiece", "Pièce en prise", "Repérer et capturer une pièce non défendue.", G_TACTIC, "Comptez les attaquants et les défenseurs de chaque pièce adverse : l'une d'elles n'est pas assez protégée."),
        ThemeInfo("trappedPiece", "Pièce enfermée", "Gagner une pièce qui n'a plus aucune case de fuite.", G_TACTIC, "Une pièce adverse manque de cases de fuite : attaquez-la après avoir vérifié que toutes ses retraites sont contrôlées."),
        ThemeInfo("capturingDefender", "Élimination du défenseur", "Supprimer la pièce qui protège la cible.", G_TACTIC, "Repérez la pièce qui protège votre cible : éliminez d'abord ce défenseur."),
        ThemeInfo("clearance", "Dégagement", "Libérer une case ou une ligne pour une autre pièce.", G_TACTIC, "Une de vos propres pièces occupe la case ou la ligne dont vous avez besoin : libérez-la avec gain de temps."),
        ThemeInfo("interference", "Interception", "Couper la communication entre deux pièces adverses.", G_TACTIC, "Deux pièces adverses se protègent à distance : cherchez la case qui coupe leur ligne de communication."),
        ThemeInfo("intermezzo", "Coup intermédiaire", "Intercaler une menace avant le coup attendu.", G_TACTIC, "Avant de jouer la reprise « évidente », cherchez un coup plus fort à intercaler, par exemple un échec."),
        ThemeInfo("xRayAttack", "Rayons X", "Attaquer ou défendre à travers une pièce adverse.", G_TACTIC, "Vos pièces alignées agissent aussi à travers une pièce adverse : comptez-les toutes."),
        ThemeInfo("quietMove", "Coup tranquille", "Jouer un coup calme qui prépare une menace imparable.", G_TACTIC, "Le meilleur coup n'est ici ni un échec ni une prise : cherchez le coup calme qui crée une menace imparable."),
        ThemeInfo("defensiveMove", "Coup défensif", "Trouver l'unique parade qui évite la catastrophe.", G_TACTIC, "Identifiez d'abord la menace adverse : un seul coup la pare tout en conservant votre avantage."),
        ThemeInfo("zugzwang", "Zugzwang", "Placer l'adversaire dans une position où tout coup l'affaiblit.", G_TACTIC, "Limitez les coups adverses, puis jouez le coup qui oblige l'adversaire à abîmer lui-même sa position."),

        ThemeInfo("kingsideAttack", "Attaque à l'aile roi", "Assaillir un roi adverse roqué du petit côté.", G_ATTACK, "Comptez vos pièces tournées vers le roque adverse : ouvrez une ligne vers le roi, quitte à sacrifier."),
        ThemeInfo("queensideAttack", "Attaque à l'aile dame", "Assaillir un roi adverse roqué du grand côté.", G_ATTACK, "Le roi adverse est à l'aile dame : cherchez à ouvrir une colonne ou une diagonale devant lui."),
        ThemeInfo("exposedKing", "Roi exposé", "Profiter d'un roi mal protégé par ses pions.", G_ATTACK, "Le roi adverse manque de protection : privilégiez les échecs qui lui retirent ses cases de fuite."),
        ThemeInfo("attackingF2F7", "Attaque sur f2 ou f7", "Viser le pion le plus fragile du début de partie.", G_ATTACK, "Le pion f, défendu par le seul roi, est la cible : combien de vos pièces peuvent le viser ?"),
        ThemeInfo("advancedPawn", "Pion avancé", "Exploiter un pion proche de la promotion.", G_ATTACK, "Un pion proche de la promotion décide de tout : chassez ou déviez la pièce qui l'arrête."),

        ThemeInfo("promotion", "Promotion", "Transformer un pion en pièce au bon moment.", G_SPECIAL, "Calculez la course jusqu'à la dernière rangée et écartez ce qui contrôle la case de promotion."),
        ThemeInfo("underPromotion", "Sous-promotion", "Choisir autre chose qu'une dame en arrivant au bout.", G_SPECIAL, "Au moment de promouvoir, la dame n'est pas toujours le meilleur choix : examinez chaque pièce possible."),
        ThemeInfo("enPassant", "Prise en passant", "Se servir de cette règle spéciale des pions.", G_SPECIAL, "Pensez à la règle spéciale des pions : elle n'est disponible qu'au coup qui suit l'avance de deux cases."),
        ThemeInfo("castling", "Roque", "Mettre le roi à l'abri… avec une idée derrière la tête.", G_SPECIAL, "N'oubliez pas le coup spécial du roi : il peut aussi servir une idée tactique."),

        ThemeInfo("mateIn1", "Mat en 1", "Donner échec et mat en un seul coup.", G_MATE, "Un seul coup suffit : examinez tous vos échecs et vérifiez que le roi n'a ni fuite, ni parade, ni capture."),
        ThemeInfo("mateIn2", "Mat en 2", "Forcer le mat en deux coups.", G_MATE, "Mat forcé en deux coups : le premier doit laisser au roi adverse le moins de réponses possible."),
        ThemeInfo("mateIn3", "Mat en 3", "Calculer une suite de mat en trois coups.", G_MATE, "Mat forcé en trois coups : ne considérez que les échecs et les menaces de mat immédiates."),
        ThemeInfo("mateIn4", "Mat en 4", "Calculer une suite de mat en quatre coups.", G_MATE, "Mat forcé en quatre coups : visualisez l'enfermement du roi avant de calculer l'ordre des coups."),
        ThemeInfo("mateIn5", "Mat en 5 ou plus", "Voir loin pour conclure par un mat forcé.", G_MATE, "Longue suite de mat : chaque coup doit être forçant, n'acceptez aucune variante qui laisse respirer le roi."),
        ThemeInfo("backRankMate", "Mat du couloir", "Mater un roi prisonnier de sa dernière rangée.", G_MATE, "Le roi adverse est enfermé derrière ses pions : la dernière rangée est-elle assez défendue ?"),
        ThemeInfo("smotheredMate", "Mat à l'étouffée", "Mater avec un cavalier un roi cerné par ses propres pièces.", G_MATE, "Le roi adverse est entouré de ses propres pièces : seule une pièce qui saute peut l'atteindre."),
        ThemeInfo("anastasiaMate", "Mat d'Anastasie", "Coordonner cavalier et pièce lourde contre un roi au bord.", G_MATE, "Le roi adverse est près du bord : coordonnez votre cavalier, qui lui retire deux cases, avec une pièce lourde."),
        ThemeInfo("arabianMate", "Mat des Arabes", "Coordonner tour et cavalier contre un roi dans le coin.", G_MATE, "Le roi adverse est dans le coin : votre tour et votre cavalier doivent s'y donner rendez-vous."),
        ThemeInfo("bodenMate", "Mat de Boden", "Mater avec deux fous sur des diagonales croisées.", G_MATE, "Vos deux fous visent des diagonales qui se croisent près du roi : il faut ouvrir la seconde."),
        ThemeInfo("doubleBishopMate", "Mat des deux fous", "Mater avec deux fous sur des diagonales voisines.", G_MATE, "Vos deux fous balaient des diagonales voisines : ouvrez celle qui mène au roi."),
        ThemeInfo("dovetailMate", "Mat en queue d'aronde", "Mater avec la dame un roi gêné par ses propres pièces.", G_MATE, "Les cases de fuite du roi sont bouchées par ses propres pièces : la dame doit s'approcher au contact."),
        ThemeInfo("hookMate", "Mat du crochet", "Combiner tour, cavalier et pion contre le roi.", G_MATE, "Tour, cavalier et pion peuvent se protéger en chaîne autour du roi adverse."),

        ThemeInfo("pawnEndgame", "Finale de pions", "Maîtriser opposition, carré et percées.", G_ENDGAME, "Comptez les temps avec précision et pensez opposition, règle du carré et percée de pions."),
        ThemeInfo("rookEndgame", "Finale de tours", "Activer sa tour et son roi dans la finale la plus fréquente.", G_ENDGAME, "Activez votre tour et votre roi ; cherchez un échec utile ou la poussée d'un pion passé."),
        ThemeInfo("bishopEndgame", "Finale de fous", "Tirer parti des diagonales quand il ne reste que des fous.", G_ENDGAME, "Vos pions doivent vivre sur la couleur que le fou adverse ne contrôle pas ; visez les défenseurs."),
        ThemeInfo("knightEndgame", "Finale de cavaliers", "Jongler avec les fourchettes en fin de partie.", G_ENDGAME, "Le cavalier est lent : gagnez un temps par une fourchette ou éloignez-le de l'action."),
        ThemeInfo("queenEndgame", "Finale de dames", "Manier échecs et pions passés avec les dames seules.", G_ENDGAME, "Cherchez la suite d'échecs qui gagne du matériel ou escorte un pion, sans autoriser l'échec perpétuel."),
        ThemeInfo("queenRookEndgame", "Finale dame et tour", "Coordonner les pièces lourdes en fin de partie.", G_ENDGAME, "Les pièces lourdes se coordonnent sur les lignes ouvertes : visez le roi ou la dernière rangée."),
    )

    val byKey = all.associateBy { it.key }

    // Du plus caractéristique au plus général : le premier thème présent définit le « type » du problème.
    private val PRIORITY = listOf(
        // 1. Mats nommés, 2. mats en N coups, 3. motifs tactiques, 4. thèmes généraux.
        "smotheredMate", "anastasiaMate", "arabianMate", "bodenMate", "doubleBishopMate", "dovetailMate", "hookMate", "backRankMate",
        "mateIn1", "mateIn2", "mateIn3", "mateIn4", "mateIn5",
        "underPromotion", "enPassant", "castling", "doubleCheck", "discoveredCheck", "zugzwang", "interference", "xRayAttack",
        "trappedPiece", "capturingDefender", "intermezzo", "clearance", "attraction", "deflection", "skewer", "pin", "fork",
        "discoveredAttack", "hangingPiece", "quietMove", "defensiveMove", "sacrifice", "promotion", "advancedPawn",
        "attackingF2F7", "exposedKing", "kingsideAttack", "queensideAttack",
        "pawnEndgame", "rookEndgame", "bishopEndgame", "knightEndgame", "queenEndgame", "queenRookEndgame",
        "opening", "endgame", "middlegame",
    )

    /**
     * Type principal d'un problème (thèmes Lichess séparés par des espaces).
     * Une seule règle, partout dans l'application : recherche, écran, Récents et images exportées.
     */
    fun primary(themes: String): ThemeInfo {
        val set = themes.trim().split(' ').toSet()
        return PRIORITY.firstOrNull { it in set }?.let { byKey[it] } ?: byKey.getValue("middlegame")
    }

    /**
     * Thème à afficher pour un problème. Un problème Lichess porte plusieurs thèmes (par exemple « mat en 1 »
     * ET « mat en queue d'aronde ») : si le joueur a choisi un thème dans le catalogue et que le problème le porte,
     * c'est ce thème qui est affiché ; sinon, le type principal.
     */
    fun shown(themes: String, chosen: String): ThemeInfo =
        byKey[chosen]?.takeIf { " $chosen " in " ${themes.trim()} " } ?: primary(themes)

    /** Ordre d'affichage du catalogue : les mats d'abord. */
    val groups = listOf(G_MATE, G_TACTIC, G_ATTACK, G_SPECIAL, G_ENDGAME, G_PHASE)

    val BANDS = listOf("Tous", "Débutant", "Intermédiaire", "Avancé", "Expert")
    fun bandRange(band: Int): IntRange = when (band) {
        1 -> 0..1199
        2 -> 1200..1599
        3 -> 1600..1999
        4 -> 2000..4000
        else -> 0..4000
    }
    fun bandOf(rating: Int): Int = when {
        rating < 1200 -> 1
        rating < 1600 -> 2
        rating < 2000 -> 3
        else -> 4
    }
}
