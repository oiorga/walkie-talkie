package walkie.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

sealed class CoroutineRuntime(
    val rootJob: RunJob,
    val dispatcher: RunDispatcher
) {
    object Main : CoroutineRuntime(rootJob = RunJob.Supervisor, dispatcher = RunDispatcher.Main)
    object UI : CoroutineRuntime(rootJob = RunJob.Supervisor, dispatcher = RunDispatcher.UI)
    object IO : CoroutineRuntime(rootJob = RunJob.Supervisor, dispatcher = RunDispatcher.IO)
    object CPU : CoroutineRuntime(rootJob = RunJob.Supervisor, dispatcher = RunDispatcher.CPU)

    /* Breaking the seal */
    class Custom(rootJob: RunJob, dispatcher: RunDispatcher) : CoroutineRuntime (rootJob, dispatcher)

    enum class RunDispatcher {
        Main, UI, IO, CPU
    }

    enum class RunJob {
        Supervisor, Regular
    }

    private val mapDispatcher = mapOf(
        RunDispatcher.Main to Dispatchers.Main,
        RunDispatcher.UI to Dispatchers.Main.immediate,
        RunDispatcher.IO to Dispatchers.IO,
        RunDispatcher.CPU to Dispatchers.Default
    )

    private val mapJob = mapOf(
        RunJob.Supervisor to { SupervisorJob() },
        RunJob.Regular to { Job() }
    )

    private val rootScope =
        CoroutineScope(
            mapJob[rootJob]!!.invoke() +
                    mapDispatcher[dispatcher]!!
        )

    fun launch(
        dispatcher: RunDispatcher = this.dispatcher,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return rootScope.launch(mapDispatcher[dispatcher]!!, block = block)
    }

    fun cancel() {
        rootScope.cancel()
    }

    fun scope(): CoroutineScope {
        return rootScope
    }
}
