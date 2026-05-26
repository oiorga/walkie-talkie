package walkie.wifidirect

import android.annotation.SuppressLint
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pConfig.GROUP_OWNER_INTENT_MAX
import android.net.wifi.p2p.WifiP2pConfig.GROUP_OWNER_INTENT_MIN
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Parcelable
import android.os.SystemClock.uptimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import walkie.talkie.api.wtsystem.NodeIdInt
import walkie.util.CallbackResult
import walkie.util.api.ChannelId
import walkie.util.api.ChannelIdInt
import walkie.util.api.ChannelMessageType
import walkie.util.api.RemoteCallId
import walkie.util.api.RemoteCallMuxInt
import walkie.util.generic.ChannelMux
import walkie.util.generic.ChannelMuxInt
import walkie.util.generic.GenericList
import walkie.util.generic.RemoteCallMux
import walkie.util.generic.typedCall
import walkie.util.getInterfaceIpAddress
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString
import walkie.util.stringSum
import walkie.wifidirect.WTWifiDirectManager.Companion.ConnectCountdown
import walkie.wifidirect.WTWifiDirectManager.Companion.TAGKClass
import walkie.wifidirect.WTWifiDirectServiceInfo.Companion.WT_SERVICE_ID
import walkie.wifidirect.WTWifiDirectServiceInfo.Companion.WT_SERVICE_LOCAL_SERVER_PORT
import walkie.wifidirect.WTWifiDirectServiceInfo.Companion.WT_SERVICE_RND
import walkie.wifidirect.WTWifiDirectServiceInfo.Companion.WT_SERVICE_UNIQUE
import walkie.wifidirect.WTWifiDirectServiceInfo.Companion.WT_SERVICE_WALKIETALKIE
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

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
    val nodeId: NodeIdInt,
    val scope: CoroutineScope,
    var channel: WifiP2pManager.Channel? = null,
    private val _channelMux: ChannelMuxInt<Any, ChannelMessageType> = ChannelMux(),
    private val _remoteCallMux: RemoteCallMuxInt = RemoteCallMux()
) :
    ChannelMuxInt<Any, ChannelMessageType> by _channelMux,
    RemoteCallMuxInt by _remoteCallMux {

    companion object {
        const val TAG = "WTWiFiDirectManager"
        const val DiscoveryCountdown = 26
        const val ConnectCountdown = 1
        const val FailCooldown = 1
        const val RestartChannelTimeout = (DiscoveryCountdown * 2).toInt()
        const val DiscoveryVsAdvertisementRatio = (2F / 3F)
        val TAGKClass = WTWifiDirectManager::class
    }

    fun attachChannel(channel: Channel) {
        this.channel = channel
    }

    private val errToString = mapOf(
        WifiP2pManager.P2P_UNSUPPORTED to "P2P UNSUPPORTED",
        WifiP2pManager.ERROR to "INTERNAL ERROR",
        WifiP2pManager.BUSY to "WifiP2pManager BUSY"
    )

    var mainLoopJob: Job? = null

    val wifiP2pEnableInfo: AtomicReference<Boolean> = AtomicReference(false)
    val wifiP2pEnable: Boolean
        get() = wifiP2pEnableInfo.get()
    val wifiP2pEnabledNInfo: AtomicReference<Boolean> = AtomicReference(false)

    val directWifiPeers = mutableMapOf<String, WTWifiDirectPeerInfo>()

    /* Buba with removeLast(), addLast(...) starting with API 35.
    val directWifiPeersN = mutableListOf<WifiP2pDevice>()
    *
    * Quick and ugly workaround
    val directWifiPeersN = BlockingQueue<WifiP2pDevice>()
    */
    /* Maybe the real fix */
    val directWifiPeersN = GenericList<WifiP2pDevice>()
    val directWifiServices = mutableMapOf<String, WTWifiDirectServiceInfo>()

    val wtWifiP2pInfo: AtomicReference<WifiP2pInfo?> = AtomicReference(null)
    val wtWifiGroupInfo: AtomicReference<WifiP2pGroup?> = AtomicReference(null)
    val wtWifiP2pInfoN: AtomicReference<WifiP2pInfo?> = AtomicReference(null)
    val wtWifiGroupInfoN: AtomicReference<WifiP2pGroup?> = AtomicReference(null)
    val thisDeviceInfo: AtomicReference<WifiP2pDevice?> = AtomicReference(null)
    val thisDeviceNInfo: AtomicReference<WifiP2pDevice?> = AtomicReference(null)

    val thisDevice: WifiP2pDevice?
        get() = (thisDeviceInfo.get())

    var wtLocalServiceRecord: Map<String, String>? = null

    val wtLocalIp: InetAddress?
        get() = wtWifiGroupInfo.get()?.`interface`?.let { iFace ->
            getInterfaceIpAddress(iFace)
        }

    var wtLocalServerPort: Int? = null

    val wtGroupServerPort: Int?
        get() =
            (if (wtIsGroupOwner) (wtLocalServerPort)
            else if (wtIsGroupFormed) (directWifiPeers[wtGroupOwner?.uniqueWifiId()]?.wtServiceInfo?.localServerPort?.toInt())
            else (null))

    val wtIsGroupOwner: Boolean
        get () = let { _ ->
            return ((true == wtWifiP2pInfo.get()?.groupFormed) && (true == wtWifiP2pInfo.get()?.isGroupOwner))
        }

    val wtIsGroupFormed: Boolean
        get() = let { _ ->
            return (true == wtWifiP2pInfo.get()?.groupFormed)
        }

    val wtGroupIp: InetAddress?
        get() = wtWifiP2pInfo.get()?.groupOwnerAddress

    val wtGroupOwnerName: String?
        get() = (if (wtIsGroupOwner) thisDevice?.deviceName else wtWifiGroupInfo.get()?.owner?.deviceName)


    val wtGroupOwner: WTWifiDirectPeerInfo?
        get() = let { _ ->
            val p2pOwner = if (wtIsGroupOwner) thisDevice else wtWifiGroupInfo.get()?.owner
            directWifiPeers[p2pOwner?.uniqueWifiId()] ?: p2pOwner?.let { device -> WTWifiDirectPeerInfo(device) }
        }

    val deviceUid: String
        get() = nodeId.uid()

    val deviceId: String
        get() = nodeId.id()

    val deviceUnique: String
        get() = nodeId.unique()

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
        return (0 == discoveryCountdown.toInt())
    }

    fun serviceAdvAdd(): Boolean {
        return (0 == (discoveryCountdown.toInt() % 5) && !connectingAllowed())
    }

    fun serviceDiscoveryActive(enable: Boolean? = null): Boolean {
        val tag = "serviceDiscoveryActive/${randomString(2U)}"
        if (null != enable) {
            discoveryCountdown = (if (enable) DiscoveryCountdown else (DiscoveryCountdown * DiscoveryVsAdvertisementRatio).toInt())
            return enable
        }
        if (newWTDevice()) {
            logd(TAGKClass, tag,"NEWWTDevice")
            discoveryCountdown = (DiscoveryCountdown * DiscoveryVsAdvertisementRatio).toInt()
            newWTDevice(false)
        }
        val ret = ((true || !connectingAllowed()) &&
                !serviceAdvAdd() &&
                (0 == (discoveryCountdown.toInt() % 5)))
        logd(TAGKClass,
            tag,"discoveryCountdown: $discoveryCountdown " +
                    "wtIsGroupFormed: ${wtIsGroupFormed} " + ret)
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
        logd(tag, "restartChannelCountdown: $restartChannelCountdown wtLocalIp: $wtLocalIp wtGroupServerPort: $wtGroupServerPort yes: $yes")
        if (null != yes) {
            restartChannelCountdown = if (yes) (RestartChannelTimeout) else restartChannelCountdown
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
            true -> { FailCooldown }
            false -> { 0 }
            else -> { (if (failCooldown > 0) (failCooldown - 1) else 0) }
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

    fun onDeviceInfoAvailable(device: WifiP2pDevice?) {
        val tag = "onDeviceInfoAvailable/${randomString(2U)}"

        if (wifiP2pEnabledNInfo.get()) {
            thisDeviceNInfo.getAndSet(device)
        }

        logd(
            tag, "onDeviceInfoAvailable: " +
                    "\n\t\t\t\tisWifiP2pEnabled: $wifiP2pEnable " +
                    "\n\t\t\t\tGO: ${thisDevice?.isGroupOwner}" +
                    "\n\t\t\t\tdeviceName: ${thisDevice?.deviceName}" +
                    "\n\t\t\t\tdeviceAddress${thisDevice?.deviceAddress}"
        )
    }

    override suspend fun channelOnReceive(
        channelId: ChannelIdInt,
        inputType: ChannelMessageType?,
        input: Any?
    ) {
        val tag = "channelOnReceive/${randomString(2U)}"

        logd(tag,"channelId: $channelId inputType: $inputType")
        when (channelId) {
            ChannelId.RCToWifi -> {
                when (inputType) {
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
                        throw (NotImplementedError("$tag: channelOnReceive: channelId: $channelId: inputType: $inputType Not Implemented "))
                    }
                }
            }
            else -> {
                throw (NotImplementedError("$tag: channelOnReceive: channelId: $channelId: inputType: $inputType Not Implemented "))
            }
        }
    }

    fun errToString(errCode: Int): String {
        return (errToString[errCode] ?: "Unknown Error")
    }

    fun resetData() {
        val tag = "resetData/${randomString(2u)}"

        logd(TAGKClass, tag, "Entry")

        channelSend(
            ChannelId.RCToComm,
            ChannelMessageType.RCGroupInfo,
            Triple(null, null, null))

        mainLoopJob = null
        wifiP2pEnableInfo.getAndSet(false)
        wifiP2pEnabledNInfo.getAndSet(false)

        directWifiPeers.clear()
        directWifiPeersN.clear()
        directWifiServices.clear()
        wtWifiP2pInfo.getAndSet(null)
        wtWifiGroupInfo.getAndSet(null)
        wtWifiP2pInfoN.getAndSet(null)
        wtWifiGroupInfoN.getAndSet(null)

        connectToDevice = null
        wtLocalServiceRecord = null

        newWTDevice(false)

        /* indicate whether the discovery process is active */
        peersDiscoveryState(true)
        connectCountdown = ConnectCountdown

        restartChannelCountdown = RestartChannelTimeout

        wifiP2PEngineFailCoolDown(false)
    }

    fun checkWifiDPermission(): Boolean {
        val tag = "checkWifiDPermission/${randomString(2u)}"
        logd(tag, "checkWifiDPermission perm = ${remoteCall(RemoteCallId.RCCheckWifiDPermission)}")
        return (true == typedCall<Boolean>(RemoteCallId.RCCheckWifiDPermission))
    }

    suspend fun requestPeersInfo(): MutableList<WifiP2pDevice> {
        val tag = "requestPeersInfo/${randomString(2u)}"
        var peerList: WifiP2pDeviceList
        var str = ""

        if (checkWifiDPermission()) {
            peerList = awaitP2pRequest { callback ->
                manager.requestPeers(channel) { peerList ->
                    callback(peerList)
                }
            }

            peerList.deviceList.forEach { wifiDevice ->
                str += wifiDevice.deviceName + "/${wifiDevice.deviceAddress}" + " "
            }
            logd(
                TAGKClass,
                tag,
                "NEW Peers: $str"
            )
            directWifiPeersN.addAll(peerList.deviceList)
        }

        return directWifiPeersN
    }

    suspend fun requestDeviceInfo(): WifiP2pDevice? {
        val tag = "requestDeviceInfo/${randomString(2u)}"
        var device: WifiP2pDevice? = null

        if (checkWifiDPermission()) {
            device = awaitP2pRequest<WifiP2pDevice?> { callback ->
                manager.requestDeviceInfo(channel!!) { device ->
                    callback(device)
                }
            }
        }

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
        var groupInfo: WifiP2pGroup? = null
        var str = ""

        logd(tag, "Entry+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++")

        if (checkWifiDPermission()) {
            groupInfo = awaitP2pRequest { callback ->
                manager.requestGroupInfo(channel) { groupInfo ->
                    callback(groupInfo)
                }
            }

            if (null != groupInfo) {
                groupInfo.clientList.forEach { dev ->
                    str += dev.deviceName + " "
                }

                logd(
                    tag,
                    "Callback:"  +
                            "\n Owner: [${groupInfo.owner.deviceName}] [$deviceId] [${groupInfo.owner.deviceAddress}]" +
                            "\n Passwd: ${groupInfo.passphrase}" +
                            "\n GO: ${groupInfo.isGroupOwner}" +
                            "\n Interface Name: ${groupInfo.`interface`} / ${
                                getInterfaceIpAddress(
                                    groupInfo.`interface`
                                )
                            }" +
                            "\n ClientList: $str"
                )
            } else {
                logd(tag, "requestGroupInfo got NULL")
            }

            if (wtWifiGroupInfoN.get() != groupInfo) wtWifiGroupInfoN.getAndSet(groupInfo)
        }

        logd(
            tag,
            "\n Owner: [${groupInfo?.owner?.deviceName}] [$deviceId] [${groupInfo?.owner?.deviceAddress}]" +
                    "\n Passwd: ${groupInfo?.passphrase}" +
                    "\n GO: ${groupInfo?.isGroupOwner}" +
                    "\n Interface Name: ${groupInfo?.`interface`} / ${groupInfo?.`interface`?.let { getInterfaceIpAddress(it) }}"
        )
        logd(tag, "Exit-----------------------------------------------------------------")

        return groupInfo
    }

    suspend fun removeGroup() {
        val tag = "removeGroup/${randomString(2u)}"
        var reasonToStr = "Success"

        logd(tag, "Entry ++++++++++++++++++++++++++++++++++++++++++++++++++++++")

        val group: WifiP2pGroup? =
            if (wtWifiGroupInfo.get() != null) { wtWifiGroupInfo.get() }
            else if (wtWifiGroupInfoN.get() != null) { wtWifiGroupInfoN.get() }
            else {
                logd(tag, "NO Group------------------------------------------------------")
                return
            }

        logd(
            tag,
            " $tag:" +
                    "\n Owner: ${group?.owner?.deviceName} / ${group?.owner?.deviceAddress}" +
                    "\n Passwd: ${group?.passphrase}" +
                    "\n GO: ${group?.isGroupOwner}" +
                    "\n Interface Name: ${group?.`interface`} / ${getInterfaceIpAddress(group!!.`interface`)}"
        )

        when (val res = awaitP2pAction { listener ->
            manager.removeGroup(channel, listener)
        }) {
            is CallbackResult.Success -> {
                logd(
                    TAGKClass,
                    tag,
                    "onSuccess"
                )
                wtWifiGroupInfo.getAndSet(null)
                wtWifiGroupInfoN.getAndSet(null)
                wtWifiFailure("$tag/$reasonToStr")
            }
            is CallbackResult.Failure -> {
                reasonToStr = errToString(res.reason!!)

                logd(
                    TAGKClass,
                    tag,
                    "onFailure: Reason(${res.reason}): $reasonToStr"
                )
                wtWifiFailure("$tag/$reasonToStr")
                wtWifiGroupInfo.getAndSet(null)
                wtWifiGroupInfoN.getAndSet(null)

                wifiP2PEngineFailCoolDown(true)
            }
        }

        logd("Exit--------------------------------------------------------")
    }

    private fun connectedToGroup(): Boolean {
        val tag = "connectedToGroup/${randomString(2U)}"
        logd(tag,
            "wtLocalIp: $wtLocalIp wtGroupIp: $wtGroupIp wtIsGroupFormed: ${wtIsGroupFormed} ${(null != wtLocalIp) && wtIsGroupFormed && (null != wtGroupIp)}")
        return ((null != wtLocalIp) && wtIsGroupFormed && (null != wtGroupIp))
    }

    suspend fun connect(device: WifiP2pDevice,
                        config: WifiP2pConfig = WifiP2pConfig().apply {
                            deviceAddress = device.deviceAddress
                            groupOwnerIntent = GROUP_OWNER_INTENT_MIN
                            wps.setup = WpsInfo.PBC }
    ): ConnectionStatus {
        val tag = "connectTo/${randomString(2u)}"
        var ret: ConnectionStatus = ConnectionStatus.Null
        var reasonToStr = "Success"

        logd(tag, "Connecting to ${device.uniqueWifiId()}")
        if (checkWifiDPermission()) {
            try {
                when (val res = awaitP2pAction { listener ->
                    manager.connect(
                        channel,
                        config,
                        listener
                    )
                }) {
                    is CallbackResult.Success -> {
                        logd(
                            TAGKClass,
                            tag,
                            "onSuccess: " +
                                    "Connect to: ${device.uniqueWifiId()}  ${device.deviceAddress} GO = ${device.isGroupOwner} Success"
                        )
                        ret = ConnectionStatus.InProgress
                        wtWifiFailure("$tag/$reasonToStr")
                    }
                    is CallbackResult.Failure -> {
                        reasonToStr = errToString(res.reason!!)
                        logd(
                            TAGKClass,
                            tag,
                            "onFailure: Connect to: ${device.uniqueWifiId()}  ${device.deviceAddress} GO = ${device.isGroupOwner} Fail reason(${res.reason}): $reasonToStr"
                        )
                        wtWifiFailure("$tag/$reasonToStr")
                        ret = ConnectionStatus.Fail
                    }
                }
            } catch (e: Exception) {
                throw (e)
            } catch (t: Throwable) {
                throw (t)
            }
        }
        return ret
    }

    suspend fun cancelConnect() {
        val tag = "cancelConnect/${randomString(2u)}"
        var reasonToStr = "Success"

        logd(tag, "Cancelling Connecting process")

        when (val res = awaitP2pAction { listener ->
            manager.cancelConnect(channel, listener)
        }) {
            is CallbackResult.Success -> {
                logd(TAGKClass, tag, "onSuccess:")
                wtWifiFailure("$tag/$reasonToStr")
            }
            is CallbackResult.Failure -> {
                reasonToStr = errToString(res.reason!!)
                logd(
                    TAGKClass, tag, "onFailure: Reason(${res.reason}): $reasonToStr"
                )
                wtWifiFailure("$tag/$reasonToStr")
            }
        }
    }

    suspend fun connectTo(device: WTWifiDirectPeerInfo): ConnectionStatus {
        val tag = "connectTo/${randomString(2u)}"
        var c = 0

        logd(tag, "($c)connect to ${device.uniqueWifiId()} ${device.wtService()} ${device.directWifiConnection}").also { c++ }

        if (device.name == deviceUid) {
            logd(tag, "($c)Connecting to self: ${device.name}/${device.address} == ${deviceUid} ???").also { c++ }
            throw(Exception("$tag Connecting to self: ${device.name}/${device.address} == ${deviceUid} ???"))
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
                    groupOwnerIntent = (if (device.isGroupOwner || (deviceUnique < device.uniqueWifiId())) GROUP_OWNER_INTENT_MIN else (1 + Random.nextInt(GROUP_OWNER_INTENT_MAX / 2)))
                    /* groupOwnerIntent = GROUP_OWNER_INTENT_MIN */
                    wps.setup = WpsInfo.PBC
                }
                if (!device.wtService()) device.directWifiConnection = ConnectionStatus.NoWTService

                if (device.wtService() && wifiP2PEngineOk()) {
                    device.directWifiConnection = connect(device.p2pInfo, config)
                    when (device.directWifiConnection) {
                        ConnectionStatus.InProgress -> {
                            logd(TAGKClass, tag,"($c)Connect to: ${device.name}  ${device.address} GO = ${device.isGroupOwner} Success").also { c++ }
                            device.cipInit()
                        }
                        ConnectionStatus.Fail -> {
                            logd(TAGKClass, tag,"($c)Connect to: ${device.name} ${device.address} GO = ${device.isGroupOwner} Failed").also { c++ }
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
                if (connectedToGroup()) {
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
                    "($c)Connect to: ${device.name} ${device.address} GO = ${device.isGroupOwner} is InProgress connectedToGroup: ${connectedToGroup()}",
                ).also { c++ }
                if (!connectedToGroup()) {
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
                //newWTDevice(false)
            }
            ConnectionStatus.Connected -> {
                logd(
                    TAGKClass,
                    tag,
                    "($c)Connect to: ${device.name} ${device.address} GO = ${device.isGroupOwner} is Already Connected").also { c++ }

                if (!connectedToGroup()) {
                    device.directWifiConnection = ConnectionStatus.Retry
                    logd(
                        TAGKClass,
                        tag,
                        "($c)Connect to: ${device.name} ${device.address} GO = ${device.isGroupOwner} is Connected but there's an anomaly?: " +
                                "wtLocalIp: $wtLocalIp wtIsGroupFormed: $wtIsGroupFormed wtGroupIp: $wtGroupIp").also { c++ }
                }
            }
            else -> {
                logd(
                    TAGKClass,
                    tag,
                    "($c)Connect to: Connection Status ${device.directWifiConnection} NOT handled").also { c++ }
                throw(NotImplementedError("Connect to: Connection Status ${device.directWifiConnection} NOT handled"))
            }
        }

        logd(tag, "($c)Exit connect to ${device.uniqueWifiId()} ${device.wtService()} ${device.directWifiConnection}").also { c++ }

        return device.directWifiConnection
    }

    suspend fun requestConnectionInfo(): WifiP2pInfo? {
        val tag = "requestConnectionInfo/${randomString(2u)}"

        logd(
            TAGKClass,
            tag,
            "Entry+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
        )

        wtWifiP2pInfoN.get()?.logD(tag)

        val wifiP2pInfo: WifiP2pInfo = awaitP2pRequest { callback ->
            manager.requestConnectionInfo(channel) { info ->
                callback(info)
            }
        }
        logd(
            TAGKClass,
            tag,
            "Entry: ${wifiP2pInfo.groupFormed}  ${wifiP2pInfo.isGroupOwner} ${wifiP2pInfo.groupOwnerAddress}"
        )

        if (wifiP2pInfo != wtWifiP2pInfoN.get()) {
            wtWifiP2pInfoN.compareAndSet(wtWifiP2pInfoN.get(), wifiP2pInfo)
        }

        wtWifiP2pInfoN.get()?.logD(tag)
        logd(
            TAGKClass,
            tag,
            "Exit--------------------------------------------------------------"
        )

        return wifiP2pInfo
    }
}

suspend fun WTWifiDirectManager.mainLoop(delay: Long = 1000L) {
    val tag = "scanPeers/${randomString(2u)}"
    val s = WTWiFiDirectStatic.INSTANCE
    val divider = 5
    var c = (Random.nextInt(delay.toInt()) % 5)
    var oldP2pInfo: Triple<InetAddress?, InetAddress?, Int?> = Triple(null, null, null)

    logd(tag, "Entry: ${s.scanPeersS}")

    if (s.scanPeersS) return
    s.scanPeersS = true

    channelSend(
        ChannelId.RCToWTActivity,
        ChannelMessageType.RCWifiDebugInfoMessage,
        wtWifiDirectInfo()
    )

    val startDelay = delay * (1 + (uptimeMillis() + stringSum(deviceUid)) % 5)
    logd(tag, "Delaying $startDelay millis")
    delay(startDelay/divider)
    cancelConnect()
    delay(startDelay/divider)
    val stallGroup = requestGroupInfo()
    stallGroup?.logD(tag)
    delay(startDelay/divider)
    clearAllServices()
    delay(startDelay/divider)
    removeGroup()
    delay(startDelay/divider)
    clearAllServices()
    delay(startDelay/divider)

    channelSend(
        ChannelId.RCToComm,
        ChannelMessageType.RCGroupInfo,
        Triple(null, null, null))

    wtServicesInit()

    logd(tag, "Entering Main Loop: ${s.scanPeersS}")
    while (true) {
        delay(c * delay/divider)
        if (!checkWifiDPermission()) {
            remoteCall(RemoteCallId.RCRequestWifiDPermission)
            continue
        }

        logd(tag, "thisDevice: ${thisDevice?.deviceName}" +
                "\nisWifiP2pEnabled: $wifiP2pEnable" +
                "\nwtIsGroupFormed: $wtIsGroupFormed" +
                "\ndiscoveryCountdown: $discoveryCountdown" +
                "\nchannelCountdown: ${channelCountdown()}" +
                "\ndeviceName = ${deviceId}/${deviceUid}" +
                "\nlocalIp = ${this.wtLocalIp}")

        if (restartChannel()) {
            logd(tag, "Restart CHANNEL")
            break
        }

        wifiP2PEngineFailCoolDown()

        if (connectCountdown > 0) connectCountdown--

        if (wifiP2PEngineOk()) {
            updatePeers(delay)
        } else {
            delay(4 * delay / 5)
        }

        val tP2pInfo = Triple(wtGroupIp, wtLocalIp, wtGroupServerPort)
        notifyOfChange(oldP2pInfo, tP2pInfo)
        oldP2pInfo = tP2pInfo
        
        channelSend(
            ChannelId.RCToWTActivity,
            ChannelMessageType.RCWifiDebugInfoMessage,
            wtWifiDirectInfo()
        )

        c = (restartChannelCountdown() + c + 1) % divider
    }

    stop()
}

suspend fun WTWifiDirectManager.updatePeers(delay: Long = 1000L) {
    val divider = 5
    val tag = "updatePeers/${randomString(2u)}"
    var count = 0

    logd(tag, "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++")
    logd(tag, "($count) deviceName = $deviceUid localIp = $wtLocalIp failCoolDown: $failCooldown wifiP2pEnable: ${wifiP2pEnable}/${wifiP2pEnabledNInfo.get()}/${wifiP2pEnable}").also { count++ }

    updateThisDevice()

    if (wifiP2pEnable) {
        delay(1 * delay)
        logd(tag, "discoverPeersJob ($count) deviceName = $deviceUid failCoolDown: $failCooldown thisDevice: ${thisDevice?.deviceName}").also { count++ }
        discoverPeersJob(delay / divider)

        logd(tag, "updateP2pInfo ($count) deviceName = $deviceUid failCoolDown: $failCooldown thisDevice: ${thisDevice?.deviceName}").also { count++ }
        updateP2pInfo()

        if (null != thisDevice) {
            delay(delay / divider)
            logd(tag, "updatePeersInfo ($count) deviceName = $deviceUid failCoolDown: $failCooldown").also { count++ }
            updatePeersInfo()

            delay(delay / divider)
            logd(tag, "updateGroupInfo ($count) deviceName = $deviceUid failCoolDown: $failCooldown").also { count++ }
            updateGroupInfo()

            delay(delay / divider)
            logd(tag, "connectToPeers ($count) deviceName = $deviceUid failCoolDown: $failCooldown").also { count++ }
            connectToPeers(delay / divider)
        }

        delay(delay / divider)
    }
    logd(tag, "--------------------------------------------------------------")
}

fun WTWifiDirectManager.updateThisDevice() {
    val tag = "updateThisDevice/${randomString(2u)}"

    if (wifiP2pEnableInfo.get() != wifiP2pEnabledNInfo.get()) {
        wifiP2pEnableInfo.getAndSet(wifiP2pEnabledNInfo.get())
    }

    if (wifiP2pEnableInfo.get() && thisDeviceInfo.get() != thisDeviceNInfo.get()) {
        thisDeviceInfo.getAndSet(thisDeviceNInfo.get())
    }
}

suspend fun WTWifiDirectManager.discoverPeersJob(delay: Long = 100L) {
    val tag = "discoverPeersJob/${randomString(2u)}"

    logd(tag, "Entry isWifiP2pEnabled: $wifiP2pEnable resetPeersDiscovery: ${peersDiscoveryState()} discoveryCountdown: $discoveryCountdown")
    if (!checkWifiDPermission()) {
        logd(tag, "Not enough WIFI-D permissions.")
    } else if (wifiP2pEnable) {
        var str = ""

        if (peersDiscoveryState()) {
            serviceDiscoveryActive(true)

            if (wtIsGroupFormed && !wtIsGroupOwner) {
                removeLocalService()
                delay(delay)
            }

            if (wtIsGroupOwner || !wtIsGroupFormed) {
                addLocalService(removeFirst = true)
                delay(delay)
            }

            if (!wtIsGroupFormed || (null == wtGroupServerPort)) {
                addServiceRequest(removeFirst = true)
                delay(delay)
                discoverServices()
            }
            /* delay(delay) */
        }

        /*
        if ((wtIsGroupOwner() || !wtIsGroupFormed) && serviceAdvAdd()) {
            addLocalService(sync = true, removeFirst = true)
            delay(delay)
        } else {
            delay(delay)
        }
        */

        if (serviceDiscoveryActive() &&
            (!wtIsGroupFormed || (null == wtGroupServerPort))){
            discoverServices()
        } else {
            delay (delay)
        }

        serviceDiscoveryCountdown()

        directWifiPeers.forEach {(_, device) ->
            str += device.name + "/${device.address}/${device.age} "
        }

        logd(
            tag,
            "isGroupOwner: ${wtIsGroupOwner}, discoveryCountdown: $discoveryCountdown resetPeersDiscovery: ${peersDiscoveryState()} peers: $str"
        )
    }
}

suspend fun WTWifiDirectManager.updatePeersInfo () {
    val tag = "updatePeersInfo/${randomString(2u)}"
    var change = false

    var str = ""
    directWifiPeers.forEach { (dUid, device) ->
        if (dUid != device.uniqueWifiId()) {
            throw (Error("$tag directWifiPeers Inconsistent Entry: $dUid != ${device.uniqueWifiId()}"))
        }
        device.age++
        if (device.age > device.maxAge &&
            device.directWifiConnection == ConnectionStatus.Connected) {
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

    if (directWifiPeersN.isEmpty()) {
        logd(tag, "No New Peers.")
    } else {
        directWifiPeersN.forEach { device ->
            str += "[${device.deviceName}/${device.uniqueWifiId()}] "
        }
        logd(tag, "Got New Peers: $str")
        change = true
    }

    while (directWifiPeersN.isNotEmpty()) {
        val wd = directWifiPeersN.removeFirst()
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

    str = ""
    directWifiPeers.forEach { (_, device) ->
        str += "[${device.uniqueWifiId()} ${thisDevice?.deviceName} ${device.name}] "
    }
    logd(tag, "directWifiPeers: $str")

    if (change) {
        channelSend(
            channelId = ChannelId.RCToWifi,
            input = null,
            inputType = ChannelMessageType.RCWifiBroadcastReceiver)
    }
}

fun WTWifiDirectManager.updateGroupInfo() {
    val tag = "updateGroupInfo/${randomString(2u)}"
    var changed = false
    val p2pInfo = wtWifiP2pInfo.get()
    val oldGroup = wtWifiGroupInfo.get()
    val newGroup = wtWifiGroupInfoN.get()

    logd(tag, "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++")
    logd(
        tag,
        "\nFormed / Group Name / Owner / IP: $wtIsGroupFormed / ${newGroup?.networkName} / ${newGroup?.owner?.deviceName} / ${p2pInfo?.groupOwnerAddress}"
    )

    if (oldGroup != newGroup) changed = true

    wtWifiGroupInfo.getAndSet(newGroup)

    if (null == newGroup && changed) connectToDevice = null

    if (null != p2pInfo && null != newGroup && changed) {
        if (newGroup.owner?.deviceName != null &&
            newGroup.owner?.uniqueWifiId() != thisDevice!!.uniqueWifiId()
        ) {
            /* To revisit the case where two devices have the same WIFID/Bluetooth name */
            logd(
                tag, "\nProcess: " +
                        "\n\t\t\tgroupOwner.UID: ${newGroup.owner?.uniqueWifiId()} " +
                        "\n\t\t\tgroupOwner.deviceName: $newGroup.owner?.deviceName} " +
                        "\n\t\t\tthis.deviceName: ${thisDevice?.deviceName} " +
                        "\n\t\t\tthis.deviceUid: $deviceUid " +
                        "for directWifiPeers"
            )

            if (newGroup.owner?.deviceName == thisDevice?.deviceName) {
                logd(
                    tag,
                    "Should not have ${newGroup.owner?.deviceName} equal to ${thisDevice?.deviceName}"
                )
                throw (NotImplementedError("Should not have ${newGroup.owner?.deviceName} equal to ${thisDevice?.deviceName}"))
            }

            if (directWifiPeers[newGroup.owner?.uniqueWifiId()] == null) {
                if (newGroup.owner?.deviceAddress != null && newGroup.owner != null) {
                    logd(tag, "Add ${newGroup.owner?.uniqueWifiId()} to directWifiPeers")

                    directWifiPeers[newGroup.owner?.uniqueWifiId()!!] = WTWifiDirectPeerInfo(
                        newGroup.owner!!,
                        newGroup.owner!!.isGroupOwner,
                        newGroup.owner!!.deviceName,
                        newGroup.owner!!.deviceAddress
                    )
                }
            }
        }
    }

    if (changed) {
        channelSend(
            ChannelId.RCToWTActivity,
            ChannelMessageType.RCWifiDebugInfoMessage,
            wtWifiDirectInfo())
    }

    logd(tag, "--------------------------------------------------------------")
}

fun WTWifiDirectManager.notifyOfChange(
    oldGroupInfo: Triple<InetAddress?, InetAddress?, Int?>,
    wtGroupInfo: Triple<InetAddress?, InetAddress?, Int?>
) : Triple<InetAddress?, InetAddress?, Int?> {
    val tag = "notifyOfChange/${randomString(2u)}"

    val (oldGroupIp, oldLocalIp, oldGroupServerPort) = oldGroupInfo
    val (wtGroupIp, wtLocalIp, wtGroupServerPort) = wtGroupInfo

    logd(tag, "\ngroupIp: $oldGroupIp -> $wtGroupIp" +
            "\nlocalIp: $oldLocalIp -> $wtLocalIp" +
            "\nserverPort: $oldGroupServerPort -> $wtGroupServerPort" +
            "\nwtGroupOwnerName: $wtGroupOwnerName" +
            "\nwtIsGroupOwner: $wtIsGroupOwner")

    if (oldGroupInfo != wtGroupInfo) {
        var localIpAlreadyChanged = false
        if (null == wtGroupIp) {
            logd(tag, "Group info changed(Null): wtGroupIp: $oldGroupIp -> $wtGroupIp wtLocalIp: $oldLocalIp -> $wtLocalIp")
            channelSend(
                ChannelId.RCToComm,
                ChannelMessageType.RCGroupInfo,
                Triple(null, null, null)
            )
        }
        if (!wtIsGroupOwner &&
            null != wtGroupServerPort &&
            null != wtGroupIp &&
            null != wtGroupOwnerName &&
            null != wtLocalIp) {
            logd(tag, "Group info changed(Group): wtGroupIp: $oldGroupIp -> $wtGroupIp wtGroupServerPort: $oldGroupServerPort -> $wtGroupServerPort wtLocalIp: $oldLocalIp -> $wtLocalIp")
            /*
            channelSend(
                WTChannelId.RCToComm,
                WTChannelMessageType.RCGroupInfo,
                Triple(null, null, null)
            )
            */
            channelSend(
                ChannelId.RCToComm,
                ChannelMessageType.RCGroupInfo,
                Triple(wtGroupOwnerName, wtGroupIp, wtGroupServerPort)
            )
            channelSend(
                ChannelId.RCToComm,
                ChannelMessageType.RCLocalIp,
                wtLocalIp)
            localIpAlreadyChanged = true
            restartChannelCountdown(true)
            peersDiscoveryState(true)
        }

        if (localIpAlreadyChanged) {
            logd(tag, "Local IP info changed(IP): wtLocalIp: $oldLocalIp -> $wtLocalIp. Already Sent Info.")
        }
        if (wtLocalIp != oldLocalIp && !localIpAlreadyChanged) {
            logd(tag, "Local IP info changed(IP): wtLocalIp: $oldLocalIp -> $wtLocalIp")
             channelSend(
                 ChannelId.RCToComm,
                 ChannelMessageType.RCLocalIp,
                 wtLocalIp)
            if (null != wtLocalIp) {
                restartChannelCountdown(true)
                peersDiscoveryState(true)
            }
        }
    }
    return wtGroupInfo
}

suspend fun WTWifiDirectManager.updateP2pInfo () {
    val tag = "updateP2pInfo/${randomString(2u)}"

    logd(tag, "Entry++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++")

    wtWifiP2pInfo.get()?.logD(tag)
    wtWifiP2pInfoN.get()?.logD(tag)

    if (null != wtWifiP2pInfo.get()?.groupOwnerAddress &&
        null == wtWifiP2pInfoN.get()?.groupOwnerAddress) {
        logd(tag, "Lost P2P Connection/Info")
        directWifiPeers.clear()
        directWifiServices.clear()
        connectToDevice = null
        wtWifiGroupInfo.getAndSet(null)
        channelSend(
            ChannelId.RCToComm,
            ChannelMessageType.RCGroupInfo,
            Triple(null, null, null)
        )
        /* channelSend(WTChannelId.RCToIpComm, WTChannelMessageType.RCStop) */
        restartChannel(true)
    }

    wtWifiP2pInfo.getAndSet(wtWifiP2pInfoN.get())

    wtWifiP2pInfo.get()?.logD(tag)
    logd(tag, "Exit--------------------------------------------------------------")
}

suspend fun WTWifiDirectManager.connectToPeers(delay: Long = 1000L) {
    val tag = "connectToPeers/${randomString(2u)}"
    var c = 0
    val divider = 5

    logd(tag, "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++")
    logd(tag,
        "($c):\n" +
                "\n\t\twtIsGroupOwner: $wtIsGroupOwner" +
                "\n\t\twtIsGroupFormed: $wtIsGroupFormed" +
                "\n\t\tisWifiP2pEnabled: $wifiP2pEnable" +
                "\n\t\tconnectCountdown: $connectCountdown" +
                "\n\t\tconnectingAllowed: ${connectingAllowed()}" +
                "\n\t\tdirectWifiPeers: ${directWifiPeers.keys}" +
                "\n\t\tdiscoverPeersProcessActive: ${serviceDiscoveryActive()}").
    also { c++ }

    if (checkWifiDPermission() &&
        wifiP2pEnable &&
        connectingAllowed()
    ) {
        val tmpPeers: MutableMap<String, WTWifiDirectPeerInfo> = directWifiPeers.toMutableMap()
        val groupOwner = wtGroupOwner
        val peersList = tmpPeers.filter { (_, device) -> device != groupOwner }.toList()

        logd(tag,
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
                        "[GO: ${wtIsGroupFormed} / ${wtIsGroupOwner} / ${connectToDevice!!.isGroupOwner}] " +
                        "[connectionStatus: ${connectToDevice!!.directWifiConnection}]"
            ).also { c++ }
            delay(delay / divider)
            connectTo(connectToDevice!!)
        } else {
            if (wtIsGroupOwner && null != connectToDevice) {
                connectTo(connectToDevice!!)
            }
        }
    }
    logd(tag, "--------------------------------------------------------------")
}

suspend fun WTWifiDirectManager.removeLocalService() {
    val tag = "removeLocalService/${randomString(2u)}"
    val instanceName = WT_SERVICE_WALKIETALKIE// + "." + deviceUid()
    /* val serviceType = "_presence._tcp" */
    val serviceType = WT_SERVICE_WALKIETALKIE

    logd(tag, "Entry: $wtLocalServiceRecord")

    if (null != wtLocalServiceRecord) {
        logd(tag, "removeLocalService: $wtLocalServiceRecord")

        when (val res = awaitP2pAction { listener ->
            manager.removeLocalService(
                channel,
                WifiP2pDnsSdServiceInfo.newInstance(
                    instanceName,
                    serviceType,
                    wtLocalServiceRecord),
                listener)
        }) {
            is CallbackResult.Success -> {
                logd(TAGKClass, tag, "Success removing local service")
                wtLocalServiceRecord = null
            }
            is CallbackResult.Failure -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed removing local service: ${res.reason}: ${errToString(res.reason!!)}"
                )
                wifiP2PEngineFailCoolDown(true)
            }
        }
    }
}

suspend fun WTWifiDirectManager.addLocalService(removeFirst: Boolean = false) {
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

    logd(tag, "addLocalService: $wtLocalServiceRecord")
    if (checkWifiDPermission()) {

        when (val res = awaitP2pAction { listener ->
            manager.addLocalService(
                channel,
                WifiP2pDnsSdServiceInfo.newInstance(
                    instanceName,
                    serviceType,
                    wtLocalServiceRecord
                ),
                listener)
        }) {
            is CallbackResult.Success -> {
                logd(TAGKClass, tag, "Success adding local service")
            }
            is CallbackResult.Failure -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed adding local service: ${res.reason}: ${errToString(res.reason!!)}"
                )
                wifiP2PEngineFailCoolDown(true)
            }
        }
    }
}

