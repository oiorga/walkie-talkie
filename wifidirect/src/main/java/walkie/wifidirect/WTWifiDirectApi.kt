package walkie.wifidirect

import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import java.net.InetAddress

interface WTWifiDirectEnv {
    fun checkWifiPermission(): Boolean
}

sealed class WTWifiEvent {
    sealed class WTWifi: WTWifiEvent() {
        object Timeout : WTWifi()
        object WifiStop : WTWifi()
        object PeersDiscoveryStart : WTWifi()
        object PeersDiscoveryStop : WTWifi()
    }

    sealed class P2p: WTWifiEvent() {
        object WifiEnabled : P2p()
        object WifiDisabled : P2p()
        object PermissionGranted : P2p()
        object PermissionWithdrawn : P2p()
        object PeersChanged : P2p()
        object ConnectionChanged : P2p()
        object ThisDeviceChanged : P2p()
        object PeersDiscoveryStarted : P2p()
        object PeersDiscoveryStopped : P2p()
        data class TxtRecordListener(
            val fullDomain: String,
            val record: Map<String, String>,
            val device: WifiP2pDevice
        ) : P2p()
        data class ServiceResponseListener(
            val instanceName: String,
            val registrationType: String,
            val resourceType: WifiP2pDevice
        ) : P2p()
    }
}

sealed class WTWifiState {
    sealed class Inactive : WTWifiState() {
        object NoWifiPermissions : Inactive()
        object Disabled : Inactive()
    }

    sealed class Enabled : WTWifiState() {
        object Ready: Enabled()
        object PeersScanning : Enabled()
        object Connecting : Enabled()
        object ServiceDiscovery: Enabled()
    }
}

data class WTWifiDB(
    val deviceUid: String,
    val state: WTWifiState,
    val thisDevice: WifiP2pDevice? = null,
    val p2pInfo: WifiP2pInfo? = null,
    val groupInfo: WifiP2pGroup? = null,
    val peers: List<WifiP2pDevice> = emptyList()) {

    val isGroupOwner: Boolean
        get() = ((true == p2pInfo?.groupFormed) && p2pInfo.isGroupOwner)
    val isGroupFormed: Boolean
        get() = (true == p2pInfo?.groupFormed)
    val groupIp: InetAddress?
        get() = p2pInfo?.groupOwnerAddress
    val groupOwnerName: String?
        get() = (if (isGroupOwner) thisDevice?.deviceName else groupInfo?.owner?.deviceName)
    val groupOwnerDevice: WifiP2pDevice?
        get() = groupInfo?.owner
}

