package walkie.comm.ip

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import walkie.comm.ip.WTIPComm.Companion.TAGKClass
import walkie.util.generic.ChannelMux
import walkie.util.generic.ChannelMuxInt
import walkie.glue.wtcomm.CommPacket
import walkie.glue.wtsystem.NodeIdInt
import walkie.glue_inc.CallBackId
import walkie.glue_inc.ChannelId
import walkie.glue_inc.ChannelIdInt
import walkie.glue_inc.ChannelMessageType
import walkie.util.TCPClient
import walkie.util.TCPServer
import walkie.util.exceptionToString
import walkie.util.generic.BlockingQueue
import walkie.util.generic.CallBack
import walkie.util.generic.CallBackInt
import walkie.util.getInetAddressByName
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString
import walkie.util.`try`
import java.net.InetAddress
import java.net.Socket
import java.util.Scanner
import kotlin.random.Random

/* Ugly. To revisit. To prevent coroutines to restart at onCreate on screen rotation */
class WTIPCommStatic private constructor() {
    companion object {
        val INSTANCE: WTIPCommStatic by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { WTIPCommStatic() }
    }
    var wifiServerS: Boolean = false
    var wifiClientS: Boolean = false
}

class WTIPComm (
    private val node: NodeIdInt,
    private val _channelMux: ChannelMuxInt<Any, ChannelMessageType> = ChannelMux<Any, ChannelMessageType>(),
    private val _callBackList: CallBackInt<Any, Any> = CallBack()
    /* private val _remoteCallMux: WTRemoteCallMuxInt<Any, Any> = WTRemoteCallMux<Any, Any>() */
) :
    /* WTRemoteCallMuxInt<Any, Any> by _remoteCallMux, */
    ChannelMuxInt<Any, ChannelMessageType> by _channelMux,
    CallBackInt<Any, Any> by _callBackList
{
    val ipOutQueue = BlockingQueue<Triple<InetAddress, Int, ByteArray>>("ipOutQueue", 100)
    val ipInQueue = BlockingQueue<String>("ipInQueue", 100)

    var wtServer: TCPServer? = null
    var localServerIpAddress: InetAddress? = null
    private var serverPort: Int? = null

    companion object {
        const val TAG = "WTIPComm"
        const val SERVERPORT: Int = 9900
        val TAGKClass = WTIPComm::class
    }

    init {
        logging(true)
        logd("init")
    }

    suspend fun stop() {
        val tag = "stop/${randomString(2u)}"
        logd(tag, "Stopping wtServer")
        wtServer?.stop()
        localServerIpAddress = null
        /* serverPort = null */
        wtServer = null
        ipOutQueue.qClear()
        ipInQueue.qClear()
    }

    fun serverPort(port: Int? = null): Int? {
        if (null !=port) {
            serverPort = port
        }
        return serverPort
    }

    suspend fun sendIpCommPacket(destIp: String?, destPort: String, type: WTIPCommPacketType, payload: String) {
        val tag = "sendIpCommPacket/${randomString(2u)}"
        val logF = (type == WTIPCommPacketType.ControlMesh)

        logd(tag, "$destIp -> $payload", logF)
        if (null != destIp) {
            val ipAddress = (getInetAddressByName(if (destIp[0] == '/') destIp.removeRange(0..0) else destIp))
            val port = destPort.toInt()

            logd(tag, "$destIp/$ipAddress -> $payload", logF)
            if (null != ipAddress) {
                logd(tag, "$destIp/$ipAddress -> $payload", logF)
                sendByteArray(ipAddress, port, WTIPPacketComm(type, payload).toByteArray())
            }
        }
    }

    suspend fun sendIpCommPacket(destIp: String?, destPort: String, ipCommPacket: WTIPPacketComm) {
        val tag = "sendIpCommPacket/${randomString(2u)}"

        logd(tag, "$destIp -> $ipCommPacket")
        if (null != destIp) {
            val ipAddress = (getInetAddressByName(if (destIp[0] == '/') destIp.removeRange(0..0) else destIp))
            val port = destPort.toInt()

            if (null != ipAddress)
                sendByteArray(ipAddress, port, ipCommPacket.toByteArray())
        }
    }

    override suspend fun channelOnReceive(
        channelId: ChannelIdInt,
        inputType: ChannelMessageType?,
        input: Any?
        ) {
        val tag = "channelOnReceive/${randomString(2u)}"

        logd(tag, "channelId: $channelId inputType: $inputType input: $input")

        when (channelId) {
            ChannelId.RCToIpComm -> {
                when (inputType) {
                    /*
                    WTChannelMessageType.RCStop -> {
                        logd(tag, "Stopping IPComm")
                        /* stop() */
                    }
                    */
                    ChannelMessageType.RCLocalIp -> {
                        localServerIpAddress = if (null != input) input as InetAddress else null
                        logd(
                            tag,
                            "\nchannelId: $channelId " +
                                    "\ninputType: $inputType " +
                                    "\ninput: $input " +
                                    "\nlocalServerIpAddress: $localServerIpAddress"
                        )
                        if (null == localServerIpAddress) {
                            logd(tag,
                                "localServerIpAddress became null. Closing server.")
                            ipOutQueue.qClear()
                            ipInQueue.qClear()
                            wtServer?.close()
                        }
                    }
                    else -> {
                        throw (NotImplementedError(
                            "Not Implemented: " +
                                    "\nchannelId: $channelId " +
                                    "\ninputType: $inputType " +
                                    "\ninput: $input "
                        ))
                    }
                }
            }
            else -> {
                throw (NotImplementedError("$tag: channelId: $channelId: inputType: $inputType Not Implemented "))
            }
        }
    }
}

