package walkie.util.generic

import kotlinx.coroutines.CoroutineScope
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
    override val pipeMap: MutableMap<PipeIdInt, MutableSharedFlow<PipeMessageInt<T, K>>> = mutableMapOf()
    override val receiverMap: MutableMap<PipeIdInt, PipeMuxInt<T, K>> = mutableMapOf()

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
        synchronized(lock) {
            if (null == pipeMap[pipeId]) {
                pipeMap[pipeId] =
                    MutableSharedFlow<PipeMessageInt<T, K>>(
                        replay = 10,
                        extraBufferCapacity = 100,
                        onBufferOverflow = BufferOverflow.SUSPEND
                    )
            }
        }
        return pipeMap[pipeId]
    }

    override fun registerReceiver (pipeId: PipeIdInt, scope: CoroutineScope, receiverObj: PipeMuxInt<T, K>) {
        val tag = "registerReceiver/${randomString(2u)}"

        logd(tag, "registerReceiver: pipeId ${pipeId.toString()} ${receiverObj.toString()}")

        synchronized(lock) {
            receiverMap.putIfAbsent(pipeId, receiverObj)

            if (null != receiverObj.pipe(pipeId)) {
                logd(tag, "registerReceiver: pipeId ${pipeId.toString()} already exists")
            } else {
                receiverObj.pipeCreate(pipeId)
                scope.launch {
                    receiverObj.pipe(pipeId)
                        ?.onEach { msg ->
                            receiverObj.pipeOnReceive(
                                pipeId = pipeId,
                                msg
                            )
                        }
                        ?.collect()
                }
            }
        }
    }

    override fun pipeSend (pipeId: PipeIdInt, scope: CoroutineScope, msg: PipeMessageInt<T, K>) {
        val tag = "pipeSend/${randomString(2u)}"

        val data= msg.data

        logd(tag, "pipeSend: pipeId ${pipeId.toString()} data: ${if (null != data) data::class else null}  type: ${msg.type}")

        if (null == receiverMap[pipeId]) {
            logd(tag, "pipeSend: receiver object for pipe $pipeId / $msg.type is not registered")
            throw (NoSuchElementException("TAG: pipeSend: receiver object for pipe $pipeId / $msg.type is not registered"))
        }

        val pipe  = receiverMap[pipeId]?.pipe(pipeId) ?: run {
            logd(tag, "pipeSend: pipe for ${receiverMap[pipeId]}[$pipeId] does not exist")
            throw (NoSuchElementException("${this}: pipeSend: pipe for ${receiverMap[pipeId]}[$pipeId] does not exist"))
        }

        scope.launch {
            pipe.emit(msg)
        }
    }

    override suspend fun pipeOnReceive(pipeId: PipeIdInt, msg: PipeMessageInt<T, K>) {
        val tag = "pipeOnReceive/${randomString(2u)}"

        logd(tag, "pipeOnReceive pipeId: $pipeId. Not implemented.")
        throw (NotImplementedError("$tag: pipeOnReceive(pipeId: $pipeId. Not implemented."))
    }

}
