package com.ivanherboch.chessduck.util

import java.text.Normalizer

private val COMBINING_MARKS = Regex("\\p{M}")

/** Forme de recherche d'un texte : sans accents ni diacritiques, en minuscules (« Échec » → « echec »). */
fun normalizeForSearch(text: String): String =
    COMBINING_MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase()
