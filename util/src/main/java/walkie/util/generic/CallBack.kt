package walkie.util.generic

import walkie.glue_inc.CallBackIdInt
import android.util.Log

interface CallBackInt<T, K> {
    fun registerCallBack (callBackId: CallBackIdInt, callBack: suspend (input: T?) -> K)
    suspend fun callBack (callBackId: CallBackIdInt, input: T? = null)
}

class CallBack<T, K> (
    private val callBackMap: MutableMap<CallBackIdInt, MutableList<suspend (input: T?) -> K>> = mutableMapOf()
) : CallBackInt<T, K> {
    private val tag = "CallBack"

    init {
        Log.d (tag, "$tag init")
    }

    override suspend fun callBack (callBackId: CallBackIdInt, input: T?) {
        if (null != callBackMap[callBackId]) {
            callBackMap[callBackId]?.forEach { callBack ->
                callBack.invoke(input)
            }
        } else {
            Log.d (tag, "$tag: call: walkie.glue_inc.CallBackId ${callBackId.toString()} does not exist")
        }
    }

    override fun registerCallBack (callBackId: CallBackIdInt, callBack: suspend (input : T?) -> K) {
        if (null == callBackMap[callBackId])
            callBackMap[callBackId] = mutableListOf()
        callBackMap[callBackId]?.add(callBack)
    }
}
