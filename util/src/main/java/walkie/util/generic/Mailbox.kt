package walkie.util.generic

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

sealed class MailboxData<out T> {
    data class Message<T>(val value: T) : MailboxData<T>()

    fun dataOrNull(): T? = if (this is Message<T>) {
        value
    } else {
        null
    }

    object Timeout : MailboxData<Nothing>()
}

open class Mailbox<T>(capacity: Int) {
    val mbox = Channel<T>(capacity)

    suspend fun receive(timeout: Duration): MailboxData<T> {
        return try {
            withTimeout(timeout) {
                MailboxData.Message (mbox.receive())
            }
        } catch (e: TimeoutCancellationException) {
            MailboxData.Timeout
        }
    }

    suspend fun await(timeout: Duration) = receive(timeout)

    suspend fun send(element: T) {
        mbox.send(element)
    }

    fun trySend(element: T) {
        mbox.trySend(element)
    }

    fun close() {
        mbox.close()
    }
}