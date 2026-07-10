package walkie.talkie.api.wtModule

import walkie.util.api.PipeIdInt
import walkie.util.generic.ModuleOp
import walkie.util.generic.ModuleOpInterface

interface ModuleOpInt : ModuleOpInterface<ModuleOp>
class ModuleOpImpl(): ModuleOpInt {
    override fun <Output> set(modReq: ModuleOp): ModuleOp.Output<Output> {
        error("Not yet implemented")
    }

    override fun <Output> get(modReq: ModuleOp): ModuleOp.Output<Output> {
        error("Not yet implemented")
    }

    override fun subscribeToEvent(modReq: ModuleOp): ModuleOp.Output.Empty {
        error("Not yet implemented")
    }

    override fun sendEvent(modReq: ModuleOp): ModuleOp.Output.Empty {
        error("Not yet implemented")
    }

    override fun registerCallback(modReq: ModuleOp): ModuleOp.Output.Empty {
        error("Not yet implemented")
    }

    companion object {
        const val TAG = "WTWiFiDirectManager"
        val TAGKClass = ModuleOpImpl::class
    }
}

fun modOpToPipeType(modOpId: WTModOpId): PipeIdInt {
    return when (modOpId) {
        WTModOpId.WTSubscribeToWifiD -> {
            PipeId.ToWifi
        }
        else -> {
            PipeId.PipeNA
        }
    }
}

enum class WTModOpId {
    WTSubscribeToWifiD,
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
    ToWTActivity,
    TOCommonData
}
