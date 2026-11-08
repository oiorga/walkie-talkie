package walkie.talkie.api;

import walkie.util.RemoteCall
import walkie.util.api.RemoteCallApi
import walkie.util.api.RemoteCallId

interface WTRemoteCallApi: RemoteCallApi<WTRemoteCallId>
class WTRemoteCall: WTRemoteCallApi, RemoteCall<WTRemoteCallId>()

/*
class WTRemoteCall(
    private val remoteCallImpl: RemoteCall<WTRemoteCallId> = RemoteCall()
) : WTRemoteCallApi by remoteCallImpl
*/

enum class WTRemoteCallId: RemoteCallId {
    RCDummy0, RCDummy1, RCDummy2, RCDummy3, RCDummy4, RCDummy5, RCDummy6, RCDummy7, RCDummy8, RCDummy9,
    RCNA,
    RCCheckWifiDPermissions,
    RCRequestWifiDPermissions,
    RCChatMessageToComm,
    RCChatMessageFromComm,
    RCSendChatMessage,
    RCUpdateUI
}
