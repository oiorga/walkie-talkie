package walkie.comm.prm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import walkie.comm.WTCommDispatchEventId
import walkie.comm.WTCommPeerInfo
import walkie.comm.WTIPMesh
import walkie.comm.ip.WTIPComm
import walkie.comm.ip.WTIPCommPacketType
import walkie.talkie.api.wtModule.ModuleOpImpl
import walkie.talkie.api.wtModule.ModuleOpInt
import walkie.util.generic.MessageBus
import walkie.talkie.api.wtchat.ChatGroupType
import walkie.talkie.api.wtcomm.CommPacket
import walkie.talkie.api.wtcomm.WTMedium
import walkie.talkie.api.wtsystem.NodeIdInt
import walkie.talkie.api.wtModule.MessageBusId
import walkie.talkie.api.wtModule.PipeMessageType
import walkie.talkie.api.wtModule.WTModOpArg
import walkie.util.api.MessageBusIdInt
import walkie.util.api.BusMessageInt
import walkie.util.api.MeshDispatchEventId
import walkie.util.api.MeshKey
import walkie.util.api.MessageBusInt
import walkie.util.generic.EventDispatcher
import walkie.util.generic.EventDispatcherInt
import walkie.util.generic.BusMessage
import walkie.util.inetToIpString
import walkie.util.logd
import walkie.util.logging
import walkie.util.mesh.Mesh
import walkie.util.randomString
import java.net.InetAddress

