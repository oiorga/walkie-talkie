package walkie.util.generic

import walkie.util.api.DispatchEventIdInt

interface EventDispatcherInt<T> {
    fun subscribeEvent (eventId: DispatchEventIdInt, callBack: suspend (input: T?) -> Unit)
    suspend fun dispatchEvent (eventId: DispatchEventIdInt, input: T? = null)
}

class EventDispatcher<T> (
    private val callBackMap: MutableMap<DispatchEventIdInt, MutableList<suspend (input: T?) -> Unit>> = mutableMapOf()
) : EventDispatcherInt<T> {
    private val tag = "EventDispatcher"

    private val lock = Any()

    override suspend fun dispatchEvent(eventId: DispatchEventIdInt, input: T?) {
        val callBacks = synchronized(lock) {
            callBackMap[eventId]?.toList() ?: emptyList()
        }

        callBacks.forEach { callBack ->
            callBack(input)
        }
    }

    override fun subscribeEvent(eventId: DispatchEventIdInt, callBack: suspend (input : T?) -> Unit) {
        synchronized(lock) {
            callBackMap.getOrPut(eventId) { mutableListOf() }.add(callBack)
        }
    }
}
