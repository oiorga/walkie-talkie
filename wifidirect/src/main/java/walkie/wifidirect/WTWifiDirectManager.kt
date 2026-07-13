package walkie.wifidirect

import android.annotation.SuppressLint
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pConfig.GROUP_OWNER_INTENT_MIN
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION
import android.os.Parcelable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import walkie.talkie.api.wtModule.ModuleOpImpl
import walkie.talkie.api.wtModule.ModuleOpInt
import walkie.talkie.api.wtdebug.wtError
import walkie.talkie.api.wtsystem.NodeIdInt
import walkie.talkie.api.wtModule.PipeId
import walkie.talkie.api.wtModule.PipeMessageType
import walkie.talkie.api.wtModule.WTModOpArg
import walkie.util.api.PipeIdInt
import walkie.util.api.PipeMessageInt
import walkie.util.api.PipeMuxInt
import walkie.util.api.RemoteCallId
import walkie.util.api.RemoteCallMuxInt
import walkie.util.generic.PipeMux
import walkie.util.generic.GenericList
import walkie.util.generic.Mailbox
import walkie.util.generic.MailboxData
import walkie.util.generic.PipeMessage
import walkie.util.generic.RemoteCallMux
import walkie.util.generic.typedCall
import walkie.util.logd
import walkie.util.logdAppend
import walkie.util.logging
import walkie.util.randomString
import walkie.wifidirect.WTWifiDB.Companion.WT_SERVICE_ID
import walkie.wifidirect.WTWifiDB.Companion.WT_SERVICE_LOCAL_SERVER_PORT
import walkie.wifidirect.WTWifiDB.Companion.WT_SERVICE_RND
import walkie.wifidirect.WTWifiDB.Companion.WT_SERVICE_UNIQUE
import walkie.wifidirect.WTWifiDB.Companion.WT_SERVICE_WALKIETALKIE
import walkie.wifidirect.WTWifiDirectResult.Error.App.ChannelNotInitialized.dataOrNull
import java.net.InetAddress
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
class WTWifiDirectManager(
    val manager: WifiP2pManager,
    val node: NodeIdInt,
    val scope: CoroutineScope,
    private val _pipeMux: PipeMuxInt<PipeMessageType, Any> = PipeMux(),
    private val _remoteCallMux: RemoteCallMuxInt = RemoteCallMux(),
    private val _moduleOp: ModuleOpInt = ModuleOpImpl()
) :
    ModuleOpInt by _moduleOp,
    PipeMuxInt<PipeMessageType, Any> by _pipeMux,
    RemoteCallMuxInt by _remoteCallMux {

    companion object {
        const val TAG = "WTWiFiDirectManager"
        val TAGKClass = WTWifiDirectManager::class
    }

    /*
    * Transition to self-contained Wi-Fi direct manager
    */
    private var wtWifiDirect: WTWifiDirect? = null
    private val wtWifiDirectEnv = object : WTWifiDirectEnv {
        override fun checkWifiPermissions(): Boolean {
            return this@WTWifiDirectManager.checkWifiPermissions()
        }
    }

    fun attachChannel(channel: Channel) {
        wtWifiDirect = WTWifiDirect(manager, channel, wtWifiDirectEnv, scope)
    }

    fun checkWifiPermissions(): Boolean {
        return (true == typedCall<Boolean>(RemoteCallId.RCCheckWifiDPermissions))
    }

    fun requestWifiPermissions() {
        remoteCall(RemoteCallId.RCRequestWifiDPermissions)
    }

    val deviceUid: String
        get() = node.uid()

    private var mainLoopJob: Job? = null

    private val mainLoopInbox = Mailbox<WTWifiEvent>(10)

    var wtWifi = WTWifiDB(
        nodeId = node,
        state = WTWifiState.Disabled(wifiPermissions = false)
    )

    val wifiP2pEnable: Boolean
        get() = wtWifi.isWifiEnabled

    val directWTPeers: Map<String, WTWifiDirectPeerInfo>
        get() = wtWifi.directWTPeers

    val directWifiServices: Map<String, WTWifiDirectServiceInfo>
        get() = wtWifi.directServices

    val wtWifiP2pInfo: WifiP2pInfo?
        get() = wtWifi.p2pInfo

    val wtGroupOwner: WTWifiDirectPeerInfo?
        get() = wtWifi.groupOwner

    val thisDevice: WifiP2pDevice?
        get() = wtWifi.thisDevice

    val wtLocalServiceRecord: Map<String, String>?
        get() = wtWifi.localServiceRecord

    val wtCachedServiceRecord: Map<String, String>?
        get() = wtWifi.cachedServiceRecord

    val wtLocalIp: InetAddress?
        get() = wtWifi.localIp

    val wtLocalServerPort: Int?
        get() = wtWifi.localServerPort

    val wtGroupServerPort: Int?
        get() = wtWifi.groupServerPort

    val wtIsGroupOwner: Boolean
        get() = wtWifi.isGroupOwner

    val wtIsGroupFormed: Boolean
        get() = wtWifi.isGroupFormed

    val wtGroupIp: InetAddress?
        get() = wtWifi.groupIpAddress

    val wtGroupOwnerName: String?
        get() = wtWifi.groupOwnerName

    val deviceId: String
        get() = node.id()

    val connectToDevice: WTWifiDirectPeerInfo?
        get() = wtWifi.connectToDevice

    val channelCountdown
        get() = wtWifi.tick

    val connectingAllowed: Boolean
        get() = ((wtWifi.state is WTWifiState.Enabled) && (wtWifi.isWTServicePeerPresent))

    val isConnectedToGroup: Boolean
        get() = wtWifi.isConnected

    fun restartChannel(yes: Boolean? = null): Boolean {
        if (yes == true) {
            wtWifi.restartChannel()
        }
        return wtWifi.restartChannel
    }

    init {
        logging(true)

        /*
        pipeSubscribe(PipeId.ToWifi, scope) { pipeId, msg ->
            onPipeMessage(pipeId, msg)
        }
        */

        subscribe(
            to = WTModOpArg.To.Wifi,
            onEvent = WTModOpArg.OnEvent (::onPipeMessage)
        )
    }

    override suspend fun onPipeMessage(
        pipeId: PipeIdInt,
        msg: PipeMessageInt<PipeMessageType, Any>
    ) {
        val tag = "channelOnReceive/${randomString(2U)}"

        logd(tag, "channelId: $pipeId inputType: ${msg.type}")
        when (pipeId) {
            PipeId.ToWifi -> {
                when (msg.type) {
                    PipeMessageType.WifiBroadcastReceiver -> {
                        if (null != msg.data) {
                            processBcastReceiverMessage(msg.data as Intent)
                        } else {
                            /***************************/
                        }
                    }

                    PipeMessageType.LocalServerPort -> {
                        mainLoopInbox.send(WTWifiEvent.WTWifi.LocalServerPort(msg.data as Int))
                    }

                    else -> {
                        throw (NotImplementedError("$tag: channelOnReceive: channelId: $pipeId: inputType: $msg.type Not Implemented "))
                    }
                }
            }

            else -> {
                throw (NotImplementedError("$tag: channelOnReceive: channelId: $pipeId: inputType: $msg.type Not Implemented "))
            }
        }
    }

    fun resetData() {
        val tag = "resetData/${randomString(2u)}"
        logd(TAGKClass, tag, "Entry")

        send(
            to = WTModOpArg.To.Comm,
            msg = WTModOpArg.Msg(
                PipeMessage(
                    type = PipeMessageType.GroupInfo,
                    data = Triple(null, null, null)
                )
            )
        )

        mainLoopJob?.cancel()
        mainLoopJob = null
        wtWifi = wtWifi.reset()
    }

    suspend fun requestPeersInfo(): GenericList<WifiP2pDevice> {
        val tag = "requestPeersInfo/${randomString(2u)}"

        logd(tag, "Entry")

        val newPeersList = GenericList<WifiP2pDevice>()
        wtWifiDirect?.requestPeersInfo()?.dataOrNull()?.deviceList?.let { newPeersList.addAll(it) }

        return newPeersList
    }

    suspend fun requestDeviceInfo(): WifiP2pDevice? {
        val tag = "requestDeviceInfo/${randomString(2u)}"

        val device = wtWifiDirect?.requestDeviceInfo()?.dataOrNull()

        logd(
            tag, "device:" +
                    "\n\t\t\t\tGO: ${device?.isGroupOwner}" +
                    "\n\t\t\t\tdeviceName: ${device?.deviceName}" +
                    "\n\t\t\t\tdeviceAddress${device?.deviceAddress}"
        )

        return device
    }

    suspend fun requestGroupInfo(): WifiP2pGroup? {
        val tag = "requestGroupInfo/${randomString(2u)}"
        return wtWifiDirect?.requestGroupInfo()?.dataOrNull()
    }

    suspend fun removeGroup() {
        val tag = "removeGroup/${randomString(2u)}"

        val wifiDirect = wtWifiDirect ?: run {
            wtError(tag, "wtWifiDirect is null")
            return
        }

        wifiDirectP2pAction(
            tag,
            "Success removing group",
            "Failed removing group}",
        ) {
            wifiDirect.removeGroup()
        }
    }

    suspend fun connect(
        device: WifiP2pDevice,
        config: WifiP2pConfig
    ): P2pConnection {
        val tag = "connect: ${randomString(2u)}"

        logd(tag, "Connecting to ${device.uniqueWifiId()}")

        val wifiDirect = wtWifiDirect ?: run {
            wtError(tag, "wtWifiDirect is null")
            return P2pConnection.Fail
        }

        return when (wifiDirectP2pAction(
            tag,
            "Success connecting to ${device.uniqueWifiId()}",
            "Failed connecting to ${device.uniqueWifiId()}",
            ignoreP2pError = false
        ) {
            wifiDirect.connect(device, config)
        }) {
            WTWifiDirectResult.Success -> {
                P2pConnection.InProgress
            }

            else -> {
                P2pConnection.Fail
            }
        }
    }

    suspend fun cancelConnect() {
        val tag = "cancelConnect/${randomString(2u)}"

        val wifiDirect = wtWifiDirect ?: run {
            wtError(tag, "wtWifiDirect is null")
            return
        }

        wifiDirectP2pAction(
            tag,
            "Success canceling connect",
            "Failed canceling connect",
        ) {
            wifiDirect.cancelConnect()
        }
    }

    suspend fun connectTo(device: WTWifiDirectPeerInfo): P2pConnection {
        val tag = "connectTo/${randomString(2u)}"

        logd(tag, "connect to ${device.uniqueWifiId} ${device.wtService} ${device.p2pConnection}")

        if (device.name == deviceUid) {
            logd(tag, "Connecting to self: ${device.name}/${device.address} == $deviceUid")
            throw (Exception("$tag Connecting to self: ${device.name}/${device.address} == $deviceUid"))
        }

        when (device.p2pConnection) {
            P2pConnection.NoWTService -> {
                if (device.wtService) {
                    device.p2pConnection = P2pConnection.NotConnected
                }
            }

            P2pConnection.Null -> {
                if (!device.wtService) {
                    device.p2pConnection = P2pConnection.NoWTService
                } else {
                    device.p2pConnection = P2pConnection.NotConnected
                }
            }

            P2pConnection.NotConnected -> {
                device.cip.start()
                val config = WifiP2pConfig().apply {
                    deviceAddress = device.address
                    groupOwnerIntent = GROUP_OWNER_INTENT_MIN
                    wps.setup = WpsInfo.PBC
                }

                device.p2pConnection = connect(device.device, config)
                when (device.p2pConnection) {
                    P2pConnection.InProgress -> {
                        logd(
                            TAGKClass,
                            tag,
                            "Connect to: ${device.name}  ${device.address} GO = ${device.isGroupOwner} Success"
                        )
                    }

                    P2pConnection.Fail -> {
                        logd(
                            TAGKClass,
                            tag,
                            "Connect to: ${device.name} ${device.address} GO = ${device.isGroupOwner} Failed"
                        )
                    }

                    else -> {
                        throw (NotImplementedError("$tag Connecting to {device.name}  ${device.address} GO = ${device.isGroupOwner} Not Expected result"))
                    }
                }
            }

            P2pConnection.Fail -> {
                wtError(
                    tag,
                    "Connect to: ${device.name} ${device.address} GO = ${device.isGroupOwner} is In Fail. Invalid State"
                )
            }

            P2pConnection.InProgress -> {
                logd(
                    TAGKClass,
                    tag,
                    "Connect to: ${device.name} ${device.address} GO = ${device.isGroupOwner} is InProgress connectedToGroup: $isConnectedToGroup",
                )
                if (!isConnectedToGroup) {
                    if (!device.cip.on) {
                        cancelConnect()
                        device.p2pConnection = P2pConnection.Retry
                    } else {
                        device.cip.tick()
                    }
                } else {
                    device.p2pConnection = P2pConnection.Connected
                    device.cip.stop()
                }
            }

            P2pConnection.Connected -> {
                logd(
                    TAGKClass,
                    tag,
                    "Connect to: ${device.name} ${device.address} GO = ${device.isGroupOwner} is Already Connected"
                )

                if (!isConnectedToGroup) {
                    device.p2pConnection = P2pConnection.Retry
                    logd(
                        TAGKClass,
                        tag,
                        "Connect to: ${device.name} ${device.address} GO = ${device.isGroupOwner} is Connected but there's an anomaly?: " +
                                "wtLocalIp: $wtLocalIp wtIsGroupFormed: $wtIsGroupFormed wtGroupIp: $wtGroupIp"
                    )
                }
            }

            else -> {
                logd(
                    TAGKClass,
                    tag,
                    "Connect to: Connection Status ${device.p2pConnection} NOT handled"
                )
                throw (NotImplementedError("Connect to: Connection Status ${device.p2pConnection} NOT handled"))
            }
        }

        logd(
            tag,
            "Exit connect to ${device.uniqueWifiId} ${device.wtService} ${device.p2pConnection}"
        )

        return device.p2pConnection
    }

    suspend fun requestConnectionInfo(): WifiP2pInfo? {
        return wtWifiDirect?.requestConnectionInfo()?.dataOrNull()
    }

    suspend fun mainLoop(cadence: Long) {
        val tag = "mainLoop/${randomString(2u)}"
        var p2pInfo: Triple<InetAddress?, InetAddress?, Int?> =
            Triple(wtGroupIp, wtLocalIp, wtGroupServerPort)
        var rndDelay = Random.nextLong(50)

        while (scope.isActive) {
            checkGroupInfoChange(p2pInfo, Triple(wtGroupIp, wtLocalIp, wtGroupServerPort))
            p2pInfo = Triple(wtGroupIp, wtLocalIp, wtGroupServerPort)

            internalMaintenance()

            rndDelay = max(Random.nextLong(50 + rndDelay), 75)
            val event = when (val v = mainLoopInbox.await((cadence + rndDelay).milliseconds)) {
                is MailboxData.Message -> {
                    v.value
                }

                MailboxData.Timeout -> {
                    WTWifiEvent.WTWifi.Timeout
                }
            }

            processEvent(event)

            if (restartChannel()) {
                logd(tag, "Restart channel")
                break
            }
        }

        stop()
    }

    suspend fun processEvent(event: WTWifiEvent) {
        val tag = "processEvent/${randomString(2u)}"
        var logStr = "\n\tevent: ${event.description}"

        logd(tag, event.description)

        when (event) {
            is WTWifiEvent.WTWifi.LocalServerPort -> {
                wtWifi = wtWifi.transition(event)
            }

            WTWifiEvent.WTWifi.MergePeersServicesInfo -> {
                wtWifi = wtWifi.transition(event)
            }

            WTWifiEvent.WTWifi.Default,
            WTWifiEvent.WTWifi.Timeout -> {
                wtWifi = wtWifi.transition(event)
                processEventTimeout()
            }

            is WTWifiEvent.P2p.WifiDisabled -> {
                wtWifi = wtWifi.transition(event)
                if (!wtWifi.hasWifiPermissions) requestWifiPermissions()
            }

            WTWifiEvent.P2p.WifiEnabled -> {
                logStr += "\n\tP2P state changed to enabled"
                wtWifi = wtWifi.transition(
                    event,
                    thisDevice = requestDeviceInfo()
                )
            }

            WTWifiEvent.P2p.PeersChanged -> {
                logStr += "\n\tP2P peers changed"
                wtWifi = wtWifi.transition(
                    event,
                    p2pPeers = requestPeersInfo()
                )
            }

            WTWifiEvent.P2p.ConnectionChanged -> {
                val p2pInfo = requestConnectionInfo()
                logStr += "\n\tP2P Connection changed: groupFormed: ${p2pInfo?.groupFormed} isGroupOwner: ${p2pInfo?.isGroupOwner}"
                wtWifi = wtWifi.transition(
                    event,
                    p2pInfo = p2pInfo,
                    thisDevice = requestDeviceInfo(),
                    groupInfo = requestGroupInfo()
                )
                if (null == p2pInfo) {
                    updateP2pInfo(null)
                }
            }

            WTWifiEvent.P2p.ThisDeviceChanged -> {
                logd(tag, "P2P this device changed")
                logStr += "\n\tP2P this device changed"
                wtWifi = wtWifi.transition(
                    event,
                    thisDevice = requestDeviceInfo(),
                    groupInfo = requestGroupInfo()
                )
            }

            is WTWifiEvent.P2p.TxtRecordListener -> {
                logStr += "\n\tP2P Process TxtRecordListener info"
                wtWifi = wtWifi.transition(
                    event,
                    directServices = dnsSdTxtRecordListener(
                        event.fullDomain,
                        event.record,
                        event.device
                    )
                )
            }

            is WTWifiEvent.P2p.ServiceResponseListener -> {
                logStr += "\n\tP2P Process ServiceResponseListener info"
                /*
                wtWifi = wtWifi.transition(
                    event,
                    directServices = dnsSdServiceResponseListener(event.instanceName, event.registrationType, event.resourceType))
                */
            }

            is WTWifiEvent.WTWifi.Command.AdvertiseLocalService -> {
                if (event.enable) {
                    addLocalService(removeFirst = true)
                } else {
                    removeLocalService()
                }
            }

            is WTWifiEvent.WTWifi.Command.ServiceDiscovery -> {
                if (event.enable) {
                    addServiceRequest(removeFirst = true)
                    discoverServices()
                }
            }

            is WTWifiEvent.WTWifi.Command.CancelConnect -> {
                if (event.apply) {
                    logStr += "\n\tRequest removing group"
                    cancelConnect()
                }
            }

            else -> {
                logStr += "\n\tUnprocessed WifiD Event: ${event.toString()}"
            }
        }

        wtWifi.onEngineCoolingDown { errInfo ->
            logStr += "\n\tGot P2p Action Error: ${errInfo.description}"
        } ?: run {
            wtWifi.consumeNextEvents().forEach { nEvent ->
                mainLoopInbox.send(nEvent)
            }
        }

        logd(tag, logStr)
    }

    fun internalMaintenance() {
        val tag = "internalMaintenance/${randomString(2u)}"
        logd(
            tag, "\n\tthisDevice: ${thisDevice?.deviceName}" +
                    "\n\tisWifiP2pEnabled: $wifiP2pEnable" +
                    "\n\twtIsGroupFormed: $wtIsGroupFormed" +
                    "\n\tchannelCountdown: $channelCountdown" +
                    "\n\tdeviceName = ${deviceId}/${deviceUid}" +
                    "\n\tlocalIp = ${this.wtLocalIp}"
        )

        send(
            to = WTModOpArg.To.Activity,
            msg = WTModOpArg.Msg(
                PipeMessage(
                    type = PipeMessageType.WifiDebugInfoMessage,
                    data = wtWifiDirectInfo()
                )
            )
        )
    }

    suspend fun processWifiPermissions(): Boolean {
        val currentP2pPermissions = checkWifiPermissions()

        if (currentP2pPermissions != wtWifi.hasWifiPermissions) {
            mainLoopInbox.send(WTWifiEvent.P2p.WifiDisabled(currentP2pPermissions))
        }

        return currentP2pPermissions
    }

    suspend fun processEventTimeout() {
        val tag = "processEventTimeout/${randomString(2u)}"

        logd(tag, "Entry")

        if (!processWifiPermissions()) {
            logd(tag, "Not enough Wifi permissions.")
            return
        }

        wtWifi.onEngineCoolingDown {
            logd(tag, "Wifi Engine cooling down after Busy error.")
            return@onEngineCoolingDown
        }?.let {
            return it
        }

        connectToPeers()
    }

    suspend fun mainLoopInit() {
        val tag = "mainLoopInit/${randomString(2u)}"

        send(
            to = WTModOpArg.To.Activity,
            msg = WTModOpArg.Msg(
                PipeMessage(
                    type = PipeMessageType.WifiDebugInfoMessage,
                    data = wtWifiDirectInfo()
                )
            )
        )

        cancelConnect()
        removeGroup()
        clearAllServices()

        send(
            to = WTModOpArg.To.Comm,
            msg = WTModOpArg.Msg(
                PipeMessage(
                    type = PipeMessageType.GroupInfo,
                    data = Triple(null, null, null)
                )
            )
        )

        initServices()
    }

    fun checkGroupInfoChange(
        oldInfo: Triple<InetAddress?, InetAddress?, Int?>,
        newInfo: Triple<InetAddress?, InetAddress?, Int?>
    ) {
        val tag = "checkGroupInfoChange/${randomString(2u)}"

        val (oldGroupIp, oldLocalIp, oldGroupServerPort) = oldInfo
        val (wtGroupIp, wtLocalIp, wtGroupServerPort) = newInfo

        logd(
            tag, "\ngroupIp: $oldGroupIp -> $wtGroupIp" +
                    "\nlocalIp: $oldLocalIp -> $wtLocalIp" +
                    "\nserverPort: $oldGroupServerPort -> $wtGroupServerPort" +
                    "\nwtGroupOwnerName: $wtGroupOwnerName" +
                    "\nwtIsGroupOwner: $wtIsGroupOwner"
        )

        if (oldInfo == newInfo) {
            return
        } else if (oldInfo != newInfo) {
            var localIpAlreadyChanged = false
            if (null == wtGroupIp) {
                logd(
                    tag,
                    "Group info changed(Null): wtGroupIp: $oldGroupIp -> $wtGroupIp wtLocalIp: $oldLocalIp -> $wtLocalIp"
                )

                send(
                    to = WTModOpArg.To.Comm,
                    msg = WTModOpArg.Msg(
                        PipeMessage(
                            type = PipeMessageType.GroupInfo,
                            data = Triple(null, null, null)
                        )
                    )
                )
            }

            if (!wtIsGroupOwner &&
                null != wtGroupServerPort &&
                null != wtGroupIp &&
                null != wtGroupOwnerName &&
                null != wtLocalIp
            ) {
                logd(
                    tag,
                    "Group info changed(Group): wtGroupIp: $oldGroupIp -> $wtGroupIp wtGroupServerPort: $oldGroupServerPort -> $wtGroupServerPort wtLocalIp: $oldLocalIp -> $wtLocalIp"
                )

                send(
                    to = WTModOpArg.To.Comm,
                    msg = WTModOpArg.Msg(
                        PipeMessage(
                            type = PipeMessageType.GroupInfo,
                            data = Triple(null, null, null)
                        )
                    )
                )

                send(
                    to = WTModOpArg.To.Comm,
                    msg = WTModOpArg.Msg(
                        PipeMessage(
                            type = PipeMessageType.GroupInfo,
                            data = Triple(wtGroupOwnerName, wtGroupIp, wtGroupServerPort)
                        )
                    )
                )

                send(
                    to = WTModOpArg.To.Comm,
                    msg = WTModOpArg.Msg(
                        PipeMessage(
                            type = PipeMessageType.LocalIp,
                            data = wtLocalIp
                        )
                    )
                )

                localIpAlreadyChanged = true
            }

            if (localIpAlreadyChanged) {
                logd(
                    tag,
                    "Local IP info changed(IP): wtLocalIp: $oldLocalIp -> $wtLocalIp. Already Sent Info."
                )
            }

            if (wtLocalIp != oldLocalIp && !localIpAlreadyChanged) {
                logd(tag, "Local IP info changed(IP): wtLocalIp: $oldLocalIp -> $wtLocalIp")

                send(
                    to = WTModOpArg.To.Comm,
                    msg = WTModOpArg.Msg(
                        PipeMessage(
                            type = PipeMessageType.LocalIp,
                            data = wtLocalIp
                        )
                    )
                )
            }
        }
    }

    suspend fun updateP2pInfo(wtWifiP2pInfoN: WifiP2pInfo?) {
        val tag = "updateP2pInfo/${randomString(2u)}"

        logd(tag, "Entry")

        wtWifiP2pInfo?.logD(tag)
        wtWifiP2pInfoN?.logD(tag)

        if (null != wtWifiP2pInfo?.groupOwnerAddress &&
            null == wtWifiP2pInfoN?.groupOwnerAddress
        ) {
            logd(tag, "Lost P2P Connection/Info")
            removeGroup()
            wtWifi = wtWifi.reset()

            send(
                to = WTModOpArg.To.Comm,
                msg = WTModOpArg.Msg(
                    PipeMessage(
                        type = PipeMessageType.GroupInfo,
                        data = Triple(null, null, null)
                    )
                )
            )

            restartChannel(true)
        }

        wtWifiP2pInfo?.logD(tag)
    }

    suspend fun connectToPeers() {
        val tag = "connectToPeers/${randomString(2u)}"

        logdAppend(
            tag,
            "\n\t\twtIsGroupOwner: $wtIsGroupOwner" +
                    "\n\t\twtIsGroupFormed: $wtIsGroupFormed" +
                    "\n\t\tisWifiP2pEnabled: $wifiP2pEnable" +
                    "\n\t\tconnectingAllowed: $connectingAllowed" +
                    "\n\t\tdirectWifiPeers: ${directWTPeers.keys}"
        )

        connectToDevice?.let { device ->
            logdAppend(tag, "\n\t\tConnecting to: ${device.name}")
            if (P2pConnection.Fail == connectTo(device)) {
                /* To revisit this */
                wtWifi.eraseP2pError()
                mainLoopInbox.send(
                    WTWifiEvent.WTWifi.Command.CancelConnect()
                )
            }
        }

        logd(tag)
    }

    suspend fun removeLocalService() {
        val tag = "removeLocalService/${randomString(2u)}"
        val instanceName = WT_SERVICE_WALKIETALKIE// + "." + deviceUid()
        /* val serviceType = "_presence._tcp" */
        val serviceType = WT_SERVICE_WALKIETALKIE

        logd(tag, "Entry: $wtLocalServiceRecord $wtCachedServiceRecord")

        val cachedServiceRecord = wtCachedServiceRecord ?: run { return }

        val wifiDirect = wtWifiDirect ?: run {
            wtError(tag, "wtWifiDirect is null")
            return
        }

        wifiDirectP2pAction(
            tag,
            "Success removing local service",
            "Failed removing local service"
        ) {
            wifiDirect.removeLocalService(
                instanceName,
                serviceType,
                cachedServiceRecord
            )
        }
    }

    suspend fun addLocalService(removeFirst: Boolean = false) {
        val tag = "addLocalService/${randomString(2u)}"
        val instanceName = WT_SERVICE_WALKIETALKIE /* + "." + deviceUid() */
        /* val serviceType = "_presence._tcp" */
        val serviceType = WT_SERVICE_WALKIETALKIE

        logd(tag, "Entry: $wtLocalServiceRecord wtLocalServerPort: $wtLocalServerPort")

        val wifiDirect = wtWifiDirect ?: run {
            wtError(tag, "wtWifiDirect is null")
            return
        }

        if (removeFirst) {
            removeLocalService()
        }

        wifiDirectP2pAction(
            tag,
            "Success adding local service",
            "Failed adding local service",
            ignoreP2pError = false
        ) {
            wifiDirect.addLocalService(
                instanceName,
                serviceType,
                wtLocalServiceRecord!!
            )
        }
    }

    suspend fun removeServiceRequest() {
        val tag = "removeServiceRequest/${randomString(2u)}"
        val instanceName = WT_SERVICE_WALKIETALKIE /* + "." + deviceUid() */
        /* val serviceType = "_presence._tcp" */
        val serviceType = WT_SERVICE_WALKIETALKIE

        val wifiDirect = wtWifiDirect ?: run {
            wtError(tag, "wtWifiDirect is null")
            return
        }

        wifiDirectP2pAction(
            tag,
            "Success removing service request",
            "Failed removing service request"
        ) {
            wifiDirect.removeServiceRequest(
                instanceName,
                serviceType
            )
        }
    }

    suspend fun addServiceRequest(removeFirst: Boolean = false) {
        val tag = "addServiceRequest/${randomString(2u)}"
        val instanceName = WT_SERVICE_WALKIETALKIE /* + "." + deviceUid() */
        /* val serviceType = "_presence._tcp" */
        val serviceType = WT_SERVICE_WALKIETALKIE

        val wifiDirect = wtWifiDirect ?: run {
            wtError(tag, "wtWifiDirect is null")
            return
        }

        if (removeFirst) {
            removeServiceRequest()
        }

        wifiDirectP2pAction(
            tag,
            "Success adding service request",
            "Failed adding service request",
            ignoreP2pError = false
        ) {
            wifiDirect.addServiceRequest(
                instanceName,
                serviceType
            )
        }
    }

    fun dnsSdTxtRecordListener(
        fullDomain: String,
        record: Map<String, String>,
        device: WifiP2pDevice
    ): Map<String, WTWifiDirectServiceInfo> {
        val tag = "dnsSdTxtRecordListener/${randomString(2u)}"

        logd(
            tag,
            "DnsSdTxtRecord available: [$fullDomain] [$record] [${device.deviceName}]"
        )

        val newDirectWifiServices = directWifiServices.toMutableMap()

        val wtService = record[WT_SERVICE_WALKIETALKIE] ?: run { return newDirectWifiServices }
        val id = record[WT_SERVICE_ID] ?: run { return newDirectWifiServices }
        val unique = record[WT_SERVICE_UNIQUE] ?: run { return newDirectWifiServices }
        val rnd = record[WT_SERVICE_RND] ?: run { return newDirectWifiServices }
        val localServerPort =
            record[WT_SERVICE_LOCAL_SERVER_PORT] ?: run { return newDirectWifiServices }

        if (wtService == WT_SERVICE_WALKIETALKIE) {
            if (null == newDirectWifiServices[device.uniqueWifiId()]) {
                newDirectWifiServices[device.uniqueWifiId()] =
                    WTWifiDirectServiceInfo(
                        id = id,
                        unique = unique,
                        rnd = rnd,
                        localServerPort = localServerPort
                    )
            } else {
                newDirectWifiServices[device.uniqueWifiId()]?.id = id
                newDirectWifiServices[device.uniqueWifiId()]?.unique = unique
                newDirectWifiServices[device.uniqueWifiId()]?.rnd = rnd
                newDirectWifiServices[device.uniqueWifiId()]?.localServerPort = localServerPort
            }
            logd(
                tag,
                "directWifiServices[${device.uniqueWifiId()}] = " +
                        "${newDirectWifiServices[device.uniqueWifiId()]?.id} " +
                        "${newDirectWifiServices[device.uniqueWifiId()]?.unique} " +
                        "${newDirectWifiServices[device.uniqueWifiId()]?.rnd} " +
                        "${newDirectWifiServices[device.uniqueWifiId()]?.localServerPort}"
            )
        }

        return newDirectWifiServices
    }

    /*
    fun dnsSdServiceResponseListener(instanceName: String,
                                     registrationType: String,
                                     resourceType: WifiP2pDevice): Map<String, WTWifiDirectServiceInfo> {
        val tag = "dnsSdServiceResponseListener/${randomString(2u)}"
        logd(tag,
            "onBonjourServiceAvailable [$instanceName] [$registrationType] [${resourceType.deviceName}]"
        )


        val newDirectWifiServices = directWifiServices.toMutableMap()

        if (WT_SERVICE_WALKIETALKIE == instanceName.subSequence(WT_SERVICE_WALKIETALKIE.indices)) {
            logd(
                TAGKClass,
                tag,
                "device.deviceName: ${resourceType.deviceName} resourceType.uniqueWifiId: ${resourceType.uniqueWifiId()} ${directWTPeers[resourceType.uniqueWifiId()]?.name}"
            )
            /*
            if (null == newDirectWifiServices[resourceType.uniqueWifiId()]) {
                newDirectWifiServices[resourceType.uniqueWifiId()] = WTWifiDirectServiceInfo()
            }
            */
        }

        return newDirectWifiServices
    }
    */

    fun registerServiceListeners() {
        val tag = "registerServiceListeners/${randomString(2u)}"

        logd(tag, "Entry")

        val txtListener = WifiP2pManager.DnsSdTxtRecordListener { fullDomain, record, device ->
            scope.launch {
                mainLoopInbox.send(WTWifiEvent.P2p.TxtRecordListener(fullDomain, record, device))
            }
        }

        val servListener =
            WifiP2pManager.DnsSdServiceResponseListener { instanceName, registrationType, resourceType ->
                scope.launch {
                    mainLoopInbox.send(
                        WTWifiEvent.P2p.ServiceResponseListener(
                            instanceName,
                            registrationType,
                            resourceType
                        )
                    )
                }
            }

        logd(tag, "Registering listeners...")
        wtWifiDirect?.registerServiceListeners(txtListener, servListener)
    }

    suspend fun initServices() {
        val tag = "initServices/${randomString(2u)}"
        val sem = Semaphore(1, 1)

        logd(tag, "Entry")

        registerServiceListeners()
        clearAllServices()
    }

    @SuppressLint("NewApi")
    suspend fun stopPeersDiscovery() {
        val tag = "stopPeerDiscovery/${randomString(2u)}"

        val wifiDirect = wtWifiDirect ?: run {
            wtError(tag, "wtWifiDirect is null")
            return
        }

        wifiDirectP2pAction(
            tag,
            "Success stopping peers discovery",
            "Failed stopping peers discovery"
        ) {
            wifiDirect.stopPeersDiscovery()
        }
    }

    suspend fun discoverServices() {
        val tag = "discoverServices/${randomString(2u)}"

        val wifiDirect = wtWifiDirect ?: run {
            wtError(tag, "wtWifiDirect is null")
            return
        }

        wifiDirectP2pAction(
            tag,
            "Success starting discovering services",
            "Failed starting discovering services",
            ignoreP2pError = false
        ) {
            wifiDirect.discoverServices()
        }
    }

    suspend fun clearAllServices() {
        val tag = "clearAllServices/${randomString(2u)}"

        val wifiDirect = wtWifiDirect ?: run {
            wtError(tag, "wtWifiDirect is null")
            return
        }

        wifiDirectP2pAction(
            tag,
            "Success clearing service requests",
            "Failed clearing service requests"
        ) {
            wifiDirect.clearServiceRequests()
        }

        wifiDirectP2pAction(
            tag,
            "Success clearing local service",
            "Failed clearing local service"
        ) {
            wifiDirect.clearLocalServices()
        }
    }

    suspend fun processBcastReceiverMessage(intent: Intent) {
        val tag = "WiFiDirectBroadcastReceiver${randomString(2u)}"
        val action = intent.action

        logd(tag, "action: ${action.toString()}")

        when (action) {
            WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1)
                logd(
                    tag, "P2P discovery has $state " +
                            if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) "Started" else "Stopped"
                )
                mainLoopInbox.send(
                    if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                        WTWifiEvent.P2p.PeersDiscoveryStarted
                    } else {
                        WTWifiEvent.P2p.PeersDiscoveryStopped
                    }
                )
            }

            WIFI_P2P_STATE_CHANGED_ACTION -> {
                val enabled: Boolean = (WifiP2pManager.WIFI_P2P_STATE_ENABLED == intent.getIntExtra(
                    WifiP2pManager.EXTRA_WIFI_STATE,
                    -1
                ))
                logd(
                    tag, "P2P state changed to " +
                            if (enabled) "enabled" else "disabled" + " " + "manager: \n\t\t\$manager"
                )
                mainLoopInbox.send(
                    if (enabled) {
                        WTWifiEvent.P2p.WifiEnabled
                    } else {
                        WTWifiEvent.P2p.WifiDisabled(checkWifiPermissions())
                    }
                )
            }

            WIFI_P2P_PEERS_CHANGED_ACTION -> {
                logd(tag, "P2P peers changed")
                mainLoopInbox.send(WTWifiEvent.P2p.PeersChanged)
            }

            WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val networkInfo = intent
                    .getParcelableExtra<Parcelable>(WifiP2pManager.EXTRA_NETWORK_INFO) as NetworkInfo
                logd(
                    tag, "P2P connection changed to: " +
                            if (networkInfo.isConnected) "Connected" else "Disconnected"
                )
                mainLoopInbox.send(WTWifiEvent.P2p.ConnectionChanged)
            }

            WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                logd(tag, "P2P this device changed")
                mainLoopInbox.send(WTWifiEvent.P2p.ThisDeviceChanged)
            }

            else -> {
                logd(
                    tag,
                    "P2P changed to ${action.toString()} NOT Addressed"
                )
            }
        }
    }

    fun main(cadence: Long = 1000L) {
        val tag = "wtWifiDirectMain/${randomString(2u)}"

        logd(tag, "Entry")

        resetData()

        mainLoopJob = scope.launch {
            logd(TAGKClass, tag, "wtWifiDirectMain Starting WifiD main loop")
            mainLoopInit()
            mainLoop(cadence)
        }
    }

    suspend fun stop() {
        val tag = "wtWifiDirectStop/${randomString(2u)}"

        logd(tag, "Entry")

        cancelConnect()
        clearAllServices()
        stopPeersDiscovery()
        removeGroup()
        clearAllServices()

        wtWifiDirect?.channelClose()

        resetData()

        send(
            to = WTModOpArg.To.Activity,
            msg = WTModOpArg.Msg(
                PipeMessage(
                    type = PipeMessageType.WifiDebugInfoMessage,
                    data = wtWifiDirectInfo()
                )
            )
        )

        send(
            to = WTModOpArg.To.Activity,
            msg = WTModOpArg.Msg(
                PipeMessage(
                    type = PipeMessageType.WifiRestartChannel,
                    data = null
                )
            )
        )

        resetData()
    }

    internal inline fun wifiDirectP2pAction(
        tag: String,
        successMessage: String? = null,
        errorMessage: String? = null,
        ignoreP2pError: Boolean = true,
        action: () -> WTWifiDirectResult<Unit>,
    ): WTWifiDirectResult<Unit> {
        val localTag = "wifiDirectP2pAction/${randomString(2U)}"

        wtWifi.onEngineCoolingDown { errT ->
            logd(localTag, "P2p engine cooling down: ${errT.description}")
            return@onEngineCoolingDown errT.err
        }?.let {
            return it
        }

        return when (val res = action()) {
            is WTWifiDirectResult.Success -> {
                if (!ignoreP2pError) wtWifi.eraseP2pError()
                logd(TAGKClass, localTag, successMessage ?: "wifiDirectP2pAction Success")
                logd(TAGKClass, tag, successMessage ?: "wifiDirectP2pAction Success")
                res
            }

            is WTWifiDirectResult.Error -> {
                if (!ignoreP2pError) wtWifi.wifiError(tag, res)
                logd(
                    TAGKClass,
                    localTag,
                    "${errorMessage ?: "wifiDirectP2pAction Error"} : ${res.errStr}"
                )
                logd(
                    TAGKClass,
                    tag,
                    "${errorMessage ?: "wifiDirectP2pAction Error"} : ${res.errStr}"
                )
                res
            }

            is WTWifiDirectResult.Data<*> -> {
                logd(
                    TAGKClass,
                    localTag,
                    "Invalid State"
                )
                wtError(tag, "Invalid state")
                WTWifiDirectResult.Error.App.InvalidState
            }
        }
    }

    override fun subscribe(
        to: WTModOpArg,
        onEvent: WTModOpArg
    ) {
        val tag = "subscribe/${randomString(2U)}"

        val to = (to as? WTModOpArg.To)?.id ?: run {
            wtError(tag, "Invalid input: to -> $to")
            return
        }

        val onEvent = (onEvent as? WTModOpArg.OnEvent)?.onEvent ?: run {
            wtError(tag, "Invalid input: onEvent -> $onEvent")
            return
        }

        pipeSubscribe(
            pipeId = to,
            scope = scope,
            autoCreate = true,
            onReceive = onEvent
        )
    }

    override fun send(
        to: WTModOpArg,
        msg: WTModOpArg
    ) {
        val tag = "sendEvent/${randomString(2U)}"

        val to = (to as? WTModOpArg.To)?.id ?: run {
            wtError(tag, "Invalid input: to -> $to")
            return
        }

        val msg = (msg as? WTModOpArg.Msg)?.msg ?: run {
            wtError(tag, "Invalid msg: $msg")
            return
        }

        pipeSendAsync(to, scope, msg)
    }
}