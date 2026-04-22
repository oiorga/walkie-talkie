package walkie.util

import android.util.Log
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

class TimedSemaphore (private val timeout: Long) {
    private val semaphore: Semaphore = Semaphore(1, 0)
    private val sema: Semaphore = Semaphore(1, 0)

    companion object {
        const val TAG = "TimedSemaphore"
    }

    init {
        MainScope().launch {
            while (true) {
                delay(timeout)
                release()
            }
        }
    }

    suspend fun release () {
        sema.acquire()
        Log.d(TAG, "$TAG: release: availablePermits: ${semaphore.availablePermits}")
        if (0 == semaphore.availablePermits) {
            semaphore.release()
        }
        sema.release()
    }

    suspend fun acquire () {
        Log.d(TAG, "$TAG: acquire 0: availablePermits: ${semaphore.availablePermits}")
        semaphore.acquire()
        Log.d(TAG, "$TAG: acquire 1: availablePermits: ${semaphore.availablePermits}")
    }
}