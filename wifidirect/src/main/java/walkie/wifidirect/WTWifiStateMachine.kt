package walkie.wifidirect

import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import walkie.talkie.api.wtsystem.NodeIdInt
import walkie.util.getEnclosingClassSimpleName
import walkie.util.getInterfaceIpAddress
import walkie.util.getRuntimeSimpleName
import walkie.util.logd
import walkie.util.logdAppend
import walkie.util.logging
import walkie.util.mesh.CountDown
import walkie.util.randomString
import java.net.InetAddress
import kotlin.random.Random
import kotlin.random.nextInt


enum class P2pConnection {
    Null,
    NoWTService,
    NotConnected,
    InProgress,
    Timeout,
    Connected,
    Retry,
    Fail
}

data class WTWifiDirectServiceInfo (
    var id: String,
    var unique: String,
    var rnd: String,
    var localServerPort: String
)

data class WTWifiDirectPeerInfo (
    val device: WifiP2pDevice,
    var p2pConnection: P2pConnection = P2pConnection.Null,
    var serviceInfo: WTWifiDirectServiceInfo? = null,
) {
    val isGroupOwner: Boolean
        get() = device.isGroupOwner
    val name: String
        get() = device.deviceName
    val address: String
        get() = device.deviceAddress
    val uniqueWifiId: String
        get() = device.uniqueWifiId()
    val wifiId: String
        get() = device.deviceName
    companion object {
        /* Connecting in Progress */
        const val CIP = 15
    }
    val wtService: Boolean
        get() = (serviceInfo != null)

    val cip = CountDown(CIP)
}

internal fun WifiP2pDevice.uniqueWifiId(): String {
    return ("${this.deviceName}.${this.deviceAddress}")
}


interface WTWifiDirectEnv {
    fun checkWifiPermissions(): Boolean
}

interface WTWifiCommandInt<T> {
    val removeGroup: T
    val peersDiscovery: T
    val serviceDiscovery: T
    val advertiseLocalService: T
}

