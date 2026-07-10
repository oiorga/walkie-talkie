package walkie.util.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow

interface PipeIdInt
interface PipeMessageInt<T, D> {
    val type: T
    val data: D?
}

interface PipeMuxInt<T, K> {
    val pipeMap: Map<PipeIdInt, MutableSharedFlow<PipeMessageInt<T, K>>>
    fun pipeMapTransfer(newPipeMap: Map<PipeIdInt, MutableSharedFlow<PipeMessageInt<T, K>>>)
    fun pipeSubscribe(pipeId: PipeIdInt, scope: CoroutineScope, autoCreate: Boolean = true, onReceive: (suspend (pipeId: PipeIdInt, msg: PipeMessageInt<T, K>) -> Unit))
    fun pipeUnsubscribe(pipeId: PipeIdInt)
    suspend fun onPipeMessage (pipeId: PipeIdInt, msg: PipeMessageInt<T, K>)
    fun pipeSendAsync (pipeId: PipeIdInt, scope: CoroutineScope, msg: PipeMessageInt<T, K>)
    suspend fun pipeSendSync (pipeId: PipeIdInt, scope: CoroutineScope, msg: PipeMessageInt<T, K>)
    fun pipeGet(pipeId: PipeIdInt) : MutableSharedFlow<PipeMessageInt<T, K>>?
    fun pipeCreate(pipeId: PipeIdInt) : MutableSharedFlow<PipeMessageInt<T, K>>?
    fun pipeMuxAdd(pipeImpl: PipeMuxInt<T, K>)
    fun pipeMuxJoin(pipeImpl: PipeMuxInt<T, K>)
}

fun <T, K> PipeMuxInt<T, K>.pipeMuxAdd (vararg pipeImplList: PipeMuxInt<T, K>) {
    pipeImplList.forEach { pipeImpl ->
        pipeMuxAdd(pipeImpl)
    }
}