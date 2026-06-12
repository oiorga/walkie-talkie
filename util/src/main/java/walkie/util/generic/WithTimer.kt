package walkie.util.generic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration

interface WithTimerInt {
    fun onTimer(scope: CoroutineScope, cycles: Long, cadence: Duration, block: () -> Unit) : Job
    fun onTimer(scope: CoroutineScope, cadence: Duration, block: () -> Unit = { }) : Job
    fun onTimerOnce(scope: CoroutineScope, cadence: Duration, block: () -> Unit = { }) : Job
}

class WithTimer() : WithTimerInt {
    override fun onTimer(scope: CoroutineScope, cycles: Long, cadence: Duration, block: () -> Unit): Job {
        return scope.launch {
            var cycleCount = cycles
            while ((cycleCount > 0) && isActive) {
                delay(cadence)
                block()
                cycleCount--
            }
        }
    }

    override fun onTimerOnce(scope: CoroutineScope, cadence: Duration, block: () -> Unit) : Job {
        return onTimer(scope = scope, cycles = 1, cadence, block)
    }

    override fun onTimer(scope: CoroutineScope, cadence: Duration, block: () -> Unit): Job {
        return onTimer(scope = scope, cycles = Long.MAX_VALUE, cadence, block)
    }
}