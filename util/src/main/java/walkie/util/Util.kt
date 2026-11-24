package walkie.util

import android.os.AsyncTask
import android.os.Build
import android.os.SystemClock.elapsedRealtime
import android.os.SystemClock.uptimeMillis
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.conn.util.InetAddressUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.UnknownHostException
import java.time.Instant
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutionException
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.milliseconds

inline fun <reified T> T.getDeclaredSimpleName(enclosingClass: KClass<*>? = null): String {
    return enclosingClass?.simpleName?.takeIf { it.isNotBlank() }
        ?: T::class.java.enclosingClass?.simpleName?.takeIf { it.isNotBlank() }
        ?: T::class.java.simpleName.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException(
            "getDeclaredSimpleName: Unable to resolve a non-blank class simple name"
        )
}

@PublishedApi
internal inline fun <reified T> T.resolveKClass(runtimeType: Boolean = false): KClass<*> =
    if (runtimeType) this?.let { it::class } ?: T::class else T::class

inline fun <reified T> T.simpleNameOrJvm(runtimeType: Boolean = false): String {
    val kClass = resolveKClass(runtimeType)
    return kClass.simpleName?.takeIf { it.isNotBlank() } ?: kClass.java.simpleName
}

inline fun <reified T> T.qualifiedNameOrJvm(runtimeType: Boolean = false): String {
    val kClass = resolveKClass(runtimeType)
    return kClass.qualifiedName?.takeIf { it.isNotBlank() } ?: kClass.java.name
}

inline fun <reified T> T.jvmEnclosingClassSimpleName(runtimeType: Boolean = false): String? {
    val kClass = resolveKClass(runtimeType)
    return kClass.java.enclosingClass?.simpleName?.takeIf { it.isNotBlank() }
}

fun getInterfaceIpv4Address(interfaceName: String):  Inet4Address? {
    val networkInterface = NetworkInterface.getNetworkInterfaces()
        ?.asSequence()
        ?.firstOrNull { it.name == interfaceName }
        ?: return null

    return networkInterface.inetAddresses
        .asSequence()
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress }
}

fun getIPAddressesString(useIPv4: Boolean = true): String {
    val tab = "\t\t\t\t"

    return try {
        NetworkInterface.getNetworkInterfaces()
            ?.asSequence()
            ?.flatMap { intf ->
                intf.inetAddresses.asSequence()
                    .filter { !it.isLoopbackAddress }
                    .filter {
                        if (useIPv4) it is Inet4Address
                        else it is Inet6Address
                    }
                    .map { addr ->
                        val address = addr.hostAddress
                            ?.substringBefore('%')
                            ?.uppercase(Locale.ROOT)

                        address?.let { "\n$tab${intf} $it" }
                    }
            }
            ?.filterNotNull()
            ?.joinToString("")
            ?: ""
    } catch (_: Exception) {
        ""
    }
}

fun inetToIpString(inetIp: String): String = inetIp.removePrefix("/")

suspend fun getInetAddressByName(name: String?): InetAddress? =
    withContext(Dispatchers.IO) {
        name?.let {
            try {
                InetAddress.getByName(inetToIpString(it))
            } catch (_: UnknownHostException) {
                null
            }
        }
    }

/**
 * delayExec
 * Exec delayed block
 *
 */
fun delayExec(
    millis: Long = 0L,
    scope: CoroutineScope = MainScope(),
    block: () -> Unit
): Job = scope.launch {
    delay(millis.milliseconds)
    block()
}

fun runBackground(
    scope: CoroutineScope,
    millis: Long = 1000L,
    addRandom: Int = 0,
    block: suspend () -> Unit
): Job = scope.launch {
    while (isActive) {
        block()

        val randomDelay = if (addRandom > 0 && millis > 0)
            addRandom * Random.nextLong(millis)
        else 0L

        delay((millis + randomDelay).milliseconds)
    }
}

fun randomString(length: UByte = 8u): String {
    val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    return buildString(length.toInt()) {
        repeat(length.toInt()) {
            append(alphabet[Random.nextInt(alphabet.length)])
        }
    }
}

fun timeNow(divider: Long = 4_000_000L): Long {
    require(divider > 0)
    return Instant.now().toEpochMilli() % divider
}

fun upTimeNow(divider: Long = 4_000_000L): Long {
    require(divider > 0)
    return elapsedRealtime() % divider
}

fun stringSum(string: String): Long = string.sumOf { it.code.toLong() }