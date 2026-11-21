package com.ivanherboch.chessduck.feature

import androidx.compose.runtime.Immutable
import com.ivanherboch.chessduck.chess.Line
import com.ivanherboch.chessduck.chess.Move
import com.ivanherboch.chessduck.content.Lesson
import com.ivanherboch.chessduck.content.LessonCategory
import com.ivanherboch.chessduck.ui.Refutation
import com.ivanherboch.chessduck.ui.BoardAnim
import com.ivanherboch.chessduck.ui.Tone

// États et modèles de l'Académie (exposés à l'écran par AcademyViewModel), et libellés des paliers.

/** Signal ponctuel de retour haptique (réussite ou erreur), identifié pour n'être joué qu'une fois. */
@Immutable
data class Pulse(val id: Long, val good: Boolean)

/** Nature d'une série de problèmes : validation d'une leçon, révision, réflexe, placement ou examen. */
enum class PracticeKind { LESSON, REVIEW, ONLY_MOVE, PLACEMENT, EXAM }

/**
 * Série de problèmes inédits. Un problème compte s'il est résolu sans erreur et sans l'indice qui montre le coup.
 */
@Immutable
data class Practice(
    val kind: PracticeKind = PracticeKind.LESSON,
    val ids: List<String> = emptyList(),
    val index: Int = 0,
    val results: List<Boolean> = emptyList(),
    val line: Line? = null,
    val cursor: Int = 0,
    val hintStage: Int = 0,
    /** Le problème en cours est raté : la solution est montrée, on passe au suivant. */
    val failed: Boolean = false,
    /** Le problème en cours est résolu. */
    val solved: Boolean = false,
    val anim: BoardAnim? = null,
    val message: String = "",
    val tone: Tone = Tone.INFO,
    val loading: Boolean = true,
    /** Série terminée (résultat dans [results]). */
    val finished: Boolean = false,
    /** Palier concerné (examen). */
    val level: Int = 2,
    /** Réfutation d'un mauvais coup (comme dans les Problèmes) : position affichée, puis compte à rebours. */
    val refute: Refutation? = null,
    val refuteUntil: Long = 0L,
    val refuteHold: Long = 0L,
    /** Une erreur a été commise sur ce problème : même résolu ensuite, il ne compte pas. */
    val hadError: Boolean = false,
    /** Demi-coups déjà joués du problème (on peut revenir en arrière et avancer jusque-là). */
    val progress: Int = 0,
) {
    val heroWhite: Boolean get() = line?.positions?.get(0)?.whiteToMove == false
    val score: Int get() = results.count { it }
}

/** QCM de fin de leçon : choix mélangés, indice de la bonne réponse, réponse donnée. */
@Immutable
data class QuizState(val choices: List<String>, val correct: Int, val picked: Int? = null)

/** Révisions du jour : file des leçons dues et nombre de révisions réussies. */
@Immutable
data class ReviewSession(val queue: List<Int>, val index: Int = 0, val passed: Int = 0, val finished: Boolean = false)

/** « Réviser mon répertoire » : suite de (leçon, variante) jouées en Défi. */
@Immutable
data class RepertoireSession(val white: Boolean, val items: List<Pair<Int, Int>>, val index: Int = 0, val results: List<Boolean> = emptyList(), val finished: Boolean = false)

/** Accueil de l'Académie : palier, leçon à reprendre, révisions dues, leçon conseillée. */
@Immutable
data class AcademyHome(
    val level: Int = 2,
    val placementDone: Boolean = false,
    val due: List<Int> = emptyList(),
    val recommended: Int? = null,
    val continueId: Int? = null,
    /** Paliers dont l'examen a été réussi. */
    val examsPassed: Set<Int> = emptySet(),
    /** Finales jouables validées (une leçon qui les entraîne est maîtrisée). */
    val endgamesDone: Set<EndgameFamily> = emptySet(),
    /** Leçons du palier en cours : maîtrisées, et au total (barre de progression du tiroir). */
    val levelMastered: Int = 0,
    val levelTotal: Int = 0,
)

/** Comment une leçon se valide après le Défi. */
enum class Validation { NONE, PUZZLES, ENDGAME }

