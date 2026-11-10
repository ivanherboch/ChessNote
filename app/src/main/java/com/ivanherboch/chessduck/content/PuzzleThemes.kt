package com.ivanherboch.chessduck.content

/** Un thème officiel Lichess, traduit, avec un objectif pédagogique sans divulgâcher la solution. */
data class ThemeInfo(val key: String, val name: String, val goal: String, val group: String)

object PuzzleThemes {
    const val G_PHASE = "Phases de la partie"
    const val G_TACTIC = "Motifs tactiques"
    const val G_ATTACK = "Attaque & défense"
    const val G_SPECIAL = "Coups spéciaux"
    const val G_MATE = "Mats"
    const val G_ENDGAME = "Finales"

    val all = listOf(
        ThemeInfo("opening", "Ouverture", "Punir une imprécision dès les premiers coups.", G_PHASE),
        ThemeInfo("middlegame", "Milieu de partie", "Trouver la tactique quand toutes les pièces sont en jeu.", G_PHASE),
        ThemeInfo("endgame", "Finale", "Être précis quand il reste peu de pièces.", G_PHASE),

        ThemeInfo("fork", "Fourchette", "Attaquer deux cibles à la fois avec une seule pièce.", G_TACTIC),
        ThemeInfo("pin", "Clouage", "Exploiter une pièce qui ne peut pas bouger sans exposer plus précieux.", G_TACTIC),
        ThemeInfo("skewer", "Enfilade", "Attaquer une pièce de valeur pour gagner celle qui se cache derrière.", G_TACTIC),
        ThemeInfo("discoveredAttack", "Attaque à la découverte", "Déplacer une pièce pour démasquer l'attaque d'une autre.", G_TACTIC),
        ThemeInfo("discoveredCheck", "Échec à la découverte", "Démasquer un échec tout en créant une seconde menace.", G_TACTIC),
        ThemeInfo("doubleCheck", "Échec double", "Donner échec avec deux pièces : seul le roi peut répondre.", G_TACTIC),
        ThemeInfo("deflection", "Déviation", "Détourner un défenseur de sa tâche essentielle.", G_TACTIC),
        ThemeInfo("attraction", "Attraction", "Attirer une pièce adverse sur une case fatale.", G_TACTIC),
        ThemeInfo("sacrifice", "Sacrifice", "Donner du matériel pour obtenir bien davantage ensuite.", G_TACTIC),
        ThemeInfo("hangingPiece", "Pièce en prise", "Repérer et capturer une pièce non défendue.", G_TACTIC),
        ThemeInfo("trappedPiece", "Pièce enfermée", "Gagner une pièce qui n'a plus aucune case de fuite.", G_TACTIC),
        ThemeInfo("capturingDefender", "Élimination du défenseur", "Supprimer la pièce qui protège la cible.", G_TACTIC),
        ThemeInfo("clearance", "Dégagement", "Libérer une case ou une ligne pour une autre pièce.", G_TACTIC),
        ThemeInfo("interference", "Interception", "Couper la communication entre deux pièces adverses.", G_TACTIC),
        ThemeInfo("intermezzo", "Coup intermédiaire", "Intercaler une menace avant le coup attendu.", G_TACTIC),
        ThemeInfo("xRayAttack", "Rayons X", "Attaquer ou défendre à travers une pièce adverse.", G_TACTIC),
        ThemeInfo("quietMove", "Coup tranquille", "Jouer un coup calme qui prépare une menace imparable.", G_TACTIC),
        ThemeInfo("defensiveMove", "Coup défensif", "Trouver l'unique parade qui évite la catastrophe.", G_TACTIC),
        ThemeInfo("zugzwang", "Zugzwang", "Placer l'adversaire dans une position où tout coup l'affaiblit.", G_TACTIC),

        ThemeInfo("kingsideAttack", "Attaque à l'aile roi", "Assaillir un roi adverse roqué du petit côté.", G_ATTACK),
        ThemeInfo("queensideAttack", "Attaque à l'aile dame", "Assaillir un roi adverse roqué du grand côté.", G_ATTACK),
        ThemeInfo("exposedKing", "Roi exposé", "Profiter d'un roi mal protégé par ses pions.", G_ATTACK),
        ThemeInfo("attackingF2F7", "Attaque sur f2 ou f7", "Viser le pion le plus fragile du début de partie.", G_ATTACK),
        ThemeInfo("advancedPawn", "Pion avancé", "Exploiter un pion proche de la promotion.", G_ATTACK),

        ThemeInfo("promotion", "Promotion", "Transformer un pion en pièce au bon moment.", G_SPECIAL),
        ThemeInfo("underPromotion", "Sous-promotion", "Choisir autre chose qu'une dame en arrivant au bout.", G_SPECIAL),
        ThemeInfo("enPassant", "Prise en passant", "Se servir de cette règle spéciale des pions.", G_SPECIAL),
        ThemeInfo("castling", "Roque", "Mettre le roi à l'abri… avec une idée derrière la tête.", G_SPECIAL),

        ThemeInfo("mateIn1", "Mat en 1", "Donner échec et mat en un seul coup.", G_MATE),
        ThemeInfo("mateIn2", "Mat en 2", "Forcer le mat en deux coups.", G_MATE),
        ThemeInfo("mateIn3", "Mat en 3", "Calculer une suite de mat en trois coups.", G_MATE),
        ThemeInfo("mateIn4", "Mat en 4", "Calculer une suite de mat en quatre coups.", G_MATE),
        ThemeInfo("mateIn5", "Mat en 5 ou plus", "Voir loin pour conclure par un mat forcé.", G_MATE),
        ThemeInfo("backRankMate", "Mat du couloir", "Mater un roi prisonnier de sa dernière rangée.", G_MATE),
        ThemeInfo("smotheredMate", "Mat à l'étouffée", "Mater avec un cavalier un roi cerné par ses propres pièces.", G_MATE),
        ThemeInfo("anastasiaMate", "Mat d'Anastasie", "Coordonner cavalier et pièce lourde contre un roi au bord.", G_MATE),
        ThemeInfo("arabianMate", "Mat des Arabes", "Coordonner tour et cavalier contre un roi dans le coin.", G_MATE),
        ThemeInfo("bodenMate", "Mat de Boden", "Mater avec deux fous sur des diagonales croisées.", G_MATE),
        ThemeInfo("doubleBishopMate", "Mat des deux fous", "Mater avec deux fous sur des diagonales voisines.", G_MATE),
        ThemeInfo("dovetailMate", "Mat en queue d'aronde", "Mater avec la dame un roi gêné par ses propres pièces.", G_MATE),
        ThemeInfo("hookMate", "Mat du crochet", "Combiner tour, cavalier et pion contre le roi.", G_MATE),

        ThemeInfo("pawnEndgame", "Finale de pions", "Maîtriser opposition, carré et percées.", G_ENDGAME),
        ThemeInfo("rookEndgame", "Finale de tours", "Activer sa tour et son roi dans la finale la plus fréquente.", G_ENDGAME),
        ThemeInfo("bishopEndgame", "Finale de fous", "Tirer parti des diagonales quand il ne reste que des fous.", G_ENDGAME),
        ThemeInfo("knightEndgame", "Finale de cavaliers", "Jongler avec les fourchettes en fin de partie.", G_ENDGAME),
        ThemeInfo("queenEndgame", "Finale de dames", "Manier échecs et pions passés avec les dames seules.", G_ENDGAME),
        ThemeInfo("queenRookEndgame", "Finale dame et tour", "Coordonner les pièces lourdes en fin de partie.", G_ENDGAME),
    )

    val byKey = all.associateBy { it.key }
    val groups = listOf(G_PHASE, G_TACTIC, G_ATTACK, G_SPECIAL, G_MATE, G_ENDGAME)

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
