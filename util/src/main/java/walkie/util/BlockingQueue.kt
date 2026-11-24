package walkie.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import walkie.util.api.BlockingQueueApi
import walkie.util.api.BlockingQueueResult
import kotlin.time.Duration.Companion.milliseconds

class BlockingQueue<T> (
    override val name: String,
    override val capacity: Int = 100,
    override val scope: CoroutineScope,
    private val channel: Channel<T> = Channel<T>(capacity),
) : BlockingQueueApi<T> {
    private val logDString: String
        get() = "name: $name"
    val tag = name

    companion object {
        const val TAG = "BlockingQueue"
        val TAGKClass = BlockingQueue::class
    }

    init {
        logging(true)
        logd(tag, "This simple name: ${this::class.java.simpleName}/$name")
    }

    fun logD(tag: String = this.tag) {
        logd(TAGKClass, tag, "$tag: name: $name")
    }

    override fun enqueue(element: T): Boolean {
        val tag = "${this.tag}/enqueue/${randomString(2U)}"

        logd(TAGKClass, tag, "enqueue Enter: $logDString")

        val sendResult = channel.trySend(element).isSuccess

        logd(TAGKClass, tag, "enqueue Exit: $logDString sendResult: $sendResult")

        return sendResult
    }

    override suspend fun enqueueRetry(element: T, retries: Int, retryDelay: Long): Boolean {
        val tag = "${this.tag}/enqueueRetry/${randomString(2U)}"

        logd(TAGKClass, tag, "enqueue Enter: $logDString, retries: $retries")

        if (retries < 0) {
            logd(TAGKClass, tag, "enqueue Exit: $logDString, retries: $retries")
            return false
        }

        repeat(retries + 1) { attempt ->
            val attemptResult = channel.trySend(element)

            if (attemptResult.isSuccess) return true
            if (attemptResult.isClosed) return false
            if (attempt < retries) {
                delay(retryDelay.milliseconds)
            }
        }

        return false
    }

    override fun enqueueRetryBestEffort(element: T, retries: Int, retryDelay: Long) {
        val tag = "${this.tag}/enqueueRetry/${randomString(2U)}"

        logd(TAGKClass, tag, "enqueue Enter: $logDString, retries: $retries")

        if (retries < 0) {
            logd(TAGKClass, tag, "enqueue Exit: $logDString, retries: $retries")
            return
        }

        val attemptResult = channel.trySend(element)

        if (attemptResult.isClosed) {
            logd(TAGKClass, tag, "enqueue Exit: Channel closed $logDString, retries: $retries")
            return
        }

        if (!attemptResult.isSuccess && retries > 0) {
            logd(TAGKClass, tag, "enqueue Exit: $logDString, retries: $retries")
            scope.launch {
                delay(retryDelay.milliseconds)
                enqueueRetryBestEffort(element, retries - 1, retryDelay)
            }
            return
        }

        logd(TAGKClass, tag, "enqueue Exit: $logDString sendResult: ${attemptResult.isSuccess}")
    }

    override fun tryDequeue(): BlockingQueueResult<T> {
        val tag = "${this.tag}/tryDequeue/${randomString(2U)}"

        logd(TAGKClass, tag, "tryDequeue Enter: $logDString")

        val res = channel.tryReceive()
        logd(TAGKClass, tag, "tryDequeue res: $res")

        return when {
            res.isSuccess -> {
                logd(TAGKClass, tag, "tryDequeue Exit Success: $logDString")
                BlockingQueueResult.Success(res.getOrThrow())
            }
            res.isClosed -> {
                logd(TAGKClass, tag, "tryDequeue Exit Queue closed: $logDString")
                BlockingQueueResult.Closed
            }
            else -> {
                BlockingQueueResult.Empty
            }
        }
    }

    override suspend fun dequeue(): T {
        val tag = "${this.tag}/dequeue/${randomString(2U)}"

        logd(TAGKClass, tag, "dequeue Enter: $logDString")
        val ret = channel.receive()

        logd(TAGKClass, tag, "dequeue Exit: $logDString")
        return ret
    }
}
