package walkie.util.generic

import android.util.Log
import walkie.util.api.RemoteCallIdInt
import walkie.util.api.RemoteCallMuxBaseInt
import walkie.util.api.RemoteCallMuxInt

open class RemoteCallMuxBase<In, Out>() : RemoteCallMuxBaseInt<In, Out> {
    private val _remoteCallMapMux: MutableMap<RemoteCallIdInt, RemoteCallMuxBaseInt<In, Out>> = mutableMapOf()
    private val _remoteCallMap: MutableMap<RemoteCallIdInt, (input : In?) -> Out> = mutableMapOf()
    private val tag = "RemoteCallMux"

    init {
        Log.d (tag, "$tag init")
    }

    override fun remoteCallById(remoteCallId: RemoteCallIdInt): ((In?) -> Out)? {
        return _remoteCallMap[remoteCallId]
    }

    override fun registerRemoteCallTo(remoteCallId: RemoteCallIdInt, callToObj: RemoteCallMuxBaseInt<In, Out>) {
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

class RemoteCallMux(): RemoteCallMuxBase<Any, Any>(), RemoteCallMuxInt

inline fun <In, reified Out>RemoteCallMuxInt.typedCall(
    id: RemoteCallIdInt,
    input: In?
): Out? {
    return (input?.let {
        this.remoteCall(id, it)
    } ?: this.remoteCall(id)) as? Out
}

inline fun <reified Out>RemoteCallMuxInt.typedCall(
    id: RemoteCallIdInt
): Out? {
    return this.remoteCall(id) as? Out
}
