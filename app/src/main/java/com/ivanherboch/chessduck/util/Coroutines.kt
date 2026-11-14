package com.ivanherboch.chessduck.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Équivalent de [runCatching] pour du code suspendu : les erreurs ordinaires sont capturées dans un [Result],
 * mais une [CancellationException] est toujours relancée afin de respecter l'annulation structurée des coroutines.
 */
suspend inline fun <T> runCatchingSuspend(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
