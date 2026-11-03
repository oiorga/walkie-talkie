package walkie.talkie.api

import walkie.util.BusMessage
import walkie.util.MessageBus
import walkie.util.api.BusMessageApi
import walkie.util.api.MessageBusApi


typealias WTMessageBusApi = MessageBusApi<WTBusMessageId, WTBusMessageType, Any>
typealias WTMessageBus = MessageBus<WTBusMessageId, WTBusMessageType, Any>
typealias WTBusMessage = BusMessage<WTBusMessageType, Any>
typealias WTBusMessageApi = BusMessageApi<WTBusMessageType, Any>
enum class WTBusMessageType {
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

enum class WTBusMessageId {
    PipeNA,
    ToComm,
    ToWifi,
    ToChat,
    ToPRMComm,
    ToIpComm,
    ToActivity,
    TOCommonData
}