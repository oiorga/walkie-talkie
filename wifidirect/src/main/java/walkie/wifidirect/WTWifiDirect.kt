package walkie.wifidirect

import android.annotation.SuppressLint
import android.content.Intent
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pConfig.GROUP_OWNER_INTENT_MAX
import android.net.wifi.p2p.WifiP2pConfig.GROUP_OWNER_INTENT_MIN
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Semaphore
import walkie.util.generic.ChannelMux
import walkie.util.generic.ChannelMuxInt
import walkie.util.generic.GenericList
import walkie.glue.wtsystem.NodeIdInt
import walkie.glue_inc.ChannelId
import walkie.glue_inc.ChannelIdInt
import walkie.glue_inc.ChannelMessageType
import walkie.util.generic.RemoteCallMux
import walkie.util.generic.RemoteCallMuxInt
import walkie.util.getInterfaceIpAddress
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString
import java.net.InetAddress
import kotlin.random.Random

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class WTWiFiDirect(
    private val _manager: WifiP2pManager,
    private var _channel: Channel,
    val node: NodeIdInt,
    private val _channelMux: ChannelMuxInt<Any, ChannelMessageType> = ChannelMux(),
    private val _remoteCallMux: RemoteCallMuxInt<Any, Any> = RemoteCallMux()
    ) :
    ChannelMuxInt<Any, ChannelMessageType> by _channelMux,
    RemoteCallMuxInt<Any, Any> by _remoteCallMux {

    companion object {
        const val TAG = "WTWiFiDirect"
        const val DiscoveryCountdown = 26
        const val ConnectCountdown = 1
        const val FailCooldown = 1
        const val RestartChannelTimeout = (DiscoveryCountdown * 2).toInt()
        const val DiscoveryVsAdvertisementRatio = (2F / 3F)
        val TAGKClass = WTWiFiDirect::class
    }

    private val errToString = mapOf(
        WifiP2pManager.P2P_UNSUPPORTED to "P2P UNSUPPORTED",
        WifiP2pManager.ERROR to "INTERNAL ERROR",
        WifiP2pManager.BUSY to "WifiP2pManager BUSY"
    )

    var scanPeersJob: Job? = null

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
        get() = node.uid()

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
    fun peersDiscoveryReset(yes: Boolean? = null): Boolean {
        if (null != yes) {
            discoveryCountdown = if (yes) 0 else DiscoveryCountdown
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

    val manager: WifiP2pManager
        get() = _manager
    val channel: Channel
        get() = _channel

    fun channel(channel: Channel) {
        _channel = channel
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

        scanPeersJob = null
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
        peersDiscoveryReset(true)
        connectCountdown = ConnectCountdown

        restartChannelCountdown = RestartChannelTimeout

        wifiP2PEngineFailCoolDown(false)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun discoverPeers(sync: Boolean = true) {
        val tag = "discoverPeers/${randomString(2u)}"
        val sem = Semaphore(1, 1)
        var reasonToStr = "Success"

        logd(tag, "Entry++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++")

        if (!checkWifiDPermission()) {
            logd(tag, "Not enough WIFI_D permissions")
            return
        }

        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                logd(TAGKClass, tag, "onSuccess")
                wtWifiFailure("$tag/$reasonToStr")
                if (sync) sem.release()
            }

            override fun onFailure(reasonCode: Int) {
                reasonToStr = errToString(reasonCode)

                logd(TAGKClass, tag, "onFailure($reasonCode): $reasonToStr")
                wtWifiFailure("$tag/$reasonToStr")

                if (sync) sem.release()
            }
        })

        logd(TAGKClass, tag, "Exit(0)--------------------------------------------------------------")
        if (sync) sem.acquire()
        logd(TAGKClass, tag, "Exit(1)--------------------------------------------------------------")
    }

    fun checkWifiDPermission(): Boolean {
        val tag = TAG

        logd(tag, "checkWifiDPermission TO FIX IT")

        /* return fineLocation == PackageManager.PERMISSION_GRANTED && nearbyWifi == PackageManager.PERMISSION_GRANTED; */

        return true
    }

    @SuppressLint("MissingPermission")
    suspend fun requestPeersInfo(sync: Boolean = true): MutableList<WifiP2pDevice> {
        val tag = "requestPeersInfo/${randomString(2u)}"
        val sem = Semaphore(1, 1)

        manager.requestPeers(channel) { peerList ->
            var str = ""
            peerList.deviceList.forEach {wifiDevice ->
                str += wifiDevice.deviceName +  "/${wifiDevice.deviceAddress}" + " "
            }
            logd(
                TAGKClass,
                tag,
                "NEW Peers: $str"
            )
            directWifiPeersN.addAll(peerList.deviceList)
            if (sync) sem.release()
        }
        if (sync) sem.acquire()
        return directWifiPeersN
    }

    @SuppressLint("MissingPermission")
    suspend fun requestDeviceInfo(sync: Boolean = true): WifiP2pDevice? {
        val tag = "requestDeviceInfo/${randomString(2u)}"
        var ret: WifiP2pDevice? = null

        val sem = Semaphore(1, 1)
        manager.requestDeviceInfo(channel) { device ->
            ret = device
            /* onDeviceInfoAvailable(device) */
            logd(
                tag, "processBcastReceiverMessage: P2P this device changed:" +
                        "\n\t\t\t\tGO: ${device?.isGroupOwner}" +
                        "\n\t\t\t\tdeviceName: ${device?.deviceName}" +
                        "\n\t\t\t\tdeviceAddress${device?.deviceAddress}"
            )
            if (sync) sem.release()
        }
        if (sync) sem.acquire()
        return ret
    }

    @SuppressLint("MissingPermission")
    suspend fun requestGroupInfo(sync: Boolean = true): WifiP2pGroup? {
        val tag = "requestGroupInfo/${randomString(2u)}"
        var ret: WifiP2pGroup? = null
        val sem = Semaphore(1, 1)

        logd(tag, "Entry+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++")

        manager.requestGroupInfo(channel) { group ->
            if (null != group) {
                var str = ""
                group.clientList.forEach { dev ->
                    str += dev.deviceName + " "
                }

                logd(
                    tag,
                    "Callback: sync: $sync" +
                            "\n Owner: [${group.owner.deviceName}] [$deviceId] [${group.owner.deviceAddress}]" +
                            "\n Passwd: ${group.passphrase}" +
                            "\n GO: ${group.isGroupOwner}" +
                            "\n Interface Name: ${group.`interface`} / ${getInterfaceIpAddress(group.`interface`)}" +
                            "\n ClientList: $str"
                )
            } else {
                logd(tag, "requestGroupInfo got NULL")
            }

            if (wtWifiGroupInfoN.get() != group) wtWifiGroupInfoN.getAndSet(group)

            ret = group

            if (sync)
                sem.release()
        }

        logd(tag, "Exit(0)-----------------------------------------------------------------")
        if (sync) {
            sem.acquire()
        }

        logd(
            tag,
            "sync: $sync" +
                    "\n Owner: [${ret?.owner?.deviceName}] [$deviceId] [${ret?.owner?.deviceAddress}]" +
                    "\n Passwd: ${ret?.passphrase}" +
                    "\n GO: ${ret?.isGroupOwner}" +
                    "\n Interface Name: ${ret?.`interface`} / ${ret?.`interface`?.let { getInterfaceIpAddress(it) }}"
        )
        logd(tag, "Exit(1)-----------------------------------------------------------------")

        return ret
    }

    suspend fun removeGroup(sync: Boolean = true) {
        val tag = "removeGroup/${randomString(2u)}"
        val sem = Semaphore(1, 1)
        var reasonToStr = "Success"

        logd(tag, "Entry sync: $sync++++++++++++++++++++++++++++++++++++++++++++++++++++++")

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

        manager.removeGroup(channel,
            object: WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    logd(
                        TAGKClass,
                        tag,
                        "onSuccess"
                    )
                    wtWifiGroupInfo.getAndSet(null)
                    wtWifiGroupInfoN.getAndSet(null)
                    wtWifiFailure("$tag/$reasonToStr")
                    if (sync) sem.release()
                }

                override fun onFailure(reasonCode: Int) {
                    reasonToStr = errToString(reasonCode)

                    logd(
                        TAGKClass,
                        tag,
                        "onFailure: Reason($reasonCode): $reasonToStr"
                    )
                    wtWifiFailure("$tag/$reasonToStr")
                    wtWifiGroupInfo.getAndSet(null)
                    wtWifiGroupInfoN.getAndSet(null)

                    wifiP2PEngineFailCoolDown(true)
                    if (sync) sem.release()
                }
            }
            )

        logd("Exit(0)------------------------------------------------------")
        if (sync) sem.acquire()
        logd("Exit(1)------------------------------------------------------")
    }

    private fun connectedToGroup(): Boolean {
        val tag = "connectedToGroup/${randomString(2U)}"
        logd(tag,
            "wtLocalIp: $wtLocalIp wtGroupIp: $wtGroupIp wtIsGroupFormed: ${wtIsGroupFormed} ${(null != wtLocalIp) && wtIsGroupFormed && (null != wtGroupIp)}")
        return ((null != wtLocalIp) && wtIsGroupFormed && (null != wtGroupIp))
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: WifiP2pDevice,
                        config: WifiP2pConfig = WifiP2pConfig().apply {
                            deviceAddress = device.deviceAddress
                            groupOwnerIntent = GROUP_OWNER_INTENT_MIN
                            wps.setup = WpsInfo.PBC },
                        sync: Boolean = true): ConnectionStatus {
        val tag = "connectTo/${randomString(2u)}"
        val sem = Semaphore(1, 1)
        var ret: ConnectionStatus = ConnectionStatus.Null
        var reasonToStr = "Success"


        logd(tag, "Connecting to ${device.uniqueWifiId()}")
        try {
            manager.connect(channel, config,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        logd(
                            TAGKClass,
                            tag,
                            "onSuccess: " +
                                    "Connect to: ${device.uniqueWifiId()}  ${device.deviceAddress} GO = ${device.isGroupOwner} Success"
                        )
                        ret = ConnectionStatus.InProgress
                        wtWifiFailure("$tag/$reasonToStr")
                        if (sync) sem.release()
                    }

                    override fun onFailure(reasonCode: Int) {
                        reasonToStr = errToString(reasonCode)
                        logd(
                            TAGKClass,
                            tag,
                            "onFailure: Connect to: ${device.uniqueWifiId()}  ${device.deviceAddress} GO = ${device.isGroupOwner} Fail reason($reasonCode): $reasonToStr"
                        )
                        wtWifiFailure("$tag/$reasonToStr")
                        ret = ConnectionStatus.Fail
                        if (sync) sem.release()
                    }
                }
            )
        } catch (e: Exception) {
            throw(e)
        } catch (t: Throwable) {
            throw(t)
        }

        if (sync) sem.acquire()
        return ret
    }

    suspend fun cancelConnect(sync: Boolean = true) {
        val tag = "cancelConnect/${randomString(2u)}"
        val sem = Semaphore(1, 1)
        var reasonToStr = "Success"

        logd(tag, "Cancelling Connecting process")

        manager.cancelConnect(channel,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    logd(TAGKClass, tag, "onSuccess:")
                    wtWifiFailure("$tag/$reasonToStr")
                    if (sync) sem.release()
                }

                override fun onFailure(reasonCode: Int) {
                    reasonToStr = errToString(reasonCode)
                    logd(
                        TAGKClass, tag, "onFailure: Reason($reasonCode): $reasonToStr"
                    )
                    wtWifiFailure("$tag/$reasonToStr")
                    if (sync) sem.release()
                }
            }
        )
        if (sync) sem.acquire()
    }

    @SuppressLint("MissingPermission")
    suspend fun connectTo(device: WTWifiDirectPeerInfo, sync: Boolean = true): ConnectionStatus {
        val tag = "connectTo/${randomString(2u)}"
        val sem = Semaphore(1, 1)
        var synC = false
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
                    synC = sync
                    device.directWifiConnection = connect(device.p2pInfo, config, sync)
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
                    if (synC) sem.release()
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
                        cancelConnect(sync = true)
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
                        cancelConnect(sync = true)
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

        logd(tag, "($c)Exit(0)--------------------------------------------------------------").also { c++ }
        if (synC) {
            sem.acquire()
        }
        logd(tag, "($c)Exit(1)--------------------------------------------------------------").also { c++ }
        logd(tag, "($c)Exit connect to ${device.uniqueWifiId()} ${device.wtService()} ${device.directWifiConnection}").also { c++ }

        return device.directWifiConnection
    }

    suspend fun requestConnectionInfo(sync: Boolean = true): WifiP2pInfo? {
        val tag = "requestConnectionInfo/${randomString(2u)}"
        var ret: WifiP2pInfo? = null
        val sem = Semaphore(1, 1)
        var reqGroup = false

        logd(
            TAGKClass,
            tag,
            "Entry sync: $sync+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
        )

        wtWifiP2pInfoN.get()?.logD(tag)

        manager.requestConnectionInfo(channel) { info ->
            logd(
            TAGKClass,
            tag,
            "Entry: ${info.groupFormed}  ${info.isGroupOwner} ${info.groupOwnerAddress}"
            )

            ret = info
            if (ret != wtWifiP2pInfoN.get()) {
                wtWifiP2pInfoN.compareAndSet(wtWifiP2pInfoN.get(), ret)
                reqGroup = true
            }

            if (sync) sem.release()
        }

        logd(
            TAGKClass,
            tag,
            "Exit(0)-----------------------------------------------------------"
        )

        if (sync) {
            sem.acquire()
        }

        wtWifiP2pInfoN.get()?.logD(tag)
        logd(
            TAGKClass,
            tag,
            "Exit(1)-----------------------------------------------------------"
        )

        return ret
    }
}