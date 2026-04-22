package walkie.util.generic

import android.util.Log
import walkie.glue_inc.RemoteCallIdInt

interface RemoteCallMuxInt<T, K> {
    fun registerRemoteCallTo (remoteCallId: RemoteCallIdInt, callToObj: RemoteCallMuxInt<T, K>)
    fun registerRemoteCall (remoteCallId: RemoteCallIdInt, callBack: suspend (input: T) -> K)
    suspend fun remoteCall (remoteCallId: RemoteCallIdInt, input: T)
    fun remoteCall (remoteCallId: RemoteCallIdInt) : (suspend (T) -> K)?
}

class RemoteCallMux<T, K>() : RemoteCallMuxInt<T, K> {
    private val remoteCallMapMux: MutableMap<RemoteCallIdInt, RemoteCallMuxInt<T, K>> = mutableMapOf()
    private val _remoteCallMap: MutableMap<RemoteCallIdInt, suspend (input : T) -> K> = mutableMapOf()
    private val tag = "RemoteCallMux"

    init {
        Log.d (tag, "$tag init")
    }

    override fun remoteCall(remoteCallId: RemoteCallIdInt): (suspend (T) -> K)? {
        return _remoteCallMap[remoteCallId]
    }

    override fun registerRemoteCallTo (remoteCallId: RemoteCallIdInt, callToObj: RemoteCallMuxInt<T, K>) {
        if (null != _remoteCallMap[remoteCallId])
            Log.d (tag, "$tag: register: callback ${remoteCallId.toString()} already exists")

        remoteCallMapMux[remoteCallId] = callToObj
    }

    override fun registerRemoteCall (remoteCallId: RemoteCallIdInt, callBack: suspend (input: T) -> K) {
        if (null != _remoteCallMap[remoteCallId])
            Log.d (tag, "$tag: register: callback ${remoteCallId.toString()} already exists")

        _remoteCallMap[remoteCallId] = callBack
    }

    override suspend fun remoteCall (remoteCallId: RemoteCallIdInt, input: T) {
        if (null != remoteCallMapMux[remoteCallId])
            remoteCallMapMux[remoteCallId]?.remoteCall(remoteCallId)?.invoke(input)
        else {
            Log.d (tag, "$tag: call: callback ${remoteCallId.toString()} does not exist")
        }
    }
}

