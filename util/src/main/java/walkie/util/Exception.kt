package walkie.util

import kotlin.reflect.KClass

inline fun <reified T> T.`try`(enclosingClass: KClass<*>? = null, tag: String? = null, throwExc: Boolean = true, tryCode: () -> Unit): Throwable? {
    val inTag = "wtTry/${tag ?: ""}/${randomString(2U)}"
    var exception: Exception? = null

    logd(enclosingClass, inTag, "${getClassSimpleName(enclosingClass)} trying code.")
    try {
        tryCode.invoke()
    } catch (e: Exception) {
        if (throwExc) {
            logd(enclosingClass, inTag, "${getClassSimpleName(enclosingClass)} throwing exception: \n${exceptionToString(e)}")
            throw (e)
        }
        exception = e
    }
    logd(enclosingClass, inTag, "${getClassSimpleName(enclosingClass)} Done." + (if (null == exception) "" else " Exception: \n${exceptionToString(exception)}"))
    return (exception)
}

fun exceptionToString (e: Exception?): String {
     val ret: String = if (null == e) "null"
     else (" ConnectException: ${e.toString()}" +
           "\n ConnectException Message: ${e.message}" +
           "\n ConnectException Localized Message: ${e.localizedMessage}" +
           "\n ConnectException Cause: ${e.cause}" +
           "\n ConnectException Cause Message: ${e.cause?.message}")
    return ret
}