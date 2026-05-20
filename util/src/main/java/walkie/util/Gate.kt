package walkie.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

class Gate() {
    private val signal = Channel<Unit>(Channel.RENDEZVOUS)

    suspend fun await(timeout: Long) {
        withTimeoutOrNull(timeout) {
            signal.receive()
        }
    }

    fun open() {
        signal.trySend(Unit)
    }
}

class Gate_CONFLATED(scope: CoroutineScope,
           val timeout: Long) {
    private val signal = Channel<Unit>(Channel.CONFLATED)

    suspend fun await() {
        withTimeoutOrNull(timeout) {
            signal.receive()
        }
    }

    fun open() {
        signal.trySend(Unit)
    }
}

