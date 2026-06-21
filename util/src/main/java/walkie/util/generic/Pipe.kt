package walkie.util.generic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import walkie.util.api.PipeIdInt
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString

interface PipeMuxInt<T, K> {
    val pipeMap: MutableMap<PipeIdInt, MutableSharedFlow<PipeMessage<T, K>>>
    val receiverMap: MutableMap<PipeIdInt, PipeMuxInt<T, K>>
    fun registerReceiver (pipeId: PipeIdInt, scope: CoroutineScope, receiverObj: PipeMuxInt<T, K>)
    suspend fun pipeOnReceive (pipeId: PipeIdInt, type: K?, input: T?)
    fun pipeSend (pipeId: PipeIdInt, scope: CoroutineScope, type: K? = null, input: T? = null)
    fun pipe(pipeId: PipeIdInt) : MutableSharedFlow<PipeMessage<T, K>>?
    fun pipeCreate(pipeId: PipeIdInt) : MutableSharedFlow<PipeMessage<T, K>>?
}

data class PipeMessage<T, K>(
    val input: T?,
    val type: K?
)

fun <T, K> PipeMuxInt<T, K>.registerAsReceiver (pipeId: PipeIdInt, scope: CoroutineScope, vararg senderObjList: PipeMuxInt<T, K>) {
    senderObjList.forEach { senderObj ->
        senderObj.registerReceiver(pipeId, scope, this)
    }
}

fun <T, K> PipeMuxInt<T, K>.registerSenders (pipeId: PipeIdInt, scope: CoroutineScope, vararg senderObjList: PipeMuxInt<T, K>) {
    senderObjList.forEach { senderObj ->
        senderObj.registerReceiver(pipeId, scope,  this)
    }
}

class PipeMux<T, K>() : PipeMuxInt<T, K> {
    override val pipeMap: MutableMap<PipeIdInt, MutableSharedFlow<PipeMessage<T, K>>> = mutableMapOf()
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

    override fun pipe(pipeId: PipeIdInt) : MutableSharedFlow<PipeMessage<T, K>>? {
        return pipeMap[pipeId]
    }

    override fun pipeCreate(pipeId: PipeIdInt) : MutableSharedFlow<PipeMessage<T, K>>? {
        synchronized(lock) {
            if (null == pipeMap[pipeId]) {
                pipeMap[pipeId] =
                    MutableSharedFlow<PipeMessage<T, K>>(
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
                                input = msg.input,
                                type = msg.type
                            )
                        }
                        ?.collect()
                }
            }
        }
    }

    override fun pipeSend (pipeId: PipeIdInt, scope: CoroutineScope, type: K?, input: T?) {
        val tag = "pipeSend/${randomString(2u)}"

        logd(tag, "pipeSend: pipeId ${pipeId.toString()} input: ${if (null != input) input::class else null}  type: $type")

        if (null == receiverMap[pipeId]) {
            logd(tag, "pipeSend: receiver object for pipe $pipeId / $type is not registered")
            throw (NoSuchElementException("TAG: pipeSend: receiver object for pipe $pipeId / $type is not registered"))
        }

        val pipe  = receiverMap[pipeId]?.pipe(pipeId) ?: run {
            logd(tag, "pipeSend: pipe for ${receiverMap[pipeId]}[$pipeId] does not exist")
            throw (NoSuchElementException("${this}: pipeSend: pipe for ${receiverMap[pipeId]}[$pipeId] does not exist"))
        }

        scope.launch {
            pipe.emit(value = PipeMessage(input, type))
        }
    }

    override suspend fun pipeOnReceive(pipeId: PipeIdInt, type: K?, input: T?) {
        val tag = "pipeOnReceive/${randomString(2u)}"

        logd(tag, "pipeOnReceive pipeId: $pipeId. Not implemented.")
        throw (NotImplementedError("$tag: pipeOnReceive(pipeId: $pipeId. Not implemented."))
    }
}
