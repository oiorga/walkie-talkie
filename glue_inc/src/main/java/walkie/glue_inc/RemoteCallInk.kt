package walkie.glue_inc


interface RemoteCallIdInt
enum class RemoteCallId : RemoteCallIdInt {
    RCDummy0, RCDummy1, RCDummy2, RCDummy3, RCDummy4, RCDummy5, RCDummy6, RCDummy7, RCDummy8, RCDummy9,
    RCNA,
    RCCheckWifiDPermission,
    RCRequestWifiDPermission,
    RCChatMessageToComm,
    RCChatMessageFromComm,
    RCSendChatMessage,
    RCUpdateUI
}