class WTPRMComm (
    private val node: NodeIdInt,
    val scope: CoroutineScope,
    private val _pipeMux: MessageBusInt<PipeMessageType, Any> = MessageBus(),
    private val _callBackList: EventDispatcherInt<Any> = EventDispatcher(),
    private val _moduleOp: ModuleOpInt = ModuleOpImpl(_pipeMux)
) :
    MessageBusInt<PipeMessageType, Any> by _pipeMux,
    ModuleOpInt by _moduleOp,
    EventDispatcherInt<Any> by _callBackList
{
    val wtIPComm: WTIPComm = WTIPComm(node, scope)
    private val wtMesh: Mesh<String, WTCommPeerInfo> = WTIPMesh(node.uid(), scope)

    private val prmMutex: Mutex = Mutex()

    @Volatile
    private var directNodes: List<String> = listOf()

    val directNodesSnapshot: List<String>
        get() =  directNodes

    private var groupInfoCache: Pair<String, String>? = null

    companion object {
        const val TAG = "WTPRMComm"
        val TAGKClass = WTPRMComm::class
    }

    fun directPeer (node: String): WTCommPeerInfo? {
        return wtMesh.directPeer(MeshKey.Key(node))
    }

    fun directPeerAgeSeconds (node: String): Long {
        return wtMesh.directPeerAgeSeconds(MeshKey.Key(node)) ?: 0
    }

    init {
        logging(true)

        /*
        pipeSubscribe(PipeId.ToPRMComm, scope, true, ::onPipeMessage)
        */

        subscribe(
            to = WTModOpArg.To.PRMComm,
            onEventInfo = WTModOpArg.OnEventInfo(::onBusMessage, scope)
        )

        wtMesh.registerSend { destPeer, jSon ->
            val tag = "wtMeshPRMSend/${randomString(2U)}"
            val dest = destPeer.umCI
            logd(TAGKClass, tag, "wtMesh sending: ${dest}/${inetToIpString(dest[0])} -> $jSon")
            wtIPComm.sendIpCommPacket(inetToIpString(dest[0]), dest[1], WTIPCommPacketType.ControlMesh, jSon)
        }
        wtMesh.subscribeEvent(MeshDispatchEventId.CBMeshNewPeer) { newPeer ->
            val added = prmMutex.withLock {
                if ((newPeer as String) !in directNodes) {
                    directNodes += newPeer
                    true
                } else {
                    false
                }
            }
            if (added) {
                dispatchEvent(MeshDispatchEventId.CBMeshNewPeer)
            }
            peersUpdateSendDebugInfo()
        }
        wtIPComm.subscribeEvent(WTCommDispatchEventId.CBServerPort) { serverPort ->
            dispatchEvent(WTCommDispatchEventId.CBServerPort, serverPort!!)
            peersUpdateSendDebugInfo()
        }
        wtMesh.subscribeEvent(MeshDispatchEventId.CBMeshResetPeers) { _ ->
            prmMutex.withLock {
                directNodes = emptyList()
            }
            dispatchEvent(MeshDispatchEventId.CBMeshResetPeers)
            peersUpdateSendDebugInfo()
        }
        wtMesh.subscribeEvent(MeshDispatchEventId.CBMeshLostPeer) { newPeer ->
            val removed = prmMutex.withLock {
                if ((newPeer as String) in directNodes) {
                    directNodes -= newPeer
                    true
                } else {
                    false
                }
            }
            if (removed) {
                dispatchEvent(MeshDispatchEventId.CBMeshLostPeer)
            }
            peersUpdateSendDebugInfo()
        }
        wtMesh.subscribeEvent(MeshDispatchEventId.CBMeshGetGroupOwner) { _ ->
            val tag = "CBMeshGetGroupOwner/${randomString(2u)}"

            logd(tag, "Requesting GroupOwnerInfo: groupInfoCache: $groupInfoCache")

            if (null != groupInfoCache) {
                wtMesh.addPeer(
                    MeshKey.Root,
                    WTCommPeerInfo(
                        node.id(),
                        node.unique,
                        WTMedium.WifiIp,
                        umCI = listOf(groupInfoCache!!.first, groupInfoCache!!.second),
                    )
                )
            }
            dispatchEvent(MeshDispatchEventId.CBMeshGetGroupOwner)
            peersUpdateSendDebugInfo()
        }
    }

    suspend fun sendCommPacket(commPacket: CommPacket): Boolean {
        val tag = "sendCommPacket/${randomString(2u)}"
        val directPeer = wtMesh.directPeer(MeshKey.Key(commPacket.receiverUId()))
        val receiverUId = commPacket.receiverUId()
        val logF =
            (commPacket.groupIdType == ChatGroupType.RemoteChat || commPacket.groupIdType == ChatGroupType.RemoteChatTesting)

        commPacket.logD(tag, logF)
        logd(tag, "${commPacket.senderUId()} -> ${commPacket.receiverUId()}/" + directPeer, logF)

        val umCI = directPeer?.umCI ?: run {
            logd(tag, "directPeer.umCI is NULL: $receiverUId $directPeer")
            return false
        }

        wtIPComm.sendIpCommPacket(
            umCI[0],
            umCI[1],
            WTIPCommPacketType.Data,
            commPacket.toJson()
        )
        return true
    }

    override suspend fun onBusMessage(
        busId: MessageBusIdInt,
        msg: BusMessageInt<PipeMessageType, Any>
    ) {
        val tag = "channelOnReceive/${randomString(2u)}"
        val type = msg.type
        val data = msg.data

        logd(
            TAGKClass,
            tag,
            "channelId: $busId inputType: $type"
        )

        when (busId) {
            MessageBusId.ToPRMComm -> {
                when (type) {
                    PipeMessageType.ControlMesh -> {
                        wtMesh.addPeersJson(data as String)
                    }
                    PipeMessageType.LocalIp -> {
                        val localIpAddress = (if (null != data) data as InetAddress else null)
                        logd(TAGKClass,
                            tag,
                            "$type localIpAddress: $localIpAddress")

                        send(
                            to = WTModOpArg.To.IpComm,
                            msg = WTModOpArg.Msg(
                                BusMessage(
                                    PipeMessageType.LocalIp,
                                    localIpAddress
                                )
                            )
                        )
                        if (null != localIpAddress) {
                            wtMesh.addPeer(
                                MeshKey.Key(node.uid()),
                                WTCommPeerInfo(
                                    node.id(),
                                    node.unique,
                                    WTMedium.WifiIp,
                                    umCI = listOf(localIpAddress.toString(), wtIPComm.serverPort()!!.toString()),
                                )
                            )
                        } else {
                            /*
                            Don't reset anything in the mesh info. It will delete the group info
                            logd(TAGKClass, tag,"Calling resetPeersInfo")
                            wtMesh.resetPeersInfo()
                            */
                        }
                    }
                    PipeMessageType.GroupServerPort -> {

                    }
                    PipeMessageType.GroupInfo -> {
                        val (name, ipAddress, serverPort) = data as Triple<*, *, *>
                        val groupOwnerName = (if (null != name ) name as String else null)
                        val groupIpAddress = (if (null != ipAddress ) ipAddress as InetAddress else null)
                        val groupServerPort = (if (null != serverPort ) serverPort as Int else null)
                        logd(TAGKClass,
                            tag,
                            "$type groupOwnerName/groupIpAddress: $groupOwnerName/$groupIpAddress/$groupServerPort")
                        if (null != groupIpAddress &&
                            null != groupOwnerName &&
                            null != groupServerPort) {
                            /*
                            channelSend(
                                WTChannelId.RCToIpComm,
                                WTChannelMessageType.RCLocalIp,
                                groupIpAddress
                            )
                            */
                            wtMesh.addPeer(
                                MeshKey.Root,
                                WTCommPeerInfo(
                                    node.id(),
                                    node.unique,
                                    WTMedium.WifiIp,
                                    umCI = listOf(groupIpAddress.toString(), groupServerPort.toString()),
                                )
                            )
                            groupInfoCache = Pair(groupIpAddress.toString(), groupServerPort.toString())
                        } else {
                            logd(TAGKClass, tag,"Calling resetPeersInfo")
                            wtMesh.resetPeersInfo()
                            groupInfoCache = null
                        }
                    }
                    else -> {
                        logd(TAGKClass,
                            tag,
                            "Unprocessed input type $type")
                        throw (NotImplementedError(
                            "Not Implemented: " +
                                    "\nchannelId: $busId " +
                                    "\ninputType: $type " +
                                    "\ninput: $data "
                        ))
                    }
                }
            }
            else -> {
                throw (NotImplementedError("$tag channelId: $busId: inputType: $type Not Implemented "))
            }
        }
    }

    fun WTPRMComm.peersUpdateSendDebugInfo() {
        scope.launch {
            var str = "Peers List:"
            val directNodesCache = prmMutex.withLock {
                directNodes.toList()
            }
            directNodesCache.forEach { k ->
                val peer = directPeer(k)
                str += peer?.let { "\n\t${peer.uid} ${peer.umCI} ${directPeerAgeSeconds(k)}" }
            }
            send(
                to = WTModOpArg.To.Activity,
                msg = WTModOpArg.Msg(
                    BusMessage(
                        PipeMessageType.MeshDebugInfoMessage,
                        str
                    )
                )
            )
        }
    }
}
