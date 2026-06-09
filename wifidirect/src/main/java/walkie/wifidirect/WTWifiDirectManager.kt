package walkie.wifidirect

import android.annotation.SuppressLint
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pConfig.GROUP_OWNER_INTENT_MAX
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
import walkie.util.logging
import walkie.util.randomString
import walkie.wifidirect.WTWifiDirectResult.LocalError.ChannelNotInitialized.dataOrNull
import walkie.wifidirect.WTWifiDirectServiceInfo.Companion.WT_SERVICE_ID
import walkie.wifidirect.WTWifiDirectServiceInfo.Companion.WT_SERVICE_LOCAL_SERVER_PORT
import walkie.wifidirect.WTWifiDirectServiceInfo.Companion.WT_SERVICE_RND
import walkie.wifidirect.WTWifiDirectServiceInfo.Companion.WT_SERVICE_UNIQUE
import walkie.wifidirect.WTWifiDirectServiceInfo.Companion.WT_SERVICE_WALKIETALKIE
import java.net.InetAddress
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
        const val DiscoveryCountdown = 39
        const val ConnectCountdown = 1
        const val FailCooldown = 1
        const val RestartChannelTimeout = (DiscoveryCountdown * 2)
        const val DiscoveryVsAdvertisementRatio = (2F / 3F)
    }

    /*
    * Transition to self-contained Wi-Fi direct manager
    */
    private var wtWifiDirect: WTWifiDirect? = null
    private val wtWifiDirectEnv = object : WTWifiDirectEnv {
        override fun checkWifiPermission(): Boolean {
            return this@WTWifiDirectManager.checkWifiPermission()
        }
    }

    fun attachChannel(channel: Channel) {
        wtWifiDirect = WTWifiDirect(manager, channel, wtWifiDirectEnv, scope)
    }

    fun checkWifiPermission(): Boolean {
        return (true == typedCall<Boolean>(RemoteCallId.RCCheckWifiDPermission))
    }

    fun requestWifiPermission() {
        remoteCall(RemoteCallId.RCRequestWifiDPermission)
    }

    val deviceUid: String
        get() = node.uid()

    private var mainLoopJob: Job? = null

    private val mainLoopInbox = Mailbox<WTWifiEvent>(10)

    private var wtWifi = WTWifiDB(node,
        if (checkWifiPermission()) {
            WTWifiState.Inactive.Disabled
        } else {
            WTWifiState.Inactive.NoWifiPermissions
        })

    val wifiP2pEnable: Boolean
        get() = wtWifi.isWifiEnabled

    val directWifiPeers = mutableMapOf<String, WTWifiDirectPeerInfo>()
    val directWifiServices = mutableMapOf<String, WTWifiDirectServiceInfo>()

    val wtWifiP2pInfo: WifiP2pInfo?
        get() = wtWifi.p2pInfo

    val wtWifiGroupInfo: WifiP2pGroup?
        get() = wtWifi.groupInfo

    val thisDevice: WifiP2pDevice?
        get() = wtWifi.thisDevice

    var wtLocalServiceRecord: Map<String, String>? = null

    val wtLocalIp: InetAddress?
        get() = wtWifi.localIp

    var wtLocalServerPort: Int? = null

    val wtGroupServerPort: Int?
        get() =
            (if (wtIsGroupOwner) (wtLocalServerPort)
            else if (wtIsGroupFormed) (directWifiPeers[wtGroupOwner?.uniqueWifiId()]?.wtServiceInfo?.localServerPort?.toInt())
            else (null))

    val wtIsGroupOwner: Boolean
        get() = wtWifi.isGroupOwner

    val wtIsGroupFormed: Boolean
        get() = wtWifi.isGroupFormed

    val wtGroupIp: InetAddress?
        get() = wtWifi.groupIpAddress

    val wtGroupOwnerName: String?
        get() = wtWifi.groupOwnerName

    val wtGroupOwner: WTWifiDirectPeerInfo?
        get() = let { _ ->
            val p2pOwner = if (wtIsGroupOwner) thisDevice else wtWifiGroupInfo?.owner
            directWifiPeers[p2pOwner?.uniqueWifiId()]
                ?: p2pOwner?.let { device -> WTWifiDirectPeerInfo(device) }
        }

    val deviceId: String
        get() = node.id()

    val deviceUnique: String
        get() = node.unique()

    var connectToDevice: WTWifiDirectPeerInfo? = null

    var connectCountdown: Int = ConnectCountdown
    private var wtWifiDFailure: String? = null
    fun wtWifiFailure(str: String? = null): String {
        if (null != str) {
            wtWifiDFailure = str
        }
        return (wtWifiDFailure ?: "")
    }

    var failCooldown: Int = 0
        private set
    private var restartChannelCountdown = RestartChannelTimeout

    /* Indicate whether the discovery process is active */
    private var _newWTDevice: Boolean = false
    fun newWTDevice(new: Boolean? = null): Boolean {
        _newWTDevice = new ?: _newWTDevice
        return _newWTDevice
    }

    var discoveryCountdown: Int = 0
    fun peersDiscoveryState(enabled: Boolean? = null): Boolean {
        if (null != enabled) {
            discoveryCountdown = if (enabled) 0 else DiscoveryCountdown
        }
        return (0 == discoveryCountdown)
    }

    fun serviceAdvAdd(): Boolean {
        return (0 == (discoveryCountdown % 5) && !connectingAllowed())
    }

    fun serviceDiscoveryActive(enable: Boolean? = null): Boolean {
        val tag = "serviceDiscoveryActive/${randomString(2U)}"
        if (null != enable) {
            discoveryCountdown =
                (if (enable) DiscoveryCountdown else (DiscoveryCountdown * DiscoveryVsAdvertisementRatio).toInt())
            return enable
        }
        if (newWTDevice()) {
            logd(TAGKClass, tag, "NEWWTDevice")
            discoveryCountdown = (DiscoveryCountdown * DiscoveryVsAdvertisementRatio).toInt()
            newWTDevice(false)
        }
        val ret = ((true || !connectingAllowed()) &&
                !serviceAdvAdd() &&
                (0 == (discoveryCountdown.toInt() % 5)))
        logd(
            TAGKClass,
            tag, "discoveryCountdown: $discoveryCountdown " +
                    "wtIsGroupFormed: ${wtIsGroupFormed} " + ret
        )
        return (ret)
    }

    fun serviceDiscoveryCountdown() {
        if (discoveryCountdown > 0) discoveryCountdown -= 1
    }

    fun connectingAllowed(): Boolean {
        return ((discoveryCountdown <= (DiscoveryCountdown * DiscoveryVsAdvertisementRatio).toInt()) ||
                (connectToDevice?.directWifiConnection == ConnectionStatus.InProgress ||
                        connectToDevice?.directWifiConnection == ConnectionStatus.Fail))
    }

    fun restartChannelCountdown(yes: Boolean? = null): Int {
        val tag = "restartChannelCountdown/${randomString(2U)}"
        logd(
            tag,
            "restartChannelCountdown: $restartChannelCountdown wtLocalIp: $wtLocalIp wtGroupServerPort: $wtGroupServerPort yes: $yes"
        )
        if (null != yes && yes) {
            restartChannelCountdown = RestartChannelTimeout
        } else if (restartChannelCountdown > 0 && (null == wtLocalIp || null == wtGroupServerPort)) {
            restartChannelCountdown--
        }
        return restartChannelCountdown
    }

    fun channelCountdown(): Int {
        return restartChannelCountdown
    }

    fun restartChannel(yes: Boolean? = null): Boolean {
        if (yes == true) {
            restartChannelCountdown = 0
        }
        return (0 >= restartChannelCountdown)
    }

    fun wifiP2PEngineFailCoolDown(yes: Boolean? = null): Boolean {
        val tag = "wifiP2PEngineFailCoolDown/${randomString(2U)}"
        logd(tag, "failCoolDown: $failCooldown yes: $yes")
        failCooldown = when (yes) {
            true -> {
                FailCooldown
            }

            false -> {
                0
            }

            else -> {
                (if (failCooldown > 0) (failCooldown - 1) else 0)
            }
        }
        if (0 == failCooldown) wtWifiFailure("")
        return (0 == failCooldown)
    }

    fun wifiP2PEngineOk(): Boolean {
        return (0 == failCooldown)
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
                        wtLocalServerPort = input as Int
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

        mainLoopJob = null

        wtWifi = wtWifi.resetAll()

        directWifiPeers.clear()
        directWifiServices.clear()
        connectToDevice = null
        wtLocalServiceRecord = null

        newWTDevice(false)

        /* indicate whether the discovery process is active */
        peersDiscoveryState(true)
        connectCountdown = ConnectCountdown

        restartChannelCountdown = RestartChannelTimeout

        wifiP2PEngineFailCoolDown(false)
    }

    /*
    * Transition to self-contained Wi-Fi direct manager
    */
    suspend fun requestPeersInfo(): GenericList<WifiP2pDevice> {
        val tag = "requestPeersInfo/${randomString(2u)}"

        logd("Entry")

        val newPeersList = GenericList<WifiP2pDevice>()

        wtWifiDirect?.requestPeersInfo()?.dataOrNull()?.deviceList?.let { newPeersList.addAll(it) }

        return newPeersList
    }

    suspend fun requestDeviceInfo(): WifiP2pDevice? {
        val tag = "requestDeviceInfo/${randomString(2u)}"

        logd("Entry")

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

        logd(tag, "Entry")

        logd(tag, if (wtWifiDirect?.removeGroup() == WTWifiDirectResult.Success) "\t-> Success" else "\t-> Failed")
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
        config: WifiP2pConfig = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            groupOwnerIntent = GROUP_OWNER_INTENT_MIN
            wps.setup = WpsInfo.PBC
        }
    ): ConnectionStatus {
        val tag = "device: ${randomString(2u)}"

        logd(tag, "Connecting to ${device.uniqueWifiId()}")

        val ret = when (val res = wtWifiDirect?.connect(device, config)) {
            is WTWifiDirectResult.Success -> {
                wtWifiFailure("$tag/Success")
                ConnectionStatus.InProgress
            }
            is WTWifiDirectResult.WifiP2pError -> {
                wtWifiFailure("$tag/${res.errStr}")
                ConnectionStatus.Fail
            }
            else -> {
                wtWifiFailure("$tag/Unknown Error")
                ConnectionStatus.Fail
            }
        }
        return ret
    }

    suspend fun cancelConnect() {
        val tag = "cancelConnect/${randomString(2u)}"

        logd(tag, "Entry")

        when (val res = wtWifiDirect?.cancelConnect()) {
            is WTWifiDirectResult.Success -> {
                wtWifiFailure("$tag/Success")
            }
            is WTWifiDirectResult.WifiP2pError -> {
                wtWifiFailure("$tag/${res.errStr}")
            }
            else -> {
                wtWifiFailure("$tag/Unknown Error")
            }
        }
    }

    suspend fun connectTo(device: WTWifiDirectPeerInfo): ConnectionStatus {
        val tag = "connectTo/${randomString(2u)}"
        var c = 0

        logd(
            tag,
            "($c)connect to ${device.uniqueWifiId()} ${device.wtService()} ${device.directWifiConnection}"
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
                if (device.wtService()) {
                    device.directWifiConnection = ConnectionStatus.NotConnected
                }
            }

            ConnectionStatus.Null -> {
                if (!device.wtService()) {
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
                    groupOwnerIntent =
                        (if (device.isGroupOwner || (deviceUnique < device.uniqueWifiId())) GROUP_OWNER_INTENT_MIN else (1 + Random.nextInt(
                            GROUP_OWNER_INTENT_MAX / 2
                        )))
                    /* groupOwnerIntent = GROUP_OWNER_INTENT_MIN */
                    wps.setup = WpsInfo.PBC
                }
                if (!device.wtService()) device.directWifiConnection = ConnectionStatus.NoWTService

                if (device.wtService() && wifiP2PEngineOk()) {
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
                            wifiP2PEngineFailCoolDown(true)
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
                    device.age = 0
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
                    device.age = 0
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
            "($c)Exit connect to ${device.uniqueWifiId()} ${device.wtService()} ${device.directWifiConnection}"
        ).also { c++ }

        return device.directWifiConnection
    }

    suspend fun requestConnectionInfo(): WifiP2pInfo? =
        wtWifiDirect?.requestConnectionInfo()?.dataOrNull()

    suspend fun mainLoop(cadence: Long) {
        val tag = "mainLoop/${randomString(2u)}"
        var p2pInfo: Triple<InetAddress?, InetAddress?, Int?> = Triple(wtGroupIp, wtLocalIp, wtGroupServerPort)

        while (scope.isActive) {
            notifyOfChange(p2pInfo, Triple(wtGroupIp, wtLocalIp, wtGroupServerPort))
            p2pInfo = Triple(wtGroupIp, wtLocalIp, wtGroupServerPort)

            internalMaintenance()

            val event = mainLoopInbox.await(cadence.milliseconds)

            if (!checkWifiPermission()) {
                logd(tag, "Not enough Wifi permissions. Requesting.")
                requestWifiPermission()
                continue
            }

            if (restartChannel()) {
                logd(tag, "Restart channel")
                break
            }

            when (event) {
                is MailboxData.Message -> {
                    processEvents(event.value)
                }
                MailboxData.Timeout -> {
                    processEventTimeout()
                }
            }
        }

        stop()
    }

    suspend fun processEvents(event: WTWifiEvent) {
        val tag = "processEvents/${randomString(2u)}"

        when (event) {
            WTWifiEvent.WTWifi.GroupInfoChanged -> {
                val newGroup = if (wtWifi.isGroupFormed) {
                    requestGroupInfo()
                } else {
                    removeGroup()
                    connectToDevice = null
                    null
                }
                logd(tag, "WTWifi group info changed(1): groupFormed: ${wtWifi.isGroupFormed} transition: ${wtWifiGroupInfo?.owner?.deviceName} -> ${newGroup?.owner?.deviceName}")
                wtWifi = wtWifi.transition(
                    event,
                    groupInfo = newGroup)

            }
            WTWifiEvent.P2p.WifiDisabled -> {
                wtWifi = wtWifi.transition(event)
            }
            WTWifiEvent.P2p.WifiEnabled -> {
                logd(tag, "P2P state changed to enabled")
                wtWifi = wtWifi.transition(
                    event,
                    thisDevice = requestDeviceInfo())
            }
            WTWifiEvent.P2p.PeersChanged -> {
                logd(tag, "P2P peers changed")
                updatePeersInfo(requestPeersInfo())
            }
            WTWifiEvent.P2p.ConnectionChanged -> {
                val p2pInfo = requestConnectionInfo()
                logd(tag, "P2P Connection changed: groupFormed: ${p2pInfo?.groupFormed} isGroupOwner: ${p2pInfo?.isGroupOwner}")
                wtWifi = wtWifi.transition(
                    event,
                    p2pInfo = p2pInfo
                )
                if (null == p2pInfo) {
                    updateP2pInfo(null)
                }
                mainLoopInbox.send(WTWifiEvent.WTWifi.GroupInfoChanged)
            }
            WTWifiEvent.P2p.ThisDeviceChanged -> {
                logd(tag, "P2P this device changed")
                wtWifi = wtWifi.transition(
                    event,
                    thisDevice = requestDeviceInfo())
                mainLoopInbox.send(WTWifiEvent.WTWifi.GroupInfoChanged)
            }
            is WTWifiEvent.P2p.TxtRecordListener -> {
                logd(tag, "P2P Process TxtRecordListener info")
                dnsSdTxtRecordListener(event.fullDomain, event.record, event.device)
            }
            is WTWifiEvent.P2p.ServiceResponseListener -> {
                logd(tag, "P2P Process ServiceResponseListener info")
                dnsSdServiceResponseListener(event.instanceName, event.registrationType, event.resourceType)
            }
            else -> {
                logd(
                    tag, "Unprocessed WifiD Event: ${event.toString()}")
            }
        }
    }

    fun internalMaintenance() {
        val tag = "internalMaintenance/${randomString(2u)}"
        logd(
            tag, "\n\tthisDevice: ${thisDevice?.deviceName}" +
                    "\n\tisWifiP2pEnabled: $wifiP2pEnable" +
                    "\n\twtIsGroupFormed: $wtIsGroupFormed" +
                    "\n\tdiscoveryCountdown: $discoveryCountdown" +
                    "\n\tconnectCountdown: $connectCountdown" +
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

    suspend fun processEventTimeout() {
        val tag = "processEventTimeout/${randomString(2u)}"

        if (!checkWifiPermission()) {
            logd(tag, "Not enough Wifi permissions. Requesting.")
            requestWifiPermission()
            return
        }

        if (restartChannel()) {
            logd(tag, "Restart channel")
            return
        }

        if (connectCountdown > 0) connectCountdown--

        if (wifiP2PEngineOk()) {
            updatePeers()
        }

        wifiP2PEngineFailCoolDown()
        restartChannelCountdown()
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
        clearAllServices()
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
        val divider = 5
        val tag = "updatePeers/${randomString(2u)}"

        logd(tag, "Entry")
        logd(
            tag,
            "deviceName = $deviceUid localIp = $wtLocalIp failCoolDown: $failCooldown wifiP2pEnable: $wifiP2pEnable"
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
            "discoverPeersJob deviceName = $deviceUid failCoolDown: $failCooldown thisDevice: ${thisDevice?.deviceName}"
        )
        discoverPeersJob()

        logd(
            tag,
            "connectToPeers deviceName = $deviceUid failCoolDown: $failCooldown"
        )
        connectToPeers()

        logd(tag, "Exit")
    }

    suspend fun discoverPeersJob() {
        val tag = "discoverPeersJob/${randomString(2u)}"

        logd(
            tag,
            "Entry isWifiP2pEnabled: $wifiP2pEnable resetPeersDiscovery: ${peersDiscoveryState()} discoveryCountdown: $discoveryCountdown"
        )

        if (wifiP2pEnable) {
            if (peersDiscoveryState()) {
                serviceDiscoveryActive(true)

                if (wtIsGroupFormed && !wtIsGroupOwner) {
                    removeLocalService()
                }

                if (wtIsGroupOwner || !wtIsGroupFormed) {
                    addLocalService(removeFirst = true)
                }

                if (!wtIsGroupFormed || (null == wtGroupServerPort)) {
                    addServiceRequest(removeFirst = true)
                    /* discoverServices() */
                }
            }

            if (serviceDiscoveryActive() &&
                (!wtIsGroupFormed || (null == wtGroupServerPort))
            ) {
                discoverServices()
            }

            serviceDiscoveryCountdown()

            logd(
                tag,
                "isGroupOwner: ${wtIsGroupOwner}, discoveryCountdown: $discoveryCountdown resetPeersDiscovery: ${peersDiscoveryState()} " +
                        "peers: ${
                            directWifiPeers.values.joinToString(" ") { device ->
                                "${device.name}/${device.address}/${device.age}"
                            }
                        }"
            )
        }
    }

    fun updatePeersInfo(newList: GenericList<WifiP2pDevice>) {
        val tag = "updatePeersInfo/${randomString(2u)}"
        var change = false

        var str = ""
        directWifiPeers.forEach { (dUid, device) ->
            if (dUid != device.uniqueWifiId()) {
                throw (Error("$tag directWifiPeers Inconsistent Entry: $dUid != ${device.uniqueWifiId()}"))
            }
            device.age++
            if (device.age > device.maxAge &&
                device.directWifiConnection == ConnectionStatus.Connected
            ) {
                device.directWifiConnection = ConnectionStatus.AgedOut
            }

            val newService = directWifiServices[dUid]?.copy()
            if (null != newService && device.wtServiceInfo != newService) {
                device.wtServiceInfo = newService

                directWifiServices.remove(dUid)
                newWTDevice(true)
                connectCountdown = ConnectCountdown
            }
            str += "[${dUid} ${thisDevice?.deviceName} ${device.name}] "
        }
        logd(tag, "directWifiPeers: $str")

        if (newList.isEmpty()) {
            logd(tag, "No New Peers.")
        } else {
            logd(tag, "Got New Peers: " +
                    newList.joinToString(" ") { device ->
                        "${device.deviceName}/${device.deviceAddress}"
            })
            change = true
        }

        while (newList.isNotEmpty()) {
            val wd = newList.removeFirst()
            val wdId = wd.uniqueWifiId()

            logd(
                tag,
                "ProcessNew Peer: $wdId walkieTalkie service: ${directWifiServices[wd.uniqueWifiId()]}"
            )

            /**********************
             * This cannot happen *
             **********************/
            if (wdId == deviceUid) {
                logd(tag, "Error: Got Self $wdId as Peer")
                throw (Exception("Error: Got Self $wdId as Peer"))
            }

            logd(tag, "directWifiPeersN.isNotEmpty ${thisDevice?.deviceName}: ${wd.deviceName}")
            if (thisDevice?.deviceName == wd.deviceName) {
                logd(tag, "wd.deviceName == ${thisDevice?.deviceName}: ${wd.deviceName}")
                throw (NotImplementedError("wd.deviceName == ${thisDevice?.deviceName}: ${wd.deviceName}"))
            }

            if (null == directWifiPeers[wdId]) {
                logd(tag, "Got new peer: $wdId")
                if (wd.isGroupOwner && wtGroupOwner?.uniqueWifiId() == wdId) {
                    directWifiPeers[wdId] = wtGroupOwner!!
                } else {
                    directWifiPeers[wdId] = WTWifiDirectPeerInfo(wd)
                }
                directWifiPeers[wdId]?.age = 0
            } else {
                if (directWifiPeers[wdId]?.isGroupOwner != wd.isGroupOwner) {
                    logd(
                        tag,
                        "New Peer is GO/Non GO? ${directWifiPeers[wdId]?.isGroupOwner} ${wd.isGroupOwner}"
                    )
                    directWifiPeers[wdId]?.isGroupOwner = wd.isGroupOwner
                }
            }
        }

        logd(tag, "directWifiPeers: " +
                directWifiPeers.values.joinToString(" ") {
                    device -> "[${device.uniqueWifiId()} ${thisDevice?.deviceName} ${device.name}]"
                }
        )

        if (change) {
            channelSend(
                channelId = ChannelId.RCToWifi,
                scope,
                input = null,
                type = ChannelMessageType.RCWifiBroadcastReceiver
            )
        }
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
                restartChannelCountdown(true)
                peersDiscoveryState(true)
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
                if (null != wtLocalIp) {
                    restartChannelCountdown(true)
                    peersDiscoveryState(true)
                }
            }
        }
    }

    fun updateP2pInfo(wtWifiP2pInfoN: WifiP2pInfo?) {
        val tag = "updateP2pInfo/${randomString(2u)}"

        logd(tag, "Entry")

        wtWifiP2pInfo?.logD(tag)
        wtWifiP2pInfoN?.logD(tag)

        if (null != wtWifiP2pInfo?.groupOwnerAddress &&
            null == wtWifiP2pInfoN?.groupOwnerAddress
        ) {
            logd(tag, "Lost P2P Connection/Info")
            directWifiPeers.clear()
            directWifiServices.clear()
            connectToDevice = null
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
                    "\n\t\tconnectCountdown: $connectCountdown" +
                    "\n\t\tconnectingAllowed: ${connectingAllowed()}" +
                    "\n\t\tdirectWifiPeers: ${directWifiPeers.keys}" +
                    "\n\t\tdiscoverPeersProcessActive: ${serviceDiscoveryActive()}"
        ).also { c++ }

        if (wifiP2pEnable &&
            connectingAllowed()
        ) {
            val tmpPeers: MutableMap<String, WTWifiDirectPeerInfo> = directWifiPeers.toMutableMap()
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
                groupOwner.wtService() &&
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
                //delay((delay / divider).milliseconds)
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

        logd(tag, "Entry: $wtLocalServiceRecord")

        val cacheServiceRecord = wtLocalServiceRecord?: run { return }

        when (val res = wtWifiDirect?.removeLocalService(
            instanceName,
            serviceType,
            cacheServiceRecord
        )) {
            is WTWifiDirectResult.Success -> {
                logd(TAGKClass, tag, "Success removing local service")
                wtLocalServiceRecord = null
            }

            is WTWifiDirectResult.WifiP2pError -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed removing local service: ${res.errStr}"
                )
                wifiP2PEngineFailCoolDown(true)
            }

            else -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed removing local service: Unknown Error"
                )
                wifiP2PEngineFailCoolDown(true)
            }
        }
    }

    suspend fun addLocalService(removeFirst: Boolean = false) {
        val tag = "addLocalService/${randomString(2u)}"
        val instanceName = WT_SERVICE_WALKIETALKIE /* + "." + deviceUid() */
        /* val serviceType = "_presence._tcp" */
        val serviceType = WT_SERVICE_WALKIETALKIE
        val record: Map<String, String> = mapOf(
            WT_SERVICE_WALKIETALKIE to WT_SERVICE_WALKIETALKIE,
            WT_SERVICE_ID to deviceId,
            WT_SERVICE_UNIQUE to deviceUnique,
            /* WT_SERVICE_RND to deviceUid(), */
            WT_SERVICE_RND to randomString(8U),
            WT_SERVICE_LOCAL_SERVER_PORT to wtLocalServerPort!!.toString()
        )

        logd(tag, "Entry: $wtLocalServiceRecord")

        if (removeFirst) {
            removeLocalService()
        }

        if (null == wtLocalServiceRecord) {
            wtLocalServiceRecord = record
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
                wifiP2PEngineFailCoolDown(true)
            }
            else -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed adding local service: Unknown Error"
                )
                wifiP2PEngineFailCoolDown(true)
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
                wifiP2PEngineFailCoolDown(true)
            }
            else -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed removing service request: Unknown Error"
                )
                wifiP2PEngineFailCoolDown(true)
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
                wifiP2PEngineFailCoolDown(true)
            }
            else -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed adding service request: Unknown Error"
                )
                wifiP2PEngineFailCoolDown(true)
            }
        }
    }

    fun dnsSdTxtRecordListener (fullDomain: String,
                                record: Map<String, String>,
                                device: WifiP2pDevice) {
        val tag = "dnsSdTxtRecordListener/${randomString(2u)}"

        logd(tag,
            "DnsSdTxtRecord available: [$fullDomain] [$record] [${device.deviceName}]"
        )
        if (record[WT_SERVICE_WALKIETALKIE] != null && record[WT_SERVICE_WALKIETALKIE] == WT_SERVICE_WALKIETALKIE) {
            if (null == directWifiServices[device.uniqueWifiId()]) {
                directWifiServices[device.uniqueWifiId()] =
                    WTWifiDirectServiceInfo(
                        id = record[WT_SERVICE_ID],
                        unique = record[WT_SERVICE_UNIQUE],
                        rnd = record[WT_SERVICE_RND],
                        localServerPort = record[WT_SERVICE_LOCAL_SERVER_PORT]
                    )
            } else {
                directWifiServices[device.uniqueWifiId()]?.id = record[WT_SERVICE_ID]
                directWifiServices[device.uniqueWifiId()]?.unique = record[WT_SERVICE_UNIQUE]
                directWifiServices[device.uniqueWifiId()]?.rnd = record[WT_SERVICE_RND]
                directWifiServices[device.uniqueWifiId()]?.localServerPort =
                    record[WT_SERVICE_LOCAL_SERVER_PORT]
            }
            logd(
                tag,
                "directWifiServices[${device.uniqueWifiId()}] = " +
                        "${directWifiServices[device.uniqueWifiId()]?.id} " +
                        "${directWifiServices[device.uniqueWifiId()]?.unique} " +
                        "${directWifiServices[device.uniqueWifiId()]?.rnd} " +
                        "${directWifiServices[device.uniqueWifiId()]?.localServerPort}"
            )
        }
    }

    fun dnsSdServiceResponseListener(instanceName: String,
                                     registrationType: String,
                                     resourceType: WifiP2pDevice) {
        val tag = "dnsSdServiceResponseListener/${randomString(2u)}"
        logd(tag,
            "onBonjourServiceAvailable [$instanceName] [$registrationType] [${resourceType.deviceName}]"
        )
        if (WT_SERVICE_WALKIETALKIE == instanceName.subSequence(WT_SERVICE_WALKIETALKIE.indices)) {
            logd(
                TAGKClass,
                tag,
                "device.deviceName: ${resourceType.deviceName} resourceType.uniqueWifiId: ${resourceType.uniqueWifiId()} ${directWifiPeers[resourceType.uniqueWifiId()]?.name}"
            )
            if (null == directWifiServices[resourceType.uniqueWifiId()]) {
                directWifiServices[resourceType.uniqueWifiId()] = WTWifiDirectServiceInfo()
            }
        }
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
        peersDiscoveryState(true)
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
                wifiP2PEngineFailCoolDown(true)
            }
            else -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed stopping peers discovery: Unknown Error"
                )
                wifiP2PEngineFailCoolDown(true)
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
                wifiP2PEngineFailCoolDown(true)
            }
            else -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed starting discovering services: Unknown Error"
                )
                wifiP2PEngineFailCoolDown(true)
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
                wifiP2PEngineFailCoolDown(true)
            }
            else -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed clearing service requests: Unknown Error"
                )
                wifiP2PEngineFailCoolDown(true)
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
                wifiP2PEngineFailCoolDown(true)
            }
            else -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed clearing local service: Unknown Error"
                )
                wifiP2PEngineFailCoolDown(true)
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
                        WTWifiEvent.P2p.WifiDisabled
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

        logd("Entry")

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

        logd("Entry")

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