suspend fun WTWifiDirectManager.removeServiceRequest() {
    val tag = "addServiceRequest/${randomString(2u)}"
    val instanceName = WT_SERVICE_WALKIETALKIE /* + "." + deviceUid() */
    /* val serviceType = "_presence._tcp" */
    val serviceType = WT_SERVICE_WALKIETALKIE

    logd(tag, "manager.removeServiceRequest Entry")
    val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance(instanceName, serviceType)

    logd(tag, "manager.removeServiceRequest")

    when (val res = awaitP2pAction { listener ->
        manager.removeServiceRequest(
            channel,
            serviceRequest,
            listener)
    }) {
        is CallbackResult.Success -> {
            logd(
                TAGKClass,
                tag,
                "Success removing service request"
            )
        }
        is CallbackResult.Failure -> {
            logd(
                TAGKClass,
                tag,
                "Failed removing service request: ${res.reason}: ${errToString(res.reason!!)}"
            )
            wifiP2PEngineFailCoolDown(true)
        }
    }
}

suspend fun WTWifiDirectManager.addServiceRequest(removeFirst: Boolean = false) {
    val tag = "addServiceRequest/${randomString(2u)}"
    val instanceName = WT_SERVICE_WALKIETALKIE /* + "." + deviceUid() */
    /* val serviceType = "_presence._tcp" */
    val serviceType = WT_SERVICE_WALKIETALKIE

    logd(tag, "manager.addServiceRequest Entry")
    val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance(instanceName, serviceType)

    if (removeFirst) {
        removeServiceRequest()
    }

    logd(tag, "manager.addServiceRequest")

    when (val res = awaitP2pAction { listener ->
        manager.addServiceRequest(
            channel,
            serviceRequest,
            listener)
    }) {
        is CallbackResult.Success -> {
            logd(
                TAGKClass,
                tag,
                "Success adding service request"
            )
        }
        is CallbackResult.Failure -> {
            logd(
                TAGKClass,
                tag,
                "Failed adding service request: ${res.reason}: ${errToString(res.reason!!)}"
            )
            wifiP2PEngineFailCoolDown(true)
        }
    }
}

