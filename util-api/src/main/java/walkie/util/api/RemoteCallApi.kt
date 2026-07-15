package walkie.util.api

interface RemoteCallIdInt

interface RemoteCallMuxBaseInt<In, Out> {
    val callMap: Map<RemoteCallIdInt, (input : In?) -> Out>

    fun callMapImport (callMap: Map<RemoteCallIdInt, (input : In?) -> Out>)
    fun registerRemoteCall (remoteCallId: RemoteCallIdInt, callBack: (input: In?) -> Out)
    fun remoteCall (remoteCallId: RemoteCallIdInt, input: In) : Out?
    fun remoteCall (remoteCallId: RemoteCallIdInt) : Out?
    fun remoteCallAdd(callImpl: RemoteCallMuxBaseInt<In, Out>)
    fun remoteCallJoin(callImpl: RemoteCallMuxBaseInt<In, Out>)
}

interface RemoteCallMuxInt: RemoteCallMuxBaseInt<Any, Any>
