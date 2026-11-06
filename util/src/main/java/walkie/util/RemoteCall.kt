package walkie.util

import android.util.Log
import walkie.util.api.RemoteCallApi
import walkie.util.api.RemoteCallBaseApi
import walkie.util.api.RemoteCallHandlerBaseApi
import walkie.util.api.RemoteCallIdBaseApi

open class RemoteCallBase<CallId, CallInput, CallResult>() :
    RemoteCallBaseApi<CallId, RemoteCallHandlerBaseApi<*, *>, CallInput, CallResult> {
    private var _callMap: MutableMap<CallId, RemoteCallHandlerBaseApi<*, *>> = mutableMapOf()
    override val callMap: Map<CallId, RemoteCallHandlerBaseApi<*, *>>
        get() = _callMap

    private val tag = "RemoteCallBaseMux"
    private val lock = Any()

    init {
        Log.d(tag, "$tag init")
    }

    override fun callMapMergeIn(callMap: Map<CallId, RemoteCallHandlerBaseApi<*, *>>) = synchronized(lock) {
        val mutableCallMap =
            callMap as? MutableMap<CallId, RemoteCallHandlerBaseApi<*, *>> ?: run {
                logd(tag, "callMapImport requires callMap to be backed by a MutableMap")
                error("callMapImport requires callMap to be backed by a MutableMap")
            }
        mutableCallMap.putAll(_callMap)
        _callMap = mutableCallMap
    }

    override fun registerRemoteCall(
        remoteCallId: CallId,
        remoteHandler: RemoteCallHandlerBaseApi<*, *>
    ) {
        synchronized(lock) {
            if (null != _callMap[remoteCallId])
                Log.d(tag, "$tag: register: callback $remoteCallId already exists")
            _callMap[remoteCallId] = remoteHandler
        }
    }

    override fun remoteCall(
        remoteCallId: CallId,
        input: CallInput
    ): CallResult? = synchronized(lock) {
        _callMap[remoteCallId] as? RemoteCallHandlerBaseApi<CallInput, CallResult>
    }?.call(input) ?: run {
        Log.d(tag, "$tag: call: callback $remoteCallId does not exist")
        null
    }

    override fun remoteCall(
        remoteCallId: CallId
    ): CallResult? = synchronized(lock) {
        _callMap[remoteCallId] as? RemoteCallHandlerBaseApi<CallInput, CallResult>
    }?.call(null) ?: run {
        Log.d(tag, "$tag: call: callback $remoteCallId does not exist")
        /* error("$tag: call: callback $remoteCallId does not exist") */
        null
    }

    override fun remoteCallAdd(callImpl: RemoteCallBaseApi<CallId, RemoteCallHandlerBaseApi<*, *>, CallInput, CallResult>) {
        callMapMergeIn(callImpl.callMap)
    }

    override fun remoteCallJoin(callImpl: RemoteCallBaseApi<CallId, RemoteCallHandlerBaseApi<*, *>, CallInput, CallResult>) {
        callImpl.callMapMergeIn(callMap)
    }

    fun <CallId, CallInput, CallResult> RemoteCallBaseApi<CallId, RemoteCallHandlerBaseApi<*, *>, CallInput, CallResult>.typedCall(
        callId: CallId,
        input: CallInput
    ): CallResult? {
        return (input?.let {
            this.remoteCall(callId, it)
        } ?: this.remoteCall(callId))
    }

    fun <CallId, CallInput, CallResult> RemoteCallBaseApi<CallId, RemoteCallHandlerBaseApi<*, *>, CallInput, CallResult>.typedCall(
        callId: CallId
    ): CallResult? {
        return this.remoteCall(callId)
    }
}

typealias RemoteCall<CallId> = RemoteCallBase<CallId, Any, Any>