fun WTWifiDirectManager.registerServiceListeners() {
    val tag = "registerServiceListeners/${randomString(2u)}"

    logd(tag, "Entry")

    val txtListener = WifiP2pManager.DnsSdTxtRecordListener { fullDomain, record, device ->
        logd(
            TAGKClass,
            tag,
            "DnsSdTxtRecord available: [$fullDomain] [$record] [${device.deviceName}]")
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
                directWifiServices[device.uniqueWifiId()]?.localServerPort = record[WT_SERVICE_LOCAL_SERVER_PORT]
            }
            logd(TAGKClass,
                tag,
                "directWifiServices[${device.uniqueWifiId()}] = " +
                        "${directWifiServices[device.uniqueWifiId()]?.id} " +
                        "${directWifiServices[device.uniqueWifiId()]?.unique} " +
                        "${directWifiServices[device.uniqueWifiId()]?.rnd} " +
                        "${directWifiServices[device.uniqueWifiId()]?.localServerPort}")
        }
    }

    val servListener =
        WifiP2pManager.DnsSdServiceResponseListener { instanceName, registrationType, resourceType ->
            logd(TAGKClass, tag,
                "onBonjourServiceAvailable [$instanceName] [$registrationType] [${resourceType.deviceName}]"
            )
            if (WT_SERVICE_WALKIETALKIE == instanceName.subSequence(WT_SERVICE_WALKIETALKIE.indices)) {
                logd(TAGKClass,
                    tag,
                    "device.deviceName: ${resourceType.deviceName} resourceType.uniqueWifiId: ${resourceType.uniqueWifiId()} ${directWifiPeers[resourceType.uniqueWifiId()]?.name}")
                if (null == directWifiServices[resourceType.uniqueWifiId()]) {
                    directWifiServices[resourceType.uniqueWifiId()] = WTWifiDirectServiceInfo()
                }
            }
        }

    logd(tag, "Registering listeners...")
    manager.setDnsSdResponseListeners(channel, servListener, txtListener)
}

