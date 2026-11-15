package com.ivanherboch.chessduck.chess

/**
 * Notation des coups (SAN anglaise) prononcée en français pour la synthèse vocale :
 * « Nxe5+ » → « cavalier prend e5, échec », « O-O » → « petit roque », « exd5 » → « e prend d5 ».
 */
object SpokenFr {
    /** Coup SAN isolé dans une phrase, avec son éventuel numéro (« 3. », « 3... »), qui n'est pas lu. */
    private val SAN_IN_TEXT = Regex("""(?<!\w)(?:\d+\.(?:\.\.)?\s?)?(O-O-O|O-O|[KQRBN]?[a-h]?[1-8]?x?[a-h][1-8](?:=[QRBN])?[+#]?)(?!\w)""")

    fun move(san: String): String {
        val suffix = when {
            san.endsWith("#") -> ", mat"
            san.endsWith("+") -> ", échec"
            else -> ""
        }
        val body = san.trimEnd('+', '#', '!', '?')
        if (body == "O-O-O") return "grand roque$suffix"
        if (body == "O-O") return "petit roque$suffix"
        val core = body.substringBefore('=')
        val type = if (core.first().isUpperCase()) pieceType(pieceFromChar(core.first())) else PAWN
        val squares = if (type == PAWN) core else core.drop(1)
        val promo = body.substringAfter('=', "").firstOrNull()?.let { "promotion en ${PieceLexiconFr.name(pieceType(pieceFromChar(it)))}" }
        return listOfNotNull(
            PieceLexiconFr.name(type).takeIf { type != PAWN },
            squares.dropLast(2).removeSuffix("x").ifEmpty { null },
            "prend".takeIf { 'x' in squares },
            squares.takeLast(2),
            promo,
        ).joinToString(" ") + suffix
    }

    /** Phrase à lire : chaque coup en notation est remplacé par sa forme parlée, la mise en gras est retirée. */
    fun text(s: String): String = SAN_IN_TEXT.replace(s.replace("**", "")) { move(it.groupValues[1]) }
}
