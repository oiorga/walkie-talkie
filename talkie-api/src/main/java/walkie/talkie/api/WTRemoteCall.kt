package walkie.talkie.api;

import walkie.util.RemoteCall
import walkie.util.api.RemoteCallApi

typealias WTRemoteCall = RemoteCall<WTRemoteCallId>
typealias WTRemoteCallApi = RemoteCallApi<WTRemoteCallId>

enum class WTRemoteCallId {
    RCDummy0, RCDummy1, RCDummy2, RCDummy3, RCDummy4, RCDummy5, RCDummy6, RCDummy7, RCDummy8, RCDummy9,
    RCNA,
    RCCheckWifiDPermissions,
    RCRequestWifiDPermissions,
    RCChatMessageToComm,
    RCChatMessageFromComm,
    RCSendChatMessage,
    RCUpdateUI
}