suspend fun WTWifiDirectManager.wtServicesInit() {
    val tag = "discoverServicesInit/${randomString(2u)}"
    val sem = Semaphore(1, 1)

    logd(tag, "Entry")

    registerServiceListeners()
    delay(100L)
    clearAllServices()
    delay(100L)
    /*
    /* addLocalService() */
    delay(1000L)
    /* addServiceRequest() */
    delay(1000L)
    /* discoverServices() */
    delay(1000L)
    */
    peersDiscoveryState(true)
}

@SuppressLint("NewApi")
suspend fun WTWifiDirectManager.stopPeerDiscovery() {
    val tag = "stopPeerDiscovery/${randomString(2u)}"

    logd(tag, "Entry")

    when (val res = awaitP2pAction { listener ->
        /* manager.stopPeerDiscovery( */
        manager.stopListening(
            channel!!,
            listener)
    }) {
        is CallbackResult.Success -> {
            logd(
                TAGKClass,
                tag,
                "Success stopping peers discovery"
            )
        }
        is CallbackResult.Failure -> {
            logd(
                TAGKClass,
                tag,
                "Failed stopping peers discovery: ${res.reason}: ${errToString(res.reason!!)}"
            )
            wifiP2PEngineFailCoolDown(true)
        }
    }

    logd(tag, "Exit 0")
}

