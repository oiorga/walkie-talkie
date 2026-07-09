package walkie.util.generic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import walkie.util.api.PipeIdInt
import walkie.util.api.PipeMuxInt
import walkie.util.api.PipeMessageInt
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString

data class PipeMessage<T, D>(
    override val type: T,
    override val data: D?
) : PipeMessageInt<T, D>

class PipeMux<T, K>() : PipeMuxInt<T, K> {
    private var _pipeMap: MutableMap<PipeIdInt, MutableSharedFlow<PipeMessageInt<T, K>>> = mutableMapOf()

    private val subscriberMap: MutableMap<PipeIdInt, Job> = mutableMapOf()

    override fun pipeMapTransfer(newPipeMap: Map<PipeIdInt, MutableSharedFlow<PipeMessageInt<T, K>>>) {
        val tag = "pipeMapTransfer/${randomString(2u)}"

        val mutablePipeMap  = newPipeMap as? MutableMap<PipeIdInt, MutableSharedFlow<PipeMessageInt<T, K>>> ?: run {
            logd(tag, "PipeMux requires pipeMap to be backed by a MutableMap")
            error("PipeMux requires pipeMap to be backed by a MutableMap")
        }
        mutablePipeMap.putAll(_pipeMap)
        _pipeMap = mutablePipeMap
    }

    override val pipeMap: MutableMap<PipeIdInt, MutableSharedFlow<PipeMessageInt<T, K>>>
        get() = _pipeMap

    override fun subscribe(pipeId: PipeIdInt, scope: CoroutineScope, autoCreate: Boolean, onReceive: (suspend (pipeId: PipeIdInt, msg: PipeMessageInt<T, K>) -> Unit)) {
        val tag = "subscribe/${randomString(2u)}"

        synchronized(lock) {
            if (null == pipeMap[pipeId]) {
                if (autoCreate) {
                    pipeCreate(pipeId)
                } else {
                    logd(tag, "Pipe $pipeId is not registered")
                    error("$tag: Pipe $pipeId is not registered")
                }
            }

            if (null == subscriberMap[pipeId]) {
                val job = scope.launch {
                    pipe(pipeId)
                        ?.onEach { msg ->
                            onReceive.invoke(
                                pipeId,
                                msg
                            )
                        }
                        ?.collect()
                }

                job.invokeOnCompletion {
                    synchronized(lock) {
                        subscriberMap.remove(pipeId)
                    }
                }

                subscriberMap[pipeId] = job
            }
        }
    }

    override fun unsubscribe(pipeId: PipeIdInt) {
        val tag = "unsubscribe/${randomString(2u)}"

        synchronized(lock) {
            if (null == pipeMap[pipeId]) {
                logd(tag, "Pipe $pipeId is not registered")
                error("$tag: Pipe $pipeId is not registered")
            }

            subscriberMap[pipeId]?.cancel()
            subscriberMap.remove(pipeId)
        }
    }

    private val lock = Any()

    companion object {
        private const val TAG = "PipeMux"
        val TAGKClass = PipeMux::class
    }

    init {
        logging()
        logd (TAG, "init")
    }

    override fun pipe(pipeId: PipeIdInt) : MutableSharedFlow<PipeMessageInt<T, K>>? {
        return pipeMap[pipeId]
    }

    override fun pipeCreate(pipeId: PipeIdInt) : MutableSharedFlow<PipeMessageInt<T, K>>? {
        val tag = "pipeCreate/${randomString(2u)}"

        synchronized(lock) {
            if (null == pipeMap[pipeId]) {
                logd(tag, "Creating pipe $pipeId")
                pipeMap[pipeId] = MutableSharedFlow<PipeMessageInt<T, K>>(
                    replay = 10,
                    extraBufferCapacity = 100,
                    onBufferOverflow = BufferOverflow.SUSPEND
                )
            }
        }
        return pipeMap[pipeId]
    }

    override fun addPipeMux(pipeImpl: PipeMuxInt<T, K>) {
        synchronized(lock) {
            pipeImpl.pipeMapTransfer(pipeMap)
        }
    }

    override fun joinPipeMux(pipeImpl: PipeMuxInt<T, K>) {
        synchronized(lock) {
            pipeMapTransfer(pipeImpl.pipeMap)
        }
    }

    override fun pipeSendAsync (pipeId: PipeIdInt, scope: CoroutineScope, msg: PipeMessageInt<T, K>) {
        val tag = "pipeSend/${randomString(2u)}"
        val data= msg.data

        logd(tag, "pipeSend: pipeId ${pipeId.toString()} data: ${if (null != data) data::class else null}  type: ${msg.type}")

        val pipe = pipeMap[pipeId] ?: run {
            logd(tag, "pipeSend: pipe for $pipeMap $pipeId -> ${pipeMap[pipeId]} does not exist")
            throw (NoSuchElementException("${this}: pipeSend: pipe for $pipeMap $pipeId -> ${pipeMap[pipeId]} does not exist"))
        }

        scope.launch {
            pipe.emit(msg)
        }
    }

    override suspend fun pipeSendSync (pipeId: PipeIdInt, scope: CoroutineScope, msg: PipeMessageInt<T, K>) {
        val tag = "pipeSend/${randomString(2u)}"
        val data= msg.data

        logd(tag, "pipeSend: pipeId ${pipeId.toString()} data: ${if (null != data) data::class else null}  type: ${msg.type}")

        val pipe = pipeMap[pipeId] ?: run {
            logd(tag, "pipeSend: pipe for $pipeMap $pipeId -> ${pipeMap[pipeId]} does not exist")
            throw (NoSuchElementException("${this}: pipeSend: pipe for $pipeMap $pipeId -> ${pipeMap[pipeId]} does not exist"))
        }

        pipe.emit(msg)
    }


    override suspend fun onPipeMessage(pipeId: PipeIdInt, msg: PipeMessageInt<T, K>) {
        val tag = "pipeOnReceive/${randomString(2u)}"

        logd(tag, "${this.toString()} pipeOnReceive pipeId: $pipeId ${msg.type} ${msg.data}. Not implemented.")
        throw (NotImplementedError("$tag: $this pipeOnReceive(pipeId: $pipeId. Not implemented."))
    }
}
