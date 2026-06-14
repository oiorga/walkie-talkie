package walkie.wifidirect

import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import walkie.talkie.api.wtsystem.NodeIdInt
import walkie.util.getEnclosingClassSimpleName
import walkie.util.getInterfaceIpAddress
import walkie.util.getRuntimeSimpleName
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString
import java.net.InetAddress

interface WTWifiDirectEnv {
    fun checkWifiPermissions(): Boolean
}

sealed class WTWifiEvent {
    val description: String
        get() = "${getEnclosingClassSimpleName()}.${getRuntimeSimpleName()}"

    sealed class WTWifi: WTWifiEvent() {
        object Timeout: WTWifi()
        object WifiStop: WTWifi()
        object PeersDiscoveryStart: WTWifi()
        object PeersDiscoveryStop: WTWifi()
        object GroupInfoChanged: WTWifi()
    }

    sealed class P2p: WTWifiEvent() {
        object WifiEnabled: P2p()
        data class WifiDisabled (
            val wifiPermissions: Boolean = false
        ) : P2p()
        object PermissionGranted: P2p()
        object PermissionWithdrawn: P2p()
        object PeersChanged: P2p()
        object ConnectionChanged: P2p()
        object ThisDeviceChanged: P2p()
        object PeersDiscoveryStarted: P2p()
        object PeersDiscoveryStopped: P2p()
        data class TxtRecordListener(
            val fullDomain: String,
            val record: Map<String, String>,
            val device: WifiP2pDevice
        ): P2p()
        data class ServiceResponseListener(
            val instanceName: String,
            val registrationType: String,
            val resourceType: WifiP2pDevice
        ): P2p()
    }
}

sealed class WTWifiState {
    fun description(): String = "${getEnclosingClassSimpleName()}.${getRuntimeSimpleName()}"
    data class Disabled (
        val wifiPermissions: Boolean = false,
    ) : WTWifiState()

    data class Enabled(
        val peersScanning: Boolean = false,
        val connecting: Boolean = false,
        val serviceDiscovery: Boolean = false,
        val advertiseLocalService: Boolean = false,
    ): WTWifiState()
}

data class WTWifiDB(
    val nodeId: NodeIdInt,
    val state: WTWifiState,
    val thisDevice: WifiP2pDevice? = null,
    val p2pInfo: WifiP2pInfo? = null,
    val groupInfo: WifiP2pGroup? = null,
    val peers: List<WifiP2pDevice> = emptyList(),
    val directPeers: Map<String, WTWifiDirectPeerInfo> = emptyMap(),
    val directServices: Map<String, WTWifiDirectServiceInfo> = emptyMap(),
    var tick: Long = 0
    ) {
    companion object {
        const val TAG = "WTWifiDB"
        val TAGKClass = WTWifiDB::class
    }

    val tag = TAG

    private var nextEvent: WTWifiEvent? = null

    fun consumeNextEvent(): WTWifiEvent? {
        val nextE = nextEvent
        nextEvent = null
        return nextE
    }

    init {
        logging(true)
    }

    fun transition(
        event: WTWifiEvent,
        thisDevice: WifiP2pDevice? = null,
        p2pInfo: WifiP2pInfo? = null,
        groupInfo: WifiP2pGroup? = null,
        directPeers: Map<String, WTWifiDirectPeerInfo> = emptyMap(),
        directServices: Map<String, WTWifiDirectServiceInfo>? = emptyMap()
    ): WTWifiDB {
        val tag = "transition/${randomString(2U)}"
        var logStr = event.description
        nextEvent = null

        val newWifiDB = when (event) {
            WTWifiEvent.P2p.WifiEnabled -> {
                if (state is WTWifiState.Enabled) this
                else copy(
                    state = WTWifiState.Enabled(),
                    thisDevice = thisDevice ?: this.thisDevice
                )
            }

            is WTWifiEvent.P2p.WifiDisabled -> {
                if (this.state == WTWifiState.Disabled(event.wifiPermissions)) this
                else copy(state = WTWifiState.Disabled(event.wifiPermissions))
            }

            WTWifiEvent.P2p.ThisDeviceChanged -> {
                logStr += " -> ${thisDevice?.deviceName}"
                copy(
                    thisDevice = thisDevice,
                    groupInfo = groupInfo ?: this.groupInfo
                )
            }

            WTWifiEvent.WTWifi.GroupInfoChanged -> {
                logStr += " -> ${groupInfo?.owner?.deviceName} -> ${this.groupInfo?.owner?.deviceName}"
                copy(groupInfo = groupInfo)
            }

            WTWifiEvent.P2p.ConnectionChanged -> {
                logStr += " -> ${p2pInfo?.isGroupOwner} / Formed: ${p2pInfo?.groupFormed}"
                copy(
                    p2pInfo = p2pInfo,
                    groupInfo = groupInfo ?: groupInfo
                )
            }

            WTWifiEvent.P2p.PeersChanged -> {
                copy(
                    directPeers = directPeers,
                    directServices = directServices ?: this.directServices
                    )
            }

            is WTWifiEvent.P2p.TxtRecordListener,
            is WTWifiEvent.P2p.ServiceResponseListener -> {
                copy(directServices = directServices ?: this.directServices)
            }

            is WTWifiEvent.WTWifi.Timeout -> {
                if (tick > 0) tick--
                logStr += " -> $tick"
                this
            }

            else -> {
                logStr += " -> Unprocessed transition event"
                this
            }
        }

        logd(tag, logStr)

        return newWifiDB
    }

    val hasWifiPermissions: Boolean
        get() = (state != WTWifiState.Disabled(false))

    val isWifiEnabled: Boolean
        get() = (state is WTWifiState.Enabled)
    val isGroupOwner: Boolean
        get() = ((true == p2pInfo?.groupFormed) && p2pInfo.isGroupOwner)
    val isGroupFormed: Boolean
        get() = (true == p2pInfo?.groupFormed)
    val groupIpAddress: InetAddress?
        get() = p2pInfo?.groupOwnerAddress
    val groupOwnerName: String?
        get() = (if (isGroupOwner) thisDevice?.deviceName else groupInfo?.owner?.deviceName)
    val groupOwnerDevice: WifiP2pDevice?
        get() = groupInfo?.owner
    val localIp: InetAddress?
        get() = groupInfo?.`interface`?.let { iFace ->
            getInterfaceIpAddress(iFace)
        }
    val deviceId: String
        get() = nodeId.id()
    val deviceUnique: String
        get() = nodeId.unique()

    fun resetAll(): WTWifiDB = WTWifiDB(
        nodeId = nodeId,
        state = WTWifiState.Disabled())
}

