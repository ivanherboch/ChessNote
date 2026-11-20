package com.ivanherboch.chessduck.content

import com.ivanherboch.chessduck.chess.START_FEN

// Fichier généré par scripts/gen_lessons.py + LessonCompilerTool — modifier scripts/lessons/*.txt.
// Coups en UCI, validés à la génération : aucune analyse de texte ni de notation SAN au démarrage.
// Cases des repères : 0 = a1 … 63 = h8.

internal val MODEL_GAME_LESSONS: List<LessonData> = listOf(
    LessonData(
        id = 114,
        title = "La partie de l'Opéra : Morphy, Paris 1858",
        summary = "Paul Morphy affronte le duc de Brunswick et le comte Isouard pendant une soirée à l'Opéra. Une démonstration de développement rapide, de lignes ouvertes et d'attaque.",
        heroWhite = true,
        fen = START_FEN,
        uci = "e2e4 e7e5 g1f3 d7d6 d2d4 c8g4 d4e5 g4f3 d1f3 d6e5 f1c4 g8f6 f3b3 d8e7 b1c3 c7c6 c1g5 b7b5 c3b5 c6b5 c4b5 b8d7 e1c1 a8d8 d1d7 d8d7 h1d1 e7e6 b5d7 f6d7 b3b8 d7b8 d1d8",
        texts = listOf(
            "Morphy occupe le centre avec son pion roi.",
            "Les Noirs répondent de la même façon.",
            "Le cavalier se développe et attaque le pion e5.",
            "La défense Philidor : solide, mais un peu passive.",
            "Morphy ouvre tout de suite le centre pour profiter de son avance de développement.",
            "Le fou cloue le cavalier f3 sur la dame. Ce fou va bientôt disparaître.",
            "Le pion prend en e5 et attaque le pion d6.",
            "Les Noirs échangent leur fou contre le cavalier pour ne pas perdre un pion. Ils cèdent leur paire de fous.",
            "La dame reprend et se trouve déjà active, face à la case f7.",
            "Les Noirs reprennent le pion. Le matériel est égal, mais les Blancs ont deux pièces de plus en jeu.",
            "Le fou sort à son tour. Avec la dame, il menace le mat en f7.",
            "Les Noirs parent la menace en développant un cavalier.",
            "La dame vise à la fois f7 et b7 : une double attaque.",
            "La dame noire protège f7. Elle bouche la route de son propre fou f8.",
            "Morphy ne prend pas le pion b7 : il préfère sortir une pièce de plus.",
            "Les Noirs protègent la case b7 et préparent b5.",
            "Le fou cloue le cavalier f6 devant la dame. Tous les Blancs participent à l'attaque.",
            "Les Noirs chassent le fou c4, mais affaiblissent leur aile dame.",
            "Un sacrifice : le cavalier se donne pour ouvrir les lignes vers le roi noir.",
            "Les Noirs acceptent le cadeau.",
            "Le fou reprend avec échec. Le roi noir est toujours au centre.",
            "Le cavalier s'interpose, mais il est cloué sur le roi.",
            "Le grand roque met le roi à l'abri et amène la tour sur la colonne d, face au cavalier cloué.",
            "La tour noire protège le cavalier d7.",
            "Morphy prend le cavalier : encore un sacrifice pour garder l'initiative.",
            "La tour noire reprend.",
            "La seconde tour arrive sur la colonne ouverte. Toutes les pièces blanches attaquent.",
            "La dame noire tente de soulager sa position.",
            "Le fou prend la tour avec échec.",
            "Le cavalier reprend. Les Noirs ont beaucoup plus de matériel, mais leur roi est sans défense.",
            "Un sacrifice de dame ! Elle se place en b8 pour attirer le cavalier loin de la case d8.",
            "Le cavalier est obligé de prendre la dame.",
            "La tour donne échec et mat, protégée par le fou g5. À retenir : développez vite toutes vos pièces, ouvrez les lignes vers le roi resté au centre, et n'hésitez pas à sacrifier du matériel pour garder l'initiative.",
        ),
        level = 3,
        kind = LessonKind.MODEL_GAME,
        takeaway = "Développez vite toutes vos pièces, ouvrez les lignes vers le roi resté au centre, et n'hésitez pas à sacrifier du matériel pour garder l'initiative.",
    ),
)
