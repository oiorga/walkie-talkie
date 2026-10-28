package walkie.util.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow

interface BusMessageIdApi
interface BusMessageApi<T, D> {
    val type: T
    val data: D?
}

interface MessageBusApi<T, K> {
    val busMap: Map<BusMessageIdApi, MutableSharedFlow<BusMessageApi<T, K>>>
    val scopeMap: Map<BusMessageIdApi, CoroutineScope>
    fun busMapImport(busMap: Map<BusMessageIdApi, MutableSharedFlow<BusMessageApi<T, K>>>, scopeMap: Map<BusMessageIdApi, CoroutineScope>)
    fun busSubscribe(busId: BusMessageIdApi, scope: CoroutineScope, autoCreate: Boolean = true, onReceive: suspend (BusMessageIdApi, BusMessageApi<T, K>) -> Unit)
    fun busUnsubscribe(busId: BusMessageIdApi)
    suspend fun onBusMessage (busId: BusMessageIdApi, msg: BusMessageApi<T, K>)
    fun busSendAsync (busId: BusMessageIdApi, msg: BusMessageApi<T, K>)
    suspend fun busSendSync (busId: BusMessageIdApi, msg: BusMessageApi<T, K>)
    fun busGet(busId: BusMessageIdApi) : MutableSharedFlow<BusMessageApi<T, K>>?
    fun scopeGet(busId: BusMessageIdApi) : CoroutineScope?
    fun busCreate(busId: BusMessageIdApi, scope: CoroutineScope) : MutableSharedFlow<BusMessageApi<T, K>>?
    fun busAdd(busImpl: MessageBusApi<T, K>)
    fun busJoin(busImpl: MessageBusApi<T, K>)
}

fun <T, K> MessageBusApi<T, K>.busAdd (vararg pipeImplList: MessageBusApi<T, K>) {
    pipeImplList.forEach { pipeImpl ->
        busAdd(pipeImpl)
    }
}