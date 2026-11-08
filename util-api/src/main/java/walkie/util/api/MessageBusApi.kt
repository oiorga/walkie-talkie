package walkie.util.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow

interface MessageBusId
interface BusMessageType

interface BusMessageBaseApi<MessageType, DataType> {
    val type: MessageType
    val data: DataType?
}

interface MessageBusBaseApi<BusId, Message: BusMessageBaseApi<MessageType, DataType>, MessageType, DataType> {
    val busMap: Map<BusId, MutableSharedFlow<Message>>
    val scopeMap: Map<BusId, CoroutineScope>
    fun busMapImport(busMap: Map<BusId, MutableSharedFlow<Message>>, scopeMap: Map<BusId, CoroutineScope>)
    fun busSubscribe(busId: BusId, scope: CoroutineScope, autoCreate: Boolean = true, onReceive: suspend (BusId, Message) -> Unit)
    fun busUnsubscribe(busId: BusId)
    suspend fun onBusMessage (busId: BusId, msg: Message)
    fun busSendAsync (busId: BusId, msg: Message)
    suspend fun busSendSync (busId: BusId, msg: Message)
    fun busGet(busId: BusId) : MutableSharedFlow<Message>?
    fun scopeGet(busId: BusId) : CoroutineScope?
    fun busCreate(busId: BusId, scope: CoroutineScope) : MutableSharedFlow<Message>?
    fun busAdd(busImpl: MessageBusBaseApi<BusId, Message, MessageType, DataType>)
    fun busJoin(busImpl: MessageBusBaseApi<BusId, Message, MessageType, DataType>)
}

fun <BusId, Message: BusMessageBaseApi<MessageType, DataType>, MessageType, DataType>
        MessageBusBaseApi<BusId, Message, MessageType, DataType>.busAdd
            (vararg busImplList: MessageBusBaseApi<BusId, Message, MessageType, DataType>) {
    busImplList.forEach { busImpl ->
        busAdd(busImpl)
    }
}

interface BusMessageApi<MessageType: BusMessageType, DataType>: BusMessageBaseApi<MessageType, DataType>
interface MessageBusApi<BusId: MessageBusId, Message: BusMessageApi<MessageType, DataType>, MessageType: BusMessageType, DataType>: MessageBusBaseApi<BusId, Message, MessageType, DataType>
