package walkie.talkie.api.wtModule

import walkie.util.api.PipeIdInt
import walkie.util.api.PipeMessageInt
import walkie.util.generic.ModuleOpInterface

interface ModuleOpInt : ModuleOpInterface<WTModOpArg>
typealias WTPipeMessage = PipeMessageInt<PipeMessageType, Any>
typealias WTPipeCallback = suspend (PipeIdInt, PipeMessageInt<PipeMessageType, Any>) -> Unit

class ModuleOpImpl(): ModuleOpInt {
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
        onEvent: WTModOpArg
    ) {
        TODO("Not yet implemented")
    }

    override fun send(
        to: WTModOpArg,
        msg: WTModOpArg
    ) {
        TODO("Not yet implemented")
    }

    override fun registerCallback(
        chId: WTModOpArg,
        callback: WTModOpArg
    ) {
        TODO("Not yet implemented")
    }

    companion object {
        const val TAG = "WTWiFiDirectManager"
        val TAGKClass = ModuleOpImpl::class
    }
}

sealed class WTModOpArg {
    sealed class To(val id: PipeIdInt): WTModOpArg() {
        object Comm: To(PipeId.ToComm)
        object Activity: To(PipeId.ToActivity)
        object Wifi: To(PipeId.ToWifi)
    }

    data class OnEvent(val onEvent: WTPipeCallback) : WTModOpArg()
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
