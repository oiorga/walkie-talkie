package walkie.talkie.api.wtModule

import walkie.util.api.PipeIdInt
import walkie.util.api.PipeMessageInt
import walkie.util.generic.ModuleOp
import walkie.util.generic.ModuleOpInterface

interface ModuleOpInt : ModuleOpInterface<WTModuleOp>

typealias WTPipeMessage = PipeMessageInt<PipeMessageType, Any>

typealias WTPipeCallback = suspend (PipeIdInt, PipeMessageInt<PipeMessageType, Any>) -> Unit

typealias WTModuleOp = ModuleOp<WTModOp>

class ModuleOpImpl(): ModuleOpInt {
    override fun <Output> set(modReq: WTModuleOp): ModuleOp.Output<Output> {
        TODO("Not yet implemented")
    }

    override fun <Output> get(modReq: WTModuleOp): ModuleOp.Output<Output> {
        TODO("Not yet implemented")
    }

    override fun subscribe(modReq: WTModuleOp): ModuleOp.Output.Empty {
        TODO("Not yet implemented")
    }

    override fun send(modReq: WTModuleOp): ModuleOp.Output.Empty {
        TODO("Not yet implemented")
    }

    override fun registerCallback(modReq: WTModuleOp): ModuleOp.Output.Empty {
        TODO("Not yet implemented")
    }

    companion object {
        const val TAG = "WTWiFiDirectManager"
        val TAGKClass = ModuleOpImpl::class
    }
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

sealed class WTModOp {
    sealed class Set : WTModOp()
    sealed class Get : WTModOp()
    data class To(val to: PipeIdInt) : WTModOp()
    data class OnEvent(val onEvent: WTPipeCallback) : WTModOp()
    data class Msg(val msg: WTPipeMessage) : WTModOp()
    data class Subscribe(val to: PipeIdInt, val onEvent: WTPipeMessage) : WTModOp()
    object None : WTModOp()
}
