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

    /**
     * Mots que la synthèse vocale française prononce mal (« pat » lu « pa », « gambit » lu « gambi »…), écrits
     * comme ils se disent. Ce lexique ne sert qu'à la voix : l'écran affiche toujours l'orthographe correcte.
     */
    private val PRONUNCIATION = listOf(
        "pat" to "patte", "mat" to "matte", "mats" to "mattes",
        "gambit" to "gambitte", "gambits" to "gambittes",
        "fianchetto" to "fianquétto", "zugzwang" to "zougtsvang",
        "Giuoco" to "Djouoco", "Fegatello" to "Fégatello", "Kieseritzky" to "Kizéritski",
        "Sveshnikov" to "Svéchnikov", "Najdorf" to "Naïdorf", "Winawer" to "Vinaver", "Tarrasch" to "Tarache",
        "Caro-Kann" to "Caro-Kane", "Grünfeld" to "Grunfèlde", "Alekhine" to "Aliékhine", "Pirc" to "Pirtse",
        "Rubinstein" to "Roubinnchtaïne", "Fischer" to "Fichère", "Lucena" to "Loucéna", "Boden" to "Bodène",
        "Evans" to "Évanse", "Petrov" to "Pétrof", "Kasparov" to "Kasparof",
        "Réti" to "Rétti", "Philidor" to "Filidor", "Anastasie" to "Anastazie", "Scandinave" to "Skandinave",
        "Nimzo-indienne" to "Nimmzo-indienne", "Nimzo" to "Nimmzo",
        "Morphy" to "Morfi", "Brunswick" to "Brunesvik", "Isouard" to "Izouar",
        // Étape 3 : noms propres et termes des nouvelles leçons.
        "Vancura" to "Vantsoura", "Saavedra" to "Saavédra", "Anderssen" to "Andersséne", "Nimzowitsch" to "Nimmzovitch",
        "Botvinnik" to "Botvinik", "Lasker" to "Laskère", "Bauer" to "Baouère", "Pillsbury" to "Pilsbéri",
        "Greco" to "Gréco", "Taimanov" to "Taïmanof", "Trompowsky" to "Trompofski", "Marshall" to "Marchal",
        "Benko" to "Bènnko", "desperado" to "dèspérado", "Smith-Morra" to "Smisse-Morra", "Kan" to "Kanne",
        "Panov" to "Panof", "Budapest" to "Boudapèste", "Tartakower" to "Tartakovère", "Rotlewi" to "Rotlévi",
        "Lodz" to "Woudj", "Sämisch" to "Zémiche", "AVRO" to "Avro", "Capablanca" to "Capablannca",
        "Rossolimo" to "Rossolimmo",
    ).map { (word, said) -> Regex("(?<![\\p{L}-])${Regex.escape(word)}(?![\\p{L}-])", RegexOption.IGNORE_CASE) to said }

    /**
     * Phrase à lire : chaque coup en notation est remplacé par sa forme parlée, la mise en gras est retirée,
     * et les mots difficiles sont réécrits comme ils se prononcent.
     */
    fun text(s: String): String =
        PRONUNCIATION.fold(SAN_IN_TEXT.replace(s.replace("**", "")) { move(it.groupValues[1]) }) { acc, (re, said) -> re.replace(acc, said) }
}
