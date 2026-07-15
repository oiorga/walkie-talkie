package walkie.util.api

interface RemoteCallIdInt

interface RemoteCallMuxBaseInt<In, Out> {
    fun registerRemoteCallTo (remoteCallId: RemoteCallIdInt, callToObj: RemoteCallMuxBaseInt<In, Out>)
    fun registerRemoteCall (remoteCallId: RemoteCallIdInt, callBack: (input: In?) -> Out)
    fun remoteCall (remoteCallId: RemoteCallIdInt, input: In) : Out?
    fun remoteCall (remoteCallId: RemoteCallIdInt) : Out?
    fun remoteCallById (remoteCallId: RemoteCallIdInt) : ((In?) -> Out)?
}

interface RemoteCallMuxInt: RemoteCallMuxBaseInt<Any, Any>
