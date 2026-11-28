package walkie.util.api

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass

interface LoggingApi {
    companion object {
        const val TAG = "LoggingApi"
        const val ILOG = false
        const val MAX_BUFFER_CHARS = 64 * 1024

        fun getClassLogName(enclosingClass: KClass<*>): String? {
            return enclosingClass.simpleName?.takeIf { it.isNotBlank() }
                ?: enclosingClass.java.name.takeIf { it.isNotBlank() }
                ?: enclosingClass.qualifiedName?.takeIf { it.isNotBlank() }
        }

        inline fun <reified T> getResolvedClassLogName(enclosingClass: KClass<*>? = null): String {
            val ret: String = enclosingClass?.let {
                getClassLogName(enclosingClass = it)
            } ?: getClassLogName(enclosingClass = T::class) ?: run {
                    val msg = "getDeclaredSimpleName: Unable to resolve a non-blank class simple name"
                    if (ILOG) Log.d(TAG, msg)
                throw IllegalArgumentException(msg)
            }

            if (ILOG) Log.d(TAG, "getClassSimpleName: enclosingClass: $enclosingClass simpleName: $ret")
            return ret
        }

        fun d(tag: String, msg: String) {
            if (!ILOG)
                Log.d(tag, msg)
        }
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
            val ret: String = getClassLogName(enclosingClass = enclosingClass) ?: run {
                val msg = "getDeclaredSimpleName: Unable to resolve a non-blank class simple name"
                Log.d(TAG, msg)
                throw IllegalArgumentException(msg)
            }
            if (ILOG) Log.d(TAG, "getClassSimpleName: enclosingClass: $enclosingClass simpleName: $ret")
            return ret
        }

        fun enable(enclosingClass: KClass<*>,
                   enable: Boolean = true,
                   extraCall: ((String, String) -> Unit)? = null) {
            val simpleName = getClassLogKey(enclosingClass)
            if (ILOG) Log.d(TAG, "$enclosingClass/$simpleName Calling logging $enable extraCall: $extraCall")
            registerLog(simpleName, enable, extraCall)
        }

        fun setGlobal(enable: Boolean) {
            if (ILOG) Log.d(TAG, "setGlobal $enable")
            globalEnable = enable
        }

        fun isEnabled(classTag: String): Boolean =
            globalEnable && enableMap[classTag] == true

        fun registerLog(
            classTag: String,
            enable: Boolean,
            extraCall: ((String, String) -> Unit)?
        ) {
            if (ILOG) Log.d(TAG, "registerLog $classTag $enable")

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

        fun extraCall(tag: String): ((String, String) -> Unit)? {
            extraCallMap[tag]?.let {
                if (ILOG) Log.d(TAG, "Extracting extraCall for execute: ${extraCallMap[tag]} tag: $tag")
                return it
            }
            return null
        }

        fun callCount(classTag: String) : Long {
            if (ILOG) Log.d(TAG, "callCount: classTag: $classTag")
            return callCountMap.computeIfAbsent(classTag) { AtomicLong() }.incrementAndGet()
        }

        fun logAppend(classTag: String, tag: String? = null, text: String) {
            val prefix = staticPrefix(classTag, tag)
            if (ILOG) Log.d(TAG, "appendLog: classTag: $classTag tag: $tag prefix: $prefix")

            synchronized(tagToTextMap) {
                val buffer = tagToTextMap.computeIfAbsent(prefix) { StringBuffer() }
                buffer.append(text)
                val overflow = buffer.length - MAX_BUFFER_CHARS
                if (overflow > 0) buffer.delete(0, overflow)
            }
        }

        fun consumeLog(classTag: String, tag: String? = null): String {
            val prefix: String = staticPrefix(classTag, tag)
            if (ILOG) Log.d(TAG, "consumeLog: classTag: $classTag tag: $tag prefix: $prefix")

            synchronized(tagToTextMap) {
                return tagToTextMap.remove(prefix)?.toString() ?: ""
            }
        }

        fun staticPrefix(classTag: String, tag: String? = null): String {
            return (if (tag == null) classTag else "$classTag/$tag")
        }

        fun prefix(classTag: String, tag: String? = null): String {
            val count = callCount(classTag)
            return (if (tag == null) "$count/$classTag" else "$count/$classTag/$tag")
        }

        fun logdEmit(classTag: String, tag: String?, message: String?) {
            val existing = consumeLog(classTag, tag)
            val prefix = prefix(classTag, tag)
            val text = "[$prefix] " + existing + (message ?: "")
            LoggingApi.d("[$prefix]", text)
            val callbackKey = tag ?: classTag
            extraCall(classTag)?.invoke(callbackKey, text)
        }

        fun logdAppendEmit(classTag: String, tag: String?, message: String) {
            logAppend(classTag, tag, message)
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

    /*
    fun extraCall(tag: String):((String, String) -> Unit)? = State.extraCall(tag)
    private fun callCount(classTag: String) : Long = State.callCount(classTag)
    */

    fun isEnabled(): Boolean = State.globalEnable

    fun isEnabled(classTag: String): Boolean = State.isEnabled(classTag)

    fun setGlobal(enable: Boolean) = State.setGlobal(enable)

    /*
    fun logAppend(classTag: String, tag: String? = null, text: String) = State.logAppend(classTag, tag, text)
    fun consumeLog(classTag: String, tag: String? = null): String  = State.consumeLog(classTag, tag)
    */

    fun logdEmit(classTag: String, tag: String?, message: String?) = State.logdEmit(classTag, tag, message)

    fun logdAppendEmit(classTag: String, tag: String?, message: String) = State.logdAppendEmit(classTag, tag, message)

    fun logD(tag: String, logF: Boolean = true) { }
}