suspend fun WTIPComm.wifiServerCallback(client: Socket, input: Scanner) {
    val tag = "wifiServerCallback/${randomString(2u)}"

    logd(TAGKClass, tag, "From ${client.inetAddress.hostAddress?.toString()}")

    var jSon = ""
    while (input.hasNextLine()) {
        val nextLine = input.nextLine()
        /* val str = client.inetAddress.hostAddress?.toString() + " " + nextLine.toString() */
        delay(1L)
        jSon += nextLine.toString()
    }
    ipInQueue.enqueue(jSon)
}

fun WTIPComm.wifiServerProcessInput(jSon: String) {
    val tag = "wifiServerProcessInput/${randomString(2u)}"
    var decodedJSon: WTIPPacketComm? = null

    logd(TAGKClass, tag,"Received(0): $jSon ")

    var exc  =  `try`(TAGKClass, tag, throwExc = false) { decodedJSon = Json.decodeFromString<WTIPPacketComm>(jSon) }
    if (null != exc) {
        logd (tag, "Exception while decoding input jSon")
        throw (exc)
        return
    }

    logd(TAGKClass,tag,"Received(1): ${decodedJSon!!.ipCommType} -> ${decodedJSon!!.toJson()} ")

    when (decodedJSon!!.ipCommType) {
        WTIPCommPacketType.Data -> {
            var commPacket: CommPacket? = null
            logd(TAGKClass, tag,"Decoding input Json CommPacket")
            exc = `try`(TAGKClass, tag, throwExc = false) { commPacket = Json.decodeFromString<CommPacket>(decodedJSon!!.jsonString) }
            if (null != exc) {
                logd (tag, "Exception while decoding encoded input jSon")
                /* Hiding a bug/crash */
                /* throw (exc) */
                return
            }
            channelSend(ChannelId.RCToComm, ChannelMessageType.RCWifiMessage, commPacket)
        }
        WTIPCommPacketType.ControlMesh -> {
            channelSend(ChannelId.RCToPRMComm, ChannelMessageType.RCControlMesh, decodedJSon!!.jsonString)
        }
        else -> {
            logd(TAGKClass,
                tag,
                "Received(2): Unknown JSon type: ${decodedJSon!!.ipCommType}")
        }
    }
}

suspend fun WTIPComm.wifiServer(context: CoroutineDispatcher = Dispatchers.IO,
                                localIp: InetAddress? = null,
                                delay: Long = 1000L) {
    val tag = "wifiServer/${randomString(2u)}"
    val s = WTIPCommStatic.INSTANCE
    var serverIp: InetAddress? = null
    val ipZero = getInetAddressByName("0.0.0.0")

    if (s.wifiServerS) return
    s.wifiServerS = true

    serverPort(WTIPComm.SERVERPORT + Random.nextInt(99))
    callBack(CallBackId.CBServerPort, serverPort())

    CoroutineScope(context).launch {
        var count: Long = 0
        while (true) {
            count++
            logd(TAGKClass,
                tag,
                "ipInMessageQueue: mainLoop $count")
            val packet = ipInQueue.dequeue()
            if (packet != null) {
                wifiServerProcessInput(packet)
            }
        }
    }

    logd(TAGKClass,
        tag,
        "Main Loop Enter: localIp: $localIp"
    )

    while (true) {
        var count: Long = 0
        /* if (null == serverIp) serverIp = localIp ?: this.localServerIpAddress */
        /* serverIp = (serverIp ?: (localIp ?: this.localServerIpAddress)) */
        serverIp = ((this.localServerIpAddress) ?: localIp)
        logd(TAGKClass,
            tag,
            "$count: mainLoop serverIp(zero): $serverIp / ${this.localServerIpAddress}"). also { count++ }

        if (null == serverIp) {
            wtServer = TCPServer(
                ipAddress = ipZero,
                port = serverPort(),
                soTimeout = 1000) { _, _ ->
                logd(TAGKClass, tag, "$count: mainLoop serverIp(null): $serverIp / ${this.localServerIpAddress}"). also { count++ }
                val cont = (null == this.localServerIpAddress)
                if (cont) delay(1010)
                return@TCPServer(cont)
            }
            logd(TAGKClass, tag, "$count: mainLoop serverIp(null Out): $serverIp / ${this.localServerIpAddress}"). also { count++ }
            wtServer?.wait()
            wtServer?.close()
            wtServer = null
        } else {
            wtServer = TCPServer(
                ipAddress= serverIp,
                port = serverPort(),
                soTimeout = 0
            ) { client, input ->
                logd(TAGKClass, tag, "$count: mainLoop serverIp(normal): $serverIp / ${this.localServerIpAddress}"). also { count++ }
                wifiServerCallback(client, input as Scanner)
                count++
                return@TCPServer(null != this.localServerIpAddress)
            }
            wtServer?.wait()
            wtServer?.close()
            wtServer = null
        }
    }
}

