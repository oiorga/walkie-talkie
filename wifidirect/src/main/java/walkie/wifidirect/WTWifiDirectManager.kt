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
import walkie.talkie.api.wtsystem.NodeIdInt
import walkie.util.api.ChannelId
import walkie.util.api.ChannelIdInt
import walkie.util.api.ChannelMessageType
import walkie.util.api.RemoteCallId
import walkie.util.api.RemoteCallMuxInt
import walkie.util.generic.ChannelMux
import walkie.util.generic.ChannelMuxInt
import walkie.util.generic.GenericList
import walkie.util.generic.Mailbox
import walkie.util.generic.MailboxData
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
import walkie.wifidirect.WTWifiDirectResult.LocalError.ChannelNotInitialized.dataOrNull
import java.net.InetAddress
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/* Ugly. To revisit. To prevent coroutines to restart at onCreate on screen rotation */
class WTWiFiDirectStatic private constructor() {
    companion object {
        val INSTANCE: WTWiFiDirectStatic by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { WTWiFiDirectStatic() }
        val ONE = INSTANCE
    }
    var scanPeersS: Boolean = false
}

class WTWifiDirectManager(
    val manager: WifiP2pManager,
    val node: NodeIdInt,
    val scope: CoroutineScope,
    private val _channelMux: ChannelMuxInt<Any, ChannelMessageType> = ChannelMux(),
    private val _remoteCallMux: RemoteCallMuxInt = RemoteCallMux()
) :
    ChannelMuxInt<Any, ChannelMessageType> by _channelMux,
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
        state = WTWifiState.Disabled(wifiPermissions = false))

    val wifiP2pEnable: Boolean
        get() = wtWifi.isWifiEnabled

    val directWTPeers: Map<String, WTWifiDirectPeerInfo>
        get() = wtWifi.directWTPeers

    val directWifiServices: Map<String, WTWifiDirectServiceInfo>
        get() = wtWifi.directServices

    val wtWifiP2pInfo: WifiP2pInfo?
        get() = wtWifi.p2pInfo

    val wtWifiGroupInfo: WifiP2pGroup?
        get() = wtWifi.groupInfo

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

    val wtGroupOwner: WTWifiDirectPeerInfo?
        get() = wtWifi.groupOwner

    val deviceId: String
        get() = node.id()

    var connectToDevice: WTWifiDirectPeerInfo? = null

    val restartChannelCountdown
        get() = wtWifi.tick

    val connectingAllowed: Boolean
        get() = ((wtWifi.state is WTWifiState.Enabled) && (wtWifi.isWTServicePeerPresent))

    fun channelCountdown(): Int {
        return restartChannelCountdown
    }

    fun restartChannel(yes: Boolean? = null): Boolean {
        if (yes == true) {
            wtWifi.restartChannel()
        }
        return wtWifi.restartChannel
    }

    init {
        logging(true)
    }

    override suspend fun channelOnReceive(
        channelId: ChannelIdInt,
        type: ChannelMessageType?,
        input: Any?
    ) {
        val tag = "channelOnReceive/${randomString(2U)}"

        logd(tag, "channelId: $channelId inputType: $type")
        when (channelId) {
            ChannelId.RCToWifi -> {
                when (type) {
                    ChannelMessageType.RCWifiBroadcastReceiver -> {
                        if (null != input) {
                            processBcastReceiverMessage(input as Intent)
                        } else {
                            /***************************/
                        }
                    }
                    ChannelMessageType.RCLocalServerPort -> {
                        mainLoopInbox.send(WTWifiEvent.WTWifi.LocalServerPort(input as Int))
                    }
                    else -> {
                        throw (NotImplementedError("$tag: channelOnReceive: channelId: $channelId: inputType: $type Not Implemented "))
                    }
                }
            }

            else -> {
                throw (NotImplementedError("$tag: channelOnReceive: channelId: $channelId: inputType: $type Not Implemented "))
            }
        }
    }

    fun resetData() {
        val tag = "resetData/${randomString(2u)}"

        logd(TAGKClass, tag, "Entry")

        channelSend(
            ChannelId.RCToComm,
            scope,
            ChannelMessageType.RCGroupInfo,
            Triple(null, null, null)
        )
        mainLoopJob?.cancel()
        mainLoopJob = null
        wtWifi = wtWifi.reset()
        connectToDevice = null
    }

    /*
    * Transition to self-contained Wi-Fi direct manager
    */
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
        logd(tag, "Entry")
        return wtWifiDirect?.requestGroupInfo()?.dataOrNull()
    }

    suspend fun removeGroup() {
        val tag = "removeGroup/${randomString(2u)}"
        val success = wtWifiDirect?.removeGroup() == WTWifiDirectResult.Success

        logd(tag, if (success) "\t-> Success" else "\t-> Failed")
    }

    private fun isConnectedToGroup(): Boolean {
        val tag = "isConnectedToGroup/${randomString(2U)}"
        logd(
            tag,
            "wtLocalIp: $wtLocalIp wtGroupIp: $wtGroupIp wtIsGroupFormed: $wtIsGroupFormed ${(null != wtLocalIp) && wtIsGroupFormed && (null != wtGroupIp)}"
        )
        return ((null != wtLocalIp) && wtIsGroupFormed && (null != wtGroupIp))
    }

    suspend fun connect(
        device: WifiP2pDevice,
        config: WifiP2pConfig
    ): ConnectionStatus {
        val tag = "device: ${randomString(2u)}"

        logd(tag, "Connecting to ${device.uniqueWifiId()}")

        val ret = when (val res = wtWifiDirect?.connect(device, config)) {
            is WTWifiDirectResult.Success -> {
                ConnectionStatus.InProgress
            }
            is WTWifiDirectResult.WifiP2pError -> {
                ConnectionStatus.Fail
            }
            else -> {
                ConnectionStatus.Fail
            }
        }
        return ret
    }

    suspend fun cancelConnect() {
        val tag = "cancelConnect/${randomString(2u)}"

        logdAppend(tag, "-> ")

        when (val res = wtWifiDirect?.cancelConnect()) {
            is WTWifiDirectResult.Success -> {
                logdAppend(tag, "Success")
            }
            is WTWifiDirectResult.WifiP2pError -> {
                logdAppend(tag, res.errStr)
            }
            else -> {
                logdAppend(tag, "Unknown Error")
            }
        }

        logd(tag)
    }

    suspend fun connectTo(device: WTWifiDirectPeerInfo): ConnectionStatus {
        val tag = "connectTo/${randomString(2u)}"
        var c = 0

        logd(
            tag,
            "($c)connect to ${device.uniqueWifiId()} ${device.wtService} ${device.directWifiConnection}"
        ).also { c++ }

        if (device.name == deviceUid) {
            logd(
                tag,
                "($c)Connecting to self: ${device.name}/${device.address} == ${deviceUid} ???"
            ).also { c++ }
            throw (Exception("$tag Connecting to self: ${device.name}/${device.address} == ${deviceUid} ???"))
        }

        when (device.directWifiConnection) {
            ConnectionStatus.AgedOut -> {
                device.directWifiConnection = ConnectionStatus.Renew
            }

            ConnectionStatus.NoWTService -> {
                if (device.wtService) {
                    device.directWifiConnection = ConnectionStatus.NotConnected
                }
            }

            ConnectionStatus.Null -> {
                if (!device.wtService) {
                    device.directWifiConnection = ConnectionStatus.NoWTService
                } else {
                    device.directWifiConnection = ConnectionStatus.NotConnected
                }
            }

            ConnectionStatus.NotConnected,
            ConnectionStatus.Renew,
            ConnectionStatus.Retry -> {
                device.cipInit()
                val config = WifiP2pConfig().apply {
                    deviceAddress = device.address
                    groupOwnerIntent = GROUP_OWNER_INTENT_MIN
                    wps.setup = WpsInfo.PBC
                }
                if (!device.wtService) device.directWifiConnection = ConnectionStatus.NoWTService

                if (device.wtService) {
                    device.directWifiConnection = connect(device.p2pInfo, config)
                    when (device.directWifiConnection) {
                        ConnectionStatus.InProgress -> {
                            logd(
                                TAGKClass,
                                tag,
                                "($c)Connect to: ${device.name}  ${device.address} GO = ${device.isGroupOwner} Success"
                            ).also { c++ }
                            device.cipInit()
                        }

                        ConnectionStatus.Fail -> {
                            logd(
                                TAGKClass,
                                tag,
                                "($c)Connect to: ${device.name} ${device.address} GO = ${device.isGroupOwner} Failed"
                            ).also { c++ }
                            device.fcdInit()
                        }

                        else -> {
                            throw (NotImplementedError("$tag Connecting to {device.name}  ${device.address} GO = ${device.isGroupOwner} Not Expected result"))
                        }
                    }
                }
            }

            ConnectionStatus.Fail -> {
                logd(
                    TAGKClass,
                    tag,
                    "($c)Connect to: ${device.name} ${device.address} GO = ${device.isGroupOwner} is In Fail. Change to Retry.",
                ).also { c++ }
                if (isConnectedToGroup()) {
                    device.directWifiConnection = ConnectionStatus.Connected
                    device.stateCounterTick(0)
                } else {
                    if (!device.fcd()) {
                        device.directWifiConnection = ConnectionStatus.Retry
                        cancelConnect()
                    }
                    device.stateCounterTick()
                }
            }

            ConnectionStatus.InProgress -> {
                logd(
                    TAGKClass,
                    tag,
                    "($c)Connect to: ${device.name} ${device.address} GO = ${device.isGroupOwner} is InProgress connectedToGroup: ${isConnectedToGroup()}",
                ).also { c++ }
                if (!isConnectedToGroup()) {
                    if (!device.cip()) {
                        cancelConnect()
                        device.directWifiConnection = ConnectionStatus.Retry
                    }
                } else {
                    device.directWifiConnection = ConnectionStatus.Connected
                    device.stateCounterTick(0)
                }
                device.stateCounterTick()
            }

            ConnectionStatus.Connected -> {
                logd(
                    TAGKClass,
                    tag,
                    "($c)Connect to: ${device.name} ${device.address} GO = ${device.isGroupOwner} is Already Connected"
                ).also { c++ }

                if (!isConnectedToGroup()) {
                    device.directWifiConnection = ConnectionStatus.Retry
                    logd(
                        TAGKClass,
                        tag,
                        "($c)Connect to: ${device.name} ${device.address} GO = ${device.isGroupOwner} is Connected but there's an anomaly?: " +
                                "wtLocalIp: $wtLocalIp wtIsGroupFormed: $wtIsGroupFormed wtGroupIp: $wtGroupIp"
                    ).also { c++ }
                }
            }

            else -> {
                logd(
                    TAGKClass,
                    tag,
                    "($c)Connect to: Connection Status ${device.directWifiConnection} NOT handled"
                ).also { c++ }
                throw (NotImplementedError("Connect to: Connection Status ${device.directWifiConnection} NOT handled"))
            }
        }

        logd(
            tag,
            "($c)Exit connect to ${device.uniqueWifiId()} ${device.wtService} ${device.directWifiConnection}"
        ).also { c++ }

        return device.directWifiConnection
    }

    suspend fun requestConnectionInfo(): WifiP2pInfo? =
        wtWifiDirect?.requestConnectionInfo()?.dataOrNull()

    suspend fun mainLoop(cadence: Long) {
        val tag = "mainLoop/${randomString(2u)}"
        var p2pInfo: Triple<InetAddress?, InetAddress?, Int?> = Triple(wtGroupIp, wtLocalIp, wtGroupServerPort)
        var rndDelay = Random.nextLong(50)

        while (scope.isActive) {
            notifyOfChange(p2pInfo, Triple(wtGroupIp, wtLocalIp, wtGroupServerPort))
            p2pInfo = Triple(wtGroupIp, wtLocalIp, wtGroupServerPort)

            internalMaintenance()

            rndDelay = max(Random.nextLong(50 + rndDelay), 75)
            val event = when (val v =  mainLoopInbox.await((cadence + rndDelay).milliseconds)) {
                is MailboxData.Message -> { v.value }
                MailboxData.Timeout -> { WTWifiEvent.WTWifi.Timeout }
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
                    thisDevice = requestDeviceInfo())
            }
            WTWifiEvent.P2p.PeersChanged -> {
                logStr += "\n\tP2P peers changed"
                wtWifi = wtWifi.transition(
                    event,
                    p2pPeers = requestPeersInfo()
                )
                if (wtWifi.directWTPeers.isEmpty())
                    connectToDevice = null
            }
            WTWifiEvent.P2p.ConnectionChanged -> {
                val p2pInfo = requestConnectionInfo()
                logStr += "\n\tP2P Connection changed: groupFormed: ${p2pInfo?.groupFormed} isGroupOwner: ${p2pInfo?.isGroupOwner}"
                wtWifi = wtWifi.transition(
                    event,
                    p2pInfo = p2pInfo,
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
                    groupInfo = requestGroupInfo())
            }
            is WTWifiEvent.P2p.TxtRecordListener -> {
                logStr += "\n\tP2P Process TxtRecordListener info"
                wtWifi = wtWifi.transition(
                    event,
                    directServices = dnsSdTxtRecordListener(event.fullDomain, event.record, event.device))
            }
            is WTWifiEvent.P2p.ServiceResponseListener -> {
                logStr += "\n\tP2P Process ServiceResponseListener info"
                wtWifi = wtWifi.transition(
                    event,
                    directServices = dnsSdServiceResponseListener(event.instanceName, event.registrationType, event.resourceType))
            }
            is WTWifiEvent.WTWifi.LocalServerPort -> {
                wtWifi = wtWifi.transition(event)
            }
            WTWifiEvent.WTWifi.MergePeersServicesInfo -> {
                wtWifi = wtWifi.transition(event)
            }
            is WTWifiEvent.WTWifi.Command -> {
                if (null != event.advertiseLocalService) {
                    if (event.advertiseLocalService) addLocalService(removeFirst = true)
                    else removeLocalService()
                }
                if (null != event.serviceDiscovery) {
                    if (event.serviceDiscovery) {
                        addServiceRequest(removeFirst = true)
                        discoverServices()
                    }
                }
                if (null != event.removeGroup && event.removeGroup) {
                    logStr += "\n\tRequest removing group"
                    removeGroup()
                    connectToDevice = null
                }
            }
            else -> {
                logStr += "\n\tUnprocessed WifiD Event: ${event.toString()}"
            }
        }

        wtWifi.consumeNextEvent()?.let { nEvent ->
            logStr += "\n\tSend nextEvent: $nEvent"
            mainLoopInbox.send(nEvent)
        }

        logd(tag, logStr)
    }

    fun internalMaintenance() {
        val tag = "internalMaintenance/${randomString(2u)}"
        logd(
            tag, "\n\tthisDevice: ${thisDevice?.deviceName}" +
                    "\n\tisWifiP2pEnabled: $wifiP2pEnable" +
                    "\n\twtIsGroupFormed: $wtIsGroupFormed" +
                    "\n\tchannelCountdown: ${channelCountdown()}" +
                    "\n\tdeviceName = ${deviceId}/${deviceUid}" +
                    "\n\tlocalIp = ${this.wtLocalIp}"
        )

        channelSend(
            ChannelId.RCToWTActivity,
            scope,
            ChannelMessageType.RCWifiDebugInfoMessage,
            wtWifiDirectInfo()
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
            logd(tag, "Not enough Wifi permissions. Requesting.")
            return
        }
        updatePeers()
    }

    suspend fun mainLoopInit() {
        val tag = "mainLoopInit/${randomString(2u)}"
        val s = WTWiFiDirectStatic.INSTANCE

        logd(tag, "Entry: ${s.scanPeersS}")

        if (s.scanPeersS) return
        s.scanPeersS = true

        channelSend(
            ChannelId.RCToWTActivity,
            scope,
            ChannelMessageType.RCWifiDebugInfoMessage,
            wtWifiDirectInfo()
        )

        cancelConnect()
        removeGroup()
        clearAllServices()

        channelSend(
            ChannelId.RCToComm,
            scope,
            ChannelMessageType.RCGroupInfo,
            Triple(null, null, null)
        )

        initServices()
    }

    suspend fun updatePeers() {
        val tag = "updatePeers/${randomString(2u)}"

        logd(tag, "Entry")
        logd(
            tag,
            "deviceName = $deviceUid localIp = $wtLocalIp wifiP2pEnable: $wifiP2pEnable"
        )

        if (null == thisDevice) {
            logd(tag, "Exit: Device info not available: $wifiP2pEnable")
            return
        }

        if (!wifiP2pEnable) {
            logd(tag, "Exit: wifiP2pEnable: $wifiP2pEnable")
            return
        }

        logd(
            tag,
            "connectToPeers deviceName = $deviceUid"
        )
        connectToPeers()

        logd(tag, "Exit")
    }

    fun notifyOfChange(
        oldGroupInfo: Triple<InetAddress?, InetAddress?, Int?>,
        wtGroupInfo: Triple<InetAddress?, InetAddress?, Int?>
    ) {
        val tag = "notifyOfChange/${randomString(2u)}"

        val (oldGroupIp, oldLocalIp, oldGroupServerPort) = oldGroupInfo
        val (wtGroupIp, wtLocalIp, wtGroupServerPort) = wtGroupInfo

        logd(
            tag, "\ngroupIp: $oldGroupIp -> $wtGroupIp" +
                    "\nlocalIp: $oldLocalIp -> $wtLocalIp" +
                    "\nserverPort: $oldGroupServerPort -> $wtGroupServerPort" +
                    "\nwtGroupOwnerName: $wtGroupOwnerName" +
                    "\nwtIsGroupOwner: $wtIsGroupOwner"
        )

        if (oldGroupInfo == wtGroupInfo)
            return

        if (oldGroupInfo != wtGroupInfo) {
            var localIpAlreadyChanged = false
            if (null == wtGroupIp) {
                logd(
                    tag,
                    "Group info changed(Null): wtGroupIp: $oldGroupIp -> $wtGroupIp wtLocalIp: $oldLocalIp -> $wtLocalIp"
                )
                channelSend(
                    ChannelId.RCToComm,
                    scope,
                    ChannelMessageType.RCGroupInfo,
                    Triple(null, null, null)
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
                /*
            channelSend(
                WTChannelId.RCToComm,
                WTChannelMessageType.RCGroupInfo,
                Triple(null, null, null)
            )
            */
                channelSend(
                    ChannelId.RCToComm,
                    scope,
                    ChannelMessageType.RCGroupInfo,
                    Triple(wtGroupOwnerName, wtGroupIp, wtGroupServerPort)
                )
                channelSend(
                    ChannelId.RCToComm,
                    scope,
                    ChannelMessageType.RCLocalIp,
                    wtLocalIp
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
                channelSend(
                    ChannelId.RCToComm,
                    scope,
                    ChannelMessageType.RCLocalIp,
                    wtLocalIp
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
            connectToDevice = null
            removeGroup()
            wtWifi = wtWifi.reset()
            channelSend(
                ChannelId.RCToComm,
                scope,
                ChannelMessageType.RCGroupInfo,
                Triple(null, null, null)
            )
            restartChannel(true)
        }

        wtWifiP2pInfo?.logD(tag)
    }

    suspend fun connectToPeers() {
        val tag = "connectToPeers/${randomString(2u)}"
        var c = 0
        val divider = 5

        logd(tag, "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++")
        logd(
            tag,
            "($c):\n" +
                    "\n\t\twtIsGroupOwner: $wtIsGroupOwner" +
                    "\n\t\twtIsGroupFormed: $wtIsGroupFormed" +
                    "\n\t\tisWifiP2pEnabled: $wifiP2pEnable" +
                    "\n\t\tconnectingAllowed: $connectingAllowed" +
                    "\n\t\tdirectWifiPeers: ${directWTPeers.keys}"
        ).also { c++ }

        if (wifiP2pEnable && connectingAllowed) {
            val tmpPeers: MutableMap<String, WTWifiDirectPeerInfo> = directWTPeers.toMutableMap()
            val groupOwner = wtGroupOwner
            val peersList = tmpPeers.filter { (_, device) -> device != groupOwner }.toList()

            logd(
                tag,
                "($c):\n" +
                        "\n\t\tgroupOwner: ${groupOwner?.name}" +
                        "\n\t\tdirectWifiPeers: ${tmpPeers.keys}" +
                        "\n\t\tpeersList: ${peersList.map { pair -> pair.first }}"
            ).also { c++ }

            if (null != groupOwner &&
                groupOwner.wtService &&
                groupOwner.p2pInfo.deviceName != thisDevice!!.deviceName &&
                !wtIsGroupOwner
            ) {
                logd(
                    tag,
                    "$tag($c): Connect to Group Owner: [${groupOwner.name}.${groupOwner.address}] " +
                            "[GO: $wtIsGroupFormed / $wtIsGroupOwner / ${groupOwner.isGroupOwner}] " +
                            "[connectionStatus: ${groupOwner.directWifiConnection}]"
                ).also { c++ }
                connectTo(groupOwner)
                return
            }

            if (wtIsGroupOwner && wtIsGroupFormed) {
                connectToDevice = null
                return
            }

            when (connectToDevice?.directWifiConnection) {
                ConnectionStatus.InProgress, ConnectionStatus.Fail, ConnectionStatus.NotConnected,
                ConnectionStatus.AgedOut, ConnectionStatus.Renew -> {
                    logd(tag, "Continue with connectToDevice: ${connectToDevice?.uniqueWifiId()}")
                }

                null -> {
                    logd(
                        tag,
                        "Parsing for new connectToDevice(NULL0): Got: $connectToDevice / ${connectToDevice?.uniqueWifiId()}"
                    )
                    if (peersList.isNotEmpty()) connectToDevice = peersList.first().second
                    logd(
                        tag,
                        "Parsing for new connectToDevice(NULL1): Got: ${connectToDevice?.uniqueWifiId()}"
                    )
                }

                else -> {
                    var brk = false
                    for ((_, device) in peersList) {
                        logd(
                            tag,
                            "Parsing for new connectToDevice(Searching): Got: ${connectToDevice?.uniqueWifiId()}"
                        )
                        if (brk) {
                            connectToDevice = device
                            logd(
                                tag,
                                "Parsing for new connectToDevice(NEW0): Got: ${connectToDevice?.uniqueWifiId()}"
                            )
                            break
                        }
                        if (device == connectToDevice) {
                            brk = true
                            if (device == peersList.last().second) {
                                connectToDevice = peersList.first().second
                                logd(
                                    tag,
                                    "Parsing for new connectToDevice(NEW1): Got: ${connectToDevice?.uniqueWifiId()}"
                                )
                            }
                        }
                    }
                }
            }

            if ((!wtIsGroupFormed || null == wtGroupOwner) && null != connectToDevice) {
                logd(
                    tag,
                    "$tag($c) Group is not formed: ${connectToDevice!!.name}/${connectToDevice!!.address} " +
                            "[GO: $wtIsGroupFormed / $wtIsGroupOwner / ${connectToDevice!!.isGroupOwner}] " +
                            "[connectionStatus: ${connectToDevice!!.directWifiConnection}]" +
                            "[wtGroupOwner: ${wtGroupOwner?.p2pInfo?.deviceName}]"
                ).also { c++ }
                connectTo(connectToDevice!!)
            } else {
                if (wtIsGroupOwner && null != connectToDevice) {
                    connectTo(connectToDevice!!)
                }
            }
        }
        logd(tag, "--------------------------------------------------------------")
    }

    suspend fun removeLocalService() {
        val tag = "removeLocalService/${randomString(2u)}"
        val instanceName = WT_SERVICE_WALKIETALKIE// + "." + deviceUid()
        /* val serviceType = "_presence._tcp" */
        val serviceType = WT_SERVICE_WALKIETALKIE

        logd(tag, "Entry: $wtLocalServiceRecord $wtCachedServiceRecord")

        val cachedServiceRecord = wtCachedServiceRecord ?: run { return }

        when (val res = wtWifiDirect?.removeLocalService(
            instanceName,
            serviceType,
            cachedServiceRecord
        )) {
            is WTWifiDirectResult.Success -> {
                logd(TAGKClass, tag, "Success removing local service")
            }

            is WTWifiDirectResult.WifiP2pError -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed removing local service: ${res.errStr}"
                )
            }

            else -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed removing local service: Unknown Error"
                )
            }
        }
    }

    suspend fun addLocalService(removeFirst: Boolean = false) {
        val tag = "addLocalService/${randomString(2u)}"
        val instanceName = WT_SERVICE_WALKIETALKIE /* + "." + deviceUid() */
        /* val serviceType = "_presence._tcp" */
        val serviceType = WT_SERVICE_WALKIETALKIE

        logd(tag, "Entry: $wtLocalServiceRecord wtLocalServerPort: $wtLocalServerPort")

        if (removeFirst) {
            removeLocalService()
        }

        when (val res =
            wtWifiDirect?.addLocalService(
                instanceName,
                serviceType,
                wtLocalServiceRecord!!
            )
        ) {
            is WTWifiDirectResult.Success -> {
                logd(TAGKClass, tag, "Success adding local service")
            }

            is WTWifiDirectResult.WifiP2pError -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed adding local service: ${res.errStr}"
                )
            }
            else -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed adding local service: Unknown Error"
                )
            }
        }
    }

    suspend fun removeServiceRequest() {
        val tag = "addServiceRequest/${randomString(2u)}"
        val instanceName = WT_SERVICE_WALKIETALKIE /* + "." + deviceUid() */
        /* val serviceType = "_presence._tcp" */
        val serviceType = WT_SERVICE_WALKIETALKIE

        logd(tag, "Entry")

        when (val res = wtWifiDirect?.removeServiceRequest(
            instanceName,
            serviceType
        )) {
            is WTWifiDirectResult.Success -> {
                logd(TAGKClass, tag, "Success removing service request")
            }
            is WTWifiDirectResult.WifiP2pError -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed removing service request: ${res.errStr}"
                )
            }
            else -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed removing service request: Unknown Error"
                )
            }
        }
    }

    suspend fun addServiceRequest(removeFirst: Boolean = false) {
        val tag = "addServiceRequest/${randomString(2u)}"
        val instanceName = WT_SERVICE_WALKIETALKIE /* + "." + deviceUid() */
        /* val serviceType = "_presence._tcp" */
        val serviceType = WT_SERVICE_WALKIETALKIE

        logd(tag, "Entry")

        if (removeFirst) {
            removeServiceRequest()
        }

        when (val res = wtWifiDirect?.addServiceRequest(
            instanceName,
            serviceType
        )) {
            is WTWifiDirectResult.Success -> {
                logd(TAGKClass, tag, "Success adding service request")
            }
            is WTWifiDirectResult.WifiP2pError -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed adding service request: ${res.errStr}"
                )
            }
            else -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed adding service request: Unknown Error"
                )
            }
        }
    }

    fun dnsSdTxtRecordListener (fullDomain: String,
                                record: Map<String, String>,
                                device: WifiP2pDevice): Map<String, WTWifiDirectServiceInfo> {
        val tag = "dnsSdTxtRecordListener/${randomString(2u)}"

        logd(tag,
            "DnsSdTxtRecord available: [$fullDomain] [$record] [${device.deviceName}]"
        )

        val newDirectWifiServices = directWifiServices.toMutableMap()

        if (record[WT_SERVICE_WALKIETALKIE] != null && record[WT_SERVICE_WALKIETALKIE] == WT_SERVICE_WALKIETALKIE) {
            if (null == newDirectWifiServices[device.uniqueWifiId()]) {
                newDirectWifiServices[device.uniqueWifiId()] =
                    WTWifiDirectServiceInfo(
                        id = record[WT_SERVICE_ID],
                        unique = record[WT_SERVICE_UNIQUE],
                        rnd = record[WT_SERVICE_RND],
                        localServerPort = record[WT_SERVICE_LOCAL_SERVER_PORT]
                    )
            } else {
                newDirectWifiServices[device.uniqueWifiId()]?.id = record[WT_SERVICE_ID]
                newDirectWifiServices[device.uniqueWifiId()]?.unique = record[WT_SERVICE_UNIQUE]
                newDirectWifiServices[device.uniqueWifiId()]?.rnd = record[WT_SERVICE_RND]
                newDirectWifiServices[device.uniqueWifiId()]?.localServerPort =
                    record[WT_SERVICE_LOCAL_SERVER_PORT]
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
            if (null == newDirectWifiServices[resourceType.uniqueWifiId()]) {
                newDirectWifiServices[resourceType.uniqueWifiId()] = WTWifiDirectServiceInfo()
            }
        }

        return newDirectWifiServices
    }

    fun registerServiceListeners() {
        val tag = "registerServiceListeners/${randomString(2u)}"

        logd(tag, "Entry")

        val txtListener = WifiP2pManager.DnsSdTxtRecordListener { fullDomain, record, device ->
            scope.launch {
                mainLoopInbox.send(WTWifiEvent.P2p.TxtRecordListener(fullDomain, record, device))
            }
        }

        val servListener = WifiP2pManager.DnsSdServiceResponseListener { instanceName, registrationType, resourceType ->
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
        wtWifiDirect?.registerServiceListeners( txtListener, servListener)
    }

    suspend fun initServices() {
        val tag = "discoverServicesInit/${randomString(2u)}"
        val sem = Semaphore(1, 1)

        logd(tag, "Entry")

        registerServiceListeners()
        clearAllServices()
    }

    @SuppressLint("NewApi")
    suspend fun stopPeersDiscovery() {
        val tag = "stopPeerDiscovery/${randomString(2u)}"

        logd(tag, "Entry")

        when (val res = wtWifiDirect?.stopPeersDiscovery()) {
            is WTWifiDirectResult.Success -> {
                logd(TAGKClass, tag, "Success stopping peers discovery")
            }
            is WTWifiDirectResult.WifiP2pError -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed stopping peers discovery: ${res.errStr}"
                )
            }
            else -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed stopping peers discovery: Unknown Error"
                )
            }
        }
    }

    suspend fun discoverServices() {
        val tag = "discoverServices/${randomString(2u)}"

        logd(tag, "Entry")

        when (val res = wtWifiDirect?.discoverServices()) {
            is WTWifiDirectResult.Success -> {
                logd(TAGKClass, tag, "Success starting discovering services")
            }
            is WTWifiDirectResult.WifiP2pError -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed starting discovering services: ${res.errStr}"
                )
            }
            else -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed starting discovering services: Unknown Error"
                )
            }
        }
    }

    suspend fun clearAllServices() {
        val tag = "clearAllServices/${randomString(2u)}"

        logd(tag, "Entry")

        when (val res = wtWifiDirect?.clearServiceRequests()) {
            is WTWifiDirectResult.Success -> {
                logd(TAGKClass, tag, "Success clearing service requests")
            }
            is WTWifiDirectResult.WifiP2pError -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed clearing service requests: ${res.errStr}"
                )
            }
            else -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed clearing service requests: Unknown Error"
                )
            }
        }

        when (val res = wtWifiDirect?.clearLocalServices()) {
            is WTWifiDirectResult.Success -> {
                logd(TAGKClass, tag, "Success clearing local service")
            }
            is WTWifiDirectResult.WifiP2pError -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed clearing local service: ${res.errStr}"
                )
            }
            else -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed clearing local service: Unknown Error"
                )
            }
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
                val enabled: Boolean = (WifiP2pManager.WIFI_P2P_STATE_ENABLED == intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1))
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
        val s = WTWiFiDirectStatic.INSTANCE

        logd(tag, "Entry")

        cancelConnect()
        clearAllServices()
        stopPeersDiscovery()
        removeGroup()
        clearAllServices()

        wtWifiDirect?.channelClose()

        resetData()
        channelSend(
            ChannelId.RCToWTActivity,
            scope,
            ChannelMessageType.RCWifiDebugInfoMessage,
            wtWifiDirectInfo()
        )

        s.scanPeersS = false
        channelSend(
            ChannelId.RCToWTActivity,
            scope,
            ChannelMessageType.RCWifiRestartChannel
        )
        resetData()
    }
}