@Immutable
data class AcademyState(
    val lessonId: Int = 1,
    /** null = toutes les catégories. */
    val filter: LessonCategory? = null,
    val challenge: Boolean = false,
    val cursor: Int = 0,
    /** Ligne affichée : 0 = principale, 1… = variante. */
    val variant: Int = 0,
    val playing: Boolean = false,
    val flipped: Boolean = false,
    /** Échiquier retourné par l'élève pendant une séance (finale, série, réflexes) : s'ajoute à l'orientation naturelle. */
    val sessionFlipped: Boolean = false,
    val anim: BoardAnim? = null,
    val hintStage: Int = 0,
    val message: String = "",
    val tone: Tone = Tone.INFO,
    val done: Boolean = false,
    /** Mode Guidé : arrêts « À vous » déjà résolus (ou dévoilés) dans la leçon ouverte. */
    val answered: Set<Int> = emptySet(),
    /** Mode Guidé : réaction au coup proposé pendant un arrêt (null : aucun essai). */
    val note: String? = null,
    val noteTone: Tone = Tone.INFO,
    /** Mode Défi : erreurs et indice le plus fort utilisé, pour les étoiles. */
    val errors: Int = 0,
    val maxHint: Int = 0,
    /** Étoiles obtenues au Défi qui vient d'être réussi (0 tant qu'il n'est pas fini). */
    val stars: Int = 0,
    /** Partie modèle (« Devinez le coup ») : points gagnés et points possibles. */
    val guessScore: Int = 0,
    val guessMax: Int = 0,
    /** Partie modèle, mode Deviner : seuls les moments clés sont à deviner ; les autres coups se jouent seuls. */
    val keyMoments: Boolean = false,
    /** L'Ordinateur juge le coup proposé : l'échiquier attend. */
    val judging: Boolean = false,
    /** Réfutation d'un mauvais coup du Défi : position après la réponse adverse, compte à rebours avant le retour. */
    val refute: Refutation? = null,
    val refuteUntil: Long = 0L,
    val refuteHold: Long = 0L,
    /**
     * Coup de l'élève affiché tel qu'il l'a joué pendant que l'Ordinateur le juge (et, s'il est aussi bon, un court
     * instant avant que la leçon ne reprenne son propre coup).
     */
    val tentative: Refutation? = null,
    /** Coup de la leçon montré par une flèche verte (gratuite) après un coup aussi bon mais différent. */
    val suggest: Move? = null,
    val pulse: Pulse? = null,
    /** Série de problèmes en cours (null : leçon). */
    val practice: Practice? = null,
    val quiz: QuizState? = null,
    val review: ReviewSession? = null,
    val repertoire: RepertoireSession? = null,
    /** La séance en cours a été lancée depuis le tiroir « Entraînements » : en la quittant, on y revient. */
    val fromDrawer: Boolean = false,
    /** Incrémenté quand une séance du tiroir se termine : l'écran rouvre alors le tiroir. */
    val drawerReturn: Int = 0,
)

/** Fiche de la leçon, affichée et lue à son ouverture : objectif, règle, conditions, idées et pièges. */
fun objectiveText(l: Lesson): String = buildString {
    append(l.summary)
    append("\n\nÀ retenir : ").append(l.takeaway)
    if (l.conditions.isNotEmpty()) append("\n\nConditions : ").append(l.conditions)
    if (l.ideas.isNotEmpty()) append("\n\nLes idées : ").append(l.ideas.joinToString(" "))
    if (l.traps.isNotEmpty()) append("\n\nLes pièges : ").append(l.traps.joinToString(" "))
}

/** Question posée à chaque arrêt du mode Guidé (lue après le commentaire précédent). */
const val ASK = "À vous : que jouez-vous ici ?"

/** Nom d'un palier pour l'affichage : « 2 · Débutant ». */
fun levelName(level: Int): String = "${level.coerceIn(1, 5)} · ${levelLabel(level)}"

/** Nom d'un palier pour TalkBack et la voix : « 2, Débutant » (sans point médian). */
fun levelSpoken(level: Int): String = "${level.coerceIn(1, 5)}, ${levelLabel(level)}"

private fun levelLabel(level: Int) = when (level) {
    1 -> "Les bases"
    2 -> "Débutant"
    3 -> "Novice"
    4 -> "Club"
    else -> "Confirmé"
}

/** En-tête d'une séance : nature (« Validation · 5 problèmes ») et sujet (titre de la leçon ou de l'exercice). */
@Immutable
data class SessionTitle(val label: String, val title: String)
