package walkie.wifidirect

import android.annotation.SuppressLint
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.os.Parcelable
import android.os.SystemClock.uptimeMillis
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import walkie.glue_inc.ChannelId
import walkie.glue_inc.ChannelMessageType
import walkie.util.logd
import walkie.util.randomString
import walkie.util.stringSum
import walkie.wifidirect.WTWiFiDirect.Companion.ConnectCountdown
import walkie.wifidirect.WTWiFiDirect.Companion.TAGKClass
import walkie.wifidirect.WTWifiDirectServiceInfo.Companion.WT_SERVICE_ID
import walkie.wifidirect.WTWifiDirectServiceInfo.Companion.WT_SERVICE_LOCAL_SERVER_PORT
import walkie.wifidirect.WTWifiDirectServiceInfo.Companion.WT_SERVICE_RND
import walkie.wifidirect.WTWifiDirectServiceInfo.Companion.WT_SERVICE_UNIQUE
import walkie.wifidirect.WTWifiDirectServiceInfo.Companion.WT_SERVICE_WALKIETALKIE
import java.net.InetAddress
import kotlin.random.Random

/* Ugly. To revisit. To prevent coroutines to restart at onCreate on screen rotation */
class WTWiFiDirectStatic private constructor() {
    companion object {
        val INSTANCE: WTWiFiDirectStatic by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { WTWiFiDirectStatic() }
        val ONE = INSTANCE
    }

    var scanPeersS: Boolean = false
}