interface WTWifiEnabledInt<T> {
    val connecting: T
    val peersDiscovery: T
    val serviceDiscovery: T
    val advertiseLocalService: T
}

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
        data class LocalServerPort(val value: Int? = null): WTWifi()
        object MergePeersServicesInfo: WTWifi()

        data class Command(
            override val removeGroup: Boolean? = null,
            override val peersDiscovery: Boolean? = null,
            override val serviceDiscovery: Boolean? = null,
            override val advertiseLocalService: Boolean? = null,
        ): WTWifiCommandInt<Boolean?>, WTWifi()
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
    val p2pPeers: List<WifiP2pDevice> = emptyList(),
    val directWTPeers: Map<String, WTWifiDirectPeerInfo> = emptyMap(),
    val directServices: Map<String, WTWifiDirectServiceInfo> = emptyMap(),
    private var connectToDeviceCached: WTWifiDirectPeerInfo? = null,
    var tick: Int = 0,
    private var cycle: Int  = CYCLE + Random.nextInt(CYCLE / 2)
    ) {
    companion object {
        const val TAG = "WTWifiDB"
        val TAGKClass = WTWifiDB::class
        const val CYCLE = 7
        const val WT_SERVICE_WALKIETALKIE = "WalkieTalkie"
        const val WT_SERVICE_WALKIETALKIE_GO = "WalkieTalkieGO"
        const val WT_SERVICE_ID = "PeerId"
        const val WT_SERVICE_UNIQUE = "PeerUnique"
        const val WT_SERVICE_RND = "Rnd"
        const val WT_SERVICE_LOCAL_SERVER_PORT = "LocalServerPort"
    }
    private val serviceDiscoveryCycle: Int
        get() = (1 * cycle + 1)
    private val advLocalServiceCycle: Int
        get() = (2 * cycle - 1)
    private val enabledCycleCountdown: Int
        get() = 4 * cycle
    private val restartChannelTimeout: Int
        get() = (enabledCycleCountdown * 3)

    val tag = TAG

    private var nextEvent: WTWifiEvent? = null

    fun consumeNextEvent(): WTWifiEvent? =
        nextEvent.also {
            nextEvent = null
        }

    init {
        logging(true)
        tick = restartChannelTimeout
    }

    fun transition(
        event: WTWifiEvent,
        thisDevice: WifiP2pDevice? = null,
        p2pInfo: WifiP2pInfo? = null,
        groupInfo: WifiP2pGroup? = null,
        p2pPeers: List<WifiP2pDevice> = emptyList(),
        directServices: Map<String, WTWifiDirectServiceInfo>? = emptyMap()
    ): WTWifiDB {
        val tag = "transition/${randomString(2U)}"

        logdAppend(tag, "\n\tevent: ${event.description} state: ${state.toString()}")

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
                logdAppend(tag, "\n\tdeviceName: ${thisDevice?.deviceName}")
                copy(
                    thisDevice = thisDevice,
                    groupInfo = groupInfo ?: this.groupInfo,
                    directWTPeers = if (null == groupInfo) emptyMap() else this.directWTPeers,
                    directServices = if (null == groupInfo) emptyMap() else this.directServices
                )
            }

            WTWifiEvent.P2p.ConnectionChanged -> {
                logdAppend(tag, "\n\tp2pInfo: ${p2pInfo?.isGroupOwner} / Formed: ${p2pInfo?.groupFormed}")
                copy(
                    p2pInfo = p2pInfo,
                    groupInfo = groupInfo ?: this.groupInfo,
                    directWTPeers = if (null == groupInfo) emptyMap() else this.directWTPeers,
                    directServices = if (null == groupInfo) emptyMap() else this.directServices
                )
            }

            WTWifiEvent.P2p.PeersChanged -> {
                logdAppend(tag,"\n\tPeersChanged: ${
                    p2pPeers.joinToString(" ") { device ->
                        "${device.deviceName} " 
                    }
                }")

                    nextEvent = WTWifiEvent.WTWifi.MergePeersServicesInfo
                copy(p2pPeers = p2pPeers)
            }

            is WTWifiEvent.P2p.TxtRecordListener,
            is WTWifiEvent.P2p.ServiceResponseListener -> {
                logdAppend(tag, "\n\tPeersChanged: ${
                    directServices?.values?.joinToString(" ") { serviceInfo ->
                        "${serviceInfo.id} ${serviceInfo.unique} ${serviceInfo.localServerPort}"
                    }
                }")

                nextEvent = WTWifiEvent.WTWifi.MergePeersServicesInfo
                copy(directServices = directServices ?: this.directServices)
            }

            is WTWifiEvent.WTWifi.LocalServerPort -> {
                event.value!!
                copy(localServerPort = event.value)
            }

            WTWifiEvent.WTWifi.Timeout,
            WTWifiEvent.WTWifi.Default -> {
                if (tick > 0) tick--
                if ((tick == 0) && isGroupFormed && isConnected) {
                    tick = restartChannelTimeout
                }
                logdAppend(tag, "\n\ttick: $tick")
                processDefault()
            }

            WTWifiEvent.WTWifi.MergePeersServicesInfo -> {
                copy(directWTPeers = mergePeersServicesInfo(),
                    p2pPeers = emptyList())
            }

            else -> {
                logdAppend(tag, "\n\tUnprocessed transition event")
                this
            }
        }

        newWifiDB.nextEvent = nextEvent

        newWifiDB.nextEvent?.let { eve ->
            logdAppend(tag, "\n\tnextEvent: ${eve.toString()}")
        }

        logd(tag)

        return newWifiDB
    }

    fun processDefault(): WTWifiDB {
        val tag = "processDefault"

        return processEnabled()
    }

    fun processEnabled(): WTWifiDB {
        val tag = "processEnabled"
        val currentState = state

        logd(tag, "tick: $tick state: ${state.toString()}")

        return when (currentState) {
            is WTWifiState.Enabled -> {
                var peersDiscovery: Boolean? = null
                var serviceDiscovery: Boolean? = null
                var advertiseLocalService: Boolean? = null
                var connecting: Boolean? = null
                var removeGroup: Boolean? = null

                if (!state.peersDiscovery) {
                    peersDiscovery = true
                    serviceDiscovery = false
                    advertiseLocalService = true
                    tick = restartChannelTimeout
                } else if (!isConnected) {
                    if (isWTServicePeerPresent && !isGroupFormed && !state.connecting) {
                        logd(tag, "directWTPeers: $directWTPeers")
                        connecting = true
                    }

                    if (!state.serviceDiscovery) {
                        serviceDiscovery = true
                    }

                    if (state.serviceDiscovery && ((tick % serviceDiscoveryCycle) == 0)) {
                        serviceDiscovery = false
                    }

                    if (!state.advertiseLocalService && !isGroupFormed) {
                        advertiseLocalService = true
                    }

                    if (state.advertiseLocalService && !isGroupFormed && ((tick % advLocalServiceCycle) == 0)) {
                        advertiseLocalService = false
                    }

                    if (state.advertiseLocalService && isGroupFormed && !isGroupOwner) {
                        advertiseLocalService = false
                    }
                } else {
                    if (isGroupOwner && state.advertiseLocalService && ((tick % advLocalServiceCycle) == 0)) {
                        advertiseLocalService = false
                    }

                    if (isGroupOwner && !state.advertiseLocalService) {
                        advertiseLocalService = true
                    }

                    if (!isGroupOwner && state.advertiseLocalService) {
                        advertiseLocalService = false
                    }

                    if (state.serviceDiscovery) {
                        serviceDiscovery = false
                    }
                }

                if ((removeGroup != null) || (peersDiscovery != null) || (serviceDiscovery != null) || (advertiseLocalService != null)) {
                    nextEvent = WTWifiEvent.WTWifi.Command(
                        removeGroup,
                        peersDiscovery,
                        serviceDiscovery,
                        advertiseLocalService
                    )
                }
                logd(tag, "nextEvent: $nextEvent")

                if ((connecting != null) || (peersDiscovery != null) || (serviceDiscovery != null) || (advertiseLocalService != null)) {
                    copy(
                        state = WTWifiState.Enabled(
                            connecting ?: state.connecting,
                            peersDiscovery ?: state.peersDiscovery,
                            serviceDiscovery ?: state.serviceDiscovery,
                            advertiseLocalService ?: state.advertiseLocalService
                        )
                    )
                } else {
                    this
                }
            }

            else -> {
                this
            }
        }
    }

    fun restartChannel() {
        tick = 0
    }
    fun resetChannelCountdown() {
        tick = restartChannelTimeout
    }
    val restartChannel: Boolean
        get() = (0 == tick)
    val isConnected: Boolean
        get() =  (null != localServerPort &&
                null != localIp &&
                null != groupServerPort &&
                isGroupFormed)
    
    val restartCountDownOn: Boolean
        get() = run {
            val tag = "restartCountDownOn"
            logd(
                tag,
                "!isConnected: ${!isConnected} " +
                        "directWTPeers: ${directWTPeers.isEmpty()} " +
                        "directServices: ${directServices.isEmpty()} "
            )
            (!isConnected || directWTPeers.isEmpty())
        }
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
            directWTPeers[p2pOwner?.uniqueWifiId()]
                ?: p2pOwner?.let { device -> WTWifiDirectPeerInfo(device) }
        }
    val groupServerPort: Int?
        get() =
            (if (isGroupOwner) (localServerPort)
            else if (isGroupFormed) (directWTPeers[groupOwnerDevice?.uniqueWifiId()]?.serviceInfo?.localServerPort?.toInt())
            else (null))
    val isWTServicePeerPresent: Boolean
        get() = directWTPeers.values.any { it.wtService }
    val localServiceRecord: Map<String, String>?
        get() =
            if (localServerPort == null)
                null
            else
                mapOf(
                    WT_SERVICE_WALKIETALKIE to WT_SERVICE_WALKIETALKIE,
                    WT_SERVICE_ID to deviceId,
                    WT_SERVICE_UNIQUE to deviceUnique,
                    WT_SERVICE_RND to randomString(8U),
                    WT_SERVICE_LOCAL_SERVER_PORT to localServerPort.toString()
                )
    val cachedServiceRecord: Map<String, String>?
        get() =
            if ((state is WTWifiState.Enabled) && (state as WTWifiState.Enabled).advertiseLocalService)
                localServiceRecord
            else
                null
    val deviceId: String
        get() = nodeId.id()
    val deviceUnique: String
        get() = nodeId.unique()

    val connectToDevice: WTWifiDirectPeerInfo?
        get() = run {
            if (connectToDeviceCached?.p2pConnection == P2pConnection.InProgress) {
                return@run connectToDeviceCached
            }
            connectToDeviceCached = selectNextConnectToDevice()
            connectToDeviceCached
        }

    private fun selectNextConnectToDevice():  WTWifiDirectPeerInfo? {
        if (isConnected)
            return null
        
        if (groupOwner?.wtService != null && !isGroupOwner) {
            return groupOwner
        }
        
        directWTPeers.values.forEach { peer ->
            if (peer.device == thisDevice) {
                error("KBOOM1")
            }

            if (peer.device.uniqueWifiId() == thisDevice?.uniqueWifiId()) {
                error("KBOOM2")
            }

            if (peer.device.deviceName == thisDevice?.deviceName) {
                error("KBOOM3")
            }

            if (peer.device.deviceAddress == thisDevice?.deviceName) {
                error("KBOOM3")
            }

            if (!peer.wtService) {
                peer.p2pConnection = P2pConnection.NoWTService
                return@forEach
            }
            when (peer.p2pConnection) {
                P2pConnection.Null,
                P2pConnection.NotConnected,
                P2pConnection.NoWTService -> {
                    peer.p2pConnection = P2pConnection.NotConnected
                    return@selectNextConnectToDevice peer
                }
                P2pConnection.Fail,
                P2pConnection.Timeout -> {
                    peer.p2pConnection = P2pConnection.Retry
                    peer.cip.startRandom()
                }
                P2pConnection.Retry -> {
                    if (0 == peer.cip.tick())
                        peer.p2pConnection = P2pConnection.NotConnected
                }
                P2pConnection.InProgress -> {
                    error("Should not get here")
                }
                /*
                P2pConnection.NoWTService -> {
                    peer.p2pConnection = P2pConnection.NotConnected
                }
                */
                P2pConnection.Connected -> { }
            }
        }
        return null
    }

    fun reset(): WTWifiDB = copy(
            nodeId = this.nodeId,
            state = WTWifiState.Disabled(),
            thisDevice = this.thisDevice,
            p2pInfo = null,
            groupInfo = null,
            localServerPort = this.localServerPort,
            p2pPeers = emptyList(),
            directWTPeers = emptyMap(),
            directServices = emptyMap(),
            connectToDeviceCached = null,
            tick = restartChannelTimeout,
            cycle = CYCLE + Random.nextInt(CYCLE / 2)
        )

    fun mergePeersServicesInfo(): Map<String, WTWifiDirectPeerInfo> {
        val tag = "mergePeersServicesInfo/${randomString(2u)}"

        logdAppend(tag, "Entry: \n\t")

        val newDirectWifiPeers = directWTPeers.toMutableMap()

        p2pPeers.forEach { device ->
            newDirectWifiPeers[device.uniqueWifiId()] =
                newDirectWifiPeers[device.uniqueWifiId()] ?: WTWifiDirectPeerInfo(device)
            val wifiPeer = newDirectWifiPeers[device.uniqueWifiId()]!!

            val wtService = directServices[device.uniqueWifiId()]
            wifiPeer.serviceInfo = wtService ?: wifiPeer.serviceInfo

            logdAppend(tag, "[${thisDevice?.deviceName} ${device.uniqueWifiId()}  ${device.deviceName}] ")
        }

        logd(tag, "\nExit")

        return newDirectWifiPeers
    }
}
