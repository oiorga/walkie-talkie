package walkie.util.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow

interface MessageBusIdInt
interface BusMessageInt<T, D> {
    val type: T
    val data: D?
}

interface MessageBusInt<T, K> {
    val busMap: Map<MessageBusIdInt, MutableSharedFlow<BusMessageInt<T, K>>>
    val scopeMap: Map<MessageBusIdInt, CoroutineScope>
    fun busMapImport(busMap: Map<MessageBusIdInt, MutableSharedFlow<BusMessageInt<T, K>>>, scopeMap: Map<MessageBusIdInt, CoroutineScope>)
    fun busSubscribe(busId: MessageBusIdInt, scope: CoroutineScope, autoCreate: Boolean = true, onReceive: suspend (MessageBusIdInt, BusMessageInt<T, K>) -> Unit)
    fun busUnsubscribe(busId: MessageBusIdInt)
    suspend fun onBusMessage (busId: MessageBusIdInt, msg: BusMessageInt<T, K>)
    fun busSendAsync (busId: MessageBusIdInt, msg: BusMessageInt<T, K>)
    suspend fun busSendSync (busId: MessageBusIdInt, msg: BusMessageInt<T, K>)
    fun busGet(busId: MessageBusIdInt) : MutableSharedFlow<BusMessageInt<T, K>>?
    fun scopeGet(busId: MessageBusIdInt) : CoroutineScope?
    fun busCreate(busId: MessageBusIdInt, scope: CoroutineScope) : MutableSharedFlow<BusMessageInt<T, K>>?
    fun busAdd(busImpl: MessageBusInt<T, K>)
    fun busJoin(busImpl: MessageBusInt<T, K>)
}

fun <T, K> MessageBusInt<T, K>.busAdd (vararg pipeImplList: MessageBusInt<T, K>) {
    pipeImplList.forEach { pipeImpl ->
        busAdd(pipeImpl)
    }
}