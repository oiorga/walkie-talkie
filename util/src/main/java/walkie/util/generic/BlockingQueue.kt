package walkie.util.generic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString
import walkie.util.`try`
import java.util.LinkedList
import kotlin.time.Duration.Companion.milliseconds

class BlockingQueue<T> (val name: String = "BlockingQueue", private val permits: Int = 1) : GenericBlockingQueueAbs<T>(name, permits)

abstract class GenericBlockingQueueAbs<T> (
    private val name: String = "GenericBlockingQueueAbs",
    private val capacity: Int = 100,
    private val channel: Channel<T> = Channel<T>(capacity),
    private val scope: CoroutineScope = MainScope()
) : Channel<T> by channel {
    private val logDString: String
        get() = "name: $name"
    val tag = name

    companion object {
        const val TAG = "GenericBlockingQueueAbs"
        val TAGKClass = GenericBlockingQueueAbs::class
    }

    init {
        logging(true)
        logd(tag, "This simple name: ${this::class.java.simpleName}")
    }

    fun logD(tag: String = this.tag) {
        logd(TAGKClass, tag, "$tag: name: $name")
    }

    fun enqueue(element: T): Boolean {
        val tag = "${this.tag}/enqueue/${randomString(2U)}"

        logd(TAGKClass, tag, "enqueue Enter: $logDString")

        val sendResult = channel.trySend(element).isSuccess

        logd(TAGKClass, tag, "enqueue Exit: $logDString sendResult: $sendResult")

        return sendResult
    }

    suspend fun enqueueRetry(element: T, retries: Int = 3, retryDelay: Long = 50L): Boolean {
        val tag = "${this.tag}/enqueueRetry/${randomString(2U)}"

        logd(TAGKClass, tag, "enqueue Enter: $logDString, retries: $retries")

        if (retries < 0 ) {
            logd(TAGKClass, tag, "enqueue Exit: $logDString, retries: $retries")
            return false
        }

        val sendResult = channel.trySend(element).isSuccess

        if (!sendResult && retries > 0) {
            logd(TAGKClass, tag, "enqueue Exit: $logDString, retries: $retries")
            delay(retryDelay.milliseconds)
            return enqueueRetry(element, retries - 1, retryDelay)
        }

        logd(TAGKClass, tag, "enqueue Exit: $logDString sendResult: $sendResult")

        return sendResult
    }

    fun enqueueRetryBestEffort(element: T, retries: Int = 3, retryDelay: Long = 500L) {
        val tag = "${this.tag}/enqueueRetry/${randomString(2U)}"

        logd(TAGKClass, tag, "enqueue Enter: $logDString, retries: $retries")

        if (retries < 0) {
            logd(TAGKClass, tag, "enqueue Exit: $logDString, retries: $retries")
            return
        }

        val sendResult = channel.trySend(element).isSuccess

        if (!sendResult && retries > 0) {
            logd(TAGKClass, tag, "enqueue Exit: $logDString, retries: $retries")
            scope.launch {
                delay(retryDelay.milliseconds)
                enqueueRetryBestEffort(element, retries - 1, retryDelay)
            }
            return
        }

        logd(TAGKClass, tag, "enqueue Exit: $logDString sendResult: $sendResult")
    }


    suspend fun dequeue(): T {
        val tag = "${this.tag}/dequeue/${randomString(2U)}"

        logd(TAGKClass, tag, "dequeue Enter: logDString")

        val ret = channel.receive()

        logd(TAGKClass, tag, "dequeue Exit: logDString")

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