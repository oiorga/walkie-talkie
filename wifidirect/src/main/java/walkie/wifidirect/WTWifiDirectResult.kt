package walkie.wifidirect

import android.net.wifi.p2p.WifiP2pManager

sealed class WTWifiDirectResult {
    abstract val errStr: String

    companion object {
        fun wifiP2pError(reason: Int): WTWifiDirectResult.WifiP2pError =
            when (reason) {
                WifiP2pManager.ERROR ->
                    WTWifiDirectResult.WifiP2pError.InternalError
                WifiP2pManager.P2P_UNSUPPORTED ->
                    WTWifiDirectResult.WifiP2pError.Unsupported
                WifiP2pManager.BUSY ->
                    WTWifiDirectResult.WifiP2pError.Busy
                else ->
                    WTWifiDirectResult.WifiP2pError.InternalError
            }
    }

    object Success: WTWifiDirectResult() {
        override val errStr: String = "Success"
    }

    data class Data<out T>(val data: T) : WTWifiDirectResult() {
        override val errStr = "SuccessData"
    }

    sealed class WifiP2pError(val errId: Int) : WTWifiDirectResult() {
        object InternalError : WifiP2pError(WifiP2pManager.ERROR) {
            override val errStr = "INTERNAL ERROR"
        }
        object Unsupported : WifiP2pError(WifiP2pManager.P2P_UNSUPPORTED) {
            override val errStr = "P2P UNSUPPORTED"
        }
        object Busy : WifiP2pError(WifiP2pManager.BUSY) {
            override val errStr = "BUSY"
        }
    }

    sealed class LocalError : WTWifiDirectResult() {
        object ChannelNotInitialized : LocalError() {
            override val errStr = "Channel not initialized"
        }
        object NoWifiPermissions : LocalError() {
            override val errStr = "Not enough Wi Fi Permissions"
        }
        object NoData : LocalError() {
            override val errStr = "No Data Available"
        }
    }
}
