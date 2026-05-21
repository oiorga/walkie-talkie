package walkie.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

sealed class CoroutineRuntime(
    val job: RunJob,
    val dispatcher: RunDispatcher
) {
    /*
    object Main : CoroutineRuntime(rootJob = RunJob.Supervisor, dispatcher = RunDispatcher.Main)
    object UI : CoroutineRuntime(rootJob = RunJob.Supervisor, dispatcher = RunDispatcher.UI)
    object IO : CoroutineRuntime(rootJob = RunJob.Supervisor, dispatcher = RunDispatcher.IO)
    object CPU : CoroutineRuntime(rootJob = RunJob.Supervisor, dispatcher = RunDispatcher.CPU)
    */

    /* Breaking the seal */
    class Custom(rootJob: RunJob, dispatcher: RunDispatcher) : CoroutineRuntime (rootJob, dispatcher)

    enum class RunDispatcher {
        Main, UI, IO, CPU, Default
    }

    enum class RunJob {
        Supervisor, Regular
    }

    val mapDispatcher: (RunDispatcher) -> CoroutineDispatcher = { dispatcher ->
        when (dispatcher) {
            RunDispatcher.Main -> Dispatchers.Main
            RunDispatcher.UI -> Dispatchers.Main.immediate
            RunDispatcher.IO -> Dispatchers.IO
            RunDispatcher.CPU -> Dispatchers.Default
            RunDispatcher.Default -> Dispatchers.Default
        }
    }

    val mapJob: (RunJob) -> Job = { runJob ->
        when (runJob) {
            RunJob.Supervisor -> SupervisorJob()
            RunJob.Regular -> Job()
        }
    }

    val scope =
        CoroutineScope(
            mapJob(job) +
                    mapDispatcher(dispatcher)
        )

    fun launch(
        dispatcher: RunDispatcher = this.dispatcher,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return scope.launch(mapDispatcher(dispatcher), block = block)
    }

    fun cancel() {
        scope.cancel()
    }
}
