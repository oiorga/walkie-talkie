package walkie.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

sealed class CorRuntime(
    val rootJob: RunJob,
    val dispatcher: RunDispatcher
) {

    object Main : CorRuntime(rootJob = RunJob.Supervisor, dispatcher = RunDispatcher.Main)
    object UI : CorRuntime(rootJob = RunJob.Supervisor, dispatcher = RunDispatcher.UI)
    object IO : CorRuntime(rootJob = RunJob.Supervisor, dispatcher = RunDispatcher.IO)
    object CPU : CorRuntime(rootJob = RunJob.Supervisor, dispatcher = RunDispatcher.CPU)

    enum class RunDispatcher {
        Main, UI, IO, CPU
    }

    enum class RunJob {
        Supervisor, Regular
    }

    private val mapDispatcher = mapOf(
        RunDispatcher.Main to Dispatchers.Main,
        RunDispatcher.UI to Dispatchers.Main,
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
    ) {
        rootScope.launch(mapDispatcher[dispatcher]!!, block = block)
    }
}

/* Breaking the seal */
class RndRuntime(rootJob: RunJob, dispatcher: RunDispatcher) : CorRuntime (rootJob, dispatcher)