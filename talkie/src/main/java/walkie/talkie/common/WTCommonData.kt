package walkie.talkie.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import walkie.chat.ChatGroupMap
import walkie.comm.WTComm
import walkie.util.generic.ChannelMux
import walkie.util.generic.ChannelMuxInt
import walkie.glue.wtchat.ChatGroupIdInt
import walkie.glue.wtchat.ChatGroupType
import walkie.glue.wtchat.ChatMessageAbs
import walkie.glue.wtsystem.NodeIdInt
import walkie.glue_inc.ChannelId
import walkie.glue_inc.ChannelIdInt
import walkie.glue_inc.ChannelMessageType
import walkie.talkie.globalmap.DiscussionMap
import walkie.talkie.playground.CounterLive
import walkie.talkie.ui.nav.wtChatUpdateUI
import walkie.talkie.ui.screens.WTUITheme
import walkie.talkie.viewmodel.WTViewModel
import walkie.talkie.BuildConfig
import walkie.util.LifeCycleObserver
import walkie.util.generic.RemoteCallMux
import walkie.util.generic.RemoteCallMuxInt
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString
import walkie.wifidirect.WTWiFiDirect
import walkie.wifidirect.WiFiDirectBroadcastReceiver

class WTCommonData private constructor (
    private val _remoteCallMux: RemoteCallMuxInt<Any, Any> = RemoteCallMux<Any, Any>(),
    private val _channelMux: ChannelMuxInt<Any, ChannelMessageType> = ChannelMux<Any, ChannelMessageType>(),
) : RemoteCallMuxInt<Any, Any> by _remoteCallMux,
    ChannelMuxInt<Any, ChannelMessageType> by _channelMux
{
    companion object {
        val ONE: WTCommonData by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { WTCommonData() }
        val initStage = arrayOf(false, false, false, false)
        const val TAG = "WTCommonData"
    }

    init {
        logging(true)
        logd("init")
        /* registerRemoteCall(RemoteCallId.RCUpdateUI) { run { updateUiLiveData.update() } } */
    }

    lateinit var wtVModel: WTViewModel

    lateinit var wtSystemNodeId: NodeIdInt
    lateinit var wtDeviceName: String

    lateinit var updateUiLiveData: UpdateUiLiveData
    lateinit var wtCurrentDiscussionId: ChatGroupIdInt
    lateinit var wtGlobalDiscussionMap: DiscussionMap
    lateinit var wtGlobalGroupMap: ChatGroupMap

    lateinit var wtComm: WTComm
    lateinit var wtWifiD: WTWiFiDirect
    lateinit var wtBcastReceiver: WiFiDirectBroadcastReceiver

    lateinit var wtScope: CoroutineScope
    lateinit var wtLCObs: LifeCycleObserver

    private var wtDebug: Boolean? = BuildConfig.DEBUG

    var customComposables: MutableMap<String, @Composable (Modifier, WTUITheme) -> Unit> = mutableMapOf()

    fun wtDebug(onOff: Boolean? = null): Boolean {
        if (null != onOff) wtDebug = onOff
        return (true == wtDebug)
    }

    override suspend fun channelOnReceive(
        channelId: ChannelIdInt,
        inputType: ChannelMessageType?,
        input: Any?
        ) {
        val tag = "channelOnReceive/${randomString(2U)}"

        logd(tag, "channelId: $channelId inputType: $inputType input: $input")
        when (channelId) {
            ChannelId.RCTOCommonData -> {
                if (updateUI(inputType!!, input))
                    updateUiLiveData.update()
            }
            else -> {
                logd(tag, "channelId: $channelId no service available")
            }
        }
    }

    fun sendChatMessage(chatMessage: ChatMessageAbs) {
        val globalDiscussionMap = wtGlobalDiscussionMap
        globalDiscussionMap.sendMessage(chatMessage)
    }

    /* To Remove */
    lateinit var counterLive: CounterLive
}

internal fun WTCommonData.updateUI(
    uiScreenMajor: ChannelMessageType,
    uiScreenMinor: Any?
): Boolean {
    val tag = "updateUI/${randomString(2U)}"
    var updateUI = false
    updateUI = when (uiScreenMajor) {
        ChannelMessageType.RCUpdateChatUI -> {
            wtChatUpdateUI(uiScreenMinor as ChatGroupType, wtVModel.currentScreen())
        }
        else -> {
            true
        }
    }
    return updateUI
}

data class UpdateUiLiveData(
    private val _counter: MutableLiveData<Long> = MutableLiveData<Long>()
) {
    init {
        _counter.value = 0
    }

    val counter: LiveData<Long>
        get() = _counter

    fun update() {
        _counter.value = _counter.value?.plus(1)
    }
}
