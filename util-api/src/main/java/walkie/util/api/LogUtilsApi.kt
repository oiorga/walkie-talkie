package walkie.util.api

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass

interface LoggingApi {
    companion object {
        const val TAG = "Logging"
        const val ILOG = false
        const val MAX_BUFFER_CHARS = 64 * 1024
    }

    object State {
        @Volatile
        var globalEnable: Boolean = false
            private set

        private val enableMap = ConcurrentHashMap<String, Boolean>()
        private val callCountMap = ConcurrentHashMap<String, AtomicLong>()
        private val extraCallMap = ConcurrentHashMap<String, (String, String) -> Unit>()
        private val tagToTextMap = ConcurrentHashMap<String, StringBuffer>()

        private fun getClassLogKey(enclosingClass: KClass<*>): String {
            val ret: String = enclosingClass.qualifiedName?.takeIf { it.isNotBlank() }
                ?: enclosingClass.java.name.takeIf { it.isNotBlank() }
                ?: run {
                    val msg = "getClassSimpleNameLog: Unable to resolve a non-blank class simple name"
                    Log.d(TAG, msg)
                    throw IllegalArgumentException(msg)
                }
            if (ILOG) Log.d(TAG, "getClassSimpleName: enclosingClass: $enclosingClass simpleName: $ret")
            return ret
        }

        fun enable (enclosingClass: KClass<*>,
                    enable: Boolean = true,
                    extraCall: ((String, String) -> Unit)? = null) {
            val simpleName = getClassLogKey(enclosingClass)
            if (ILOG) Log.d(TAG, "$enclosingClass/$simpleName Calling logging $enable")
            registerLog(simpleName, enable, extraCall
            )
        }

        fun setGlobal(enable: Boolean) {
            globalEnable = enable
        }

        fun isEnabled(classTag: String): Boolean =
            globalEnable && enableMap[classTag] == true

        fun registerLog(
            classTag: String,
            enable: Boolean,
            extraCall: ((String, String) -> Unit)?
        ) {
            val previous = enableMap.put(classTag, enable)

            if (previous != enable) {
                callCountMap.computeIfAbsent(classTag) { AtomicLong() }.set(0L)
            }

            if (extraCall != null) {
                extraCallMap[classTag] = extraCall
            } else {
                extraCallMap.remove(classTag)
            }
        }

        fun extraCall(tag: String):((String, String) -> Unit)? {
            return extraCallMap[tag]
        }

        fun callCount(classTag: String) : Long {
            return callCountMap.computeIfAbsent(classTag) { AtomicLong() }.incrementAndGet()
        }

        fun appendLog(classTag: String, tag: String? = null, text: String) {
            val prefix = staticPrefix(classTag, tag)
            synchronized(tagToTextMap) {
                val buffer = tagToTextMap.computeIfAbsent(prefix) { StringBuffer() }
                buffer.append(text)
                val overflow = buffer.length - MAX_BUFFER_CHARS
                if (overflow > 0) buffer.delete(0, overflow)
            }
        }

        fun consumeLog(classTag: String, tag: String? = null): String {
            val prefix: String = staticPrefix(classTag, tag)
            synchronized(tagToTextMap) {
                return tagToTextMap.remove(prefix)?.toString() ?: ""
            }
        }

        fun staticPrefix(classTag: String, tag: String? = null): String {
            return (if (tag == null) classTag else "$classTag/$tag")
        }
    }

    fun iLog(): Boolean {
        return ILOG
    }

    fun registerLog(
        classTag: String,
        enable: Boolean = false,
        extraCall: ((String, String) -> Unit)? = null
    ) = State.registerLog(classTag, enable, extraCall)

    fun extraCall(tag: String):((String, String) -> Unit)? = State.extraCall(tag)

    fun prefix (classTag: String, tag: String? = null): String {
        val count = callCount(classTag)
        return (if (tag == null) "$count/$classTag" else "$count/$classTag/$tag")
    }

    private fun callCount(classTag: String) : Long = State.callCount(classTag)

    fun isEnabled(): Boolean = State.globalEnable

    fun isEnabled(classTag: String): Boolean = State.isEnabled(classTag)

    fun setGlobal(enable: Boolean) = State.setGlobal(enable)

    fun appendLog(classTag: String, tag: String? = null, text: String) = State.appendLog(classTag, tag, text)

    fun consumeLog(classTag: String, tag: String? = null): String  = State.consumeLog(classTag, tag)
}
