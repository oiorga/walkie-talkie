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
import walkie.util.CallbackResult
import walkie.util.api.ChannelMessageType
import walkie.util.api.RemoteCallId
import walkie.util.api.RemoteCallMuxInt
import walkie.util.generic.ChannelMux
import walkie.util.generic.ChannelMuxInt
import walkie.util.generic.RemoteCallMux
import walkie.util.generic.typedCall
import walkie.util.logd
import walkie.util.randomString

class WTWifiDirect(
    val manager: WifiP2pManager,
    var channel: WifiP2pManager.Channel? = null,
    private val _channelMux: ChannelMuxInt<Any, ChannelMessageType> = ChannelMux(),
    private val _remoteCallMux: RemoteCallMuxInt = RemoteCallMux()
) :
    ChannelMuxInt<Any, ChannelMessageType> by _channelMux,
    RemoteCallMuxInt by _remoteCallMux {

    companion object {
        const val TAG = "WTWifiDirect"
        val TAGKClass = WTWifiDirect::class
    }

    val tag = TAG

    fun checkWifiDPermission(): Boolean {
        val tag = tag
        logd(tag, "checkWifiDPermission perm = ${remoteCall(RemoteCallId.RCCheckWifiDPermission)}")
        return (true == typedCall<Boolean>(RemoteCallId.RCCheckWifiDPermission))
    }

    @SuppressLint("MissingPermission")
    suspend fun requestPeersInfo(): WTWifiDirectResult<WifiP2pDeviceList> {
        val tag = "requestPeersInfo/${randomString(2u)}"

        logd(tag, "Enter")

        val res: WTWifiDirectResult<WifiP2pDeviceList> = p2pRequest(
            tag,
            "No peers info available"
        ) { ch, callback ->
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
                            "${device.deviceName}/${device.deviceAddress}" }
            )
        }

        return res
    }

    @SuppressLint("MissingPermission")
    suspend fun requestDeviceInfo(): WTWifiDirectResult<WifiP2pDevice?> {
        val tag = "requestDeviceInfo/${randomString(2u)}"

        logd(tag, "Enter")

        val res: WTWifiDirectResult<WifiP2pDevice?> = p2pRequest(
            tag,
            "No device info available"
        ) { ch, callback ->
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
    suspend fun requestGroupInfo(): WTWifiDirectResult<WifiP2pGroup> {
        val tag = "requestGroupInfo/${randomString(2u)}"

        logd(tag, "Enter")

        val res: WTWifiDirectResult<WifiP2pGroup> = p2pRequest(
            tag,
            "No group info available"
        ) { ch, callback ->
            manager.requestGroupInfo(ch) { info ->
                callback(info)
            }
        }

        (res as? WTWifiDirectResult.Data<WifiP2pGroup>)?.let {
            logd(
                TAGKClass,
                tag,
                "Group Owner: ${it.data.owner?.deviceName} -> " +
                        it.data.clientList?.joinToString(" ") { device -> device.deviceName }
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
            "Failed:",
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

    suspend fun requestConnectionInfo(): WTWifiDirectResult<WifiP2pInfo> {
        val tag = "requestConnectionInfo/${randomString(2u)}"

        logd(tag, "Enter")

        val res: WTWifiDirectResult<WifiP2pInfo> = p2pRequest<WifiP2pInfo>(
            tag,
            "No connection info available"
            ) { ch, callback ->
            manager.requestConnectionInfo(ch) { info ->
                callback(info)
            }
        }

        (res as? WTWifiDirectResult.Data)?.let {
            logd(
                TAGKClass,
                tag,
                "Exit: ${it.data.groupFormed}  ${it.data.isGroupOwner} ${it.data.groupOwnerAddress}"
            )
        }

        /*
        if (res is WTWifiDirectResult.Data) {
            logd(
                TAGKClass,
                tag,
                "Exit: ${res.data.groupFormed}  ${res.data.isGroupOwner} ${res.data.groupOwnerAddress}"
            )
        }
        */

        return res
    }

    @SuppressLint("MissingPermission")
    suspend fun addLocalService(record: Map<String, String>,
                                instanceName: String,
                                serviceType: String): WTWifiDirectResult<Unit> {
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

    suspend fun removeLocalService(record: Map<String, String>,
                                   instanceName: String,
                                   serviceType: String): WTWifiDirectResult<Unit> {
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
        val tag = "addServiceRequest/${randomString(2u)}"
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
}
