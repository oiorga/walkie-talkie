package walkie.talkie.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import walkie.chat.ChatGroupMap
import walkie.comm.WTComm
import walkie.util.generic.PipeMux
import walkie.util.generic.PipeMuxInt
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
import walkie.util.CoroutineRuntime
import walkie.util.api.PipeId
import walkie.util.api.PipeIdInt
import walkie.util.api.PipeMessageType
import walkie.util.api.RemoteCallMuxInt
import walkie.util.generic.RemoteCallMux
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString
import walkie.wifidirect.WTWifiDirectManager
import walkie.wifidirect.WiFiDirectBroadcastReceiver

class WTCommonData private constructor (
    private val _remoteCallMux: RemoteCallMuxInt = RemoteCallMux(),
    private val _channelMux: PipeMuxInt<Any, PipeMessageType> = PipeMux<Any, PipeMessageType>(),
) : RemoteCallMuxInt by _remoteCallMux,
    PipeMuxInt<Any, PipeMessageType> by _channelMux,
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
        type: PipeMessageType?,
        input: Any?
        ) {
        val tag = "channelOnReceive/${randomString(2U)}"

        logd(tag, "channelId: $pipeId inputType: $type input: $input")
        when (pipeId) {
            PipeId.RCTOCommonData -> {
                if (updateUI(type!!, input))
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
