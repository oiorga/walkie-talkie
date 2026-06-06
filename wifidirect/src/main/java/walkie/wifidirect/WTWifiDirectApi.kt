package walkie.wifidirect

import android.net.wifi.p2p.WifiP2pDevice

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
    object WifiNull: WTWifiState()
    object WifiNoPermissions: WTWifiState()
    object WifiEnabled: WTWifiState()
    object WifiDisabled: WTWifiState()
    object WifiPeersScanning: WTWifiState()
    object WifiConnecting: WTWifiState()
}
