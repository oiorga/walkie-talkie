package walkie.wifidirect

import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import walkie.util.CallbackResult
import walkie.util.awaitResult
import walkie.util.awaitValue
import walkie.util.getInterfaceIpAddress
import walkie.util.randomString

internal fun WifiP2pGroup.logD(tag: String = "WifiP2pGroup") {
    Log.d(
        tag,
        "WifiP2pGroup:" +
                "\n\tnetworkName: " + this.networkName +
                "\n\tisGroupOwner: " + this.isGroupOwner +
                "\n\tdeviceName: " + this.owner?.deviceName +
                "\n\tclientList: " + this.clientList +
                "\n\tpassphrase: " + this.passphrase +
                "\n\tinterface: " + this.`interface` + "/" + getInterfaceIpAddress(this.`interface`!!)
    )
}

internal fun WifiP2pInfo.logD(tag: String = "WifiP2pInfo") {
    Log.d(
        tag,
        "WifiP2pInfo:" +
                "\n\tisGroupOwner: " + this.isGroupOwner +
                "\n\tgroupFormed: " + this.groupFormed +
                "\n\tgroupOwnerAddress: " + this.groupOwnerAddress?.hostAddress
    )
}

internal fun WTWifiDirectManager.wtWifiDirectInfo() : String {
    val tag = "wtWifiDirectInfo/${randomString(2u)}"

    var info = ""

    info += "P2P Info: " + if (null == this.wtWifiP2pInfo) "null" else ""
    info += "\n\tWifiD is: " + (if (this.wifiP2pEnable) "Enabled" else "Disabled") +
            "\n\tis Owner: " + this.wtWifiP2pInfo?.isGroupOwner +
            "\n\tis Formed: " + this.wtWifiP2pInfo?.groupFormed +
            "\n\tGroup Address: " + this.wtWifiP2pInfo?.groupOwnerAddress +
            "\n\tLocal IPAddress: " + this.wtWifiGroupInfo?.`interface`?.let { getInterfaceIpAddress(it) }

    info += "\nGroup Info: " + "${this.wtWifiGroupInfo?.owner?.uniqueWifiId()}/${wtGroupOwnerName}" + " " + wtGroupOwner?.p2pInfo?.uniqueWifiId()

    info += "\n\tCurrent Device: ${thisDevice?.uniqueWifiId()} $deviceUid" +
            "\n\tGroup Owner: " + this.wtWifiGroupInfo?.owner?.deviceName +
            "\n\tGroup IP Address: " + this.wtGroupIp + " $wtGroupServerPort" +
            //"\n\tOwner device address: " + wtWifiGroupInfo?.owner?.deviceAddress +
            "\n\tis Owner: " + this.wtWifiGroupInfo?.isGroupOwner + " " + wtIsGroupOwner + " " + this.wtWifiGroupInfo?.owner?.isGroupOwner +
            "\n\tis Formed: " + wtIsGroupFormed +
            //"\n\tPassphrase: " + wtWifiGroupInfo?.passphrase +
            "\n\tnetworkName: " + this.wtWifiGroupInfo?.networkName +
            "\n\tClient List: " + this.wtWifiGroupInfo?.clientList?.joinToString(" ") { "\t[${it.deviceName}]" } +
            "\n\tInterface: " + this.wtWifiGroupInfo?.`interface` + " " + this.wtWifiGroupInfo?.`interface`?.let { getInterfaceIpAddress(it) }
    //"\n\tnetworkId: " + wtWifiGroupInfo?.networkId +
    //"\n\tIPAddress: " + this.wtWifiGroupInfo?.`interface`?.let { getInterfaceIpAddress(it) }

    val currentState = wtWifi.state
    val peersDiscoveryState = (currentState is WTWifiState.Enabled && (currentState as WTWifiState.Enabled).peersDiscovery)
    val serviceDiscoveryActive = (currentState is WTWifiState.Enabled && (currentState as WTWifiState.Enabled).serviceDiscovery)
    val serviceAdvAdd = (currentState is WTWifiState.Enabled && (currentState as WTWifiState.Enabled).advertiseLocalService)
    val connecting = wtWifi.isWTServicePeerPresent

    info += "\nWIFI Peers List "
    info += "\n Tick: ${wtWifi.tick}"
    info += "\n Discovery/Services/LocalService: $peersDiscoveryState/$serviceDiscoveryActive/$serviceAdvAdd"
    info += "\n connectingAllowed: " + if (connecting) "Yes" else "No"
    info += "\n connectTo: ${connectToDevice?.uniqueWifiId()} ${connectToDevice?.directWifiConnection}"
    /* info += "\n failCoolDown: ($failCooldown) " + (if (wifiP2PEngineOk()) "Ok" else "NOT Ok") + " " + wtWifiFailure() */
    info += "\n restartCountDown: ${channelCountdown()}"

    this.directWTPeers.forEach { (_, device) ->
        val sInfo = directWTPeers[device.uniqueWifiId()]?.wtServiceInfo
        val serviceInfo = if (null != sInfo) ("${sInfo.id} ${sInfo.unique} ${sInfo.rnd} ${sInfo.localServerPort}") else ("null")

        info += "\n " + device.name + " [${device.uniqueWifiId()}]" +
                "\n\t" + "walkieTalkie service: $serviceInfo" + " ${device.wtService} " +
                "\n\t" + "isGroupOwner: " + device.isGroupOwner +
                //"\n\t" + "maAddress: " + device.address +
                "\n\t" + "connection status: ${device.directWifiConnection} ${device.stateCounter}"
    }

    info += "\nWIFI WalkieTalkie Peers List "
    this.directWTPeers.forEach { (_, device) ->
        info += "\n${device.uniqueWifiId()} ${device.wtService}"
    }

    /* logd(tag, info) */

    return info
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

