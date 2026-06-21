package walkie.util.api

interface PipeIdInt
interface PipeMessageInt

enum class PipeMessageType : PipeMessageInt {
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

enum class PipeId : PipeIdInt {
    RCDummy0, RCDummy1, RCDummy2, RCDummy3, RCDummy4, RCDummy5, RCDummy6, RCDummy7, RCDummy8, RCDummy9,
    RCNA,
    RCToComm,
    RCToWifi,
    RCWifiToComm,
    RCChatToComm,
    RCCommToChat,
    RCToPRMComm,
    RCToIpComm,
    RCToWTActivity,
    RCTOCommonData
}
