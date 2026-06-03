package walkie.util.generic

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

sealed class MailboxData<out T> {
    data class Message<T>(val value: T) : MailboxData<T>()
    object Timeout : MailboxData<Nothing>()
}

open class Mailbox<T>(val capacity: Int) {
    val mbox = Channel<T>(capacity)

    /*
    suspend fun receiveDataOrNull(timeout: Long): T? {
        return withTimeoutOrNull(timeout) {
            mbox.receive()
        }
    }
    */

    suspend fun receive(timeout: Long): MailboxData<T> {
        return try {
            withTimeout(timeout) {
                MailboxData.Message (mbox.receive())
            }
        } catch (e: TimeoutCancellationException) {
            MailboxData.Timeout
        }
    }

    suspend fun await(timeout: Long) = receive(timeout)

    suspend fun send(element: T) {
        mbox.send(element)
    }

    fun close() {
        mbox.close()
    }
}