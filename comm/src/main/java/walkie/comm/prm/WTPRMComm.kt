package walkie.comm.prm

import kotlinx.coroutines.CoroutineScope
import walkie.comm.WTCommPeerInfo
import walkie.comm.WTIPMesh
import walkie.comm.ip.WTIPComm
import walkie.comm.ip.WTIPCommPacketType
import walkie.util.generic.PipeMux
import walkie.util.generic.PipeMuxInt
import walkie.talkie.api.wtchat.ChatGroupType
import walkie.talkie.api.wtcomm.CommPacket
import walkie.talkie.api.wtcomm.WTMedium
import walkie.talkie.api.wtsystem.NodeIdInt
import walkie.util.api.PipeId
import walkie.util.api.PipeIdInt
import walkie.util.api.PipeMessageType
import walkie.util.api.DispatchEventId
import walkie.util.generic.EventDispatcher
import walkie.util.generic.EventDispatcherInt
import walkie.util.inetToIpString
import walkie.util.logd
import walkie.util.logging
import walkie.util.mesh.Mesh
import walkie.util.randomString
import java.net.InetAddress

class WTPRMComm (
    private val node: NodeIdInt,
    val scope: CoroutineScope,
    private val _channelMux: PipeMuxInt<Any, PipeMessageType> = PipeMux<Any, PipeMessageType>(),
    private val _callBackList: EventDispatcherInt<Any> = EventDispatcher()
    /* private val _remoteCallMux: WTRemoteCallMuxInt<Any, Any> = WTRemoteCallMux<Any, Any>() */
    /* private val _callBackList: WTCallBackInt<Any, Any> = WTCallBack() */
) :
    /* WTRemoteCallMuxInt<Any, Any> by _remoteCallMux, */
    PipeMuxInt<Any, PipeMessageType> by _channelMux,
    EventDispatcherInt<Any> by _callBackList
