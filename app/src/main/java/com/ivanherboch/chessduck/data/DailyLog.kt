package com.ivanherboch.chessduck.data

/**
 * Journal de la « Séance du jour » : les étapes faites le jour [day] (jour époque, fuseau du téléphone) et la série de
 * jours consécutifs où la séance a été terminée ([streak], dernier jour réussi [streakDay]).
 */
data class DailyLog(val day: Long = 0, val done: Set<String> = emptySet(), val streak: Int = 0, val streakDay: Long = 0) {
    /** Étapes faites [today] (vide si le journal date d'un autre jour). */
    fun doneOn(today: Long): Set<String> = if (day == today) done else emptySet()

    /** Série en cours : elle tient tant que la séance a été terminée aujourd'hui ou hier. */
    fun streakOn(today: Long): Int = if (streakDay >= today - 1) streak else 0

    /** Note l'étape [step] faite [today] ; la séance est terminée quand toutes les étapes [required] le sont. */
    fun mark(step: String, today: Long, required: Set<String>): DailyLog {
        val d = doneOn(today) + step
        if (streakDay == today || required.isEmpty() || !d.containsAll(required)) return copy(day = today, done = d)
        return DailyLog(today, d, if (streakDay == today - 1) streak + 1 else 1, today)
    }

    fun encode(): String = "$day|${done.sorted().joinToString(",")}|$streak|$streakDay"

    companion object {
        fun decode(v: String?): DailyLog {
            val p = (v ?: return DailyLog()).split('|')
            if (p.size != 4) return DailyLog()
            return DailyLog(
                p[0].toLongOrNull() ?: 0, p[1].split(',').filter { it.isNotBlank() }.toSet(), p[2].toIntOrNull() ?: 0, p[3].toLongOrNull() ?: 0,
            )
        }
    }
}

/** Gaffe arrêtée par le filet de sécurité du Jeu Libre : point faible ([topic], nom de ReviewTopic) et moment. */
data class NetEvent(val topic: String, val at: Long)

internal fun encodeNetEvents(list: List<NetEvent>) = list.joinToString(",") { "${it.topic}:${it.at}" }

internal fun decodeNetEvents(v: String?): List<NetEvent> = (v ?: "").split(',').mapNotNull { e ->
    val (t, at) = e.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
    NetEvent(t.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null, at.trim().toLongOrNull() ?: return@mapNotNull null)
}

/** Nombre de gaffes arrêtées gardées en mémoire (les plus anciennes sont oubliées). */
const val NET_EVENTS_MAX = 40
