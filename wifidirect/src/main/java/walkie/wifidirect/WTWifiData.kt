package walkie.wifidirect

import android.net.wifi.p2p.WifiP2pDevice
import android.os.Build
import androidx.annotation.RequiresApi

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
    companion object {
        const val WT_SERVICE_WALKIETALKIE = "WalkieTalkie"
        const val WT_SERVICE_WALKIETALKIE_GO = "WalkieTalkieGO"
        const val WT_SERVICE_ID = "PeerId"
        const val WT_SERVICE_UNIQUE = "PeerUnique"
        const val WT_SERVICE_RND = "Rnd"
        const val WT_SERVICE_LOCAL_SERVER_PORT = "LocalServerPort"
    }

    private fun isNull(): Boolean {
        return ((null == id) || (null == unique) || (null == rnd) || (null == localServerPort))
    }

    fun isNotNull(): Boolean {
        return (!isNull())
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
data class WTWifiDirectPeerInfo (
    val p2pInfo: WifiP2pDevice,
    var isGroupOwner: Boolean = p2pInfo.isGroupOwner,
    val name: String = p2pInfo.deviceName,
    val address: String = p2pInfo.deviceAddress,
    var directWifiConnection: ConnectionStatus = ConnectionStatus.Null,
    var wtServiceInfo: WTWifiDirectServiceInfo? = null,
    var age: Long = 0L
) {
    companion object {
        /* Connecting in Progress */
        const val CIP = 10
        /* Fail Cool Down */
        const val FCD = 1
        const val MAXAGE = (WTWiFiDirect.DiscoveryCountdown * 3)
    }
    var stateCounter: Int = 0
        private set

    private val wtService: Boolean
        get() = (wtServiceInfo?.isNotNull() == true)

    fun wtService() : Boolean = wtService

    val maxAge: Int
        get() = MAXAGE

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
