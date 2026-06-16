package walkie.wifidirect

import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import walkie.talkie.api.wtsystem.NodeIdInt
import walkie.util.getEnclosingClassSimpleName
import walkie.util.getInterfaceIpAddress
import walkie.util.getRuntimeSimpleName
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString
import java.net.InetAddress

interface WTWifiDirectEnv {
    fun checkWifiPermissions(): Boolean
}

interface WTWifiEnabledInt<T> {
    val connecting: T
    val peersDiscovery: T
    val serviceDiscovery: T
    val advertiseLocalService: T
}

typealias WTWifiEnabledState = WTWifiEnabledInt<Boolean>
typealias WTWifiEnabledCommand = WTWifiEnabledInt<Boolean?>

sealed class WTWifiEvent {
    val description: String
        get() = "${getEnclosingClassSimpleName()}.${getRuntimeSimpleName()}"
    override fun toString(): String {
        return description
    }

    sealed class WTWifi: WTWifiEvent() {
        object Timeout: WTWifi()
        object Default: WTWifi()
        object WifiStop: WTWifi()
        object PeersDiscoveryStart: WTWifi()
        object PeersDiscoveryStop: WTWifi()
        object GroupInfoChanged: WTWifi()
        data class LocalServerPort(val value: Int? = null): WTWifi()

        data class Enabled(
            override val connecting: Boolean? = null,
            override val peersDiscovery: Boolean? = null,
            override val serviceDiscovery: Boolean? = null,
            override val advertiseLocalService: Boolean? = null,
        ): WTWifiEnabledInt<Boolean?>, WTWifi()
    }

    sealed class P2p: WTWifiEvent() {
        object WifiEnabled: P2p()
        data class WifiDisabled (
            val wifiPermissions: Boolean = false
        ) : P2p()
        object PermissionGranted: P2p()
        object PermissionWithdrawn: P2p()
        object PeersChanged: P2p()
        object ConnectionChanged: P2p()
        object ThisDeviceChanged: P2p()
        object PeersDiscoveryStarted: P2p()
        object PeersDiscoveryStopped: P2p()
        data class TxtRecordListener(
            val fullDomain: String,
            val record: Map<String, String>,
            val device: WifiP2pDevice
        ): P2p()
        data class ServiceResponseListener(
            val instanceName: String,
            val registrationType: String,
            val resourceType: WifiP2pDevice
        ): P2p()
    }
}

sealed class WTWifiState {
    val description: String
        get() = "${getEnclosingClassSimpleName()}.${getRuntimeSimpleName()}"
    override fun toString(): String {
        return description
    }
    data class Disabled (
        val wifiPermissions: Boolean = false,
    ) : WTWifiState()

    data class Enabled(
        override val connecting: Boolean = false,
        override val peersDiscovery: Boolean = false,
        override val serviceDiscovery: Boolean = false,
        override val advertiseLocalService: Boolean = false,
    ): WTWifiEnabledInt<Boolean>, WTWifiState()
}

