package walkie.talkie.api.wtModule

import walkie.util.api.PipeIdInt

enum class WTModOpType {
    WTWifiNotifyGroupChange,
    WTWifiNotifyIpChange,
    WTWifiNotifyAllChange
}

enum class PipeMessageType {
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
    Dummy0,
    ToComm,
    ToWifi,
    ToChat,
    ToPRMComm,
    ToIpComm,
    ToWTActivity,
    TOCommonData
}
