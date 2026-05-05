package walkie.util.generic

import android.util.Log
import walkie.util.api.RemoteCallIdInt

interface RemoteCallMuxInt<In, Out> {
    fun registerRemoteCallTo (remoteCallId: RemoteCallIdInt, callToObj: RemoteCallMuxInt<In, Out>)
    fun registerRemoteCall (remoteCallId: RemoteCallIdInt, callBack: (input: In?) -> Out)
    fun remoteCall (remoteCallId: RemoteCallIdInt, input: In) : Out?
    fun remoteCall (remoteCallId: RemoteCallIdInt) : Out?
    fun remoteCallById (remoteCallId: RemoteCallIdInt) : ((In?) -> Out)?
}

class RemoteCallMux<In, Out>() : RemoteCallMuxInt<In, Out> {
    private val _remoteCallMapMux: MutableMap<RemoteCallIdInt, RemoteCallMuxInt<In, Out>> = mutableMapOf()
    private val _remoteCallMap: MutableMap<RemoteCallIdInt, (input : In?) -> Out> = mutableMapOf()
    private val tag = "RemoteCallMux"

    init {
        Log.d (tag, "$tag init")
    }

    override fun remoteCallById(remoteCallId: RemoteCallIdInt): ((In?) -> Out)? {
        return _remoteCallMap[remoteCallId]
    }

    override fun registerRemoteCallTo(remoteCallId: RemoteCallIdInt, callToObj: RemoteCallMuxInt<In, Out>) {
        if (null != _remoteCallMap[remoteCallId])
            Log.d (tag, "$tag: register: callback ${remoteCallId.toString()} already exists")

        _remoteCallMapMux[remoteCallId] = callToObj
    }

    override fun registerRemoteCall(remoteCallId: RemoteCallIdInt, callBack:  (input: In?) -> Out) {
        if (null != _remoteCallMap[remoteCallId])
            Log.d (tag, "$tag: register: callback ${remoteCallId.toString()} already exists")

        _remoteCallMap[remoteCallId] = callBack
    }

    override fun remoteCall(remoteCallId: RemoteCallIdInt, input: In) : Out? {
        return _remoteCallMapMux[remoteCallId]
            ?.remoteCallById(remoteCallId)
            ?.invoke(input)
            ?: run {
                Log.d(tag, "$tag: call: callback $remoteCallId does not exist")
                null
            }
    }

    override fun remoteCall(remoteCallId: RemoteCallIdInt): Out? {
        return _remoteCallMapMux[remoteCallId]
            ?.remoteCallById(remoteCallId)
            ?.invoke(null)
            ?: run {
                Log.d(tag, "$tag: call: callback $remoteCallId does not exist")
                null
            }
    }
}

