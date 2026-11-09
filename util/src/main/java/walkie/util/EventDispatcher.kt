package walkie.util

import walkie.util.api.DispatchEventId
import walkie.util.api.EventDispatcherApi

class EventDispatcher<T> (
    private val callBackMap: MutableMap<DispatchEventId, MutableList<suspend (input: T?) -> Unit>> = mutableMapOf()
) : EventDispatcherApi<T> {
    private val tag = "EventDispatcher"

    private val lock = Any()

    override suspend fun <EventId: DispatchEventId>dispatchEvent(eventId: EventId, input: T?) {
        val callBacks = synchronized(lock) {
            callBackMap[eventId]?.toList() ?: emptyList()
        }

        callBacks.forEach { callBack ->
            callBack(input)
        }
    }

    override fun <EventId: DispatchEventId>subscribeEvent(eventId: EventId, callBack: suspend (input : T?) -> Unit) {
        synchronized(lock) {
            callBackMap.getOrPut(eventId) { mutableListOf() }.add(callBack)
        }
    }
}
