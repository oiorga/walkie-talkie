package walkie.wifidirect

import android.net.wifi.p2p.WifiP2pManager

sealed class WTWifiDirectResult<out T> {
    abstract val errStr: String

    companion object {
        fun p2pError(reason: Int): Error.P2p =
            when (reason) {
                WifiP2pManager.ERROR ->
                    Error.P2p.InternalError
                WifiP2pManager.P2P_UNSUPPORTED ->
                    Error.P2p.Unsupported
                WifiP2pManager.BUSY ->
                    Error.P2p.Busy
                else ->
                    Error.P2p.InternalError
            }
    }

    object Success: WTWifiDirectResult<Nothing>() {
        override val errStr: String = "Success"
    }

    data class Data<out T>(val data: T) : WTWifiDirectResult<T>() {
        override val errStr = "SuccessData"
    }

    fun <T> WTWifiDirectResult<T>.dataOrNull(): T? {
        return (this as? WTWifiDirectResult.Data)?.data
    }

    inline fun <T> WTWifiDirectResult<T>.onData(block: (T) -> Unit) {
        if (this is WTWifiDirectResult.Data<T>) {
            block(data)
        }
    }

    fun <T> WTWifiDirectResult.Data<T>.onData(block: (T) -> Unit) {
        block(data)
    }

    sealed class Error: WTWifiDirectResult<Nothing>() {
        sealed class P2p(val errId: Int) : Error() {
            abstract val p2pString: String
            override val errStr
                get() = "($errId) -> $p2pString"
            object InternalError : P2p(WifiP2pManager.ERROR) {
                override val p2pString = "INTERNAL ERROR"
            }

            object Unsupported : P2p(WifiP2pManager.P2P_UNSUPPORTED) {
                override val p2pString = "P2P UNSUPPORTED"
            }

            object Busy : P2p(WifiP2pManager.BUSY) {
                override val p2pString = "BUSY"
            }
        }

        sealed class App : Error() {
            object ChannelNotInitialized : App() {
                override val errStr = "Channel not initialized"
            }

            object NoWifiPermissions : App() {
                override val errStr = "Not enough Wi Fi Permissions"
            }

            object InvalidState : App() {
                override val errStr = "Invalid State"
            }
        }
    }
}