@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
suspend fun WTWiFiDirect.scanPeers(coroutineScope: CoroutineScope = MainScope(), delay: Long = 1000L) {
    val tag = "scanPeers/${randomString(2u)}"
    val s = WTWiFiDirectStatic.INSTANCE
    val divider = 5
    var c = (Random.nextInt(delay.toInt()) % 5)
    var oldP2pInfo: Triple<InetAddress?, InetAddress?, Int?> = Triple(null, null, null)

    logd(tag, "Entry: ${s.scanPeersS}")

    if (s.scanPeersS) return
    s.scanPeersS = true

    channelSend(
        ChannelId.RCToWalkieTalkie,
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
        logd(tag, "thisDevice: ${thisDevice?.deviceName}" +
                "\nisWifiP2pEnabled: ${wifiP2pEnable}" +
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
            ChannelId.RCToWalkieTalkie,
            ChannelMessageType.RCWifiDebugInfoMessage,
            wtWifiDirectInfo()
        )

        c = (restartChannelCountdown() + c + 1) % divider
        delay(c * delay/divider)
    }

    wtWifiDirectStop()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
suspend fun WTWiFiDirect.updatePeers(delay: Long = 1000L) {
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

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun WTWiFiDirect.updateThisDevice() {
    val tag = "updateThisDevice/${randomString(2u)}"

    if (wifiP2pEnableInfo.get() != wifiP2pEnabledNInfo.get()) {
        wifiP2pEnableInfo.getAndSet(wifiP2pEnabledNInfo.get())
    }

    if (wifiP2pEnableInfo.get() && thisDeviceInfo.get() != thisDeviceNInfo.get()) {
        thisDeviceInfo.getAndSet(thisDeviceNInfo.get())
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
suspend fun WTWiFiDirect.discoverPeersJob(delay: Long = 100L) {
    val tag = "discoverPeersJob/${randomString(2u)}"

    logd(tag, "Entry isWifiP2pEnabled: $wifiP2pEnable resetPeersDiscovery: ${peersDiscoveryReset()} discoveryCountdown: $discoveryCountdown")
    if (!checkWifiDPermission()) {
        logd(tag, "Not enough WIFI-D permissions.")
    } else if (wifiP2pEnable) {
        var str = ""

        if (peersDiscoveryReset()) {
            serviceDiscoveryActive(true)

            if (wtIsGroupFormed && !wtIsGroupOwner) {
                removeLocalService(true)
                delay(delay)
            }

            if (wtIsGroupOwner || !wtIsGroupFormed) {
                addLocalService(sync = true, removeFirst = true)
                delay(delay)
            }

            if (!wtIsGroupFormed || (null == wtGroupServerPort)) {
                addServiceRequest(removeFirst = true)
                delay(delay)
                discoverServices(sync = true)
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
            discoverServices(sync = true)
        } else {
            delay (delay)
        }

        serviceDiscoveryCountdown()

        directWifiPeers.forEach {(_, device) ->
            str += device.name + "/${device.address}/${device.age} "
        }

        logd(
            tag,
            "isGroupOwner: ${wtIsGroupOwner}, discoveryCountdown: $discoveryCountdown resetPeersDiscovery: ${peersDiscoveryReset()} peers: $str"
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
suspend fun WTWiFiDirect.updatePeersInfo () {
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

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun WTWiFiDirect.updateGroupInfo() {
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
            ChannelId.RCToWalkieTalkie,
            ChannelMessageType.RCWifiDebugInfoMessage,
            wtWifiDirectInfo())
    }

    logd(tag, "--------------------------------------------------------------")
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun WTWiFiDirect.notifyOfChange(
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
            peersDiscoveryReset(true)
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
                peersDiscoveryReset(true)
            }
        }
    }
    return wtGroupInfo
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
suspend fun WTWiFiDirect.updateP2pInfo () {
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

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
suspend fun WTWiFiDirect.connectToPeers(delay: Long = 1000L) {
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
                "\n\t\tdiscoverPeersProcessActive: ${serviceDiscoveryActive()}").
    also { c++ }

    if (checkWifiDPermission() &&
        wifiP2pEnable &&
        connectingAllowed()
    ) {
        val tmpPeers: MutableMap<String, WTWifiDirectPeerInfo> = directWifiPeers.toMutableMap()

        val groupOwner = wtGroupOwner
        if (null != groupOwner &&
            groupOwner.wtService() &&
            groupOwner.p2pInfo.deviceName != thisDevice!!.deviceName &&
            !wtIsGroupOwner
        ) {
            logd(
                tag,
                "$tag($c): Connect to Group Owner: [${groupOwner.name}.${groupOwner.address}] " +
                        "[GO: ${wtIsGroupFormed} / ${wtIsGroupOwner} / ${groupOwner.isGroupOwner}] " +
                        "[connectionStatus: ${groupOwner.directWifiConnection}]"
            ).also { c++ }
            connectTo(groupOwner)
            return
        }

        if (wtIsGroupOwner && wtIsGroupFormed) {
            connectToDevice = null
            return
        }

        val peersList = tmpPeers.filter { (_, device) -> device != groupOwner }.toList()
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

@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
suspend fun WTWiFiDirect.removeLocalService(sync: Boolean = true) {
    val tag = "removeLocalService/${randomString(2u)}"
    val instanceName = WT_SERVICE_WALKIETALKIE// + "." + deviceUid()
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
    val sem = Semaphore(1, 1)

    logd(tag, "Entry: $wtLocalServiceRecord")

    if (null != wtLocalServiceRecord) {
        logd(tag, "removeLocalService: $wtLocalServiceRecord")
        manager.removeLocalService(
            channel,
            WifiP2pDnsSdServiceInfo.newInstance(
                instanceName,
                serviceType,
                wtLocalServiceRecord),
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    logd(TAGKClass, tag, "Success removing local service")
                    wtLocalServiceRecord = null
                    if (sync) sem.release()
                }
                override fun onFailure(errCode: Int) {
                    logd(
                        TAGKClass,
                        tag,
                        "Failed removing local service: $errCode: ${errToString(errCode)}"
                    )
                    wifiP2PEngineFailCoolDown(true)
                    if (sync) sem.release()
                }
            }
        )
        if (sync) sem.acquire()
    }
}

@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
suspend fun WTWiFiDirect.addLocalService(sync: Boolean = true, removeFirst: Boolean = true) {
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
    val sem = Semaphore(1, 1)

    logd(tag, "Entry: $wtLocalServiceRecord")

    if (removeFirst) {
        removeLocalService(sync)
    }

    if (null == wtLocalServiceRecord) {
        wtLocalServiceRecord = record
    }

    logd(tag, "addLocalService: $wtLocalServiceRecord")
    manager.addLocalService(
        channel,
        WifiP2pDnsSdServiceInfo.newInstance(
            instanceName,
            serviceType,
            wtLocalServiceRecord),
        object: WifiP2pManager.ActionListener {
            override fun onSuccess() {
                logd(TAGKClass, tag,"Success adding local service")
                if (sync) sem.release()
            }
            override fun onFailure(errCode: Int) {
                logd(TAGKClass, tag,"Failed adding local service: $errCode: ${errToString(errCode)}")
                wifiP2PEngineFailCoolDown(true)
                if (sync) sem.release()
            }
        }
    )
    if (sync) sem.acquire()
}

@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
suspend fun WTWiFiDirect.removeServiceRequest(sync: Boolean = true) {
    val tag = "addServiceRequest/${randomString(2u)}"
    val sem = Semaphore(1, 1)
    val instanceName = WT_SERVICE_WALKIETALKIE /* + "." + deviceUid() */
    /* val serviceType = "_presence._tcp" */
    val serviceType = WT_SERVICE_WALKIETALKIE

    logd(tag, "manager.removeServiceRequest Entry")
    val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance(instanceName, serviceType)

    logd(tag, "manager.removeServiceRequest")
    manager.removeServiceRequest(
        channel,
        serviceRequest,
        object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                logd(
                    TAGKClass,
                    tag,
                    "Success removing service request"
                )
                if (sync) sem.release()
            }

            override fun onFailure(errCode: Int) {
                logd(
                    TAGKClass,
                    tag,
                    "Failed removing service request: $errCode: ${errToString(errCode)}"
                )
                wifiP2PEngineFailCoolDown(true)
                if (sync) sem.release()
            }
        }
    )
    if (sync) sem.acquire()
}

@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
suspend fun WTWiFiDirect.addServiceRequest(sync: Boolean = true, removeFirst: Boolean = true) {
    val tag = "addServiceRequest/${randomString(2u)}"
    val sem = Semaphore(1, 1)
    val instanceName = WT_SERVICE_WALKIETALKIE /* + "." + deviceUid() */
    /* val serviceType = "_presence._tcp" */
    val serviceType = WT_SERVICE_WALKIETALKIE

    logd(tag, "manager.addServiceRequest Entry")
    val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance(instanceName, serviceType)

    if (removeFirst) {
        removeServiceRequest(sync)
    }

    logd(tag, "manager.addServiceRequest")
    manager.addServiceRequest(
        channel,
        serviceRequest,
        object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                logd(
                    TAGKClass,
                    tag,
                    "Success adding service request"
                )
                if (sync) sem.release()
            }
            override fun onFailure(errCode: Int) {
                logd(
                    TAGKClass,
                    tag,
                    "Failed adding service request: $errCode: ${errToString(errCode)}"
                )
                wifiP2PEngineFailCoolDown(true)
                if (sync) sem.release()
            }
        }
    )
    if (sync) sem.acquire()
}

@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun WTWiFiDirect.registerServiceListeners() {
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

@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
suspend fun WTWiFiDirect.wtServicesInit(sync: Boolean = true) {
    val tag = "discoverServicesInit/${randomString(2u)}"
    val sem = Semaphore(1, 1)

    logd(tag, "Entry")

    registerServiceListeners()
    delay(1000L)
    clearAllServices(sync)
    delay(1000L)
    /*
    /* addLocalService(sync) */
    delay(1000L)
    /* addServiceRequest(sync) */
    delay(1000L)
    /* discoverServices(sync) */
    delay(1000L)
    */
    peersDiscoveryReset(true)
}

/* For some reason is not working */
@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
suspend fun WTWiFiDirect.stopPeerDiscovery(sync: Boolean = true) {
    val tag = "stopPeerDiscovery/${randomString(2u)}"
    val sem = Semaphore(1, 1)

    logd(tag, "Entry")

    /* manager.stopPeerDiscovery( */
    manager.stopListening(
        channel,
        object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                logd(TAGKClass,
                    tag,
                    "Success stopping peers discovery"
                )
                if (sync) sem.release()
            }
            override fun onFailure(errCode: Int) {
                logd(TAGKClass,
                    tag,
                    "Failed stopping peers discovery: $errCode: ${errToString(errCode)}"
                )
                wifiP2PEngineFailCoolDown(true)
                if (sync) sem.release()
            }
        }
    )

    logd(tag, "Exit 0")
    if (sync) sem.acquire()
    logd(tag, "Exit 1")
}

@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
suspend fun WTWiFiDirect.discoverServices(sync: Boolean = true) {
    val tag = "discoverServices/${randomString(2u)}"
    val sem = Semaphore(1, 1)

    logd(tag, "manager.discoverServices")
    manager.discoverServices(
        channel,
        object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                logd(TAGKClass,
                    tag,
                    "Success starting discoverServices"
                )
                if (sync) sem.release()
            }
            override fun onFailure(errCode: Int) {
                logd(TAGKClass,
                    tag,
                    "Failed starting discoverServices: $errCode: ${errToString(errCode)}"
                )
                wifiP2PEngineFailCoolDown(true)
                if (sync) sem.release()
            }
        }
    )
    if (sync) sem.acquire()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
