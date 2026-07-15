package walkie.util.generic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import walkie.util.api.MessageBusIdInt
import walkie.util.api.MessageBusInt
import walkie.util.api.BusMessageInt
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString

data class BusMessage<T, D>(
    override val type: T,
    override val data: D?
) : BusMessageInt<T, D>

class MessageBus<T, K>() : MessageBusInt<T, K> {
    private var _busMap: MutableMap<MessageBusIdInt, MutableSharedFlow<BusMessageInt<T, K>>> = mutableMapOf()
    private var _scopeMap: MutableMap<MessageBusIdInt, CoroutineScope> = mutableMapOf()
    private val subscriberMap: MutableMap<MessageBusIdInt, Job> = mutableMapOf()
    override val busMap: MutableMap<MessageBusIdInt, MutableSharedFlow<BusMessageInt<T, K>>>
        get() = _busMap
    override val scopeMap: MutableMap<MessageBusIdInt, CoroutineScope>
        get() = _scopeMap
    private val lock = Any()

    companion object {
        private const val TAG = "BusMux"
        val TAGKClass = MessageBus::class
    }

    init {
        logging()
        logd (TAG, "init")
    }

    override fun busMapImport(busMap: Map<MessageBusIdInt, MutableSharedFlow<BusMessageInt<T, K>>>,
                              scopeMap: Map<MessageBusIdInt, CoroutineScope>) {
        val tag = "busMapTransfer/${randomString(2u)}"

        val mutableBusMap  = busMap as? MutableMap<MessageBusIdInt, MutableSharedFlow<BusMessageInt<T, K>>> ?: run {
            logd(tag, "BusMux requires busMap to be backed by a MutableMap")
            error("BusMux requires busMap to be backed by a MutableMap")
        }
        val mutableScopeMap  = scopeMap as? MutableMap<MessageBusIdInt, CoroutineScope> ?: run {
            logd(tag, "BusMux requires busMap to be backed by a MutableMap")
            error("BusMux requires scopeMap to be backed by a MutableMap")
        }
        mutableBusMap.putAll(_busMap)
        _busMap = mutableBusMap
        mutableScopeMap.putAll(_scopeMap)
        _scopeMap = mutableScopeMap

    }

    override fun busSubscribe(busId: MessageBusIdInt, scope: CoroutineScope, autoCreate: Boolean, onReceive: suspend (MessageBusIdInt, BusMessageInt<T, K>) -> Unit) {
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

    override fun busUnsubscribe(busId: MessageBusIdInt) {
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

    override fun busGet(busId: MessageBusIdInt) : MutableSharedFlow<BusMessageInt<T, K>>? = synchronized(lock) {
        busMap[busId]
    }

    override fun scopeGet(busId: MessageBusIdInt) : CoroutineScope? = synchronized(lock) {
        scopeMap[busId]
    }

    override fun busCreate(busId: MessageBusIdInt, scope: CoroutineScope) : MutableSharedFlow<BusMessageInt<T, K>>? {
        val tag = "busCreate/${randomString(2u)}"

        synchronized(lock) {
            if (null == busMap[busId]) {
                logd(tag, "Creating bus $busId")
                busMap[busId] = MutableSharedFlow<BusMessageInt<T, K>>(
                    replay = 10,
                    extraBufferCapacity = 100,
                    onBufferOverflow = BufferOverflow.SUSPEND
                )
            }
            scopeMap[busId] = scope
        }
        return busMap[busId]
    }

    override fun busAdd(busImpl: MessageBusInt<T, K>) {
        synchronized(lock) {
            busImpl.busMapImport(busMap, scopeMap)
        }
    }

    override fun busJoin(busImpl: MessageBusInt<T, K>) {
        synchronized(lock) {
            busMapImport(busImpl.busMap, busImpl.scopeMap)
        }
    }

    override suspend fun busSendSync (busId: MessageBusIdInt, msg: BusMessageInt<T, K>) {
        val tag = "busSend/${randomString(2u)}"
        val data= msg.data

        logd(tag, "busSend: busId ${busId.toString()} data: ${if (null != data) data::class else null}  type: ${msg.type}")

        val bus = synchronized(lock) { busMap[busId] } ?: run {
            logd(tag, "busSend: bus for $busMap $busId -> ${busMap[busId]} does not exist")
            throw (NoSuchElementException("${this}: busSend: bus for $busMap $busId -> ${busMap[busId]} does not exist"))
        }

        bus.emit(msg)
    }

    override fun busSendAsync (busId: MessageBusIdInt, msg: BusMessageInt<T, K>) {
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


    override suspend fun onBusMessage(busId: MessageBusIdInt, msg: BusMessageInt<T, K>) {
        val tag = "busOnReceive/${randomString(2u)}"

        logd(tag, "${this.toString()} busOnReceive busId: $busId ${msg.type} ${msg.data}. Not implemented.")
        throw (NotImplementedError("$tag: $this busOnReceive(busId: $busId. Not implemented."))
    }
}
