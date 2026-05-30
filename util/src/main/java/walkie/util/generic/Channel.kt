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
import walkie.util.api.ChannelIdInt

interface ChannelMuxInt<T, K> {
    val channelScope: CoroutineScope
    val channelMap: MutableMap<ChannelIdInt, MutableSharedFlow<ChannelMessage<T, K>>>
    val receiverMap: MutableMap<ChannelIdInt, ChannelMuxInt<T, K>>
    fun registerReceiver (channelId: ChannelIdInt, receiverObj: ChannelMuxInt<T, K>)
    suspend fun channelOnReceive (channelId: ChannelIdInt, type: K?, input: T?)
    fun channelSend (channelId: ChannelIdInt, type: K? = null, input: T? = null)
    fun channel(channelId: ChannelIdInt) : MutableSharedFlow<ChannelMessage<T, K>>?
    fun channelCreate(channelId: ChannelIdInt) : MutableSharedFlow<ChannelMessage<T, K>>?
}

data class ChannelMessage<T, K>(
    val input: T?,
    val type: K?
)

fun <T, K> ChannelMuxInt<T, K>.registerAsReceiver (channelId: ChannelIdInt, vararg senderObjList: ChannelMuxInt<T, K>) {
    senderObjList.forEach { senderObj ->
        senderObj.registerReceiver(channelId, this)
    }
}

fun <T, K> ChannelMuxInt<T, K>.registerSenders (channelId: ChannelIdInt, vararg senderObjList: ChannelMuxInt<T, K>) {
    senderObjList.forEach { senderObj ->
        senderObj.registerReceiver(channelId, this)
    }
}

class ChannelMux<T, K>() : ChannelMuxInt<T, K> {
    override val channelMap: MutableMap<ChannelIdInt, MutableSharedFlow<ChannelMessage<T, K>>> = mutableMapOf()
    override val receiverMap: MutableMap<ChannelIdInt, ChannelMuxInt<T, K>> = mutableMapOf()

    companion object {
        private const val TAG = "ChannelMux"
        private var count: Long = 0
    }

    init {
        Log.d (TAG, "$TAG init")
    }

    override fun channel(channelId: ChannelIdInt) : MutableSharedFlow<ChannelMessage<T, K>>? {
        return channelMap[channelId]
    }

    override fun channelCreate(channelId: ChannelIdInt) : MutableSharedFlow<ChannelMessage<T, K>>? {
        if (null == channelMap[channelId]) {
            channelMap[channelId] =
                MutableSharedFlow<ChannelMessage<T, K>>(
                    replay = 10,
                    extraBufferCapacity = 100,
                    onBufferOverflow = BufferOverflow.SUSPEND
                    )
        }
        return channelMap[channelId]
    }

    override fun registerReceiver (channelId: ChannelIdInt, receiverObj: ChannelMuxInt<T, K>) {
        Log.d(TAG, "$TAG: registerReceiver: channelId ${channelId.toString()} ${receiverObj.toString()}")

        if (null == receiverMap[channelId]) {
            receiverMap[channelId] = receiverObj
        }

        if (null != receiverObj.channel(channelId)) {
            Log.d(TAG, "$TAG: registerReceiver: channelId ${channelId.toString()} already exists")
        } else {
            receiverObj.channelCreate(channelId)
            receiverObj.channelScope.launch {
                receiverObj.channel(channelId)
                    ?.onEach { msg ->
                        receiverObj.channelOnReceive(channelId = channelId, input = msg.input, type = msg.type)
                    }
                    ?.collect()
            }
        }
    }

    override fun channelSend (channelId: ChannelIdInt, type: K?, input: T?) {
        Log.d(TAG, "$TAG: channelSend: channelId ${channelId.toString()} input: $input type: $type $count")
        count++

        if (null == receiverMap[channelId]) {
            Log.d(TAG, "$TAG: channelSend: receiver object for channel $channelId / $type is not registered")
            throw (NoSuchElementException("TAG: channelSend: receiver object for channel $channelId / $type is not registered"))
        }

        val channel  = receiverMap[channelId]?.channel(channelId) ?: run {
            Log.d(TAG, "$TAG: channelSend: channel for ${receiverMap[channelId]}[$channelId] does not exist")
            throw (NoSuchElementException("${this}: channelSend: channel for ${receiverMap[channelId]}[$channelId] does not exist"))
        }

        channelScope.launch {
            channel.emit(value = ChannelMessage(input, type))
        }
    }

    override suspend fun channelOnReceive(channelId: ChannelIdInt, type: K?, input: T?) {
        Log.d(TAG, "$TAG: channelOnReceive channelId: $channelId input: $input. Not implemented")
        throw (NotImplementedError("$TAG: channelOnReceive(channelId: $channelId input: $input) not implemented"))
    }

    override val channelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
