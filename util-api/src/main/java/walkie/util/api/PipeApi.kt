package walkie.util.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow

interface PipeIdInt
interface PipeMessageInt<T, D> {
    val type: T
    val data: D?
}

interface PipeMuxInt<T, K> {
    var pipeMap: MutableMap<PipeIdInt, MutableSharedFlow<PipeMessageInt<T, K>>>
    val receiverMap: MutableMap<PipeIdInt, PipeMuxInt<T, K>>
    fun registerReceiver (pipeId: PipeIdInt, scope: CoroutineScope, receiverObj: PipeMuxInt<T, K>)
    suspend fun pipeOnReceive (pipeId: PipeIdInt, msg: PipeMessageInt<T, K>)
    fun pipeSend (pipeId: PipeIdInt, scope: CoroutineScope, msg: PipeMessageInt<T, K>)
    fun pipe(pipeId: PipeIdInt) : MutableSharedFlow<PipeMessageInt<T, K>>?
    fun pipeCreate(pipeId: PipeIdInt) : MutableSharedFlow<PipeMessageInt<T, K>>?
    fun addPipeMux(pipeImpl: PipeMuxInt<T, K>)
    fun joinPipeMux(pipeImpl: PipeMuxInt<T, K>)
}

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

fun <T, K> PipeMuxInt<T, K>.addPipeMux (vararg pipeImplList: PipeMuxInt<T, K>) {
    pipeImplList.forEach { pipeImpl ->
        addPipeMux(pipeImpl)
    }
}