suspend fun WTIPComm.sendByteArray(ipAddress: InetAddress, port: Int, byteArray: ByteArray) {
    val tag = "sendByteArray/${randomString(2U)}"
    logd(tag, "ipAddress: $ipAddress port: $port")
    ipOutQueue.enqueue(Triple(ipAddress, port, byteArray))
}

suspend fun WTIPComm.sendJsonString(ipAddress: InetAddress, port: Int, jsonString: String) {
    sendByteArray(ipAddress, port, jsonString.toByteArray())
}

suspend fun WTIPComm.wifiClient(
    context: CoroutineDispatcher = Dispatchers.IO,
    inOutQueue: BlockingQueue<Triple<InetAddress, Int, ByteArray>>? = null
    ) {
    val tag = "wifiClient/${randomString(2u)}"
    val s = WTIPCommStatic.INSTANCE

    if (s.wifiClientS) return
    s.wifiClientS = true

    val ioQueue = inOutQueue ?: ipOutQueue

    var count = 0
    while (true) {
        try {
            /***************************/
            withContext(context) {
                logd(TAGKClass,
                    tag,
                    "$count: main loop ipOutQueue before deQueue. Size: ${ioQueue.size}")
                val triple = ioQueue.dequeue()
                val ipAddress = triple?.first
                val serverPort = triple?.second
                val byteArray = triple?.third
                var logF: Boolean = true

                //wtTry(TAGKClass, tag) { logF = ((byteArray?.decodeToString()?.let { Json.decodeFromString<WTIPPacketComm>(it) })?.ipCommType == WTIPCommPacketType.ControlMesh) }

                logd(TAGKClass,
                    tag,
                    "$count: main loop ipOutQueue after deQueue. Size: ${ioQueue.size}")

                if (null != ipAddress &&
                    null != serverPort &&
                    null != byteArray) {
                    logd(TAGKClass,
                        tag,
                        "$count: Creating Client Socket to: $ipAddress:$serverPort ${byteArray.decodeToString()}")
                    val client = TCPClient(ipAddress, serverPort)
                    if (client.init(timeOut = 10000)) {
                        logd(TAGKClass,
                            tag,
                            "$count: Sending Remote to: $ipAddress:$serverPort ${byteArray.decodeToString()}")
                        client.send(byteArray, timeOut = 10000)
                        client.close()
                        logd(TAGKClass,
                            tag,
                            "$count: DONE Sending Remote to: $ipAddress:$serverPort. TimedOut: ${client.timedOut()}")
                    } else {
                        logd(TAGKClass,
                            tag,
                            "$count: Creating Client Socket Failed to: $ipAddress:$serverPort ${byteArray.decodeToString()}")
                        delay(1000L)
                    }
                    count++
                }
            }
            /***************************/
        } catch (e: Exception) {
            val triple = ioQueue.dequeue()
            val ipAddress = triple?.first
            val serverPort = triple?.second
            val byteArray = triple?.third

            logd(TAGKClass, tag,
                "$count: Main loop: Got Exception when sending to: " +
                        "$ipAddress:$serverPort ${byteArray?.decodeToString()}" +
                        "\n" + exceptionToString(e)
            )
            throw (e)
        }
    }
}

fun WTIPComm.wtIPCommMain(scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {

    val wifiServer: Job = scope.launch {
        /* wifiServer(context = Dispatchers.IO, localIp = getInetAddressByName("0.0.0.0")) */
        wifiServer(context = Dispatchers.IO)
    }

    val wifiClient: Job = scope.launch {
        wifiClient(context = Dispatchers.IO)
    }
}

internal fun CommPacket.toIpComm() : WTIPPacketComm {
    return WTIPPacketComm(WTIPCommPacketType.Data, this.toJson())
}
