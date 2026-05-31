package walkie.util.generic

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import walkie.util.api.ChannelIdInt
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString
import java.util.concurrent.locks.ReentrantLock

interface ChannelMuxInt<T, K> {
    val channelMap: MutableMap<ChannelIdInt, MutableSharedFlow<ChannelMessage<T, K>>>
    val receiverMap: MutableMap<ChannelIdInt, ChannelMuxInt<T, K>>
    fun registerReceiver (channelId: ChannelIdInt, scope: CoroutineScope, receiverObj: ChannelMuxInt<T, K>)
    suspend fun channelOnReceive (channelId: ChannelIdInt, type: K?, input: T?)
    fun channelSend (channelId: ChannelIdInt, scope: CoroutineScope, type: K? = null, input: T? = null)
    fun channel(channelId: ChannelIdInt) : MutableSharedFlow<ChannelMessage<T, K>>?
    fun channelCreate(channelId: ChannelIdInt) : MutableSharedFlow<ChannelMessage<T, K>>?
}

data class ChannelMessage<T, K>(
    val input: T?,
    val type: K?
)

fun <T, K> ChannelMuxInt<T, K>.registerAsReceiver (channelId: ChannelIdInt, scope: CoroutineScope, vararg senderObjList: ChannelMuxInt<T, K>) {
    senderObjList.forEach { senderObj ->
        senderObj.registerReceiver(channelId, scope, this)
    }
}

fun <T, K> ChannelMuxInt<T, K>.registerSenders (channelId: ChannelIdInt, scope: CoroutineScope,  vararg senderObjList: ChannelMuxInt<T, K>) {
    senderObjList.forEach { senderObj ->
        senderObj.registerReceiver(channelId, scope,  this)
    }
}

class ChannelMux<T, K>() : ChannelMuxInt<T, K> {
    override val channelMap: MutableMap<ChannelIdInt, MutableSharedFlow<ChannelMessage<T, K>>> = mutableMapOf()
    override val receiverMap: MutableMap<ChannelIdInt, ChannelMuxInt<T, K>> = mutableMapOf()

    private val lock = Any()

    companion object {
        private const val TAG = "ChannelMux"
        val TAGKClass = ChannelMux::class
    }

    init {
        logging()
        logd (TAG, "init")
    }

    override fun channel(channelId: ChannelIdInt) : MutableSharedFlow<ChannelMessage<T, K>>? {
        return channelMap[channelId]
    }

    override fun channelCreate(channelId: ChannelIdInt) : MutableSharedFlow<ChannelMessage<T, K>>? {
        synchronized(lock) {
            if (null == channelMap[channelId]) {
                channelMap[channelId] =
                    MutableSharedFlow<ChannelMessage<T, K>>(
                        replay = 1,
                        extraBufferCapacity = 100,
                        onBufferOverflow = BufferOverflow.SUSPEND
                    )
            }
        }
        return channelMap[channelId]
    }

    override fun registerReceiver (channelId: ChannelIdInt, scope: CoroutineScope, receiverObj: ChannelMuxInt<T, K>) {
        val tag = "registerReceiver/${randomString(2u)}"

        logd(tag, "registerReceiver: channelId ${channelId.toString()} ${receiverObj.toString()}")

        synchronized(lock) {
            receiverMap.putIfAbsent(channelId, receiverObj)

            if (null != receiverObj.channel(channelId)) {
                logd(tag, "registerReceiver: channelId ${channelId.toString()} already exists")
            } else {
                receiverObj.channelCreate(channelId)
                scope.launch {
                    receiverObj.channel(channelId)
                        ?.onEach { msg ->
                            receiverObj.channelOnReceive(
                                channelId = channelId,
                                input = msg.input,
                                type = msg.type
                            )
                        }
                        ?.collect()
                }
            }
        }
    }

    override fun channelSend (channelId: ChannelIdInt, scope: CoroutineScope, type: K?, input: T?) {
        val tag = "channelSend/${randomString(2u)}"

        logd(tag, "channelSend: channelId ${channelId.toString()} input: $input type: $type")

        if (null == receiverMap[channelId]) {
            logd(tag, "channelSend: receiver object for channel $channelId / $type is not registered")
            throw (NoSuchElementException("TAG: channelSend: receiver object for channel $channelId / $type is not registered"))
        }

        val channel  = receiverMap[channelId]?.channel(channelId) ?: run {
            logd(tag, "channelSend: channel for ${receiverMap[channelId]}[$channelId] does not exist")
            throw (NoSuchElementException("${this}: channelSend: channel for ${receiverMap[channelId]}[$channelId] does not exist"))
        }

        scope.launch {
            channel.emit(value = ChannelMessage(input, type))
        }
    }

    override suspend fun channelOnReceive(channelId: ChannelIdInt, type: K?, input: T?) {
        val tag = "channelOnReceive/${randomString(2u)}"

        logd(tag, "channelOnReceive channelId: $channelId. Not implemented.")
        throw (NotImplementedError("$tag: channelOnReceive(channelId: $channelId. Not implemented."))
    }
}