suspend fun WTWifiDirectManager.discoverServices() {
    val tag = "discoverServices/${randomString(2u)}"

    logd(tag, "manager.discoverServices")
    if (checkWifiDPermission()) {
        when (val res = awaitP2pAction { listener ->
            manager.discoverServices(
                channel,
                listener)
        }) {
            is CallbackResult.Success -> {
                logd(
                    TAGKClass,
                    tag,
                    "Success starting discoverServices"
                )
            }
            is CallbackResult.Failure -> {
                logd(
                    TAGKClass,
                    tag,
                    "Failed starting discoverServices: ${res.reason}: ${errToString(res.reason!!)}"
                )
                wifiP2PEngineFailCoolDown(true)
            }
        }
    }
}

suspend fun WTWifiDirectManager.clearAllServices() {
    val tag = "clearAllServices/${randomString(2u)}"

    logd(tag, "Entry")

    logd(tag, "manager.clearServiceRequests")

    when (val res = awaitP2pAction { listener ->
        manager.clearServiceRequests(
            channel, listener)
    }) {
        is CallbackResult.Success -> {
            logd(TAGKClass,
                tag,
                "Success clearServiceRequests"
            )
        }
        is CallbackResult.Failure -> {
            logd(TAGKClass,
                tag,
                "Failed clearServiceRequests: ${res.reason}: ${errToString(res.reason!!)}"
            )
            wifiP2PEngineFailCoolDown(true)
        }
    }

    logd(tag, "manager.clearLocalServices")

    when (val res = awaitP2pAction { listener ->
        manager.clearLocalServices(
            channel,
            listener)
    }) {
        is CallbackResult.Success -> {
            logd(TAGKClass,
                tag,
                "Success clearLocalServices"
            )
        }
        is CallbackResult.Failure -> {
            wifiP2PEngineFailCoolDown(true)
            logd(TAGKClass,
                tag,
                "Failed clearLocalServices: ${res.reason}: ${errToString(res.reason!!)}"
            )
            wifiP2PEngineFailCoolDown(true)
        }
    }
}

