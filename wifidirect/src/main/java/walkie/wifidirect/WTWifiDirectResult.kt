package walkie.wifidirect

import android.net.wifi.p2p.WifiP2pManager

sealed class WTWifiDirectResult<out T> {
    abstract val errStr: String

    companion object {
        fun wifiP2pError(reason: Int): WifiP2pError =
            when (reason) {
                WifiP2pManager.ERROR ->
                    WifiP2pError.InternalError
                WifiP2pManager.P2P_UNSUPPORTED ->
                    WifiP2pError.Unsupported
                WifiP2pManager.BUSY ->
                    WifiP2pError.Busy
                else ->
                    WifiP2pError.InternalError
            }
    }

    object Success: WTWifiDirectResult<Nothing>() {
        override val errStr: String = "Success"
    }

    data class Data<out T>(val data: T) : WTWifiDirectResult<T>() {
        override val errStr = "SuccessData"
    }

    data class Exception(val throwable: Throwable): WTWifiDirectResult<Nothing>() {
        override val errStr: String = throwable.message ?: throwable::class.simpleName ?: "Unknown exception"
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

    sealed class WifiP2pError(val errId: Int) : WTWifiDirectResult<Nothing>() {
        object InternalError : WifiP2pError(WifiP2pManager.ERROR) {
            override val errStr = "($errId) -> INTERNAL ERROR"
        }
        object Unsupported : WifiP2pError(WifiP2pManager.P2P_UNSUPPORTED) {
            override val errStr = "($errId) ->P2P UNSUPPORTED"
        }
        object Busy : WifiP2pError(WifiP2pManager.BUSY) {
            override val errStr = "($errId) -> BUSY"
        }
    }

    sealed class LocalError : WTWifiDirectResult<Nothing>() {
        object ChannelNotInitialized : LocalError() {
            override val errStr = "Channel not initialized"
        }
        object NoWifiPermissions : LocalError() {
            override val errStr = "Not enough Wi Fi Permissions"
        }
        /*
        object NoData : LocalError() {
            override val errStr = "No Data Available"
        }
        */
    }
}
