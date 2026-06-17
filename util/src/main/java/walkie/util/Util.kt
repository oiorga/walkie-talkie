package walkie.util

import android.os.AsyncTask
import android.os.Build
import android.os.SystemClock.elapsedRealtime
import android.os.SystemClock.uptimeMillis
import android.util.Log
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.conn.util.InetAddressUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
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
inline fun <reified T> T.getDeclaredSimpleName(enclosingClass: KClass<*>? = null): String {
    val declaredSimpleName: String = (enclosingClass?.simpleName) ?: T::class.java.enclosingClass?.simpleName ?: (T::class.java.simpleName)
    if (declaredSimpleName.isBlank()) {
        throw IllegalArgumentException("getClassSimpleName: enclosingClass cannot be null when invoked from an anonymous class")
    }
    return declaredSimpleName
}

fun Any.getEnclosingClassSimpleName(): String =
    this::class.java.enclosingClass?.simpleName ?: "null"

fun Any.getRuntimeSimpleName(): String =
    this::class.simpleName ?: this::class.java.simpleName ?: "null"

fun Any.getRuntimeQualifiedName(): String =
    this::class.qualifiedName ?: this::class.java.name ?: "null"

fun getInterfaceIpAddress(interfaceName: String, ipV4: Boolean = true): InetAddress? {
    var ipAddress: InetAddress? = null
    val interfaces: List<NetworkInterface> =
        Collections.list(NetworkInterface.getNetworkInterfaces())
    for (interFace in interfaces) {
        if (interFace.displayName == interfaceName) {
            val addresses: List<InetAddress> = Collections.list(interFace.inetAddresses)
            for (address in addresses) {
                if (!address.isLoopbackAddress) {
                    val sAddress = address.hostAddress?.uppercase(Locale.getDefault())
                    val isIPv4: Boolean = InetAddressUtils.isIPv4Address(sAddress)
                    if (ipV4 && isIPv4) {
                         ipAddress = address
                    }
                }
            }
        }
    }
    return ipAddress
}

fun getIPAddressesString(useIPv4: Boolean = true): String {
    val tab = "\t\t\t\t"
    var sAddrs = ""
    try {
        val interfaces: List<NetworkInterface> =
            Collections.list(NetworkInterface.getNetworkInterfaces())
        for (intf in interfaces) {
            val addrs: List<InetAddress> = Collections.list(intf.inetAddresses)
            for (addr in addrs) {
                if (!addr.isLoopbackAddress) {
                    val sAddr = addr.hostAddress?.uppercase(Locale.getDefault())
                    val isIPv4: Boolean = InetAddressUtils.isIPv4Address(sAddr)
                    if (useIPv4) {
                        //if (isIPv4) return sAddr
                        if (isIPv4) sAddrs += "\n$tab$intf $sAddr"
                    } else {
                        if (!isIPv4) {
                            val delim = sAddr?.indexOf('%') // drop ip6 port suffix
                            if (sAddr != null) {
                                if (delim != null) {
                                    //return if (delim < 0) sAddr else sAddr.substring(0, delim)
                                    sAddrs += if (delim < 0) sAddr else sAddr.substring(0, delim)
                                }
                            }
                        }
                    }
                }
            }
        }
    } catch (_: java.lang.Exception) {
        // for now eat exceptions
    }

    return sAddrs
}

fun inetToIpString(inetIp: String): String {
    return (if (inetIp[0] == '/') inetIp.removeRange(0..0) else inetIp)
}

fun getInetAddressByName(name: String?): InetAddress? {
    if (null == name) return null
    val task: AsyncTask<String?, Void?, InetAddress?> =
        object : AsyncTask<String?, Void?, InetAddress?>( ) {
            @Deprecated("Deprecated in Java")
            override fun doInBackground(vararg params: String?): InetAddress? {
                return try {
                    InetAddress.getByName(params[0])
                } catch (e: UnknownHostException) {
                    null
                }
            }
        }
    return try {
        task.execute(inetToIpString(name)).get()
    } catch (e: InterruptedException) {
        null
    } catch (e: ExecutionException) {
        null
    }
}

fun noop() {
    Thread.sleep(0)
}

/**
 * delayExec
 * Exec delayed block
 *
 */
fun delayExec(millis: Long = 0L, scope: CoroutineScope? = null, block: () -> Unit) = runBlocking {
    val localS: CoroutineScope = scope ?: MainScope()

    localS.launch {
        delay (millis)
        block()
    }
}

fun whileBackGround (scope: CoroutineScope = MainScope(), millis: Long = 1000L, addRandom: Int = 0, block: () -> Unit) = runBlocking {
    val job = scope.launch {
        while (true) {
            block()
            delay(millis + addRandom * Random.nextLong(millis))
        }
    }
}

fun randomString(length: UByte = 8u): String {
    /* val alphabet: String = "0123456789abcdefghijklmnopqrxyzuvABCDEFGHIJKLMNOPQRXYZUV" */
    /* val alphabet: String = "0a1B2cD34e5F6g7H89ibdfhjklmnopqrxyzuvACEGIJKLMNOPQRXYZUV" */
    val alphabet: String = "0a1B2cD34e5F6g7H89iAbCdEfGhIjJkKlLmMnNoOpPqQrRxXyYzZuUvV"
    var str = ""
    val randomBase: Long = uptimeMillis()
    var rand: Long = Random.nextLong(randomBase)

    for (i in 0 ..<length.toInt()) {
        rand = (Random.nextLong(rand + randomBase) % alphabet.length)
        str += alphabet[rand.toInt()]
    }

    return str
}

fun timeNow(divider: Long = 4000000L): Long {
    return ((if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Instant.now().toEpochMilli()
    } else {
        Date().time
    }) % divider)
}

fun upTimeNow(divider: Long = 4000000L): Long {
    return (elapsedRealtime() % divider)
}

fun stringSum(string: String): Long {
    var sum = 0L
    string.forEach { it ->
        sum += it.code.toLong()
    }
    return sum
}


fun appLabelToFile(file: String, name: String) {
    val buffer =
        "<resources>" +
                "\t\t\t\t<string name=\"app_label\">$name</string>" +
                "</resources>"
    val fd = try {
        File(file)
    } catch (e: Exception) {
        throw (e)
    }
    try {
        fd.createNewFile()
        fd.printWriter().use { out ->
            out.println(buffer)
        }
    } catch (e: Exception) {
        throw (e)
    }
}

fun appendTextToFile(file: String, string: String? = null, nl: Boolean = true) {
    val fd = try {
        File(file)
    } catch (e: Exception) {
        throw (e)
    }
    try {
        if (!fd.exists() || (null == string)) {
            fd.delete()
            fd.createNewFile()
            fd.appendText("<resources> </resources>")
        } else {
            fd.appendText(
                (if (nl) "\n" else "") +
                        "<!-- " +
                        string +
                        " -->"
            )
        }
    } catch (e: Exception) {
        throw (e)
    }
}

fun textSum(str: String): Int {
    var sum = 0
    str.forEach { c ->
        sum += c.code
    }
    return sum
}