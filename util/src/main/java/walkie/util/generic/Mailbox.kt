package walkie.util.generic

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout

class Mailbox<T>(val capacity: Int) {
    val mbox = Channel<T>(capacity)

    /*
    suspend fun receive(timeout: Long): T? {
        return withTimeoutOrNull(timeout) {
            mbox.receive()
        }
    }
    */

    suspend fun receive(timeout: Long): T? {
        return try {
            withTimeout(timeout) {
                mbox.receive()
            }
        } catch (e: TimeoutCancellationException) {
            null
        }
    }

    suspend fun await(timeout: Long): T? = receive (timeout)

    suspend fun send(element: T) {
        mbox.send(element)
    }

    fun tryReceive(): T? {
        return mbox.tryReceive().getOrNull()
    }

    fun close() {
        mbox.close()
    }
}