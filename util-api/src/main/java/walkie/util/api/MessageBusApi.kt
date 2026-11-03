package walkie.util.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow

interface BusMessageIdBaseApi
interface BusMessageTypeBaseApi

interface BusMessageBaseApi<MessageType, DataType> {
    val type: MessageType
    val data: DataType?
}

interface MessageBusBaseApi<BusId, MessageType, DataType> {
    val busMap: Map<BusId, MutableSharedFlow<BusMessageBaseApi<MessageType, DataType>>>
    val scopeMap: Map<BusId, CoroutineScope>
    fun busMapImport(busMap: Map<BusId, MutableSharedFlow<BusMessageBaseApi<MessageType, DataType>>>, scopeMap: Map<BusId, CoroutineScope>)
    fun busSubscribe(busId: BusId, scope: CoroutineScope, autoCreate: Boolean = true, onReceive: suspend (BusId, BusMessageBaseApi<MessageType, DataType>) -> Unit)
    fun busUnsubscribe(busId: BusId)
    suspend fun onBusMessage (busId: BusId, msg: BusMessageBaseApi<MessageType, DataType>)
    fun busSendAsync (busId: BusId, msg: BusMessageBaseApi<MessageType, DataType>)
    suspend fun busSendSync (busId: BusId, msg: BusMessageBaseApi<MessageType, DataType>)
    fun busGet(busId: BusId) : MutableSharedFlow<BusMessageBaseApi<MessageType, DataType>>?
    fun scopeGet(busId: BusId) : CoroutineScope?
    fun busCreate(busId: BusId, scope: CoroutineScope) : MutableSharedFlow<BusMessageBaseApi<MessageType, DataType>>?
    fun busAdd(busImpl: MessageBusBaseApi<BusId, MessageType, DataType>)
    fun busJoin(busImpl: MessageBusBaseApi<BusId, MessageType, DataType>)
}

fun <BusId, MessageType, DataType> MessageBusBaseApi<BusId, MessageType, DataType>.busAdd (vararg pipeImplList: MessageBusBaseApi<BusId, MessageType, DataType>) {
    pipeImplList.forEach { pipeImpl ->
        busAdd(pipeImpl)
    }
}

typealias BusMessageApi<MessageType, DataType> = BusMessageBaseApi<MessageType, DataType>
typealias MessageBusApi<BusId, MessageType, DataType> = MessageBusBaseApi<BusId, MessageType, DataType>
