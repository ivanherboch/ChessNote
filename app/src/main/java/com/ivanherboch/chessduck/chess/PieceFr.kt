package com.ivanherboch.chessduck.chess

/**
 * Lexique français unique des pièces (indicé par type : PAWN … KING) : nom, pluriel, genre,
 * articles, pronom et possessifs. Toutes les phrases de l'application (descriptions TalkBack,
 * commentaires de revue, images de solution, barres de prises) s'appuient sur ces formes.
 */
object PieceLexiconFr {
    private val NAMES = arrayOf("", "pion", "cavalier", "fou", "tour", "dame", "roi")
    private val PLURALS = arrayOf("", "pions", "cavaliers", "fous", "tours", "dames", "rois")

    /** Nom commun en minuscules : « cavalier ». */
    fun name(type: Int): String = NAMES[type]

    /** Nom avec majuscule initiale : « Cavalier ». */
    fun title(type: Int): String = PIECE_NAMES_FR[type]

    fun plural(type: Int): String = PLURALS[type]

    /** Accord en nombre : « 1 pion », « 3 pions ». */
    fun count(type: Int, n: Int): String = "$n ${if (n > 1) plural(type) else name(type)}"

    /** Genre grammatical : seules la tour et la dame sont féminines. */
    fun isFeminine(type: Int): Boolean = type == ROOK || type == QUEEN

    /** Article défini : « le pion », « la dame ». */
    fun definite(type: Int): String = (if (isFeminine(type)) "la " else "le ") + name(type)

    /** Article défini en début de phrase : « Le pion », « La dame ». */
    fun definiteCapitalized(type: Int): String = definite(type).replaceFirstChar { it.uppercaseChar() }

    /** Article indéfini : « un cavalier », « une dame ». */
    fun indefinite(type: Int): String = (if (isFeminine(type)) "une " else "un ") + name(type)

    /** Pronom complément : « le », « la ». */
    fun pronoun(type: Int): String = if (isFeminine(type)) "la" else "le"

    /** Possessif à la 3ᵉ personne : « sa dame », « son fou ». */
    fun possessive(type: Int): String = (if (isFeminine(type)) "sa " else "son ") + name(type)
}
