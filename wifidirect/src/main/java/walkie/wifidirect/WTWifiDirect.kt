package walkie.wifidirect

import android.annotation.SuppressLint
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pConfig.GROUP_OWNER_INTENT_MIN
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import kotlinx.coroutines.CoroutineScope
import walkie.talkie.api.wtsystem.PipeMessageType
import walkie.util.CallbackResult
import walkie.util.api.PipeMuxInt
import walkie.util.api.RemoteCallMuxInt
import walkie.util.awaitResult
import walkie.util.awaitValue
import walkie.util.generic.PipeMux
import walkie.util.generic.RemoteCallMux
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString

class WTWifiDirect(
    private val manager: WifiP2pManager,
    private var channel: WifiP2pManager.Channel,
    private var env: WTWifiDirectEnv,
    scope: CoroutineScope,
    private val _channelMux: PipeMuxInt<Any, PipeMessageType> = PipeMux(),
    private val _remoteCallMux: RemoteCallMuxInt = RemoteCallMux()
) :
    PipeMuxInt<Any, PipeMessageType> by _channelMux,
    RemoteCallMuxInt by _remoteCallMux {
    companion object {
        const val TAG = "WTWifiDirect"
        val TAGKClass = WTWifiDirect::class
    }
    val tag = TAG

    init {
        logging(true)
    }

    fun checkWifiPermissions(): Boolean {
        val tag = "checkWifiPermissions/${randomString(2u)}"
        val res = env.checkWifiPermissions()
        logd(tag, ": $res")
        return res
    }

    fun channelClose() {
        channel.close()
    }

    @SuppressLint("MissingPermission")
    suspend fun requestPeersInfo(): WTWifiDirectResult<WifiP2pDeviceList> {
        val tag = "requestPeersInfo/${randomString(2u)}"

        logd(tag, "Enter")

        val res: WTWifiDirectResult<WifiP2pDeviceList> =
            p2pRequest(tag) { ch, callback ->
                manager.requestPeers(ch) { peers ->
                    callback(peers)
                }
            }

        (res as? WTWifiDirectResult.Data<WifiP2pDeviceList>)?.let {
            logd(
                TAGKClass,
                tag,
                "NEW Peers: " +
                        it.data.deviceList.joinToString(" ") { device ->
                            "${device.deviceName}/${device.deviceAddress}"
                        }
            )
        }

        return res
    }

    @SuppressLint("MissingPermission")
    suspend fun requestDeviceInfo(): WTWifiDirectResult<WifiP2pDevice?> {
        val tag = "requestDeviceInfo/${randomString(2u)}"

        logd(tag, "Enter")

        val res: WTWifiDirectResult<WifiP2pDevice?> =
            p2pRequest(tag) { ch, callback ->
                manager.requestDeviceInfo(ch) { device ->
                    callback(device)
                }
            }

        (res as? WTWifiDirectResult.Data<WifiP2pDevice?>)?.let {
            logd(
                TAGKClass,
                tag,
                "Device: ${it.data?.deviceName} ${it.data?.deviceAddress} ${if (true == it.data?.isGroupOwner) "Group Owner" else ""} "
            )
        }

        return res
    }

    @SuppressLint("MissingPermission")
    suspend fun requestGroupInfo(): WTWifiDirectResult<WifiP2pGroup?> {
        val tag = "requestGroupInfo/${randomString(2u)}"

        logd(tag, "Enter")

        val res: WTWifiDirectResult<WifiP2pGroup?> =
            p2pRequest(tag) { ch, callback ->
                manager.requestGroupInfo(ch) { info ->
                    callback(info)
                }
            }

        (res as? WTWifiDirectResult.Data<WifiP2pGroup?>)?.let {
            logd(
                TAGKClass,
                tag,
                "Group Owner: ${it.data?.owner?.deviceName} -> " +
                        it.data?.clientList?.joinToString(" ") { device -> device.deviceName }
            )
        }

        return res
    }

    @SuppressLint("MissingPermission")
    suspend fun removeGroup(): WTWifiDirectResult<Unit> {
        val tag = "removeGroup/${randomString(2u)}"

        logd(tag, "Enter")

        return p2pAction(tag,
            "Success.",
            "Failed",
        ) { ch, listener ->
            manager.removeGroup(
                ch,
                listener
            )
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(
        device: WifiP2pDevice,
        config: WifiP2pConfig = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            groupOwnerIntent = GROUP_OWNER_INTENT_MIN
            wps.setup = WpsInfo.PBC
        }
    ): WTWifiDirectResult<Unit> {
        val tag = "connect/${randomString(2u)}"

        logd(tag, "Enter connect to ${device.uniqueWifiId()}")

        return p2pAction(tag,
            "Success connecting to device: ${device.uniqueWifiId()}",
            "Failed connecting to device: ${device.uniqueWifiId()} - ",
        ) { ch, listener ->
            manager.connect(
                ch,
                config,
                listener
            )
        }
    }

    suspend fun cancelConnect(): WTWifiDirectResult<Unit> {
        val tag = "cancelConnect/${randomString(2u)}"

        logd(tag, "Enter")

        return p2pAction(tag,
            "Success cancelling connecting.",
            "Failed cancelling connecting:",
        ) { ch, listener ->
            manager.cancelConnect(
                ch,
                listener
            )
        }
    }

    suspend fun clearServiceRequests(): WTWifiDirectResult<Unit> {
        val tag = "clearServiceRequests/${randomString(2u)}"

        logd(tag, "Enter")

        return p2pAction(tag,
            "Success clearing service requests.",
            "Failed clearing service requests:",
        ) { ch, listener ->
            manager.clearServiceRequests(
                ch,
                listener
            )
        }
    }

    suspend fun clearLocalServices(): WTWifiDirectResult<Unit> {
        val tag = "clearLocalServices/${randomString(2u)}"

        logd(tag, "Enter")

        return p2pAction(tag,
            "Success clearing local service.",
            "Failed clearing local service:",
        ) { ch, listener ->
            manager.clearLocalServices(
                ch,
                listener
            )
        }
    }

    @SuppressLint("NewApi")
    suspend fun stopPeersDiscovery(): WTWifiDirectResult<Unit> {
        val tag = "stopPeerDiscovery/${randomString(2u)}"

        logd(tag, "Entry")

        return p2pAction(tag,
            "Success stopping peers discovery.",
            "Failed stopping peers discovery:",
        ) { ch, listener ->
            manager.stopListening(
                ch,
                listener
            )
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun discoverServices(): WTWifiDirectResult<Unit> {
        val tag = "discoverServices/${randomString(2u)}"

        return p2pAction(tag,
            "Success starting discovering services.",
            "Failed starting discovering services:",
        ) { ch, listener ->
            manager.discoverServices(
                ch,
                listener
            )
        }
    }

    suspend fun requestConnectionInfo(): WTWifiDirectResult<WifiP2pInfo?> {
        val tag = "requestConnectionInfo/${randomString(2u)}"

        logd(tag, "Enter")

        val res: WTWifiDirectResult<WifiP2pInfo?> =
            p2pRequest<WifiP2pInfo>(tag) { ch, callback ->
                manager.requestConnectionInfo(ch) { info ->
                    callback(info)
                }
            }

        (res as? WTWifiDirectResult.Data)?.let {
            logd(
                TAGKClass,
                tag,
                "Exit: ${it.data?.groupFormed}  ${it.data?.isGroupOwner} ${it.data?.groupOwnerAddress}"
            )
        }
        return res
    }

    @SuppressLint("MissingPermission")
    suspend fun addLocalService(instanceName: String,
                                serviceType: String,
                                record: Map<String, String>): WTWifiDirectResult<Unit> {
        val tag = "addLocalService/${randomString(2u)}"

        logd(tag, "Entry: $record")

        return p2pAction(tag,
            "Success adding local service.",
            "Failed adding local service:",
        ) { ch, listener ->
            manager.addLocalService(
                ch,
                WifiP2pDnsSdServiceInfo.newInstance(
                    instanceName,
                    serviceType,
                    record
                ),
                listener
            )
        }
    }

    suspend fun removeLocalService(instanceName: String,
                                   serviceType: String,
                                   record: Map<String, String>): WTWifiDirectResult<Unit> {
        val tag = "removeLocalService/${randomString(2u)}"

        logd(tag, "Entry: $record")

        return p2pAction(tag,
            "Success removing local service.",
            "Failed removing local service:",
        ) { ch, listener ->
            manager.removeLocalService(
                ch,
                WifiP2pDnsSdServiceInfo.newInstance(
                    instanceName,
                    serviceType,
                    record
                ),
                listener
            )
        }
    }

    suspend fun removeServiceRequest(instanceName: String,
                                     serviceType: String): WTWifiDirectResult<Unit> {
        val tag = "removeServiceRequest/${randomString(2u)}"
        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance(instanceName, serviceType)

        logd(tag, "Entry:")

        return p2pAction(tag,
            "Success removing service request.",
            "Failed removing service request:",
        ) { ch, listener ->
            manager.removeServiceRequest(
                ch,
                serviceRequest,
                listener)
        }
    }

    suspend fun addServiceRequest(instanceName: String,
                                  serviceType: String): WTWifiDirectResult<Unit> {
        val tag = "addServiceRequest/${randomString(2u)}"

        logd(tag, "Entry")

        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance(instanceName, serviceType)

        return p2pAction(tag,
            "Success adding service request.",
            "Failed adding service request:",
            ) { ch, listener ->
            manager.addServiceRequest(
                ch,
                serviceRequest,
                listener)
        }
    }

    fun registerServiceListeners(
        dnsSdTxtRecordListener: WifiP2pManager.DnsSdTxtRecordListener,
        dnsSdServiceResponseListener: WifiP2pManager.DnsSdServiceResponseListener
        ) {
        val tag = "registerServiceListeners/${randomString(2u)}"

        logd(tag, "Registering listeners...")

        manager.setDnsSdResponseListeners(channel, dnsSdServiceResponseListener, dnsSdTxtRecordListener)
    }

    internal suspend inline fun p2pAction(
        tag: String,
        successMsg: String? = null,
        failureMsg: String? = null,
        crossinline action:
            (WifiP2pManager.Channel,
             WifiP2pManager.ActionListener) -> Unit
    ): WTWifiDirectResult<Unit> {
        if (!checkWifiPermissions()) {
            logd(TAGKClass, tag,
                "Not enough Wi Fi Permissions")
            return WTWifiDirectResult.Error.App.NoWifiPermissions
        }

        return when (
            val res = awaitP2pAction { listener ->
                action(channel, listener)
            }
        ) {
            is CallbackResult.Success -> {
                if (null != successMsg) logd(
                    TAGKClass,
                    tag,
                    successMsg)
                WTWifiDirectResult.Success
            }
            is CallbackResult.Failure -> {
                val error =
                    WTWifiDirectResult.p2pError(
                        res.reason
                            ?: WifiP2pManager.ERROR
                    )

                if (null != failureMsg) logd(
                    TAGKClass,
                    tag,
                    "$failureMsg: ${error.errStr}"
                )
                error
            }
        }
    }

    internal suspend inline fun <T>p2pRequest(
        tag: String,
        crossinline action:
            (WifiP2pManager.Channel,
             (T) -> Unit) -> Unit
    ): WTWifiDirectResult<T> {
        if (!checkWifiPermissions()) {
            logd(
                TAGKClass, tag,
                "No Wi Fi Permissions"
            )
            return WTWifiDirectResult.Error.App.NoWifiPermissions
        }

        val data: T = awaitP2pRequest { callback ->
            action(channel, callback)
        }

        logd(TAGKClass,
            tag,
            data.toString())
        return WTWifiDirectResult.Data(data)
    }
}

internal suspend inline fun awaitP2pAction(
    crossinline action: (WifiP2pManager.ActionListener) -> Unit
): CallbackResult<Unit, Int> = awaitResult<Unit, Int> { listener ->
    action(object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
            listener.onSuccess(Unit)
        }

        override fun onFailure(reason: Int) {
            listener.onFailure(reason)
        }
    })
}

internal suspend inline fun <T>awaitP2pRequest(
    crossinline action: ((T) -> Unit) -> Unit
): T = awaitValue (action)

