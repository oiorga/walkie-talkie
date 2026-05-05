package walkie.util.api

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

interface RemoteCallMuxBaseInt<In, Out> {
    fun registerRemoteCallTo (remoteCallId: RemoteCallIdInt, callToObj: RemoteCallMuxBaseInt<In, Out>)
    fun registerRemoteCall (remoteCallId: RemoteCallIdInt, callBack: (input: In?) -> Out)
    fun remoteCall (remoteCallId: RemoteCallIdInt, input: In) : Out?
    fun remoteCall (remoteCallId: RemoteCallIdInt) : Out?
    fun remoteCallById (remoteCallId: RemoteCallIdInt) : ((In?) -> Out)?
}

interface RemoteCallMuxInt: RemoteCallMuxBaseInt<Any, Any>
