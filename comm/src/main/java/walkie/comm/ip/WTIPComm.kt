package walkie.comm.ip

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import walkie.comm.ip.WTIPComm.Companion.TAGKClass
import walkie.talkie.api.wtModule.ModuleOpImpl
import walkie.talkie.api.wtModule.ModuleOpInt
import walkie.util.generic.MessageBus
import walkie.talkie.api.wtcomm.CommPacket
import walkie.talkie.api.wtsystem.NodeIdInt
import walkie.talkie.api.wtModule.MessageBusId
import walkie.talkie.api.wtModule.PipeMessageType
import walkie.talkie.api.wtModule.WTModOpArg
import walkie.util.TCPClient
import walkie.util.TCPServer
import walkie.util.api.DispatchEventId
import walkie.util.api.MessageBusIdInt
import walkie.util.api.BusMessageInt
import walkie.util.api.MessageBusInt
import walkie.util.exceptionToString
import walkie.util.generic.BlockingQueue
import walkie.util.generic.EventDispatcher
import walkie.util.generic.EventDispatcherInt
import walkie.util.generic.BusMessage
import walkie.util.getInetAddressByName
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString
import walkie.util.`try`
import java.net.InetAddress
import java.net.Socket
import java.util.Scanner
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

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
    val scope: CoroutineScope,
    private val _pipeMux: MessageBusInt<PipeMessageType, Any> = MessageBus<PipeMessageType, Any>(),
    private val _callBackList: EventDispatcherInt<Any> = EventDispatcher(),
    private val _moduleOp: ModuleOpInt = ModuleOpImpl(_pipeMux)
) :
    MessageBusInt<PipeMessageType, Any> by _pipeMux,
    ModuleOpInt by _moduleOp,
    EventDispatcherInt<Any> by _callBackList
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

    val tag = TAG

    init {
        logging(true)
        logd(tag, "init")

        /*
        pipeSubscribe(PipeId.ToIpComm, scope, true,::onPipeMessage)
        */

        subscribe(
            to = WTModOpArg.To.IpComm,
            onEventInfo = WTModOpArg.OnEventInfo(::onBusMessage, scope)
        )
    }

    suspend fun stop() {
        val tag = "stop/${randomString(2u)}"
        logd(tag, "Stopping wtServer")
        wtServer?.stop()
        localServerIpAddress = null
        /* serverPort = null */
        wtServer = null
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

    override suspend fun onBusMessage(
        busId: MessageBusIdInt,
        msg: BusMessageInt<PipeMessageType, Any>
        ) {
        val tag = "channelOnReceive/${randomString(2u)}"
        val type = msg.type
        val data = msg.data

        logd(tag, "channelId: $busId inputType: $type input: $data")

        when (busId) {
            MessageBusId.ToIpComm -> {
                when (type) {
                    /*
                    WTChannelMessageType.RCStop -> {
                        logd(tag, "Stopping IPComm")
                        /* stop() */
                    }
                    */
                    PipeMessageType.LocalIp -> {
                        localServerIpAddress = if (null != data) data as InetAddress else null
                        logd(
                            tag,
                            "\nchannelId: $busId " +
                                    "\ninputType: $type " +
                                    "\ninput: $data " +
                                    "\nlocalServerIpAddress: $localServerIpAddress"
                        )
                        if (null == localServerIpAddress) {
                            logd(tag,
                                "localServerIpAddress became null. Closing server.")
                            wtServer?.close()
                        }
                    }
                    else -> {
                        throw (NotImplementedError(
                            "Not Implemented: " +
                                    "\nchannelId: $busId " +
                                    "\ninputType: $type " +
                                    "\ninput: $data "
                        ))
                    }
                }
            }
            else -> {
                throw (NotImplementedError("$tag: channelId: $busId: inputType: $type Not Implemented "))
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

    when (decodedJSon.ipCommType) {
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

            send(
                to = WTModOpArg.To.Comm,
                msg = WTModOpArg.Msg(
                    BusMessage(
                        PipeMessageType.WifiMessage,
                        commPacket
                    )
                )
            )
        }
        WTIPCommPacketType.ControlMesh -> {
            send(
                to = WTModOpArg.To.PRMComm,
                msg = WTModOpArg.Msg(
                    BusMessage(
                        PipeMessageType.ControlMesh,
                        decodedJSon.jsonString
                    )
                )
            )
        }
        else -> {
            logd(TAGKClass,
                tag,
                "Received(2): Unknown JSon type: ${decodedJSon.ipCommType}")
        }
    }
}

suspend fun WTIPComm.wifiServer(localIp: InetAddress? = null,
                                delay: Long = 1000L) = coroutineScope {
    val tag = "wifiServer/${randomString(2u)}"
    val s = WTIPCommStatic.INSTANCE
    var serverIp: InetAddress? = null
    val ipZero = getInetAddressByName("0.0.0.0")

    if (s.wifiServerS) return@coroutineScope
    s.wifiServerS = true

    serverPort(WTIPComm.SERVERPORT + Random.nextInt(99))
    dispatchEvent(DispatchEventId.CBServerPort, serverPort())

    scope.launch(Dispatchers.Default) {
        var count: Long = 0
        while (isActive) {
            count++
            logd(
                TAGKClass,
                tag,
                "ipInMessageQueue: mainLoop $count"
            )
            val packet = ipInQueue.dequeue()
            wifiServerProcessInput(packet)
        }
    }

    logd(
        TAGKClass,
        tag,
        "Main Loop Enter: localIp: $localIp"
    )

    while (isActive) {
        var count: Long = 0
        /* if (null == serverIp) serverIp = localIp ?: this.localServerIpAddress */
        /* serverIp = (serverIp ?: (localIp ?: this.localServerIpAddress)) */
        serverIp = ((this@wifiServer.localServerIpAddress) ?: localIp)
        logd(
            TAGKClass,
            tag,
            "$count: mainLoop serverIp(zero): $serverIp / ${this@wifiServer.localServerIpAddress}"
        ).also { count++ }

        if (null == serverIp) {
            wtServer = TCPServer(
                ipAddress = ipZero,
                port = serverPort(),
                soTimeout = 1000,
                this
            ) { _, _ ->
                logd(
                    TAGKClass,
                    tag,
                    "$count: mainLoop serverIp(null): $serverIp / ${this@wifiServer.localServerIpAddress}"
                ).also { count++ }
                val cont = (null == this@wifiServer.localServerIpAddress)
                if (cont) delay(1010.milliseconds)
                return@TCPServer (cont)
            }
            logd(
                TAGKClass,
                tag,
                "$count: mainLoop serverIp(null Out): $serverIp / ${this@wifiServer.localServerIpAddress}"
            ).also { count++ }
            wtServer?.wait()
            wtServer?.close()
            wtServer = null
        } else {
            wtServer = TCPServer(
                ipAddress = serverIp,
                port = serverPort(),
                soTimeout = 0,
                this
            ) { client, input ->
                logd(
                    TAGKClass,
                    tag,
                    "$count: mainLoop serverIp(normal): $serverIp / ${this@wifiServer.localServerIpAddress}"
                ).also { count++ }
                wifiServerCallback(client, input)
                count++
                return@TCPServer (null != this@wifiServer.localServerIpAddress)
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
    inOutQueue: BlockingQueue<Triple<InetAddress, Int, ByteArray>>? = null
    ) = coroutineScope {
     val tag = "wifiClient/${randomString(2u)}"
    val s = WTIPCommStatic.INSTANCE

    if (s.wifiClientS) return@coroutineScope
    s.wifiClientS = true

    val ioQueue = inOutQueue ?: ipOutQueue

    var count = 0
    while (isActive) {
        try {
            /***************************/
            logd(
                TAGKClass,
                tag,
                "$count: main loop ipOutQueue before deQueue"
            )
            val triple = ioQueue.dequeue()
            val ipAddress = triple.first
            val serverPort = triple.second
            val byteArray = triple.third
            val logF: Boolean = true

            //wtTry(TAGKClass, tag) { logF = ((byteArray?.decodeToString()?.let { Json.decodeFromString<WTIPPacketComm>(it) })?.ipCommType == WTIPCommPacketType.ControlMesh) }

            logd(
                TAGKClass,
                tag,
                "$count: main loop ipOutQueue after deQueue"
            )

            if (null != ipAddress &&
                null != serverPort &&
                null != byteArray
            ) {
                logd(
                    TAGKClass,
                    tag,
                    "$count: Creating Client Socket to: $ipAddress:$serverPort ${byteArray.decodeToString()}"
                )
                val client = TCPClient(ipAddress, serverPort, this)
                if (client.init(timeOut = 10000)) {
                    logd(
                        TAGKClass,
                        tag,
                        "$count: Sending Remote to: $ipAddress:$serverPort ${byteArray.decodeToString()}"
                    )
                    client.send(byteArray, timeOut = 10000)
                    client.close()
                    logd(
                        TAGKClass,
                        tag,
                        "$count: DONE Sending Remote to: $ipAddress:$serverPort. TimedOut: ${client.timedOut}"
                    )
                } else {
                    logd(
                        TAGKClass,
                        tag,
                        "$count: Creating Client Socket Failed to: $ipAddress:$serverPort ${byteArray.decodeToString()}"
                    )
                    delay(1000L)
                }
                count++
            }
            /***************************/
        } catch (e: Exception) {
            val triple = ioQueue.dequeue()
            val ipAddress = triple.first
            val serverPort = triple.second
            val byteArray = triple.third

            logd(TAGKClass, tag,
                "$count: Main loop: Got Exception when sending to: " +
                        "$ipAddress:$serverPort ${byteArray?.decodeToString()}" +
                        "\n" + exceptionToString(e)
            )
            throw (e)
        }
    }
}

fun WTIPComm.wtIPCommMain(scope: CoroutineScope): Job {
    return scope.launch(Dispatchers.IO) {
        launch { wifiServer() }
        launch { wifiClient() }
    }
}

internal fun CommPacket.toIpComm() : WTIPPacketComm {
    return WTIPPacketComm(WTIPCommPacketType.Data, this.toJson())
}
