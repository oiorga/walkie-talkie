package walkie.util.api

interface DispatchEventId

interface EventDispatcherApi<T> {
    fun <EventId : DispatchEventId> subscribeEvent(
        eventId: EventId,
        callBack: suspend (input: T?) -> Unit
    )

    suspend fun <EventId : DispatchEventId> dispatchEvent(eventId: EventId, input: T? = null)
}
