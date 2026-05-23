package walkie.wifidirect

import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
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

    private val errMap = mapOf(
        WifiP2pManager.P2P_UNSUPPORTED to "P2P UNSUPPORTED",
        WifiP2pManager.ERROR to "INTERNAL ERROR",
        WifiP2pManager.BUSY to "WifiP2pManager BUSY"
    )

    val errToString: (Int) -> String = { errCode ->
        errMap[errCode] ?: "Unknown Error"
    }

    suspend fun removeGroup() {
        val tag = "removeGroup/${randomString(2u)}"

        when (val res = awaitP2pAction { listener ->
            manager.removeGroup(channel, listener)
        }) {
            is CallbackResult.Success -> {
                logd(
                    TAGKClass,
                    tag,
                    "Success"
                )
            }
            is CallbackResult.Failure -> {
                val reasonToStr = errToString(res.reason ?: -1)

                logd(
                    TAGKClass,
                    tag,
                    "Failure: Reason(${res.reason}): $reasonToStr"
                )
            }
        }
    }

    fun checkWifiDPermission(): Boolean {
        val tag = tag
        logd(tag, "checkWifiDPermission perm = ${remoteCall(RemoteCallId.RCCheckWifiDPermission)}")
        return (true == typedCall<Boolean>(RemoteCallId.RCCheckWifiDPermission))
    }

    suspend fun requestPeersInfo():  WifiP2pDeviceList {
        val tag = "requestPeersInfo/${randomString(2u)}"
        val ch = channel ?: run {
            logd(TAGKClass, tag, "Channel not initialized")
            return WifiP2pDeviceList()
        }

        if (!checkWifiDPermission()) {
            logd(TAGKClass, tag, "Not enough Wi Fi Permissions")
            return WifiP2pDeviceList()
        }

        val peerList = awaitP2pRequest { callback ->
            manager.requestPeers(ch) { peers ->
                callback(peers) }
        }

        val str = peerList.deviceList.joinToString(" ") { device ->
            "${device.deviceName}/${device.deviceAddress}"
        }

        logd(TAGKClass, tag, "NEW Peers: $str")

        return peerList
    }

    suspend fun requestDeviceInfo(): WifiP2pDevice? {
        val tag = "requestDeviceInfo/${randomString(2u)}"
        val ch = channel ?: run {
            logd(TAGKClass, tag, "Channel not initialized")
            return null
        }

        if (!checkWifiDPermission()) {
            logd(TAGKClass, tag, "Not enough Wi Fi Permissions")
            return null
        }

        val device = awaitP2pRequest<WifiP2pDevice?> { callback ->
            manager.requestDeviceInfo(ch) { device ->
                callback(device)
            }
        }

        logd(
            tag, "Device: ${device?.deviceName} ${device?.deviceAddress} ${if (true == device?.isGroupOwner) "Group Owner" else ""} "
        )
        return device
    }

    suspend fun requestGroupInfo(): WifiP2pGroup? {
        val tag = "requestGroupInfo/${randomString(2u)}"
        val ch = channel ?: run {
            logd(TAGKClass, tag, "Channel not initialized")
            return null
        }

        if (!checkWifiDPermission()) {
            logd(TAGKClass, tag, "Not enough Wi Fi Permissions")
            return null
        }

        val groupInfo = awaitP2pRequest<WifiP2pGroup?> { callback ->
            manager.requestGroupInfo(ch) { gInfo ->
                callback(gInfo)
            }
        } ?: run {
            logd(TAGKClass, tag, "No group info available")
            return null
        }

        val clientList = groupInfo.clientList?.joinToString(" ") { device ->
            device.deviceName
        }

        logd(
            tag,
            "Group Owner: ${groupInfo.owner?.deviceName} -> $clientList"
        )

        return groupInfo
    }



}