suspend fun WTWifiDirectManager.processBcastReceiverMessage(intent: Intent) {
    val action = intent.action
    val tag = "WiFiDirectBroadcastReceiver"

    logd (tag,"action: ${action.toString()}")

    when (action) {
        WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
            val state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1)
            logd(tag, "P2P discovery has $state " +
                    if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) "Started" else "Stopped")
            //discoverPeersProcessActive = WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED == state
        }
        WIFI_P2P_STATE_CHANGED_ACTION -> {
            val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
            val enabled: Boolean = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)

            logd(tag, "processBcastReceiverMessage(0): P2P state changed to " +
                    if (enabled) "enabled" else "disabled" + " " + "manager: \n\t\t\$manager" )

            wifiP2pEnabledNInfo.getAndSet(enabled)
            if (wifiP2pEnabledNInfo.get()) {
                val device = requestDeviceInfo()
                onDeviceInfoAvailable(device)
                logd(tag, "processBcastReceiverMessage(1): P2P state changed to enabled" +
                        "\n\t\t\t\tGO: ${device?.isGroupOwner}" +
                        "\n\t\t\t\tdeviceName: ${device?.deviceName}" +
                        "\n\t\t\t\tdeviceAddress: ${device?.deviceAddress}")
            }
        }

        WIFI_P2P_PEERS_CHANGED_ACTION -> {
            /*
             Request available peers from the wifi p2p manager. This is an
             asynchronous call and the calling activity is notified with a
             callback on PeerListListener.onPeersAvailable()
            */
            logd(tag, "processBcastReceiverMessage: P2P peers changed")

            if (!checkWifiDPermission()) {
                logd(tag, "processBcastReceiverMessage: Not enough permissions.")
                return
            }
            requestPeersInfo()
        }

        WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
            val networkInfo = intent
                .getParcelableExtra<Parcelable>(WifiP2pManager.EXTRA_NETWORK_INFO) as NetworkInfo

            logd(tag, "processBcastReceiverMessage: P2P connection changed to: " +
                        if (networkInfo.isConnected) "Connected" else "Disconnected")

            requestConnectionInfo()
            requestGroupInfo()
        }

        WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
            val device = requestDeviceInfo()
            onDeviceInfoAvailable(device)
            requestGroupInfo()
            logd(tag, "processBcastReceiverMessage: P2P this device changed:" +
                    "\n\t\t\t\tGO: ${device?.isGroupOwner}" +
                    "\n\t\t\t\tdeviceName: ${device?.deviceName}" +
                    "\n\t\t\t\tdeviceAddress${device?.deviceAddress}")
        }
        else -> {
            logd(tag, "processBcastReceiverMessage: P2P changed to ${action.toString()} NOT Addressed")
        }
    }
}