data class WTWifiDB(
    val nodeId: NodeIdInt,
    val state: WTWifiState,
    val thisDevice: WifiP2pDevice? = null,
    val p2pInfo: WifiP2pInfo? = null,
    val groupInfo: WifiP2pGroup? = null,
    val localServerPort: Int? = null,
    val peers: List<WifiP2pDevice> = emptyList(),
    val directPeers: Map<String, WTWifiDirectPeerInfo> = emptyMap(),
    val directServices: Map<String, WTWifiDirectServiceInfo> = emptyMap(),
    var tick: Int = 0
    ) {
    companion object {
        const val TAG = "WTWifiDB"
        val TAGKClass = WTWifiDB::class
        const val DiscoveryCountdown = 17
        const val ConnectCountdown = 25
        const val FailCooldown = 1
        const val RestartChannelTimeout = (DiscoveryCountdown * 3)
        const val DiscoveryVsAdvertisementRatio = (2F / 3F)
    }

    val tag = TAG

    private var nextEvent: WTWifiEvent? = null

    fun consumeNextEvent(): WTWifiEvent? =
        nextEvent.also {
            nextEvent = null
        }


    init {
        logging(true)
    }

    fun transition(
        event: WTWifiEvent,
        thisDevice: WifiP2pDevice? = null,
        p2pInfo: WifiP2pInfo? = null,
        groupInfo: WifiP2pGroup? = null,
        directPeers: Map<String, WTWifiDirectPeerInfo> = emptyMap(),
        directServices: Map<String, WTWifiDirectServiceInfo>? = emptyMap()
    ): WTWifiDB {
        val tag = "transition/${randomString(2U)}"
        var logStr = "\n\tevent: ${event.description} state: ${state.toString()}"

        val newWifiDB = when (event) {
            WTWifiEvent.P2p.WifiEnabled -> {
                if (state is WTWifiState.Enabled) this
                else {
                    nextEvent = WTWifiEvent.WTWifi.Default
                    copy(
                        state = WTWifiState.Enabled(),
                        thisDevice = thisDevice ?: this.thisDevice
                    )
                }
            }

            is WTWifiEvent.P2p.WifiDisabled -> {
                if (this.state == WTWifiState.Disabled(event.wifiPermissions)) this
                else copy(state = WTWifiState.Disabled(event.wifiPermissions))
            }

            WTWifiEvent.P2p.ThisDeviceChanged -> {
                logStr += "\n\tdeviceName: ${thisDevice?.deviceName}"
                copy(
                    thisDevice = thisDevice,
                    groupInfo = groupInfo ?: this.groupInfo
                )
            }

            WTWifiEvent.WTWifi.GroupInfoChanged -> {
                logStr += "\n\tgroupInfo: ${groupInfo?.owner?.deviceName} -> ${this.groupInfo?.owner?.deviceName}"
                copy(groupInfo = groupInfo)
            }

            WTWifiEvent.P2p.ConnectionChanged -> {
                logStr += "\n\tp2pInfo: ${p2pInfo?.isGroupOwner} / Formed: ${p2pInfo?.groupFormed}"
                copy(
                    p2pInfo = p2pInfo,
                    groupInfo = groupInfo ?: groupInfo
                )
            }

            WTWifiEvent.P2p.PeersChanged -> {
                copy(
                    directPeers = directPeers,
                    directServices = directServices ?: this.directServices
                )
            }

            is WTWifiEvent.P2p.TxtRecordListener,
            is WTWifiEvent.P2p.ServiceResponseListener -> {
                copy(directServices = directServices ?: this.directServices)
            }

            is WTWifiEvent.WTWifi.LocalServerPort -> {
                event.value!!
                copy(localServerPort = event.value)
            }

            WTWifiEvent.WTWifi.Timeout,
            WTWifiEvent.WTWifi.Default -> {
                if (tick > 0) tick--
                logStr += "\n\ttick: $tick"
                processDefault()
            }

            else -> {
                logStr += "\n\tUnprocessed transition event"
                this
            }
        }

        newWifiDB.nextEvent = nextEvent

        newWifiDB.nextEvent?.let { eve ->
            logStr += "\n\tnextEvent: ${eve.toString()}"
        }

        logd(tag, logStr)

        return newWifiDB
    }

    fun processDefault(): WTWifiDB {
        val tag = "processDefault"
        val currentState = state

        logd(tag, "tick: $tick state: ${state.toString()}")

        return when (currentState) {
            is WTWifiState.Enabled -> {
                var peersDiscovery: Boolean? = null
                var serviceDiscovery: Boolean? = null
                var advertiseLocalService: Boolean? = null
                var connecting: Boolean? = null
                var sendEvent = false
                if (state.peersDiscovery) {
                    if (tick == 0) {
                        if (directServices.isNotEmpty()) {
                            logd(tag, "directPeers: $directServices")
                            connecting = true
                            tick = ConnectCountdown
                        } else {
                            tick = DiscoveryCountdown
                            serviceDiscovery = true
                            sendEvent = true
                        }
                    }
                } else {
                    peersDiscovery = true
                    if (!state.serviceDiscovery) {
                        tick = DiscoveryCountdown
                        serviceDiscovery = true
                    }
                    sendEvent = true
                }

                if (serviceDiscovery == true) {
                    /*
                    if (!isGroupFormed || (isGroupFormed && isGroupOwner)) {
                        advertiseLocalService = true
                        sendEvent = true
                    }
                    */
                    advertiseLocalService = !(isGroupFormed && !isGroupOwner)
                    sendEvent = true
                }

                if (state.connecting) {
                    if (tick == 0) {
                        peersDiscovery = true
                        sendEvent = true
                    }
                }

                val newWifiDB = copy(
                    state = WTWifiState.Enabled(
                        connecting ?: state.connecting,
                        peersDiscovery ?: state.peersDiscovery,
                        serviceDiscovery ?: state.serviceDiscovery,
                        advertiseLocalService ?: state.advertiseLocalService
                    )
                )

                if (sendEvent) {
                    nextEvent = WTWifiEvent.WTWifi.Enabled(
                        connecting,
                        peersDiscovery,
                        serviceDiscovery,
                        advertiseLocalService
                    )
                    logd(tag, "nextEvent: $nextEvent")
                }
                newWifiDB
            }
            else -> {
                this
            }
        }
    }

    val isReady: Boolean
        get() = (null != localServerPort &&
                null != localIp &&
                null != groupServerPort)
    val restartCountDownOn: Boolean
        get() = (!isReady && directPeers.isEmpty() && directServices.isEmpty())
    val hasWifiPermissions: Boolean
        get() = (state != WTWifiState.Disabled(false))
    val isWifiEnabled: Boolean
        get() = (state is WTWifiState.Enabled)
    val isGroupOwner: Boolean
        get() = ((true == p2pInfo?.groupFormed) && p2pInfo.isGroupOwner)
    val isGroupFormed: Boolean
        get() = (true == p2pInfo?.groupFormed)
    val groupIpAddress: InetAddress?
        get() = p2pInfo?.groupOwnerAddress
    val groupOwnerName: String?
        get() = (if (isGroupOwner) thisDevice?.deviceName else groupInfo?.owner?.deviceName)
    val groupOwnerDevice: WifiP2pDevice?
        get() = groupInfo?.owner
    val localIp: InetAddress?
        get() = groupInfo?.`interface`?.let { iFace ->
            getInterfaceIpAddress(iFace)
        }
    val groupOwner: WTWifiDirectPeerInfo?
        get() = let { _ ->
            val p2pOwner = if (isGroupOwner) thisDevice else groupInfo?.owner
            directPeers[p2pOwner?.uniqueWifiId()]
                ?: p2pOwner?.let { device -> WTWifiDirectPeerInfo(device) }
        }
    val groupServerPort: Int?
        get() =
            (if (isGroupOwner) (localServerPort)
            else if (isGroupFormed) (directPeers[groupOwnerDevice?.uniqueWifiId()]?.wtServiceInfo?.localServerPort?.toInt())
            else (null))

    val deviceId: String
        get() = nodeId.id()
    val deviceUnique: String
        get() = nodeId.unique()

    fun reset(): WTWifiDB = copy(
        nodeId = this.nodeId,
        state = WTWifiState.Disabled(),
        thisDevice = this.thisDevice,
        p2pInfo = null,
        groupInfo = null,
        localServerPort = this.localServerPort,
        peers = emptyList(),
        directPeers = emptyMap(),
        directServices = emptyMap(),
        tick = 0
    )
}

