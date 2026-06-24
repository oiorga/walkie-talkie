package walkie.wifidirect

import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.util.Log
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
    info += "\n\tWifi Permissions / State: ${(if (wifiP2pEnable) "Enabled" else "Disabled")} / ${(if (checkWifiPermissions()) "Yes" else "No")}" +
            "\n\tis Formed/Owner: ${wtWifi.isGroupFormed}/${wtWifi.isGroupOwner}" +
            "\n\tGroup Address: " + this.wtWifiP2pInfo?.groupOwnerAddress +
            "\n\tLocal IPAddress: " + this.wtLocalIp

    info += "\nGroup Info: " + wtGroupOwner?.device?.uniqueWifiId()

    info += "\n\tCurrent Device: ${thisDevice?.uniqueWifiId()} $deviceUid" +
            "\n\tGroup Owner: $wtGroupOwnerName" +
            "\n\tInterface: ${wtWifi.groupInterface} ${wtWifi.groupInterfaceIpAddress}" +
            "\n\tGroup IP Address: $wtGroupIp $wtGroupServerPort" +
            "\n\tis Formed/Owner: $wtIsGroupFormed/$wtIsGroupOwner "+
            "\n\tnetworkName: " + wtWifi.networkName +
            "\n\tClient List: " + wtWifi.clientList?.joinToString(" ") { "\t[${it.deviceName}]" }

    val currentState = wtWifi.state
    val peersDiscoveryState = (currentState is WTWifiState.Enabled && (currentState as WTWifiState.Enabled).peersDiscovery)
    val serviceDiscoveryActive = (currentState is WTWifiState.Enabled && (currentState as WTWifiState.Enabled).serviceDiscovery)
    val serviceAdvAdd = (currentState is WTWifiState.Enabled && (currentState as WTWifiState.Enabled).advertiseLocalService)
    val connecting = wtWifi.isWTServicePeerPresent
    val engineCoolingDownInfo = wtWifi.engineCoolingDownInfo()

    info += "\nTick / Reset Countdown: ${wtWifi.tick} / ${if (wtWifi.isReady) "Off" else channelCountdown}"
    engineCoolingDownInfo?.let {
        info += if (it.coolingDown) "\nLast P2p Error: ${it.err.errStr}" else ""
        info += if (it.coolingDown) "\nP2p Engine cool down: ${it.description}" else ""
    }

    info += "\nWIFI Peers List "
    info += "\n Discovery/Services/LocalService: $peersDiscoveryState/$serviceDiscoveryActive/$serviceAdvAdd"
    info += "\n connectingAllowed: " + if (connecting) "Yes" else "No"
    info += "\n connectTo: ${connectToDevice?.uniqueWifiId} ${connectToDevice?.p2pConnection} ${connectToDevice?.cip?.value}"

    this.directWTPeers.forEach { (_, device) ->
        val sInfo = directWTPeers[device.uniqueWifiId]?.serviceInfo
        val serviceInfo = if (null != sInfo) ("${sInfo.id} ${sInfo.unique} ${sInfo.rnd} ${sInfo.localServerPort}") else ("null")

        info += "\n " + device.name + " [${device.uniqueWifiId}]" +
                "\n\t" + "walkieTalkie service: $serviceInfo" + " ${device.wtService} " +
                "\n\t" + "isGroupOwner: " + device.isGroupOwner +
                //"\n\t" + "maAddress: " + device.address +
                "\n\t" + "connection status: ${device.p2pConnection} ${device.cip.value}"
    }

    info += "\nWIFI WalkieTalkie Peers List "
    this.directWTPeers.forEach { (key, device) ->
        info += "\n$key -> ${if (key != device.uniqueWifiId) "${device.uniqueWifiId} ???" else "" } ${device.wtService}"
    }

    /* logd(tag, info) */

    return info
}
