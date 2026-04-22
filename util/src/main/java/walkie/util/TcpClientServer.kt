package walkie.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.net.BindException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.Scanner

class TCPServer (
    private val ipAddress: InetAddress? = null,
    private var port: Int? = null,
    private val soTimeout: Int = 0,
    private val context: CoroutineDispatcher = Dispatchers.IO,
    private val callBackLoop: (suspend (client: Socket, input : Any) -> Boolean)? = null
) {
    companion object {
        const val TAG = "TCPServer"
        val TAGKClass = TCPServer::class
    }

    private lateinit var socketAddress: SocketAddress
    private lateinit var server: ServerSocket
    private val sem: Semaphore = Semaphore(1, 1)
    private lateinit var serverJob: Job

    private fun isClosed(): Boolean {
        return server.isClosed
    }

    init {
        val tag = "$TAG[init/${randomString(2u)}]"

        logging(true)

        logd(
            TAGKClass,
            tag,
            "init")
        init()
    }

    val serverPort = {
        server.localPort
    }

    fun restart() {
        sem.release()
    }

    fun stop() {
        val tag = "stop/${randomString(2u)}"
        logd(tag, "Stop Server")
        server.close()
        //serverJob.cancel()
    }

    fun close() {
        val tag = "close/${randomString(2u)}"
        logd(tag, "Close Server")
        //if (!isClosed) server.close()
        server.close()
    }

    suspend fun wait() {
        val tag = "wait/${randomString(2u)}"

        logd(
            TAGKClass,
            tag,
            "wait")
        sem.acquire()
    }

    private fun init() {
        val tag = "init/${randomString(2u)}"

        serverJob = CoroutineScope(context).launch {
            try {
                socketAddress = InetSocketAddress(ipAddress!!, port!!)
                server = ServerSocket()
                server.soTimeout = soTimeout
                logd(
                    TAGKClass,
                    tag,
                    "Binding socket: isClosed: ${isClosed()} to $ipAddress")
                server.bind(socketAddress)
            } catch (e: BindException) {
                logd(
                    TAGKClass,
                    tag,
                    "BindException creating server socket: isClosed: ${isClosed()}" +
                            "\n${exceptionToString(e)}"
                )

                /*
                 * Group may get reset and the IP address become NULL.
                 * Wait for th message to come up from WifiDirect, as the
                 * Android IP stack may react faster and give here an exception
                 * Don't throw nothing.
                 */
                delay(1000L)
                sem.release()
                /* throw (e) */
            } catch (e: SocketException) {
                logd(
                    TAGKClass,
                    tag,
                    "SocketException creating server socket: isClosed: ${isClosed()}" +
                            "\n${exceptionToString(e)}"
                )
                delay(1000L)
                sem.release()
                /* throw (e) */
            } catch (e: Exception) {
                logd(
                    TAGKClass,
                    tag,
                    "Exception creating server socket: isClosed: ${isClosed()}" +
                            "\n${exceptionToString(e)}"
                )
                delay(1000L)
                sem.release()
                throw (e)
            }
            while (true) {
                try {
                    while (!isClosed()) {
                        logd(
                            TAGKClass,
                            tag,
                            "Server Main Loop($ipAddress): Waiting for input")
                        val client = server.accept()
                        logd(
                            TAGKClass,
                            tag,
                            "Server Main Loop($ipAddress): GOT input")
                        val scanner = Scanner(client.inputStream)
                        if (null == callBackLoop) {
                            delay(1000L)
                            logd(tag, "callBackLoop is null")
                            throw(NotImplementedError("$tag callBackLoop is null"))
                        }
                        if (!callBackLoop.invoke(client, scanner)) break
                    }
                } catch (e: SocketException) {
                    logd(
                        TAGKClass,
                        tag,
                        "Server Main Loop:\n SocketException" +
                                "\n${exceptionToString(e)}")
                    close()
                    delay(1000L)
                    sem.release()
                    break
                    /* throw(e) */
                }  catch (e: SocketTimeoutException) {
                    logd(
                        TAGKClass,
                        tag,
                        "Server Main Loop:\n SocketTimeoutException" +
                                "\n${exceptionToString(e)}")
                    close()
                    delay(1000L)
                    sem.release()
                    break
                    /* throw(e) */
                } catch (e: Exception) {
                    logd(
                        TAGKClass,
                        tag,
                        "Server Main Loop:\n Exception" +
                                "\n${exceptionToString(e)}")
                    close()
                    delay(1000L)
                    sem.release()
                    throw(e)
                }
            }
        }
    }
}