fun WTWifiDirectManager.main(scanInterval: Long = 1000L) {
    val tag = "wtWifiDirectMain/${randomString(2u)}"

    logd(tag, "wtWifiDirectMain Entry 0")

    resetData()

    logd(tag, "wtWifiDirectMain Entry 1")

    mainLoopJob = scope.launch {
        logd(TAGKClass, tag, "wtWifiDirectMain Starting Peers Scanning")
        mainLoop(scanInterval)
    }

    logd(tag, "wtWifiDirectMain Entry 2 scanPeersJob: ${mainLoopJob.toString()}")
}

suspend fun WTWifiDirectManager.stop(delay: Long = 1000L) {
    val tag = "wtWifiDirectStop/${randomString(2u)}"
    val s = WTWiFiDirectStatic.INSTANCE
    val divider = 5

    logd(tag, "cancelConnect")
    delay(delay/divider)
    cancelConnect()

    logd(tag, "clearAllServices")
    delay(delay/divider)
    clearAllServices()

    logd(tag, "stopPeerDiscovery")
    delay(delay/divider)
    stopPeerDiscovery()

    /*
    logd(tag, "stopPeerDiscovery")
    delay(delay/divider)
    stopPeerDiscovery(false)
    */

    logd(tag, "requestGroupInfo/removeGroup/clearAllServices")
    delay(delay/divider)
    requestGroupInfo()
    delay(delay/divider)
    removeGroup()
    delay(delay/divider)
    clearAllServices()

    channel?.close()

    logd(tag, "resetData")
    resetData()

    channelSend(
        ChannelId.RCToWTActivity,
        ChannelMessageType.RCWifiDebugInfoMessage,
        wtWifiDirectInfo()
    )

    logd(tag, "wtWifiDirectStop 3")
    s.scanPeersS = false
    logd(tag, "wtWifiDirectStop 4: ${s.scanPeersS}")
    delay(delay/divider)
    channelSend(
        ChannelId.RCToWTActivity,
        ChannelMessageType.RCWifiRestartChannel
    )
    logd(tag, "wtWifiDirectStop 5: ${s.scanPeersS}")
    resetData()
    delay(delay/divider)
}