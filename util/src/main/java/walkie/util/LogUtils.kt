package walkie.util

/**
 * From: https://gist.github.com/paolop
 *       https://gist.github.com/paolop/0bd59e49b33d18d6089fb1bf5488e212
 *
 */
import android.util.Log
import kotlin.reflect.KClass

/** Wrapper over [Log.d] */
inline fun <reified T> T.logd(tag: String, message: String, logF: Boolean = true) =
    if (logF && Logging.ONE.isEnabled()) {
        if (Logging.ONE.iLog()) Log.d(Logging.TAG, "logd calling log: tag: $tag")
        log(null) { classTag ->
            val logging = Logging.ONE
            val prefix = logging.prefix(classTag, tag)
            val nm = "[${prefix}] " + message

            Log.d("[${prefix}]", nm)

            logging.extraCall(tag)?.invoke(tag, nm)
        }
    }
    else { }

/** Wrapper over [Log.d] */
inline fun <reified T> T.logd(message: String, logF: Boolean = true) =
    if (logF && Logging.ONE.isEnabled()) {
        if (Logging.ONE.iLog()) Log.d(Logging.TAG, "logd calling log with defaults")
        log(null) { classTag ->
            val logging = Logging.ONE
            val nm = "[${logging.prefix(classTag)}] " + message

            Log.d("[${logging.prefix(classTag)}]", nm)

            logging.extraCall(classTag)?.invoke(classTag, nm)
        }
    }
    else { }

/** Wrapper over [Log.d] */
inline fun <reified T> T.logd(enclosingClass: KClass<*>? = null, message: String, logF: Boolean = true) =
    if (logF && Logging.ONE.isEnabled()) {
        if (Logging.ONE.iLog()) Log.d(Logging.TAG, "logd calling log: enclosingClass: $enclosingClass")
        log(enclosingClass) { classTag ->
            val logging = Logging.ONE
            val nm = "[${logging.prefix(classTag)}] " + message

            Log.d("[${logging.prefix(classTag)}]", nm)

            logging.extraCall(classTag)?.invoke(classTag, nm)
        }
    }
    else { }

/** Wrapper over [Log.d] */
inline fun <reified T> T.logd(enclosingClass: KClass<*>? = null, tag: String, message: String, logF: Boolean = true) =
    if (logF && Logging.ONE.isEnabled()) {
        if (Logging.ONE.iLog()) Log.d(Logging.TAG, "logd calling log: enclosingClass: $enclosingClass tag: $tag")
        log(enclosingClass) { classTag ->
            val logging = Logging.ONE
            val nm = "[${logging.prefix(classTag, tag)}] " + message

            Log.d("[${logging.prefix(classTag, tag)}]", nm)

            logging.extraCall(tag)?.invoke(tag, nm)
        }
    }
    else { }

inline fun <reified T> T.log(
    enclosingClass: KClass<*>? = null,
    logger: ((String) -> Unit)
) {
    val logging = Logging.ONE
    val classTag = this.getClassSimpleName(enclosingClass)

    if (!logging.isEnabled(classTag)) return

    if (Logging.ONE.iLog()) {
        Log.d(
            Logging.TAG, "log calling logger: " +
                "\nenclosingClass: $enclosingClass " +
                "\nclassTag: [$classTag] - [${(T::class.java.simpleName)}/${(T::class.simpleName)}]")
    }

    logger(classTag)
}

/**
 * Utility that returns the name of the class from within it is invoked.
 * It allows to handle invocations from anonymous classes given that the string returned by `T::class.java.simpleName`
 * in this case is an empty string.
 *
 * From: https://gist.github.com/paolop
 *       https://gist.github.com/paolop/0bd59e49b33d18d6089fb1bf5488e212
 *
 * @throws IllegalArgumentException if `enclosingClass` is `null` and this function is invoked within an anonymous class
 */