class TCPClient (
    private val serverIpAddress: InetAddress,
    private val serverPort: Int,
    private val context: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        const val TAG = "TCPClient"
        val TAGKClass = TCPClient::class
    }

    private var client: Socket? = null
    private var _init: Boolean = false
    private lateinit var clientJob: Job
    private var timedOut = false

    fun timedOut(): Boolean {
        return timedOut
    }

    data class Builder(
        private var serverIpAddress: InetAddress,
        private var serverPort: Int = 9999,
        private var context: CoroutineDispatcher = Dispatchers.IO
    ) {
        fun serverIpAddress(serverIpAddress: InetAddress) = apply {
            this.serverIpAddress = serverIpAddress
        }
        fun serverPort(serverPort: Int = 9999) = apply {
            this.serverPort = serverPort
        }
        fun context(context: CoroutineDispatcher = Dispatchers.IO) = apply {
            this.context = context
        }
        suspend fun build() {
            TCPClient(
                serverIpAddress = this.serverIpAddress,
                serverPort = this.serverPort,
                context = this.context
            ).init()
        }
    }

    val init
        get() = _init

    fun stop() {
        clientJob.cancel()
    }

    init {
        logging(true)
    }

    suspend fun init(timeOut: Int = 0): Boolean {
        val tag = "init/${randomString(2u)}"
        val timeOutS = Semaphore(2, 2)
        val bSem = Semaphore(1)
        var timedOut: Boolean = false
        var tInit = false

        logd(TAGKClass, tag, "Entry: init: $_init timeOut: $timeOut")

        if (!_init) {
            clientJob = CoroutineScope(context).launch {
                try {
                    logd(TAGKClass, tag, "Creating Socket: $_init")
                    client = Socket(serverIpAddress, serverPort)
                    tInit = true
                    logd(TAGKClass, tag, "Creating Socket DONE: $_init")
                } catch (e: ConnectException) {
                    logd(
                        TAGKClass,
                        tag,
                        "Client ConnectException while creating client socket: " +
                                "\n${exceptionToString(e)}"
                    )
                    delay(10L)
                    /* throw(e) */
                } catch (e: NoRouteToHostException) {
                    logd(
                        TAGKClass,
                        tag,
                        "Client NoRouteToHostException while creating client socket: " +
                                "\n${exceptionToString(e)}"
                    )
                    delay(10L)
                    /* throw(e) */
                } catch (e: Exception) {
                    logd(
                        TAGKClass,
                        tag,
                        "Client Exception while creating client socket: " +
                                "\n${exceptionToString(e)}"
                    )
                    delay(10L)
                    throw (e)
                }
                bSem.acquire()
                if (!tInit) client = null
                if (tInit) {
                    if (timedOut) {
                        client?.close()
                        client = null
                        tInit = false
                    }
                }
                _init = tInit
                if (timeOut > 0) timeOutS.release()
                bSem.release()
            }
            val timeOutJob = if (0 == timeOut) null else CoroutineScope(context).launch {
                logd(TAGKClass, tag, "(0)Client timeOutJob entry")
                delay(timeOut.toLong())
                logd(TAGKClass, tag, "(1)Client timeOutJob")
                bSem.acquire()
                if (!tInit) {
                    timedOut = true
                    logd(TAGKClass, tag, "(2)Client TIMEOUT")
                }
                if (timeOut > 0) timeOutS.release()
                bSem.release()
                logd(TAGKClass, tag, "(3)Client timeOutJob")
            }
            logd(TAGKClass, tag, "Waiting creating socket job to finish(0): $tInit $_init $timedOut")
            if (timeOut > 0) timeOutS.acquire()
            logd(TAGKClass, tag, "Waiting creating socket job to finish(1): $tInit $_init $timedOut")
            bSem.acquire()
            if (timedOut) {
                _init = false
                withContext(context) {
                    client?.close()
                }
                client = null
            } else {
                timeOutJob?.cancel()
            }

            this.timedOut = timedOut
            bSem.release()
            logd(TAGKClass, tag, "Creating socket job DONE: $tInit $_init $timedOut")
        }
        logd(TAGKClass, tag, "Exit: init: $_init ${client != null}")
        return _init
    }

    suspend fun send(toSend: ByteArray, timeOut: Int = 0, autoClose: Boolean = true, waitFinish: Boolean = true) {
        val tag = "send/${randomString(2u)}"
        val timeOutS = Semaphore(2, 2)
        val bSem = Semaphore(1)
        var timedOut: Boolean = false
        var tSend = false
        var eSend = false

        logd(TAGKClass, tag, "(-2)Client Send Entry: init: $_init timeOut: $timeOut")

        if (!_init) {
            init(timeOut)
        }

        logd(TAGKClass, tag, "(-1)Client Send Entry: init: $_init")
        if (_init && (null != client)) {
            clientJob = CoroutineScope(context).launch {
                try {
                    withContext(context) {
                        client?.outputStream?.write(toSend)
                        client?.outputStream?.flush()
                        tSend = true
                    }
                } catch (e: SocketException) {
                    eSend = true
                    logd(
                        TAGKClass,
                        tag,
                        "Client SocketException hile sending: $toSend" +
                                "\n${exceptionToString(e)}"
                    )
                    delay(10L)
                    /* Testing */
                    /* throw(e) */
                } catch (e: Exception) {
                    eSend = true
                    logd(
                        TAGKClass,
                        tag,
                        "Client Exception while sending: $toSend" +
                                "\n${exceptionToString(e)}"
                    )
                    delay(10L)
                    throw(e)
                }
                bSem.acquire()
                if (tSend && (autoClose || (timeOut > 0))) {
                    logd(TAGKClass, tag, "(0)Client after send: withTimeout: ${(timeOut > 0)}")
                    if (false == client?.isClosed) {
                        client?.close()
                        client = null
                    }
                    logd(TAGKClass, tag, "(1)Client after send: withTimeout: ${(timeOut > 0)}")
                }
                if (tSend && timedOut) timedOut = false
                if (timeOut > 0) timeOutS.release()
                bSem.release()
            }
            val timeOutJob = if (0 == timeOut) null else CoroutineScope(context).launch {
                logd(TAGKClass, tag, "(0)Client timeOutJob entry")
                delay(timeOut.toLong())
                logd(TAGKClass, tag, "(1)Client timeOutJob TIMEOUT after $timeOut millis")
                bSem.acquire()
                logd(TAGKClass, tag, "(2)Client timeOutJob TIMEOUT after $timeOut millis")
                timedOut = true
                if (false == client?.isClosed) {
                    logd(TAGKClass, tag, "Client timeOutJob TIMEOUT after $timeOut millis")
                    client?.close()
                    clientJob.cancel()
                    logd(TAGKClass, tag, "(3)Client TIMEOUT after $timeOut millis")
                }
                client = null
                _init = false
                logd(TAGKClass, tag, "(4)Client Time out job")
                if (timeOut > 0) timeOutS.release()
                bSem.release()
            }
            logd(TAGKClass, tag, "(5)Client Send Exit")
            if (timeOut > 0) timeOutS.acquire()
            bSem.acquire()
            if (tSend) timeOutJob?.cancel()
            this.timedOut = timedOut
            bSem.release()
            logd(TAGKClass, tag, "(6)Client Send Exit: timeOut: $timedOut")
        }
        logd(TAGKClass, tag, "(7)Client Send Exit: timeOut: $timedOut")
    }

    fun close() {
        if (false == client?.isClosed) {
            client?.outputStream?.flush()
            client?.close()
        }
        _init = false
    }
}