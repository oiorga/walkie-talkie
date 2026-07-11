package walkie.talkie.api.wtModule

import walkie.util.api.PipeIdInt
import walkie.util.api.PipeMessageInt
import walkie.util.generic.ModuleOp
import walkie.util.generic.ModuleOpInterface

interface ModuleOpInt : ModuleOpInterface<WTModuleOp>

typealias WTPipeMessage = PipeMessageInt<PipeMessageType, Any>

typealias WTModuleOp = ModuleOp<
        WTModOpId,
        PipeIdInt,
        WTPipeMessage>

class ModuleOpImpl(): ModuleOpInt {
    override fun <Output> set(modReq: WTModuleOp): ModuleOp.Output<Output> {
        TODO("Not yet implemented")
    }

    override fun <Output> get(modReq: WTModuleOp): ModuleOp.Output<Output> {
        TODO("Not yet implemented")
    }

    override fun subscribeToEvent(modReq: WTModuleOp): ModuleOp.Output.Empty {
        TODO("Not yet implemented")
    }

    override fun sendEvent(modReq: WTModuleOp): ModuleOp.Output.Empty {
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

fun modOpToPipeType(modOpId: WTModOpId): PipeIdInt {
    return when (modOpId) {
        WTModOpId.WTToWifiD -> {
            PipeId.ToWifi
        }
        WTModOpId.WTToComm -> {
            PipeId.ToComm
        }
        WTModOpId.WTToActivity -> {
            PipeId.ToActivity
        }
        else -> {
            PipeId.PipeNA
        }
    }
}

enum class WTModOpId {
    WTToWifiD,
    WTToComm,
    WTToActivity,
    WTWifiNotifyGroupChange,
    WTWifiNotifyIpChange,
    WTWifiNotifyAllChange
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
