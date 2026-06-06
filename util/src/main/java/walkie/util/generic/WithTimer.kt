package walkie.util.generic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration

interface WithTimerInt {
    val scope: CoroutineScope
    val cadence: Duration
    fun onTimer(cycles: Long, block: () -> Unit) : Job
    fun onTimer(block: () -> Unit) : Job
    fun onTimerOnce(block: () -> Unit) : Job

}

class WithTimer(
    override val scope: CoroutineScope,
    override val cadence: Duration,
) : WithTimerInt {

    override fun onTimer(cycles: Long, block: () -> Unit): Job {
        return scope.launch {
            var cycleCount = cycles
            while ((cycleCount > 0) && isActive) {
                delay(cadence)
                block()
                cycleCount--
            }
        }
    }

    override fun onTimerOnce(block: () -> Unit) : Job {
        return onTimer(1, block)
    }

    override fun onTimer(block: () -> Unit): Job {
        return onTimer(Long.MAX_VALUE, block)
    }
}