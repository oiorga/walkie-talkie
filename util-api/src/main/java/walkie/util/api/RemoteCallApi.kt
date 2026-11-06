package walkie.util.api

interface RemoteCallIdBaseApi

interface RemoteCallBaseApi<CallId, RemoteHandler, CallInput, CallResult> {
    val callMap: Map<CallId, RemoteHandler>
    fun callMapMergeIn (callMap: Map<CallId, RemoteHandler>)
    fun registerRemoteCall(remoteCallId: CallId, remoteHandler: RemoteHandler)
    fun remoteCall (remoteCallId: CallId, input: CallInput) : CallResult?
    fun remoteCall (remoteCallId: CallId) : CallResult?
    fun remoteCallAdd(callImpl: RemoteCallBaseApi<CallId, RemoteHandler, CallInput, CallResult>)
    fun remoteCallJoin(callImpl: RemoteCallBaseApi<CallId, RemoteHandler, CallInput, CallResult>)
}

fun interface RemoteCallHandlerBaseApi<CallInput, CallResult> {
    fun execute(input: CallInput?): CallResult?
    fun call(input: CallInput?): CallResult? = execute(input)
}

/*
typealias RemoteRemoteCallHandler<CallInput, CallResult><CallInput, CallResult> = (CallInput) -> CallResult?

interface RemoteRemoteCallHandler<CallInput, CallResult> {
    fun <CallInput, CallResult> execute(input: CallInput): CallResult?
}
*/

typealias RemoteCallApi<CallId> = RemoteCallBaseApi<CallId, RemoteCallHandlerBaseApi<*, *>, Any, Any>
typealias RemoteCallIdApi = RemoteCallIdBaseApi