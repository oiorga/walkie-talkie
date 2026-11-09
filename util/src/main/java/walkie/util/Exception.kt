package walkie.util

import java.io.PrintWriter
import java.io.StringWriter
import kotlin.reflect.KClass

inline fun <reified T> T.`try`(enclosingClass: KClass<*>? = T::class, tag: String? = null, rethrowException: Boolean = true, crossinline tryCode: () -> Unit): Exception? {
    val inTag = "wtTry/${tag ?: ""}/${randomString(2U)}"
    var exception: Exception? = null

    logd(enclosingClass, inTag, "${getDeclaredSimpleName(enclosingClass)} trying code.")
    try {
        tryCode.invoke()
    } catch (e: Exception) {
        if (rethrowException) {
            logd(enclosingClass, inTag, "${getDeclaredSimpleName(enclosingClass)} throwing exception: \n${exceptionToString(e)}")
            throw (e)
        }
        exception = e
    }
    logd(enclosingClass, inTag, "${getDeclaredSimpleName(enclosingClass)} Done." + (if (null == exception) "" else " Exception: \n${exceptionToString(exception)}"))
    return (exception)
}

fun exceptionToString (e: Exception?): String {
     val ret: String = if (null == e) "null"
     else {
         val sTrace = StringWriter()
         PrintWriter(sTrace).use { e.printStackTrace(it) }
         val stackTrace = sTrace.toString()
         (
          " Exception: ${e.toString()}" +
          "\n Exception Type: ${e::class.qualifiedName}" +
          "\n Message: ${e.message}" +
          "\n Localized Message: ${e.localizedMessage}" +
          "\n Cause: ${e.cause.toString()}" +
          "\n Cause Message: ${e.cause?.message}" +
          "\n Stack Trace: $stackTrace")
     }
    return ret
}