suspend fun WTWiFiDirect.clearAllServices(sync: Boolean = true) {
    val tag = "clearAllServices/${randomString(2u)}"
    val sem = Semaphore(1, 1)

    logd(tag, "Entry")

    logd(tag, "manager.clearServiceRequests")
    manager.clearServiceRequests(
        channel,
        object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                logd(TAGKClass,
                    tag,
                    "Success clearServiceRequests"
                )
                if (sync) sem.release()
            }
            override fun onFailure(errCode: Int) {
                logd(TAGKClass,
                    tag,
                    "Failed clearServiceRequests: $errCode: ${errToString(errCode)}"
                )
                wifiP2PEngineFailCoolDown(true)
                if (sync) sem.release()
            }
        }
    )
    if (sync) sem.acquire()

    logd(tag, "manager.clearLocalServices")
    manager.clearLocalServices(
        channel,
        object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                logd(TAGKClass,
                    tag,
                    "Success clearLocalServices"
                )
                if (sync) sem.release()
            }
            override fun onFailure(errCode: Int) {
                wifiP2PEngineFailCoolDown(true)
                logd(TAGKClass,
                    tag,
                    "Failed clearLocalServices: $errCode: ${errToString(errCode)}"
                )
                wifiP2PEngineFailCoolDown(true)
                if (sync) sem.release()
            }
        }
    )
    if (sync) sem.acquire()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
