package walkie.glue_inc

interface ChannelIdInt
interface ChannelMessageInt

enum class ChannelMessageType : ChannelMessageInt {
    RCDummy0, RCDummy1, RCDummy2, RCDummy3, RCDummy4, RCDummy5, RCDummy6, RCDummy7, RCDummy8, RCDummy9,
    RCNA,
    RCPrimitiveType,
    RCControlMesh,
    RCChatMessage,
    RCChatMessageLoopback,
    RCWifiMessage,
    RCWifiDebugInfoMessage,
    RCWifiRestartChannel,
    RCWTMeshDebugInfoMessage,
    RCWifiBroadcastReceiver,
    RCDirectIpPeersIn,
    RCDirectIpPeersChanged,
    RCLocalIp,
    /* RCStop, */
    RCLocalServerPort,
    RCGroupInfo,
    RCGroupServerPort,
    RCUpdateChatUI,
    RCUpdateMainUI,
    RCUpdateDebugUI,
    RCUpdatePeersUI
}

enum class ChannelId : ChannelIdInt {
    RCDummy0, RCDummy1, RCDummy2, RCDummy3, RCDummy4, RCDummy5, RCDummy6, RCDummy7, RCDummy8, RCDummy9,
    RCNA,
    RCToComm,
    RCToWifi,
    RCWifiToComm,
    RCChatToComm,
    RCCommToChat,
    RCToPRMComm,
    RCToIpComm,
    RCToWalkieTalkie,
    RCTOCommonData
}


interface RemoteCallIdInt
enum class RemoteCallId : RemoteCallIdInt {
    RCDummy0, RCDummy1, RCDummy2, RCDummy3, RCDummy4, RCDummy5, RCDummy6, RCDummy7, RCDummy8, RCDummy9,
    RCNA,
    RCChatMessageToComm,
    RCChatMessageFromComm,
    RCSendChatMessage,
    RCUpdateUI
}