inline fun <reified T> T.getClassSimpleName(enclosingClass: KClass<*>? = null): String {
    val ret: String = (enclosingClass?.simpleName) ?: (T::class.java.simpleName)
    if (ret.isBlank()) {
        Log.d(Logging.TAG,"getClassSimpleName: enclosingClass cannot be null when invoked from an anonymous class")
        throw IllegalArgumentException("getClassSimpleName: enclosingClass cannot be null when invoked from an anonymous class")
    }
    if (Logging.ONE.iLog()) Log.d(Logging.TAG, "getClassSimpleName: enclosingClass: $enclosingClass simpleName: $ret")
    return ret
}

inline fun <reified T> T.logging(enable: Boolean = true,
                                         noinline extraCall: ((String, String) -> Unit)? = null) {
    if (Logging.ONE.iLog()) Log.d(Logging.TAG, "${this.getClassSimpleName()} calling logging $enable")
    Logging.ONE.registerLog(
        this.getClassSimpleName(),
        enable,
        extraCall)
}

/*
fun getClassSimpleName(enclosingClass: KClass<*>): String? {
    val ret: String? = (enclosingClass.simpleName)
    Log.d(Logging.TAG, "getClassSimpleName: enclosingClass: $enclosingClass simpleName: $ret")
    return ret
}

fun logging(enclosingClass: KClass<*>,
            enable: Boolean = true,
            extraCall: ((String, String) -> Unit)? = null) {
    val simpleName = getClassSimpleName(enclosingClass)
    Log.d(Logging.TAG, "$enclosingClass/$simpleName Calling logging $enable")
    if (null == simpleName) {
        throw (NotImplementedError("${Logging.TAG} $enclosingClass/null calling logging $enable for NULL"))
    } else {
        Logging.ONE.registerLog(
            simpleName,
            enable,
            extraCall
        )
    }
}
*/

class Logging private constructor () {
    private var globalEnable: Boolean = false
    private val enableMap: MutableMap<String, Boolean> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { mutableMapOf() }
    private val callCountMap: MutableMap<String, Long> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { mutableMapOf() }
    private val extraCallMap: MutableMap<String, ((String, String) -> Unit)?> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { mutableMapOf() }

    companion object {
        val ONE: Logging by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Logging() }
        const val TAG = "Logging"
        private const val ILOG = false

        private fun getClassSimpleName(enclosingClass: KClass<*>): String? {
            val ret: String? = (enclosingClass.simpleName)
            if (ILOG) Log.d(TAG, "getClassSimpleName: enclosingClass: $enclosingClass simpleName: $ret")
            return ret
        }

        fun enable (enclosingClass: KClass<*>,
                    enable: Boolean = true,
                    extraCall: ((String, String) -> Unit)? = null) {
            val simpleName = getClassSimpleName(enclosingClass)
            if (ILOG) Log.d(TAG, "$enclosingClass/$simpleName Calling logging $enable")
            if (null == simpleName) {
                throw (NotImplementedError("$TAG $enclosingClass/null calling logging $enable for NULL"))
            } else {
                ONE.registerLog(
                    simpleName,
                    enable,
                    extraCall
                )
            }
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
        if (enable != enableMap[classTag]) {
            enableMap[classTag] = enable
            callCountMap[classTag] = 0L
            extraCallMap[classTag] = extraCall
        }
    }

    fun extraCall(tag: String):((String, String) -> Unit)? {
        return extraCallMap[tag]
    }

    fun prefix (classTag: String, tag: String? = null): String {
        val count = callCount(classTag)
        if (tag == null) return "$count/$classTag"
        return "$count/$classTag/$tag"
    }

    private fun callCount(classTag: String) : Long {
        val count: Long = callCountMap[classTag] ?: 0
        callCountMap[classTag] = count + 1
        return callCountMap[classTag]!!
    }

    fun isEnabled(): Boolean {
        return globalEnable
    }

    fun isEnabled(classTag: String): Boolean {
        return (globalEnable && true == enableMap[classTag])
    }

    fun setGlobal(enable: Boolean) {
        if (enable != globalEnable) {
            globalEnable = enable
        }
    }
}