package walkie.wifidirect

interface WTWifiDirectEnv {
    fun checkWifiPermission(): Boolean
}

sealed class WTWifiEvent {
    object Timeout: WTWifiEvent()

    object WifiEnabled: WTWifiEvent()
    object WifiDisabled: WTWifiEvent()
    object WifiPermissionGranted: WTWifiEvent()
    object WifiPermissionWithdrawn: WTWifiEvent()
    object WifiPeersChanged: WTWifiEvent()
    object WifiConnectionChanged: WTWifiEvent()
    object WifiThisDeviceChanged: WTWifiEvent()
}
