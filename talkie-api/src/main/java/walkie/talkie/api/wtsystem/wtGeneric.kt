package walkie.talkie.api.wtsystem

import walkie.util.api.PipeIdInt

enum class PipeMessageType {
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
    RCUpdatePeersUI,
    RCCommToChatPacket
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
