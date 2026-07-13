package walkie.util.generic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
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
    private var _scopeMap: MutableMap<PipeIdInt, CoroutineScope> = mutableMapOf()

    private val subscriberMap: MutableMap<PipeIdInt, Job> = mutableMapOf()

    override fun pipeMapTransfer(newPipeMap: Map<PipeIdInt, MutableSharedFlow<PipeMessageInt<T, K>>>,
                                 newScopeMap: Map<PipeIdInt, CoroutineScope>) {
        val tag = "pipeMapTransfer/${randomString(2u)}"

        val mutablePipeMap  = newPipeMap as? MutableMap<PipeIdInt, MutableSharedFlow<PipeMessageInt<T, K>>> ?: run {
            logd(tag, "PipeMux requires pipeMap to be backed by a MutableMap")
            error("PipeMux requires pipeMap to be backed by a MutableMap")
        }
        val mutableScopeMap  = newScopeMap as? MutableMap<PipeIdInt, CoroutineScope> ?: run {
            logd(tag, "PipeMux requires pipeMap to be backed by a MutableMap")
            error("PipeMux requires scopeMap to be backed by a MutableMap")
        }
        mutablePipeMap.putAll(_pipeMap)
        _pipeMap = mutablePipeMap
        mutableScopeMap.putAll(_scopeMap)
        _scopeMap = mutableScopeMap

    }

    override val pipeMap: MutableMap<PipeIdInt, MutableSharedFlow<PipeMessageInt<T, K>>>
        get() = _pipeMap

    override val scopeMap: MutableMap<PipeIdInt, CoroutineScope>
        get() = _scopeMap

    override fun pipeSubscribe(pipeId: PipeIdInt, scope: CoroutineScope, autoCreate: Boolean, onReceive: (suspend (pipeId: PipeIdInt, msg: PipeMessageInt<T, K>) -> Unit)) {
        val tag = "subscribe/${randomString(2u)}"

        synchronized(lock) {
            if (null == pipeMap[pipeId]) {
                if (autoCreate) {
                    pipeCreate(pipeId, scope)
                } else {
                    logd(tag, "Pipe $pipeId is not registered")
                    error("$tag: Pipe $pipeId is not registered")
                }
            }

            if (null == subscriberMap[pipeId]) {
                val job = scopeMap[pipeId]!!.launch {
                    pipeGet(pipeId)
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

    override fun pipeUnsubscribe(pipeId: PipeIdInt) {
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

    override fun pipeGet(pipeId: PipeIdInt) : MutableSharedFlow<PipeMessageInt<T, K>>? {
        return pipeMap[pipeId]
    }

    override fun scopeGet(pipeId: PipeIdInt) : CoroutineScope? {
        return scopeMap[pipeId]
    }

    override fun pipeCreate(pipeId: PipeIdInt, scope: CoroutineScope) : MutableSharedFlow<PipeMessageInt<T, K>>? {
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
            scopeMap[pipeId] = scope
        }
        return pipeMap[pipeId]
    }

    override fun pipeMuxAdd(pipeImpl: PipeMuxInt<T, K>) {
        synchronized(lock) {
            pipeImpl.pipeMapTransfer(pipeMap, scopeMap)
        }
    }

    override fun pipeMuxJoin(pipeImpl: PipeMuxInt<T, K>) {
        synchronized(lock) {
            pipeMapTransfer(pipeImpl.pipeMap, pipeImpl.scopeMap)
        }
    }

    override suspend fun pipeSendSync (pipeId: PipeIdInt, msg: PipeMessageInt<T, K>) {
        val tag = "pipeSend/${randomString(2u)}"
        val data= msg.data

        logd(tag, "pipeSend: pipeId ${pipeId.toString()} data: ${if (null != data) data::class else null}  type: ${msg.type}")

        val pipe = pipeMap[pipeId] ?: run {
            logd(tag, "pipeSend: pipe for $pipeMap $pipeId -> ${pipeMap[pipeId]} does not exist")
            throw (NoSuchElementException("${this}: pipeSend: pipe for $pipeMap $pipeId -> ${pipeMap[pipeId]} does not exist"))
        }

        pipe.emit(msg)
    }

    override fun pipeSendAsync (pipeId: PipeIdInt, msg: PipeMessageInt<T, K>) {
        val tag = "pipeSend/${randomString(2u)}"
        val data= msg.data

        logd(tag, "pipeSend: pipeId ${pipeId.toString()} data: ${if (null != data) data::class else null}  type: ${msg.type}")

        val pipe = pipeMap[pipeId] ?: run {
            logd(tag, "pipeSend: pipe for $pipeMap $pipeId -> ${pipeMap[pipeId]} does not exist")
            throw (NoSuchElementException("${this}: pipeSend: pipe for $pipeMap $pipeId -> ${pipeMap[pipeId]} does not exist"))
        }

        scopeMap[pipeId]!!.launch {
            pipe.emit(msg)
        }
    }


    override suspend fun onPipeMessage(pipeId: PipeIdInt, msg: PipeMessageInt<T, K>) {
        val tag = "pipeOnReceive/${randomString(2u)}"

        logd(tag, "${this.toString()} pipeOnReceive pipeId: $pipeId ${msg.type} ${msg.data}. Not implemented.")
        throw (NotImplementedError("$tag: $this pipeOnReceive(pipeId: $pipeId. Not implemented."))
    }
}
