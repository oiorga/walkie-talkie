package walkie.util

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

class Gate() {
    private val signal = Channel<Unit>(Channel.CONFLATED)

    suspend fun await(timeout: Long) {
        withTimeoutOrNull(timeout.milliseconds) {
            signal.receive()
        }
    }

    fun open() {
        signal.trySend(Unit)
    }
}
