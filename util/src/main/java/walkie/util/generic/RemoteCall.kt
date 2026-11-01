package walkie.util.generic

import android.util.Log
import walkie.util.api.RemoteCallBaseApi
import walkie.util.api.RemoteCallBaseHandler
import walkie.util.api.RemoteCallBaseIdApi

import walkie.util.logd

open class RemoteCallBase<CallId, CallInput, CallResult>() :
    RemoteCallBaseApi<CallId, RemoteCallBaseHandler<*, *>, CallInput, CallResult> {
    private var _callMap: MutableMap<CallId, RemoteCallBaseHandler<*, *>> = mutableMapOf()
    override val callMap: Map<CallId, RemoteCallBaseHandler<*, *>>
        get() = _callMap

    private val tag = "RemoteCallBaseMux"
    private val lock = Any()

    init {
        Log.d(tag, "$tag init")
    }

    override fun callMapMergeIn(callMap: Map<CallId, RemoteCallBaseHandler<*, *>>) = synchronized(lock) {
        val mutableCallMap =
            callMap as? MutableMap<CallId, RemoteCallBaseHandler<*, *>> ?: run {
                logd(tag, "callMapImport requires callMap to be backed by a MutableMap")
                error("callMapImport requires callMap to be backed by a MutableMap")
            }
        mutableCallMap.putAll(_callMap)
        _callMap = mutableCallMap
    }

    override fun registerRemoteCall(
        remoteCallId: CallId,
        remoteHandler: RemoteCallBaseHandler<*, *>
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
        _callMap[remoteCallId] as? RemoteCallBaseHandler<CallInput, CallResult>
    }?.call(input) ?: run {
        Log.d(tag, "$tag: call: callback $remoteCallId does not exist")
        null
    }

    override fun remoteCall(
        remoteCallId: CallId
    ): CallResult? = synchronized(lock) {
        _callMap[remoteCallId] as? RemoteCallBaseHandler<CallInput, CallResult>
    }?.call(null) ?: run {
        Log.d(tag, "$tag: call: callback $remoteCallId does not exist")
        null
    }

    override fun remoteCallAdd(callImpl: RemoteCallBaseApi<CallId, RemoteCallBaseHandler<*, *>, CallInput, CallResult>) {
        callMapMergeIn(callImpl.callMap)
    }

    override fun remoteCallJoin(callImpl: RemoteCallBaseApi<CallId, RemoteCallBaseHandler<*, *>, CallInput, CallResult>) {
        callImpl.callMapMergeIn(callMap)
    }

    fun <CallId, CallInput, CallResult> RemoteCallBaseApi<CallId, RemoteCallBaseHandler<*, *>, CallInput, CallResult>.typedCall(
        callId: CallId,
        input: CallInput
    ): CallResult? {
        return (input?.let {
            this.remoteCall(callId, it)
        } ?: this.remoteCall(callId))
    }

    fun <CallId, CallInput, CallResult> RemoteCallBaseApi<CallId, RemoteCallBaseHandler<*, *>, CallInput, CallResult>.typedCall(
        callId: CallId
    ): CallResult? {
        return this.remoteCall(callId)
    }
}

class RemoteCall<CallId>(
    private val remoteCallImpl: RemoteCallBaseApi<CallId, RemoteCallBaseHandler<*, *>, Any, Any> = RemoteCallBase<CallId, Any, Any>()
):  RemoteCallApi<CallId>,
    RemoteCallBaseApi<CallId, RemoteCallBaseHandler<*, *>, Any, Any> by remoteCallImpl

interface RemoteCallApi<CallId>: RemoteCallBaseApi<CallId, RemoteCallBaseHandler<*, *>, Any, Any>
interface RemoteCallIdApi: RemoteCallBaseIdApi

/*
open class RemoteCallBase<CallId, CallInput, CallResult>() :
    RemoteCallApi<CallId, RemoteCallHandler<CallInput, CallResult>, CallInput, CallResult> {
    private var _callMap: MutableMap<CallId, RemoteCallHandler<CallInput, CallResult>> = mutableMapOf()
    override val callMap: Map<CallId, RemoteCallHandler<CallInput, CallResult>>
        get() = _callMap

    private val tag = "RemoteCallMux"
    private val lock = Any()

    init {
        Log.d (tag, "$tag init")
    }

    override fun callMapImport(callMap: Map<CallId, RemoteCallHandler<CallInput, CallResult>>) {
        TODO("Not yet implemented")
    }

    override fun registerRemoteCall(
        remoteCallId: RemoteCallIdApi<CallId>,
        remoteHandler: RemoteCallHandler<CallInput, CallResult>
    ) {
        synchronized(lock) {
            if (null != _callMap[remoteCallId.value])
                Log.d(tag, "$tag: register: callback $remoteCallId already exists")
            _callMap[remoteCallId.value] = remoteHandler
        }    }

    override fun remoteCall(
        remoteCallId: RemoteCallIdApi<CallId>,
        input: CallInput
    ): CallResult? = synchronized(lock) {
            _callMap[remoteCallId.value] }?.call(input) ?: run {
        Log.d(tag, "$tag: call: callback $remoteCallId does not exist")
        null
    }

    override fun remoteCall(
        remoteCallId: RemoteCallIdApi<CallId>
    ): CallResult? = synchronized(lock) {
        _callMap[remoteCallId.value] }?.call(null)
        ?: run {
            Log.d(tag, "$tag: call: callback $remoteCallId does not exist")
            null
        }

    override fun remoteCallAdd(callImpl: RemoteCallApi<CallId, RemoteCallHandler<CallInput, CallResult>, CallInput, CallResult>) {
        callMapImport(callImpl.callMap)
    }

    override fun remoteCallJoin(callImpl: RemoteCallApi<CallId, RemoteCallHandler<CallInput, CallResult>, CallInput, CallResult>) {
        callImpl.callMapImport(callMap)
    }
}
*/
