package walkie.wifidirect

import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import walkie.util.getInterfaceIpAddress

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

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun WTWiFiDirect.wtWifiDirectInfo() : String {
    var info = ""

    info += "P2P Info: " + if (null == this.wtWifiP2pInfo.get()) "null" else ""
    info += "\n\tWifiD is: " + (if (this.wifiP2pEnable) "Enabled" else "Disabled") +
            "\n\tis Owner: " + this.wtWifiP2pInfo.get()?.isGroupOwner +
            "\n\tis Formed: " + this.wtWifiP2pInfo.get()?.groupFormed +
            "\n\tGroup Address: " + this.wtWifiP2pInfo.get()?.groupOwnerAddress +
            "\n\tLocal IPAddress: " + this.wtWifiGroupInfo.get()?.`interface`?.let { getInterfaceIpAddress(it) }

    info = info + "\nGroup Info: " + "${this.wtWifiGroupInfo.get()?.owner?.uniqueWifiId()}/${wtGroupOwnerName}" + " " + wtGroupOwner?.p2pInfo?.uniqueWifiId()
    var cList = ""
    this.wtWifiGroupInfo.get()?.clientList?.forEach { device ->
        cList += "\t[${device.deviceName}]"
    }

    info = info + "\n\tCurrent Device: ${thisDevice?.uniqueWifiId()} $deviceUid" +
            "\n\tGroup Owner: " + this.wtWifiGroupInfo.get()?.owner?.deviceName +
            "\n\tGroup IP Address: " + this.wtGroupIp + " $wtGroupServerPort"
            //"\n\tOwner device address: " + wtWifiGroupInfo?.owner?.deviceAddress +
            "\n\tis Owner: " + this.wtWifiGroupInfo.get()?.isGroupOwner + " " + wtIsGroupOwner + " " + this.wtWifiGroupInfo.get()?.owner?.isGroupOwner +
            "\n\tis Formed: " + wtIsGroupFormed +
            //"\n\tPassphrase: " + wtWifiGroupInfo?.passphrase +
            "\n\tnetworkName: " + this.wtWifiGroupInfo.get()?.networkName +
            "\n\tClient List: " + cList +
            "\n\tInterface: " + this.wtWifiGroupInfo.get()?.`interface` + "" + this.wtWifiGroupInfo.get()?.`interface`?.let { getInterfaceIpAddress(it) }
            //"\n\tnetworkId: " + wtWifiGroupInfo?.networkId +
            //"\n\tIPAddress: " + this.wtWifiGroupInfo?.`interface`?.let { getInterfaceIpAddress(it) }

    info += "\nWIFI Peers List "
    info += "\n discoverPeers: ($discoveryCountdown/${serviceDiscoveryActive()}) " + if (peersDiscoveryReset()) "Reset" else if (serviceDiscoveryActive()) "Active" else "Paused"
    info += "\n advertise Service: ${serviceAdvAdd()}"
    info += "\n connectingAllowed: newWT: ${newWTDevice(newWTDevice())} ($discoveryCountdown) " + if (connectingAllowed()) "Yes" else "No"
    info += "\n connectTo: ${connectToDevice?.uniqueWifiId()} ${connectToDevice?.directWifiConnection}"
    info += "\n failCoolDown: ($failCooldown) " + (if (wifiP2PEngineOk()) "Ok" else "NOT Ok") + " " + wtWifiFailure()
    info += "\n restartCountDown: ${channelCountdown()}"

    this.directWifiPeers.forEach { (_, device) ->
        val sInfo = directWifiPeers[device.uniqueWifiId()]?.wtServiceInfo
        val serviceInfo = if (null != sInfo) ("${sInfo.id} ${sInfo.unique} ${sInfo.rnd} ${sInfo.localServerPort}") else ("null")

        info += "\n " + device.name + " [${device.uniqueWifiId()}]" +
                "\n\t" + "walkieTalkie service: $serviceInfo" + " ${device.wtService()} " +
                "\n\t" + "isGroupOwner: " + device.isGroupOwner +
                //"\n\t" + "maAddress: " + device.address +
                "\n\t" + "age: " + device.age +
                "\n\t" + "connection status: ${device.directWifiConnection} ${device.stateCounter}"
    }

    info += "\nWIFI WalkieTalkie Peers List "
    this.directWifiPeers.forEach { (_, device) ->
        info += if (device.wtService()) "\n${device.uniqueWifiId()} " else " "
    }

    return info
}
