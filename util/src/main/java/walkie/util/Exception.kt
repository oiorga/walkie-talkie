package walkie.util

import java.io.PrintWriter
import java.io.StringWriter
import kotlin.reflect.KClass

inline fun <reified T> T.`try`(enclosingClass: KClass<*>? = T::class, tag: String? = null, rethrowException: Boolean = true, crossinline tryCode: () -> Unit): Exception? {
    val inTag = "wtTry/${tag ?: ""}/${randomString(2U)}"
    val name = getDeclaredSimpleName(enclosingClass)
    var exception: Exception? = null

    logd(enclosingClass, inTag, "$name trying code.")
    try {
        tryCode()
    } catch (e: Exception) {
        if (rethrowException) {
            logd(enclosingClass, inTag, "$name throwing exception: \n${exceptionToString(e)}")
            throw e
        }
        exception = e
    }
    logd(enclosingClass, inTag, "$name Done." + (exception?.let { " Exception: \n${exceptionToString(it)}" } ?: ""))
    return exception
}

fun exceptionToString(e: Exception?): String {
    if (e == null) return "null"
    return " Exception: $e" +
            "\n Exception Type: ${e::class.qualifiedName ?: e.javaClass.name}" +
            "\n Message: ${e.message}" +
            "\n Localized Message: ${e.localizedMessage}" +
            "\n Cause: ${e.cause}" +
            "\n Cause Message: ${e.cause?.message}" +
            "\n Stack Trace: ${e.stackTraceToString()}"
}
