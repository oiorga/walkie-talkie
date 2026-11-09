package walkie.talkie.api

import walkie.util.MessageBus
import walkie.util.api.BusMessageApi
import walkie.util.api.MessageBusId
import walkie.util.api.BusMessageType
import walkie.util.api.MessageBusApi

interface WTMessageBusApi: MessageBusApi<WTMessageBusId, WTBusMessage, WTBusMessageType, Any>
class WTMessageBus: MessageBus<WTMessageBusId, WTBusMessage, WTBusMessageType, Any>(), WTMessageBusApi
class WTBusMessage(
    override val type: WTBusMessageType,
    override val data: Any?
): WTBusMessageApi
interface WTBusMessageApi: BusMessageApi<WTBusMessageType, Any>

enum class WTBusMessageType: BusMessageType {
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

enum class WTMessageBusId : MessageBusId {
    PipeNA,
    ToComm,
    ToWifi,
    ToChat,
    ToPRMComm,
    ToIpComm,
    ToActivity,
    TOCommonData
}