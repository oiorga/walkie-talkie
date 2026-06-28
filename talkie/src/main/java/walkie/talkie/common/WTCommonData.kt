package walkie.talkie.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import walkie.chat.ChatGroupMap
import walkie.comm.WTComm
import walkie.util.generic.PipeMux
import walkie.talkie.api.wtchat.ChatGroupIdInt
import walkie.talkie.api.wtchat.ChatGroupType
import walkie.talkie.api.wtchat.ChatMessageAbs
import walkie.talkie.api.wtsystem.NodeIdInt
import walkie.talkie.globalmap.DiscussionMap
import walkie.talkie.ui.nav.wtChatUpdateUI
import walkie.talkie.ui.screens.WTUITheme
import walkie.talkie.viewmodel.WTViewModel
import walkie.talkie.BuildConfig
import walkie.talkie.api.wtdebug.WTDebugInt
import walkie.talkie.api.wtsystem.PipeId
import walkie.talkie.api.wtsystem.PipeMessageType
import walkie.util.CoroutineRuntime
import walkie.util.api.PipeIdInt
import walkie.util.api.PipeMessageInt
import walkie.util.api.PipeMuxInt
import walkie.util.api.RemoteCallMuxInt
import walkie.util.generic.RemoteCallMux
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString
import walkie.wifidirect.WTWifiDirectManager
import walkie.wifidirect.WiFiDirectBroadcastReceiver

class WTCommonData private constructor (
    private val _remoteCallMux: RemoteCallMuxInt = RemoteCallMux(),
    private val _pipeMux: PipeMuxInt<PipeMessageType, Any> = PipeMux(),
) : RemoteCallMuxInt by _remoteCallMux,
    PipeMuxInt<PipeMessageType, Any> by _pipeMux,
    WTDebugInt
{
    companion object {
        val ONE: WTCommonData by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { WTCommonData() }
        const val TAG = "WTCommonData"
    }

    val tag = TAG

    val initStage = arrayOf(false, false, false, false)

    init {
        logging(true)
        logd(tag, "init")
        /* registerRemoteCall(RemoteCallId.RCUpdateUI) { run { updateUiLiveData.update() } } */
    }

    // They say view model should not be exposed here, in the global data structures that keeps general info related to app modules
    var wtVModel: WTViewModel? = null

    lateinit var wtSystemNodeId: NodeIdInt
    lateinit var wtDeviceName: String

    lateinit var updateUiLiveData: UpdateUiLiveData
    lateinit var wtCurrentDiscussionId: ChatGroupIdInt
    lateinit var wtGlobalDiscussionMap: DiscussionMap
    lateinit var wtGlobalGroupMap: ChatGroupMap

    lateinit var wtComm: WTComm
    lateinit var wtWifiD: WTWifiDirectManager
    lateinit var wtBcastReceiver: WiFiDirectBroadcastReceiver

    lateinit var wtRuntime: CoroutineRuntime
    lateinit var wtScope: CoroutineScope

    /* lateinit var wtLCObs: LifeCycleObserver */

    private var wtDebug: Boolean? = BuildConfig.DEBUG

    var customComposables: MutableMap<String, @Composable (Modifier, WTUITheme) -> Unit> = mutableMapOf()

    override fun wtDebug(onOff: Boolean? ): Boolean {
        if (null != onOff) wtDebug = onOff
        return (true == wtDebug)
    }

    override suspend fun pipeOnReceive(
        pipeId: PipeIdInt,
        msg: PipeMessageInt<PipeMessageType, Any>
        ) {
        val tag = "channelOnReceive/${randomString(2U)}"
        val type = msg.type
        val data = msg.data

        logd(tag, "channelId: $pipeId inputType: $type input: $data")
        when (pipeId) {
            PipeId.RCTOCommonData -> {
                if (updateUI(type, data))
                    updateUiLiveData.update()
            }
            else -> {
                logd(tag, "channelId: $pipeId no service available")
                throw (NoSuchElementException("${this}: channelId: $pipeId no service available"))
            }
        }
    }

    suspend fun sendChatMessage(chatMessage: ChatMessageAbs) {
        val globalDiscussionMap = wtGlobalDiscussionMap
        globalDiscussionMap.sendMessage(chatMessage)
    }
}

internal fun WTCommonData.updateUI(
    uiScreenMajor: PipeMessageType,
    uiScreenMinor: Any?
): Boolean {
    val tag = "updateUI/${randomString(2U)}"
    val updateUI: Boolean = when (uiScreenMajor) {
        PipeMessageType.RCUpdateChatUI -> {
            wtChatUpdateUI(uiScreenMinor as ChatGroupType, wtVModel?.currentScreen())
        }
        else -> {
            true
        }
    }
    return updateUI
}

class UpdateUiLiveData {
    private var _counter: MutableLiveData<Long> = MutableLiveData<Long>()

    init {
        _counter.value = 0
    }

    val counter: LiveData<Long>
        get() = _counter

    fun update() {
        _counter.value = _counter.value?.plus(1)
    }
}