suspend fun WTWiFiDirect.processBcastReceiverMessage(intent: Intent) {
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
                val device = requestDeviceInfo(sync = true)
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

            requestConnectionInfo(sync = true)
            requestGroupInfo(sync = true)
        }

        WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
            val device = requestDeviceInfo(sync = true)
            onDeviceInfoAvailable(device)
            requestGroupInfo(sync = true)
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

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun WTWiFiDirect.wtWifiDirectMain(scope: CoroutineScope = MainScope(), scanInterval: Long = 1000L) {
    val tag = "wtWifiDirectMain/${randomString(2u)}"

    logd(tag, "wtWifiDirectMain Entry 0")

    resetData()

    logd(tag, "wtWifiDirectMain Entry 1")

    scanPeersJob = scope.launch {
        logd(TAGKClass, tag, "wtWifiDirectMain Starting Peers Scanning")
        scanPeers(scope, scanInterval)
    }

    logd(tag, "wtWifiDirectMain Entry 2 scanPeersJob: ${scanPeersJob.toString()}")
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
suspend fun WTWiFiDirect.wtWifiDirectStop(delay: Long = 1000L) {
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
    stopPeerDiscovery(true)

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

    channel.close()

    logd(tag, "resetData")
    resetData()

    channelSend(
        ChannelId.RCToWalkieTalkie,
        ChannelMessageType.RCWifiDebugInfoMessage,
        wtWifiDirectInfo()
    )

    logd(tag, "wtWifiDirectStop 3")
    s.scanPeersS = false
    logd(tag, "wtWifiDirectStop 4: ${s.scanPeersS}")
    delay(delay/divider)
    channelSend(
        ChannelId.RCToWalkieTalkie,
        ChannelMessageType.RCWifiRestartChannel
    )
    logd(tag, "wtWifiDirectStop 5: ${s.scanPeersS}")
    resetData()
    delay(delay/divider)
}