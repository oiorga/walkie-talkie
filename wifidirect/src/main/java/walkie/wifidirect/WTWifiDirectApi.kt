package walkie.wifidirect

import android.net.wifi.p2p.WifiP2pDevice

interface WTWifiDirectEnv {
    fun checkWifiPermission(): Boolean
}

sealed class WTWifiEvent {
    object Timeout : WTWifiEvent()
    object WifiStop : WTWifiEvent()
    object WifiEnabled : WTWifiEvent()
    object WifiDisabled : WTWifiEvent()
    object WifiPermissionGranted : WTWifiEvent()
    object WifiPermissionWithdrawn : WTWifiEvent()
    object PeersChanged : WTWifiEvent()
    object ConnectionChanged : WTWifiEvent()
    object ThisDeviceChanged : WTWifiEvent()
    object PeersDiscoveryStarted : WTWifiEvent()
    object PeersDiscoveryStopped : WTWifiEvent()
    data class TxtRecordListener(
        val fullDomain: String,
        val record: Map<String, String>,
        val device: WifiP2pDevice
    ): WTWifiEvent()
    data class ServiceResponseListener(
        val instanceName: String,
        val registrationType: String,
        val resourceType: WifiP2pDevice
    ): WTWifiEvent()
}

sealed class WTWifiState {
    object WifiNull: WTWifiState()
    object WifiNoPermissions: WTWifiState()
    object WifiEnabled: WTWifiState()
    object WifiDisabled: WTWifiState()
    object WifiPermissionMissing: WTWifiState()
    object WifiPeersScanning: WTWifiState()
    object WifiConnecting: WTWifiState()
}
