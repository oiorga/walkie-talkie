package walkie.util.generic

import android.util.Log
import walkie.glue_inc.RemoteCallIdInt

interface RemoteCallMuxInt<T, K> {
    fun registerRemoteCallTo (remoteCallId: RemoteCallIdInt, callToObj: RemoteCallMuxInt<T, K>)
    fun registerRemoteCall (remoteCallId: RemoteCallIdInt, callBack: (input: T?) -> K)
    fun remoteCall (remoteCallId: RemoteCallIdInt, input: T) : K?
    fun remoteCall (remoteCallId: RemoteCallIdInt) : K?
    fun remoteCallById (remoteCallId: RemoteCallIdInt) : ((T?) -> K)?
}

class RemoteCallMux<T, K>() : RemoteCallMuxInt<T, K> {
    private val _remoteCallMapMux: MutableMap<RemoteCallIdInt, RemoteCallMuxInt<T, K>> = mutableMapOf()
    private val _remoteCallMap: MutableMap<RemoteCallIdInt, (input : T?) -> K> = mutableMapOf()
    private val tag = "RemoteCallMux"

    init {
        Log.d (tag, "$tag init")
    }

    override fun remoteCallById(remoteCallId: RemoteCallIdInt): ((T?) -> K)? {
        return _remoteCallMap[remoteCallId]
    }

    override fun registerRemoteCallTo(remoteCallId: RemoteCallIdInt, callToObj: RemoteCallMuxInt<T, K>) {
        if (null != _remoteCallMap[remoteCallId])
            Log.d (tag, "$tag: register: callback ${remoteCallId.toString()} already exists")

        _remoteCallMapMux[remoteCallId] = callToObj
    }

    override fun registerRemoteCall(remoteCallId: RemoteCallIdInt, callBack:  (input: T?) -> K) {
        if (null != _remoteCallMap[remoteCallId])
            Log.d (tag, "$tag: register: callback ${remoteCallId.toString()} already exists")

        _remoteCallMap[remoteCallId] = callBack
    }

    override fun remoteCall(remoteCallId: RemoteCallIdInt, input: T) : K? {
        return _remoteCallMapMux[remoteCallId]
            ?.remoteCallById(remoteCallId)
            ?.invoke(input)
            ?: run {
                Log.d(tag, "$tag: call: callback $remoteCallId does not exist")
                null
            }
    }

    override fun remoteCall(remoteCallId: RemoteCallIdInt): K? {
        return _remoteCallMapMux[remoteCallId]
            ?.remoteCallById(remoteCallId)
            ?.invoke(null)
            ?: run {
                Log.d(tag, "$tag: call: callback $remoteCallId does not exist")
                null
            }
    }
}

