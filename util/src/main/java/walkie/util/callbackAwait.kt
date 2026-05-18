package walkie.util

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend inline fun <T>awaitValue(
    crossinline action: ((T) -> Unit) -> Unit
): T = suspendCancellableCoroutine { continuation ->
    action { out: T ->
        if (continuation.isActive) {
            continuation.resume(out)
        }
    }

    continuation.invokeOnCancellation {
        /* TBD */
    }
}

sealed class CallbackResult<out T, out E> {
    data class Success<out T>(val value: T) : CallbackResult<T, Nothing>()
    data class Failure<out E>(val reason: E?) : CallbackResult<Nothing, E>()
}

interface CallbackListener<T, E> {
    fun onSuccess(value: T)
    fun onFailure(reason: E? = null)
}

suspend inline fun <T, E>awaitResult(
    crossinline action: (CallbackListener<T, E>) -> Unit
): CallbackResult<T, E> = suspendCancellableCoroutine { continuation ->
    val listener = object : CallbackListener<T, E> {
        override fun onSuccess(value: T) {
            if (continuation.isActive) {
                continuation.resume(CallbackResult.Success(value))
            }
        }

        override fun onFailure(reason: E?) {
            if (continuation.isActive) {
                continuation.resume(CallbackResult.Failure(reason))
            }
        }
    }

    action(listener)

    continuation.invokeOnCancellation {
        /* TBD */
    }
}