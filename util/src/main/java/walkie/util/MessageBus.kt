package walkie.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import walkie.util.api.BusMessageApi
import walkie.util.api.BusMessageBaseApi
import walkie.util.api.MessageBusId
import walkie.util.api.BusMessageType
import walkie.util.api.MessageBusApi
import walkie.util.api.MessageBusBaseApi

open class MessageBusBase<BusId, Message: BusMessageBaseApi<MessageType, DataType>, MessageType, DataType>() : MessageBusBaseApi<BusId, Message, MessageType, DataType> {
    private var _busMap: MutableMap<BusId, MutableSharedFlow<Message>> = mutableMapOf()
    private var _scopeMap: MutableMap<BusId, CoroutineScope> = mutableMapOf()
    private val subscriberMap: MutableMap<BusId, Job> = mutableMapOf()
    override val busMap: MutableMap<BusId, MutableSharedFlow<Message>>
        get() = _busMap
    override val scopeMap: MutableMap<BusId, CoroutineScope>
        get() = _scopeMap
    private val lock = Any()

    companion object {
        private const val TAG = "BusMux"
        val TAGKClass = MessageBusBase::class
    }

    init {
        logging()
        logd (TAG, "init")
    }

    override fun busMapImport(busMap: Map<BusId, MutableSharedFlow<Message>>,
                              scopeMap: Map<BusId, CoroutineScope>) {
        val tag = "busMapTransfer/${randomString(2u)}"

        val mutableBusMap  = busMap as? MutableMap<BusId, MutableSharedFlow<Message>> ?: run {
            logd(tag, "BusMux requires busMap to be backed by a MutableMap")
            error("BusMux requires busMap to be backed by a MutableMap")
        }
        val mutableScopeMap  = scopeMap as? MutableMap<BusId, CoroutineScope> ?: run {
            logd(tag, "BusMux requires busMap to be backed by a MutableMap")
            error("BusMux requires scopeMap to be backed by a MutableMap")
        }
        mutableBusMap.putAll(_busMap)
        _busMap = mutableBusMap
        mutableScopeMap.putAll(_scopeMap)
        _scopeMap = mutableScopeMap

    }

    override fun busSubscribe(busId: BusId, scope: CoroutineScope, autoCreate: Boolean, onReceive: suspend (BusId, Message) -> Unit) {
        val tag = "subscribe/${randomString(2u)}"

        synchronized(lock) {
            if (null == busMap[busId]) {
                if (autoCreate) {
                    busCreate(busId, scope)
                } else {
                    logd(tag, "Bus $busId is not registered")
                    error("$tag: Bus $busId is not registered")
                }
            }

            if (null == subscriberMap[busId]) {
                val job = scopeMap[busId]!!.launch {
                    busGet(busId)
                        ?.onEach { msg ->
                            onReceive.invoke(
                                busId,
                                msg
                            )
                        }
                        ?.collect()
                }

                job.invokeOnCompletion {
                    synchronized(lock) {
                        subscriberMap.remove(busId)
                    }
                }

                subscriberMap[busId] = job
            }
        }
    }

    override fun busUnsubscribe(busId: BusId) {
        val tag = "unsubscribe/${randomString(2u)}"

        synchronized(lock) {
            if (null == busMap[busId]) {
                logd(tag, "Bus $busId is not registered")
                error("$tag: Bus $busId is not registered")
            }

            subscriberMap[busId]?.cancel()
            subscriberMap.remove(busId)
        }
    }

    override fun busGet(busId: BusId) : MutableSharedFlow<Message>? = synchronized(lock) {
        busMap[busId]
    }

    override fun scopeGet(busId: BusId) : CoroutineScope? = synchronized(lock) {
        scopeMap[busId]
    }

    override fun busCreate(busId: BusId, scope: CoroutineScope) : MutableSharedFlow<Message>? {
        val tag = "busCreate/${randomString(2u)}"

        synchronized(lock) {
            if (null == busMap[busId]) {
                logd(tag, "Creating bus $busId")
                busMap[busId] = MutableSharedFlow<Message>(
                    replay = 10,
                    extraBufferCapacity = 100,
                    onBufferOverflow = BufferOverflow.SUSPEND
                )
            }
            scopeMap[busId] = scope
        }
        return busMap[busId]
    }

    override fun busAdd(busImpl: MessageBusBaseApi<BusId, Message, MessageType, DataType>) {
        synchronized(lock) {
            busImpl.busMapImport(busMap, scopeMap)
        }
    }

    override fun busJoin(busImpl: MessageBusBaseApi<BusId, Message, MessageType, DataType>) {
        synchronized(lock) {
            busMapImport(busImpl.busMap, busImpl.scopeMap)
        }
    }

    override suspend fun busSendSync (busId: BusId, msg: Message) {
        val tag = "busSend/${randomString(2u)}"
        val data= msg.data

        logd(tag, "busSend: busId ${busId.toString()} data: ${if (null != data) data::class else null}  type: ${msg.type}")

        val bus = synchronized(lock) { busMap[busId] } ?: run {
            logd(tag, "busSend: bus for $busMap $busId -> ${busMap[busId]} does not exist")
            throw (NoSuchElementException("${this}: busSend: bus for $busMap $busId -> ${busMap[busId]} does not exist"))
        }

        bus.emit(msg)
    }

    override fun busSendAsync (busId: BusId, msg: Message) {
        val tag = "busSend/${randomString(2u)}"
        val data= msg.data

        logd(tag, "busSend: busId ${busId.toString()} data: ${if (null != data) data::class else null}  type: ${msg.type}")

        val bus = synchronized(lock) { busMap[busId] } ?: run {
            logd(tag, "busSend: bus for $busMap $busId -> ${busMap[busId]} does not exist")
            throw (NoSuchElementException("${this}: busSend: bus for $busMap $busId -> ${busMap[busId]} does not exist"))
        }

        synchronized(lock) {
            scopeMap[busId]
        }?.launch {
            bus.emit(msg)
        }
    }

    override suspend fun onBusMessage(busId: BusId, msg: Message) {
        val tag = "onBusMessage/${randomString(2u)}"

        logd(tag, "${this.toString()} busOnReceive busId: $busId ${msg.type} ${msg.data}. Not implemented.")
        throw (NotImplementedError("$tag: $this busOnReceive(busId: $busId. Not implemented."))
    }
}

open class BusMessage<MessageType: BusMessageType, DataType>(
    override val type: MessageType,
    override val data: DataType?
) : BusMessageApi<MessageType, DataType>

open class MessageBus<BusId: MessageBusId, Message: BusMessageApi<MessageType, DataType>, MessageType: BusMessageType, DataType>(): MessageBusBase<BusId, Message, MessageType, DataType>(), MessageBusApi<BusId, Message, MessageType, DataType>