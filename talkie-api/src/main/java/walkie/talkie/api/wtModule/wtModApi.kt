package walkie.talkie.api.wtModule

import kotlinx.coroutines.CoroutineScope
import walkie.talkie.api.wtdebug.wtError
import walkie.util.api.PipeIdInt
import walkie.util.api.PipeMessageInt
import walkie.util.api.PipeMuxInt
import walkie.util.generic.ModuleOpInterface
import walkie.util.randomString

interface ModuleOpInt : ModuleOpInterface<WTModOpArg>
typealias WTPipeMessage = PipeMessageInt<PipeMessageType, Any>

class ModuleOpImpl(
    private val _pipeMux: PipeMuxInt<PipeMessageType, Any>):
    PipeMuxInt<PipeMessageType, Any> by _pipeMux,
    ModuleOpInt
{
    companion object {
        const val TAG = "WTWiFiDirectManager"
        val TAGKClass = ModuleOpImpl::class
    }

    override fun set(
        propId: WTModOpArg,
        value: WTModOpArg
    ): WTModOpArg {
        TODO("Not yet implemented")
    }

    override fun get(propId: WTModOpArg): WTModOpArg {
        TODO("Not yet implemented")
    }

    override fun subscribe(
        to: WTModOpArg,
        onEventInfo: WTModOpArg
    ) {
        val tag = "subscribe/${randomString(2U)}"

        val to = (to as? WTModOpArg.To)?.id ?: run {
            wtError(tag, "Invalid input: to -> $to")
            return
        }

        (onEventInfo as? WTModOpArg.OnEventInfo)?.let { eventInfo ->
            pipeSubscribe(
                pipeId = to,
                scope = eventInfo.scope,
                autoCreate = true,
                onReceive = eventInfo.onEvent
            )
        } ?: run {
            wtError(tag, "Invalid input: onEvent -> $onEventInfo")
            return
        }
    }

    override fun send(
        to: WTModOpArg,
        msg: WTModOpArg
    ) {
        val tag = "sendEvent/${randomString(2U)}"

        val to = (to as? WTModOpArg.To)?.id ?: run {
            wtError(tag, "Invalid input: to -> $to")
            return
        }

        val msg = (msg as? WTModOpArg.Msg)?.msg ?: run {
            wtError(tag, "Invalid msg: $msg")
            return
        }

        pipeSendAsync(pipeId = to, msg = msg)
    }

    override fun registerCallback(
        to: WTModOpArg,
        callback: WTModOpArg
    ) {
        TODO("Not yet implemented")
    }
}

sealed class WTModOpArg {
    sealed class To(val id: PipeIdInt): WTModOpArg() {
        object Comm: To(PipeId.ToComm)
        object Activity: To(PipeId.ToActivity)
        object Wifi: To(PipeId.ToWifi)
        object CommonData: To(PipeId.TOCommonData)
        object IpComm: To(PipeId.ToIpComm)
        object PRMComm: To(PipeId.ToPRMComm)
        object Chat: To(PipeId.ToChat)
    }
    data class OnEventInfo(
        val onEvent: (suspend (PipeIdInt, PipeMessageInt<PipeMessageType, Any>) -> Unit),
        val scope: CoroutineScope
    ) : WTModOpArg()
    data class Msg(val msg: WTPipeMessage) : WTModOpArg()
    object None : WTModOpArg()
}

enum class PipeMessageType {
    PipeMessageNA,
    ControlMesh,
    ChatMessage,
    ChatMessageLoopback,
    WifiMessage,
    WifiDebugInfoMessage,
    WifiRestartChannel,
    MeshDebugInfoMessage,
    WifiBroadcastReceiver,
    LocalIp,
    LocalServerPort,
    GroupInfo,
    GroupServerPort,
    UpdateChatUI,
    UpdatePeersUI,
    CommToChatPacket
}

enum class PipeId : PipeIdInt {
    PipeNA,
    ToComm,
    ToWifi,
    ToChat,
    ToPRMComm,
    ToIpComm,
    ToActivity,
    TOCommonData
}
