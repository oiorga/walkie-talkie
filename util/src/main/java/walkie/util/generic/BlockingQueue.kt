package walkie.util.generic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import walkie.util.api.BlockingQueueApi
import walkie.util.api.BlockingQueueResult
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString
import kotlin.time.Duration.Companion.milliseconds

class BlockingQueue<T> (
    override val name: String = "BlockingQueue",
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
        logd(tag, "This simple name: ${this::class.java.simpleName}")
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

/**
 * old era abstract class GenericQueueAbs
 *
 * Working/Alias against with semaphore protection:
 *        itemList: MutableList<T> = mutableListOf()
 **/
/*
abstract class GenericBlockingQueueAbsOld<T> (
    private val name: String = "GenericBlockingQueueAbs",
    private val capacity: Int = 1,
    private val itemList: LinkedList<T> = LinkedList<T>()
) : MutableList<T> by itemList {
    private val sem: Semaphore = Semaphore(capacity, capacity)
    private val bSem = Semaphore(1)
    private var postpone = 0

    private val logDString: String
        get() = "name: $name availablePermits(bSem/sem)/size: ${bSem.availablePermits}/${sem.availablePermits}/$size"
    private val maxSize = capacity
    val tag = name
    final override val size: Int
        get() = itemList.size

    companion object {
        const val TAG = "GenericBlockingQueueAbs"
        val TAGKClass = GenericBlockingQueueAbsOld::class
    }

    init {
        logging(true)
        logd(tag, "This simple name: ${this::class.java.simpleName} size: $size")
    }

    fun logD(tag: String = this.tag) {
        logd(TAGKClass, tag, "$tag: name: $name availablePermits: ${sem.availablePermits} Q size: $size/$maxSize")
    }

    override fun removeFirst(): T {
        val tag = "removeFirst/${randomString(2U)}"
        logd(tag, "")
        return itemList.removeFirst()
    }

    override fun removeLast(): T {
        val tag = "removeLast/${randomString(2U)}"
        logd(tag, "")
        return itemList.removeLast()
    }

    override fun clear() {
        val tag = "clear/${randomString(2U)}"
        logd(tag, "GenericBlockingQueueAbs/$name clear() should not be called directly")
        /* throw (Error("GenericBlockingQueueAbs/$name clear() should not be called directly")) */
    }

    suspend fun qClear() {
        val tag = "${this.tag}/qClear/${randomString(2U)}"
        var lastAcquire: Boolean? = null
        bSem.acquire()
        logd(tag, "Queue(0) $name calling qClear permits: $capacity ${sem.availablePermits} $size")
        itemList.clear()
        `try`(TAGKClass, tag) {
            for (i in 0..<sem.availablePermits) {
                lastAcquire = sem.tryAcquire()
            }
        }
        logd(tag, "Queue(1) $name calling qClear permits: $capacity/${sem.availablePermits}/$size lastAcquire: $lastAcquire")
        bSem.release()
    }

    suspend fun enqueue(element: T): Boolean {
        val tag = "${this.tag}/enqueue/${randomString(2U)}"
        var b = false

        logd(TAGKClass, tag, "add: $logDString")

        bSem.acquire()
        if (postpone > 0) {
            postpone--
        } else {
            if (maxSize > size) {
                if (sem.availablePermits < capacity) {
                    b = itemList.add(element)
                    sem.release()
                } else {
                    logd(tag, "$tag -> [size/maxSize: $size/$maxSize] [sem.availablePermits < permits: ${sem.availablePermits} < $capacity] [$logDString]")
                    /*
                    b = itemList.add(element)
                    sem.release()
                    */
                }
            } else {
                postpone = maxSize / 2
                logd(TAGKClass, tag, "add $logDString: Q is full. Dropping.")
            }
        }
        bSem.release()
        return b
    }

    suspend fun dequeue(): T? {
        val tag = "${this.tag}/dequeue/${randomString(2U)}"
        logd(TAGKClass, tag, logDString)
        sem.acquire()
        bSem.acquire()
        val ret = if (itemList.isEmpty()) {
            logd(tag, "itemList is Empty?")
            null
        } else {
            itemList.removeFirst()
        }
        bSem.release()
        logd(TAGKClass, tag, logDString)
        return ret
    }

}
*/