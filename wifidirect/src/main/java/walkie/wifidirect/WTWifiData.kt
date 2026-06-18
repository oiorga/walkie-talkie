package walkie.wifidirect

import android.net.wifi.p2p.WifiP2pDevice

enum class ConnectionStatus {
    Null,
    NoWTService,
    NotConnected,
    /* NotWTConnected, */
    InProgress,
    Connected,
    AgedOut,
    Renew,
    Retry,
    Fail
}

data class WTWifiDirectServiceInfo (
    var id: String? = null,
    var unique: String? = null,
    var rnd: String? = null,
    var localServerPort: String? = null
) {
    private fun isNull(): Boolean {
        return ((null == id) || (null == unique) || (null == rnd) || (null == localServerPort))
    }

    fun isNotNull(): Boolean {
        return (!isNull())
    }
}

data class WTWifiDirectPeerInfo (
    val p2pInfo: WifiP2pDevice,
    var directWifiConnection: ConnectionStatus = ConnectionStatus.Null,
    var wtServiceInfo: WTWifiDirectServiceInfo? = null,
) {
    val isGroupOwner: Boolean
        get() = p2pInfo.isGroupOwner
    val name: String
        get() = p2pInfo.deviceName
    val address: String
        get() = p2pInfo.deviceAddress

    companion object {
        /* Connecting in Progress */
        const val CIP = 15
        /* Fail Cool Down */
        const val FCD = 1
    }
    var stateCounter: Int = 0
        private set

    val wtService: Boolean
        get() = (wtServiceInfo?.isNotNull() == true)

    fun cipInit() {
        stateCounter = CIP
    }
    fun stateCounterTick(sct: Int? = null): Int {
        stateCounter = sct ?: stateCounter
        stateCounter = (if (stateCounter > 0) (stateCounter - 1) else 0)
        return stateCounter
    }
    fun cip(): Boolean {
        return ((0 < stateCounter) && (CIP >= stateCounter))
    }

    fun fcdInit() {
        stateCounter = FCD + CIP + 1
    }
    fun fcd(): Boolean {
        return (CIP < stateCounter )
    }

    fun uniqueWifiId(): String = p2pInfo.uniqueWifiId()

    fun wifiId(): String = p2pInfo.deviceName

    val wifiId: String = p2pInfo.deviceName
}

internal fun WifiP2pDevice.uniqueWifiId(): String {
    return ("${this.deviceName}.${this.deviceAddress}")
}