/* WTCallBackInt<Any, Any> by _callBackList */
{
    val wtIPComm: WTIPComm = WTIPComm(node, scope)
    private val wtMesh: Mesh<String, WTCommPeerInfo> = WTIPMesh(node.uid(), scope)
    private val directNodes: MutableList<String> = mutableListOf()
    private var groupInfoCache: Pair<String, String>? = null

    companion object {
        const val TAG = "WTPRMComm"
        val TAGKClass = WTPRMComm::class
    }

    fun directUnderlay (node: String): WTCommPeerInfo? {
        return wtMesh.directUnderlay(node)
    }

    init {
        logging(true)

        this.registerReceiver(PipeId.RCToIpComm, wtIPComm.scope,wtIPComm)
        wtIPComm.registerReceiver(PipeId.RCToPRMComm, scope,this)
        wtMesh.registerSend { destPeer, jSon ->
            val tag = "wtMeshPRMSend/${randomString(2U)}"
            val dest = destPeer.umCI
            logd(TAGKClass, tag, "wtMesh sending: ${dest}/${inetToIpString(dest[0])} -> $jSon")
            wtIPComm.sendIpCommPacket(inetToIpString(dest[0]), dest[1], WTIPCommPacketType.ControlMesh, jSon)
        }
        wtMesh.registerToEvent(DispatchEventId.CBMeshNewPeer) { newPeer ->
            if ((newPeer as String) !in directNodes) {
                directNodes.add(newPeer)
                dispatchEvent(DispatchEventId.CBMeshNewPeer)
            }
            peersUpdateSendDebugInfo()
        }
        wtIPComm.registerToEvent(DispatchEventId.CBServerPort) { serverPort ->
            dispatchEvent(DispatchEventId.CBServerPort, serverPort!!)
            peersUpdateSendDebugInfo()
        }
        wtMesh.registerToEvent(DispatchEventId.CBMeshResetPeers) { _ ->
            directNodes.clear()
            dispatchEvent(DispatchEventId.CBMeshResetPeers)
            peersUpdateSendDebugInfo()
        }
        wtMesh.registerToEvent(DispatchEventId.CBMeshLostPeer) { newPeer ->
            if ((newPeer as String) in directNodes) {
                directNodes.remove(newPeer)
                dispatchEvent(DispatchEventId.CBMeshNewPeer)
            }
            peersUpdateSendDebugInfo()
        }
        wtMesh.registerToEvent(DispatchEventId.CBMeshGetGroupOwner) { _ ->
            val tag = "CBMeshGetGroupOwner/${randomString(2u)}"

            logd(tag, "Requesting GroupOwnerInfo: groupInfoCache: $groupInfoCache")

            if (null != groupInfoCache) {
                wtMesh.addPeer(
                    null,
                    WTCommPeerInfo(
                        node.id(),
                        node.unique,
                        WTMedium.WifiIp,
                        umCI = listOf(groupInfoCache!!.first, groupInfoCache!!.second),
                        //true
                    )
                )
            }
            dispatchEvent(DispatchEventId.CBMeshGetGroupOwner)
            peersUpdateSendDebugInfo()
        }
    }

    fun directNodes() : List<String> {
        return directNodes.toList()
    }

    suspend fun sendCommPacket(commPacket: CommPacket): Boolean {
        val tag = "sendCommPacket/${randomString(2u)}"
        val directUnderlay = wtMesh.directUnderlay(commPacket.receiverUId())
        val logF = (commPacket.groupIdType == ChatGroupType.RemoteChat || commPacket.groupIdType == ChatGroupType.RemoteChatTesting)
        var success = true

        commPacket.logD(tag, logF)
        logd(tag, "${commPacket.senderUId()} -> ${commPacket.receiverUId()}/" + directUnderlay, logF)
        if (null != directUnderlay?.umCI) {
            wtIPComm.sendIpCommPacket(
                wtMesh.directUnderlay(commPacket.receiverUId())!!.umCI[0],
                wtMesh.directUnderlay(commPacket.receiverUId())!!.umCI[1],
                WTIPCommPacketType.Data,
                commPacket.toJson()
            )
        } else {
            logd(tag, "wtMesh.directUnderlay(commPacket.receiverUId()).umCI are NULL: ${commPacket.receiverUId()} $directUnderlay")
            /* throw (NullPointerException("wtMesh.directUnderlay(commPacket.receiverUId()).umCI: ${commPacket.receiverUId()} $directUnderlay")) */
            success = false
        }

        return success
    }

    override suspend fun pipeOnReceive(
        pipeId: PipeIdInt,
        inputType: PipeMessageType?,
        input: Any?
    ) {
        val tag = "channelOnReceive/${randomString(2u)}"

        logd(
            TAGKClass,
            tag,
            "channelId: $pipeId inputType: $inputType"
        )

        when (pipeId) {
            PipeId.RCToPRMComm -> {
                when (inputType) {
                    PipeMessageType.RCControlMesh -> {
                        wtMesh.addPeersJson(input as String)
                    }
                    PipeMessageType.RCLocalIp -> {
                        val localIpAddress = (if (null != input) input as InetAddress else null)
                        logd(TAGKClass,
                            tag,
                            "$inputType localIpAddress: $localIpAddress")
                        pipeSend(
                            PipeId.RCToIpComm,
                            scope,
                            PipeMessageType.RCLocalIp,
                            localIpAddress
                        )
                        if (null != localIpAddress) {
                            wtMesh.addPeer(
                                node.uid(),
                                WTCommPeerInfo(
                                    node.id(),
                                    node.unique,
                                    WTMedium.WifiIp,
                                    umCI = listOf(localIpAddress.toString(), wtIPComm.serverPort()!!.toString()),
                                    //true
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
                    PipeMessageType.RCGroupServerPort -> {

                    }
                    PipeMessageType.RCGroupInfo -> {
                        val (name, ipAddress, serverPort) = input as Triple<*, *, *>
                        val groupOwnerName = (if (null != name ) name as String else null)
                        val groupIpAddress = (if (null != ipAddress ) ipAddress as InetAddress else null)
                        val groupServerPort = (if (null != serverPort ) serverPort as Int else null)
                        logd(TAGKClass,
                            tag,
                            "$inputType groupOwnerName/groupIpAddress: $groupOwnerName/$groupIpAddress/$groupServerPort")
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
                                null,
                                WTCommPeerInfo(
                                    node.id(),
                                    node.unique,
                                    WTMedium.WifiIp,
                                    umCI = listOf(groupIpAddress.toString(), groupServerPort.toString()),
                                    //true
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
                            "Unprocessed input type $inputType")
                        throw (NotImplementedError(
                            "Not Implemented: " +
                                    "\nchannelId: $pipeId " +
                                    "\ninputType: $inputType " +
                                    "\ninput: $input "
                        ))
                    }
                }
            }
            else -> {
                throw (NotImplementedError("$tag channelId: $pipeId: inputType: $inputType Not Implemented "))
            }
        }
    }
}

internal fun WTPRMComm.peersUpdateSendDebugInfo() {
    var str = ""
    str += "Peers List: ${directNodes()}"
    directNodes().forEach { k ->
        str += "\n\tDest: " + directUnderlay(k)?.uid  + " IP Address: " + directUnderlay(k)?.umCI
    }
    pipeSend(
        PipeId.RCToWTActivity,
        scope,
        PipeMessageType.RCWTMeshDebugInfoMessage,
        str)
}