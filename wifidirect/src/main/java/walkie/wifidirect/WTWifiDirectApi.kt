package walkie.wifidirect

import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import walkie.talkie.api.wtsystem.NodeIdInt
import walkie.util.getClassSimpleName
import walkie.util.getInterfaceIpAddress
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString
import java.net.InetAddress
import kotlin.reflect.KClass

interface WTWifiDirectEnv {
    fun checkWifiPermission(): Boolean
}

sealed class WTWifiEvent {
    override fun toString(): String = getClassSimpleName()

    sealed class WTWifi: WTWifiEvent() {
        object Timeout: WTWifi()
        object WifiStop: WTWifi()
        object PeersDiscoveryStart: WTWifi()
        object PeersDiscoveryStop: WTWifi()
        object GroupInfoChanged: WTWifi()
    }

    sealed class P2p: WTWifiEvent() {
        object WifiEnabled: P2p()
        object WifiDisabled: P2p()
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
    override fun toString(): String = getClassSimpleName()
    sealed class Inactive: WTWifiState() {
        object NoWifiPermissions: Inactive()
        object Disabled: Inactive()
    }

    sealed class Enabled: WTWifiState() {
        object Ready: Enabled()
        object PeersScanning: Enabled()
        object Connecting: Enabled()
        object ServiceDiscovery: Enabled()
    }
}

data class WTWifiDB(
    val nodeId: NodeIdInt,
    val state: WTWifiState,
    val thisDevice: WifiP2pDevice? = null,
    val p2pInfo: WifiP2pInfo? = null,
    val groupInfo: WifiP2pGroup? = null,
    val peers: List<WifiP2pDevice> = emptyList(),
    val directPeers: Map<String, WTWifiDirectPeerInfo> = emptyMap(),
    val directWifiServices: Map<String, WTWifiDirectServiceInfo> = emptyMap()
    ) {

    companion object {
        const val TAG = "WTWifiDB"
        val TAGKClass = WTWifiDB::class
    }

    val tag = TAG

    init {
        logging(true)
    }

    fun transition(
        event: WTWifiEvent,
        thisDevice: WifiP2pDevice? = null,
        p2pInfo: WifiP2pInfo? = null,
        groupInfo: WifiP2pGroup? = null,
        peers: List<WifiP2pDevice> = emptyList(),
    ): WTWifiDB {
        val tag = "transition/${randomString(2U)}"

        logd(tag, event.toString())

        return when (event) {
            WTWifiEvent.P2p.WifiEnabled ->
                if (state is WTWifiState.Enabled) this
                else copy(
                    state = WTWifiState.Enabled.Ready,
                    thisDevice = thisDevice ?: this.thisDevice
                )

            WTWifiEvent.P2p.WifiDisabled ->
                copy(state = WTWifiState.Inactive.Disabled)

            WTWifiEvent.P2p.ThisDeviceChanged -> {
                logd(tag, "${thisDevice?.deviceName}")
                copy(thisDevice = thisDevice)
            }

            WTWifiEvent.WTWifi.GroupInfoChanged -> {
                logd(tag, "${groupInfo?.owner?.deviceName} -> ${this.groupInfo?.owner?.deviceName}")
                copy(groupInfo = groupInfo)
            }

            WTWifiEvent.P2p.ConnectionChanged -> {
                logd(tag, "P2pConnection Owner: ${p2pInfo?.isGroupOwner} / Formed: ${p2pInfo?.groupFormed}")
                copy(p2pInfo = p2pInfo)
            }

            else -> {
                logd(tag, "Unprocessed transition event: ${event.toString()}")
                this
            }
        }
    }

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
        state = WTWifiState.Inactive.Disabled)
}

