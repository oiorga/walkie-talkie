package walkie.util.generic

import android.util.Log
import walkie.util.api.RemoteCallIdInt
import walkie.util.api.RemoteCallMuxBaseInt
import walkie.util.api.RemoteCallMuxInt
import walkie.util.logd

open class RemoteCallMuxBase<In, Out>() : RemoteCallMuxBaseInt<In, Out> {
    private var _callMap: MutableMap<RemoteCallIdInt, (input : In?) -> Out> = mutableMapOf()
    override val callMap: Map<RemoteCallIdInt, (input : In?) -> Out>
        get() = _callMap
    private val tag = "RemoteCallMux"
    private val lock = Any()

    init {
        Log.d (tag, "$tag init")
    }

    override fun remoteCallAdd(callImpl: RemoteCallMuxBaseInt<In, Out>) {
        callMapImport(callImpl.callMap)
    }

    override fun remoteCallJoin(callImpl: RemoteCallMuxBaseInt<In, Out>) {
        callImpl.callMapImport(callMap)
    }

    override fun registerRemoteCall(remoteCallId: RemoteCallIdInt, callBack:  (input: In?) -> Out) =
        synchronized(lock) {
            if (null != _callMap[remoteCallId])
                Log.d(tag, "$tag: register: callback ${remoteCallId.toString()} already exists")
            _callMap[remoteCallId] = callBack
        }

    override fun remoteCall(remoteCallId: RemoteCallIdInt, input: In) : Out? = synchronized(lock) {
        _callMap[remoteCallId] }?.invoke(input) ?: run {
        Log.d(tag, "$tag: call: callback $remoteCallId does not exist")
        null
    }

    override fun remoteCall(remoteCallId: RemoteCallIdInt): Out? =
        synchronized(lock) { _callMap[remoteCallId] }
            ?.invoke(null)
            ?: run {
                Log.d(tag, "$tag: call: callback $remoteCallId does not exist")
                null
            }

    override fun callMapImport(callMap: Map<RemoteCallIdInt, (input : In?) -> Out>) =
        synchronized(lock) {
            val mutableCallMap =
                callMap as? MutableMap<RemoteCallIdInt, (input: In?) -> Out> ?: run {
                    logd(tag, "callMapImport requires callMap to be backed by a MutableMap")
                    error("callMapImport requires callMap to be backed by a MutableMap")
                }
            mutableCallMap.putAll(_callMap)
            _callMap = mutableCallMap
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
