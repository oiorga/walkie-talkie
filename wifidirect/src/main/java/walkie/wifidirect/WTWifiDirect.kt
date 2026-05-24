package walkie.wifidirect

import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pConfig.GROUP_OWNER_INTENT_MIN
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import kotlinx.coroutines.CoroutineScope
import walkie.talkie.api.wtsystem.NodeIdInt
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
    val nodeId: NodeIdInt,
    val scope: CoroutineScope,
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

    suspend fun requestPeersInfo():  WTWifiDirectResult {
        val tag = "requestPeersInfo/${randomString(2u)}"

        logd(tag, "Enter")

        if (!checkWifiDPermission()) {
            logd(TAGKClass, tag, "Not enough Wi Fi Permissions")
            return WTWifiDirectResult.LocalError.NoWifiPermissions
        }

        val ch = channel ?: run {
            logd(TAGKClass, tag, "Channel not initialized")
            return WTWifiDirectResult.LocalError.ChannelNotInitialized
        }

        val peerList = awaitP2pRequest { callback ->
            manager.requestPeers(ch) { peers ->
                callback(peers) }
        }

        /*
        val str = peerList.deviceList.joinToString(" ") { device ->
            "${device.deviceName}/${device.deviceAddress}"
        }
        */

        logd(TAGKClass, tag, "NEW Peers: " +
                peerList.deviceList.joinToString(" ") { device ->
                    "${device.deviceName}/${device.deviceAddress}"
                }
        )

        return WTWifiDirectResult.Data(peerList)
    }

    suspend fun requestDeviceInfo(): WTWifiDirectResult {
        val tag = "requestDeviceInfo/${randomString(2u)}"

        logd(tag, "Enter")

        if (!checkWifiDPermission()) {
            logd(TAGKClass, tag, "Not enough Wi Fi Permissions")
            return WTWifiDirectResult.LocalError.NoWifiPermissions
        }

        val ch = channel ?: run {
            logd(TAGKClass, tag, "Channel not initialized")
            return WTWifiDirectResult.LocalError.ChannelNotInitialized
        }

        val device = awaitP2pRequest<WifiP2pDevice?> { callback ->
            manager.requestDeviceInfo(ch) { device ->
                callback(device)
            }
        }

        logd(
            tag, "Device: ${device?.deviceName} ${device?.deviceAddress} ${if (true == device?.isGroupOwner) "Group Owner" else ""} "
        )
        return if (null != device) WTWifiDirectResult.Data(device) else WTWifiDirectResult.LocalError.NoData
    }

    suspend fun requestGroupInfo(): WTWifiDirectResult {
        val tag = "requestGroupInfo/${randomString(2u)}"

        logd(tag, "Enter")

        if (!checkWifiDPermission()) {
            logd(TAGKClass, tag, "Not enough Wi Fi Permissions")
            return WTWifiDirectResult.LocalError.NoWifiPermissions
        }

        val ch = channel ?: run {
            logd(TAGKClass, tag, "Channel not initialized")
            return WTWifiDirectResult.LocalError.ChannelNotInitialized
        }

        val groupInfo = awaitP2pRequest<WifiP2pGroup?> { callback ->
            manager.requestGroupInfo(ch) { gInfo ->
                callback(gInfo)
            }
        } ?: run {
            logd(TAGKClass, tag, "No group info available")
            return WTWifiDirectResult.LocalError.NoData
        }

        logd(
            tag,
            "Group Owner: ${groupInfo.owner?.deviceName} -> " +
                    groupInfo.clientList?.joinToString(" ") { device -> device.deviceName })

        return WTWifiDirectResult.Data(groupInfo)
    }

    suspend fun removeGroup(): WTWifiDirectResult {
        val tag = "removeGroup/${randomString(2u)}"

        logd(tag, "Enter")

        if (!checkWifiDPermission()) {
            logd(TAGKClass, tag, "Not enough Wi Fi Permissions")
            return WTWifiDirectResult.LocalError.NoWifiPermissions
        }

        val ch = channel ?: run {
            logd(TAGKClass, tag, "Channel not initialized")
            return WTWifiDirectResult.LocalError.ChannelNotInitialized
        }

        when (val res = awaitP2pAction { listener ->
            manager.removeGroup(ch, listener)
        }) {
            is CallbackResult.Success -> {
                logd(
                    TAGKClass,
                    tag,
                    "Success"
                )
                return WTWifiDirectResult.Success
            }
            is CallbackResult.Failure -> {
                val error = WTWifiDirectResult.wifiP2pError(res.reason ?: WifiP2pManager.ERROR)
                logd(
                    TAGKClass,
                    tag,
                    "Failure: Reason: ${error.errStr}"
                )
                return error
            }
        }
    }

    suspend fun connect(device: WifiP2pDevice,
                        config: WifiP2pConfig = WifiP2pConfig().apply {
                            deviceAddress = device.deviceAddress
                            groupOwnerIntent = GROUP_OWNER_INTENT_MIN
                            wps.setup = WpsInfo.PBC }
    ): WTWifiDirectResult {
        val tag = "connect/${randomString(2u)}"

        logd(tag, "Enter ${device.uniqueWifiId()}")

        if (!checkWifiDPermission()) {
            logd(TAGKClass, tag, "Not enough Wi Fi Permissions")
            return WTWifiDirectResult.LocalError.NoWifiPermissions
        }

        val ch = channel ?: run {
            logd(TAGKClass, tag, "Channel not initialized")
            return WTWifiDirectResult.LocalError.ChannelNotInitialized
        }

        return when (val res = awaitP2pAction { listener ->
            manager.connect(
                ch,
                config,
                listener
            )
        }) {
            is CallbackResult.Success -> {
                logd(
                    TAGKClass,
                    tag,
                    "onSuccess: " +
                            "Connect to: ${device.uniqueWifiId()}  ${device.deviceAddress} GO = ${device.isGroupOwner} Success"
                )
                WTWifiDirectResult.Success
            }

            is CallbackResult.Failure -> {
                val error = WTWifiDirectResult.wifiP2pError(res.reason ?: WifiP2pManager.ERROR)
                logd(
                    WTWifiDirectManager.Companion.TAGKClass,
                    tag,
                    "onFailure: Connect to: ${device.uniqueWifiId()}  ${device.deviceAddress} GO = ${device.isGroupOwner} Fail reason: ${error.errStr}"
                )
                error
            }
        }
    }

    suspend fun cancelConnect(): WTWifiDirectResult {
        val tag = "cancelConnect/${randomString(2u)}"

        logd(tag, "Enter")

        if (!checkWifiDPermission()) {
            logd(TAGKClass, tag, "Not enough Wi Fi Permissions")
            return WTWifiDirectResult.LocalError.NoWifiPermissions
        }

        val ch = channel ?: run {
            logd(TAGKClass, tag, "Channel not initialized")
            return WTWifiDirectResult.LocalError.ChannelNotInitialized
        }

        return when (val res = awaitP2pAction { listener ->
            manager.cancelConnect(ch, listener)
        }) {
            is CallbackResult.Success -> {
                logd(WTWifiDirectManager.Companion.TAGKClass, tag, "onSuccess:")
                WTWifiDirectResult.Success
            }
            is CallbackResult.Failure -> {
                val error = WTWifiDirectResult.wifiP2pError(res.reason ?: WifiP2pManager.ERROR)

                logd(
                    WTWifiDirectManager.Companion.TAGKClass, tag, "onFailure: ${error.errStr}"
                )
                error
            }
        }
    }

    suspend fun requestConnectionInfo(): WTWifiDirectResult {
        val tag = "requestConnectionInfo/${randomString(2u)}"

        logd(tag, "Enter")

        if (!checkWifiDPermission()) {
            logd(TAGKClass, tag, "Not enough Wi Fi Permissions")
            return WTWifiDirectResult.LocalError.NoWifiPermissions
        }

        val ch = channel ?: run {
            logd(TAGKClass, tag, "Channel not initialized")
            return WTWifiDirectResult.LocalError.ChannelNotInitialized
        }

        val wifiP2pInfo: WifiP2pInfo = awaitP2pRequest { callback ->
            manager.requestConnectionInfo(ch) { info ->
                callback(info)
            }
        } ?: run {
            logd(TAGKClass, tag, "No connection info available")
            return WTWifiDirectResult.LocalError.NoData
        }

        logd(
            TAGKClass,
            tag,
            "Exit: ${wifiP2pInfo.groupFormed}  ${wifiP2pInfo.isGroupOwner} ${wifiP2pInfo.groupOwnerAddress}"
        )

        return WTWifiDirectResult.Data(wifiP2pInfo)
    }



}