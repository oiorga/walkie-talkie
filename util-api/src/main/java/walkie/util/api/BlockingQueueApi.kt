package walkie.util.api

import kotlinx.coroutines.CoroutineScope

interface BlockingQueueApi<T> {
    val name: String
    val capacity: Int
    val scope: CoroutineScope
    fun enqueue(element: T): Boolean
    suspend fun enqueueRetry(element: T, retries: Int = 3, retryDelay: Long = 50L): Boolean
    fun enqueueRetryBestEffort(element: T, retries: Int = 3, retryDelay: Long = 500L)
    fun tryDequeue(): BlockingQueueResult<T>
    suspend fun dequeue(): T
}


sealed interface BlockingQueueResult<out T> {
    data class Success<T>(val value: T): BlockingQueueResult<T>
    data object Empty : BlockingQueueResult<Nothing>
    data object Closed : BlockingQueueResult<Nothing>
}