package walkie.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import walkie.util.generic.Mailbox

/*
class Gate: Mailbox<Unit>(Channel.RENDEZVOUS) {
    override fun open() {
        mbox.trySend(Unit)
    }
}

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
*/

class Gate() {
    private val signal = Channel<Unit>(Channel.CONFLATED)

    suspend fun await(timeout: Long) {
        withTimeoutOrNull(timeout) {
            signal.receive()
        }
    }

    fun open() {
        signal.trySend(Unit)
    }
}
