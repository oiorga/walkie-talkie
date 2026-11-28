package walkie.util

import walkie.util.api.LoggingApi
import kotlin.jvm.java
import kotlin.reflect.KClass

/**
*
*/

object Logging: LoggingApi {
    const val TAG = "Logging"

    fun enable (enclosingClass: KClass<*>,
                enable: Boolean = true,
                extraCall: ((String, String) -> Unit)? = null)
            = LoggingApi.State.enable(enclosingClass, enable, extraCall)
}

@PublishedApi
internal inline fun <reified T: LoggingApi> T.getDeclaredSimpleNameLog(
    enclosingClass: KClass<*>? = null
): String = LoggingApi.getResolvedClassLogName<T>(enclosingClass)

inline fun <reified T: LoggingApi> T.logging(enable: Boolean = true,
                                 noinline extraCall: ((String, String) -> Unit)? = null) {
    val classTag = getDeclaredSimpleNameLog()
    if (Logging.iLog()) LoggingApi.d(Logging.TAG, "$classTag calling logging $enable")
    Logging.registerLog(classTag, enable, extraCall)
}

inline fun <reified T: LoggingApi> T.logd(enclosingClass: KClass<*>? = null, message: String? = null, logF: Boolean = true) {
    if (logF && Logging.isEnabled()) {
        if (Logging.iLog()) {
            val iTag = "${Logging.TAG}/logd1"
            LoggingApi.d(iTag , "logd calling log: enclosingClass: $enclosingClass")
        }
        log(enclosingClass) { classTag -> logdEmit(classTag, null, message) }
    }
}

inline fun <reified T: LoggingApi> T.logd(enclosingClass: KClass<*>? = null, tag: String, message: String? = null, logF: Boolean = true) {
    if (logF && Logging.isEnabled()) {
        if (Logging.iLog()) {
            val iTag = "${Logging.TAG}/logd2"
            LoggingApi.d(iTag, "logd calling log: enclosingClass: $enclosingClass tag: $tag")
        }
        log(enclosingClass) { classTag -> logdEmit(classTag, tag, message) }
    }
}

@PublishedApi
internal inline fun <reified T: LoggingApi> T.log(
    enclosingClass: KClass<*>? = null,
    logger: (String) -> Unit
) {
    val classTag = this.getDeclaredSimpleNameLog(enclosingClass)
    if (!Logging.isEnabled(classTag)) return
    if (Logging.iLog()) {
        val iTag = "${Logging.TAG}/log1"
        LoggingApi.d(iTag,
            "log calling logger: " +
                    "\nenclosingClass: $enclosingClass " +
                    "\nclassTag: [$classTag] - [${T::class.java.simpleName}/${T::class.simpleName}]")
    }
    logger(classTag)
}

inline fun <reified T: LoggingApi> T.logd(tag: String, message: String? = null, logF: Boolean = true) {
    if (logF && Logging.isEnabled()) {
        if (Logging.iLog()) {
            val iTag = "${Logging.TAG}/log3"
            LoggingApi.d(iTag, "logd calling log: tag: $tag")
        }
        log(null) { classTag -> logdEmit(classTag, tag, message) }
    }
}

inline fun <reified T: LoggingApi> T.logdAppend(tag: String, message: String = "", logF: Boolean = true) {
    if (logF && Logging.isEnabled()) {
        if (Logging.iLog()) {
            val iTag = "${Logging.TAG}/logAppend3"
            LoggingApi.d(iTag, "logdAppend calling log: tag: $tag")
        }
        log(null) { classTag -> logdAppendEmit(classTag, tag, message) }
    }
}

/*
class Logging() {
    @Volatile
    private var globalEnable: Boolean = false
    private val enableMap = ConcurrentHashMap<String, Boolean>()
    private val callCountMap = ConcurrentHashMap<String, AtomicLong>()
    private val extraCallMap = ConcurrentHashMap<String, (String, String) -> Unit>()
    private val tagToTextMap = ConcurrentHashMap<String, StringBuffer>()

    companion object {
        val ONE: Logging by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Logging() }
        const val TAG = "Logging"
        private const val ILOG = false // internal log
        private const val MAX_BUFFER_CHARS = 64 * 1024

        private fun getClassSimpleNameLog(enclosingClass: KClass<*>): String {
            val ret: String = enclosingClass.simpleName?.takeIf { it.isNotBlank() }
                ?: enclosingClass.java.name.takeIf { it.isNotBlank() }
                ?: run {
                    val msg = "getClassSimpleNameLog: Unable to resolve a non-blank class simple name"
                    Log.d(Logging.TAG, msg)
                    throw IllegalArgumentException(msg)
                }
            if (ILOG) Log.d(TAG, "getClassSimpleName: enclosingClass: $enclosingClass simpleName: $ret")
            return ret
        }

        fun enable (enclosingClass: KClass<*>,
                    enable: Boolean = true,
                    extraCall: ((String, String) -> Unit)? = null) {
            val simpleName = getClassSimpleNameLog(enclosingClass)
            if (ILOG) Log.d(TAG, "$enclosingClass/$simpleName Calling logging $enable")
            ONE.registerLog(
                simpleName,
                enable,
                extraCall
            )
        }
    }

    fun iLog(): Boolean {
        return ILOG
    }

    fun registerLog(
        classTag: String,
        enable: Boolean = false,
        extraCall: ((String, String) -> Unit)? = null
    ) {
        val previous = enableMap.put(classTag, enable)
        if (previous != enable) {
            callCountMap.computeIfAbsent(classTag) { AtomicLong() }.set(0L)
        }
        if (extraCall != null) extraCallMap[classTag] = extraCall else extraCallMap.remove(classTag)
    }

    fun extraCall(tag: String):((String, String) -> Unit)? {
        return extraCallMap[tag]
    }

    fun prefix (classTag: String, tag: String? = null): String {
        val count = callCount(classTag)
        return (if (tag == null) "$count/$classTag" else "$count/$classTag/$tag")
    }

    private fun callCount(classTag: String) : Long {
        return callCountMap.computeIfAbsent(classTag) { AtomicLong() }.incrementAndGet()
    }

    fun isEnabled(): Boolean {
        return globalEnable
    }

    fun isEnabled(classTag: String): Boolean {
        return (globalEnable && enableMap[classTag] == true)
    }

    fun setGlobal(enable: Boolean) {
        globalEnable = enable
    }

    fun staticPrefix(classTag: String, tag: String? = null): String {
        return (if (tag == null) classTag else "$classTag/$tag")
    }

    fun appendLog(classTag: String, tag: String? = null, text: String) {
        val prefix = staticPrefix(classTag, tag)
        val buffer = tagToTextMap.computeIfAbsent(prefix) { StringBuffer() }
        synchronized(buffer) {
            buffer.append(text)
            val overflow = buffer.length - MAX_BUFFER_CHARS
            if (overflow > 0) buffer.delete(0, overflow)
        }
    }

    fun consumeLog(classTag: String, tag: String? = null): String {
        val prefix: String = staticPrefix(classTag, tag)
        return tagToTextMap.remove(prefix)?.toString() ?: ""
    }
}
*/
