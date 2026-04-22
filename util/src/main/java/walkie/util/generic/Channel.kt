package walkie.util.generic

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import walkie.glue_inc.ChannelIdInt

interface ChannelMuxInt<T, K> {
    val channelOnReceiveScope: CoroutineScope
    val channelMap: MutableMap<ChannelIdInt, MutableSharedFlow<Pair<T?, K?>>>
    val receiverMap: MutableMap<ChannelIdInt, ChannelMuxInt<T, K>>
    fun registerReceiver (channelId: ChannelIdInt, receiverObj: ChannelMuxInt<T, K>)
    suspend fun channelOnReceive (channelId: ChannelIdInt, inputType: K?, input: T?)
    fun channelSend (channelId: ChannelIdInt, inputType: K? = null, input: T? = null)
    fun channel(channelId: ChannelIdInt) : MutableSharedFlow<Pair<T?, K?>>?
    fun channelCreate(channelId: ChannelIdInt) : MutableSharedFlow<Pair<T?, K?>>?
}

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
    override val channelMap: MutableMap<ChannelIdInt, MutableSharedFlow<Pair<T?, K?>>> = mutableMapOf()
    override val receiverMap: MutableMap<ChannelIdInt, ChannelMuxInt<T, K>> = mutableMapOf()

    companion object {
        private const val TAG = "ChannelMux"
        private var count: Long = 0
    }

    init {
        Log.d (TAG, "$TAG init")
    }

    override fun channel(channelId: ChannelIdInt) : MutableSharedFlow<Pair<T?, K?>>? {
        return channelMap[channelId]
    }

    override fun channelCreate(channelId: ChannelIdInt) : MutableSharedFlow<Pair<T?, K?>>? {
        if (null == channelMap[channelId]) {
            channelMap[channelId] =
                MutableSharedFlow<Pair<T?, K?>>(
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
            receiverObj.channelOnReceiveScope.launch {
                receiverObj.channel(channelId)
                    ?.onEach { (input, inputType) ->
                        receiverObj.channelOnReceive(channelId = channelId, input = input, inputType = inputType)
                    }
                    ?.collect()
            }
        }
    }

    override fun channelSend (channelId: ChannelIdInt, inputType: K?, input: T?) {
        Log.d(TAG, "$TAG: channelSend: channelId ${channelId.toString()} input: $input inputType: $inputType $count")
        count++

        if (null == receiverMap[channelId]) {
            Log.d(TAG, "$TAG: channelSend: receiver object for channel $channelId / $inputType is not registered")
            throw (NotImplementedError("TAG: channelSend: receiver object for channel $channelId / $inputType is not registered"))
        }

        if (null == receiverMap[channelId]?.channel(channelId)) {
            Log.d(TAG, "$TAG: channelSend: channel for ${receiverMap[channelId]}[$channelId] does not exist")
            throw (NotImplementedError("${this}: channelSend: channel for ${receiverMap[channelId]}[$channelId] does not exist"))
        }

        channelOnReceiveScope.launch {
            receiverMap[channelId]?.channel(channelId)?.emit(value = Pair(input, inputType))
        }
    }

    override suspend fun channelOnReceive(channelId: ChannelIdInt, inputType: K?, input: T?) {
        Log.d(TAG, "$TAG: channelOnReceive channelId: $channelId input: $input. Not implemented")
        throw (NotImplementedError("$TAG: channelOnReceive(channelId: $channelId input: $input) not implemented"))
    }

    override val channelOnReceiveScope: CoroutineScope
        get() = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}

