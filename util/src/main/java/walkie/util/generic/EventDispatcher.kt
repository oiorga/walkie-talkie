package walkie.util.generic

import walkie.util.api.DispatchEventIdInt

interface EventDispatcherInt<T> {
    fun registerToEvent (eventId: DispatchEventIdInt, callBack: suspend (input: T?) -> Unit)
    suspend fun dispatchEvent (eventId: DispatchEventIdInt, input: T? = null)
}

class EventDispatcher<T> (
    private val callBackMap: MutableMap<DispatchEventIdInt, MutableList<suspend (input: T?) -> Unit>> = mutableMapOf()
) : EventDispatcherInt<T> {
    private val tag = "EventDispatcher"

    /*
    init {
        Log.d (tag, "$tag init")
    }
    */

    override suspend fun dispatchEvent(eventId: DispatchEventIdInt, input: T?) {
        callBackMap[eventId]?.forEach { callBack ->
            callBack(input)
        }
    }

    override fun registerToEvent(eventId: DispatchEventIdInt, callBack: suspend (input : T?) -> Unit) {
        callBackMap.getOrPut(eventId) { mutableListOf() }.add(callBack)
    }
}
