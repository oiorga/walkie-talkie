package walkie.comm.prm

import android.os.Build
import androidx.annotation.RequiresApi
import walkie.comm.WTCommPeerInfo
import walkie.comm.WTIPMesh
import walkie.comm.ip.WTIPComm
import walkie.comm.ip.WTIPCommPacketType
import walkie.util.generic.ChannelMux
import walkie.util.generic.ChannelMuxInt
import walkie.glue.wtchat.ChatGroupType
import walkie.glue.wtcomm.CommPacket
import walkie.glue.wtcomm.WTMedium
import walkie.glue.wtsystem.NodeIdInt
import walkie.glue_inc.CallBackId
import walkie.glue_inc.ChannelId
import walkie.glue_inc.ChannelIdInt
import walkie.glue_inc.ChannelMessageType
import walkie.util.generic.CallBack
import walkie.util.generic.CallBackInt
import walkie.util.inetToIpString
import walkie.util.logd
import walkie.util.logging
import walkie.util.mesh.Mesh
import walkie.util.randomString
import java.net.InetAddress

/* Ugly. To revisit. To prevent coroutines to restart at onCreate on screen rotation
class WTPRMCommStatic private constructor() {
    companion object {
        val INSTANCE: WTPRMCommStatic by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { WTPRMCommStatic() }
    }

    var count: Int = 0
    var wifiControlS: Boolean = false
    var buildMesh: Boolean = false
}
*/

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class WTPRMComm (
    private val node: NodeIdInt,
    private val _channelMux: ChannelMuxInt<Any, ChannelMessageType> = ChannelMux<Any, ChannelMessageType>(),
    private val _callBackList: CallBackInt<Any, Any> = CallBack()
    /* private val _remoteCallMux: WTRemoteCallMuxInt<Any, Any> = WTRemoteCallMux<Any, Any>() */
    /* private val _callBackList: WTCallBackInt<Any, Any> = WTCallBack() */
) :
    /* WTRemoteCallMuxInt<Any, Any> by _remoteCallMux, */
    ChannelMuxInt<Any, ChannelMessageType> by _channelMux,
    CallBackInt<Any, Any> by _callBackList
/* WTCallBackInt<Any, Any> by _callBackList */
{
    private val wtIPComm: WTIPComm = WTIPComm(node)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val wtMesh: Mesh<String, WTCommPeerInfo> = WTIPMesh(node.uid())
    private val directNodes: MutableList<String> = mutableListOf()

    private var groupInfoCache: Pair<String, String>? = null

    companion object {
        const val TAG = "WTPRMComm"
        val TAGKClass = WTPRMComm::class
    }

    fun wtIPComm() : WTIPComm {
        return wtIPComm
    }

    fun wtMesh() : Mesh<String, WTCommPeerInfo> {
        return wtMesh
    }

    fun directUnderlay (node: String): WTCommPeerInfo? {
        return wtMesh.directUnderlay(node)
    }

    init {
        logging(true)

        this.registerReceiver(ChannelId.RCToIpComm, wtIPComm)
        wtIPComm.registerReceiver(ChannelId.RCToPRMComm, this)

        wtMesh.registerSend { destPeer, jSon ->
            val tag = "wtMeshPRMSend/${randomString(2U)}"
            val dest = destPeer.umCI
            logd(TAGKClass, tag, "wtMesh sending: ${dest}/${inetToIpString(dest[0])} -> $jSon")
            wtIPComm.sendIpCommPacket(inetToIpString(dest[0]), dest[1], WTIPCommPacketType.ControlMesh, jSon)
        }
        wtMesh.registerCallBack(CallBackId.CBMeshNewPeer) { newPeer ->
            if ((newPeer as String) !in directNodes) {
                directNodes.add(newPeer)
                callBack(CallBackId.CBMeshNewPeer)
            }
            peersUpdateSendDebugInfo()
        }
        wtIPComm.registerCallBack(CallBackId.CBServerPort) { serverPort ->
            callBack(CallBackId.CBServerPort, serverPort!!)
            peersUpdateSendDebugInfo()
        }
        wtMesh.registerCallBack(CallBackId.CBMeshResetPeers) { _ ->
            directNodes.clear()
            callBack(CallBackId.CBMeshResetPeers)
            peersUpdateSendDebugInfo()
        }
        wtMesh.registerCallBack(CallBackId.CBMeshLostPeer) { newPeer ->
            if ((newPeer as String) in directNodes) {
                directNodes.remove(newPeer)
                callBack(CallBackId.CBMeshNewPeer)
            }
            peersUpdateSendDebugInfo()
        }
        wtMesh.registerCallBack(CallBackId.CBMeshGetGroupOwner) { _ ->
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
            callBack(CallBackId.CBMeshGetGroupOwner)
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

    override suspend fun channelOnReceive(
        channelId: ChannelIdInt,
        inputType: ChannelMessageType?,
        input: Any?
    ) {
        val tag = "channelOnReceive/${randomString(2u)}"

        logd(
            TAGKClass,
            tag,
            "channelId: $channelId inputType: $inputType"
        )

        when (channelId) {
            ChannelId.RCToPRMComm -> {
                when (inputType) {
                    ChannelMessageType.RCControlMesh -> {
                        wtMesh.addPeersJson(input as String)
                    }
                    ChannelMessageType.RCLocalIp -> {
                        val localIpAddress = (if (null != input) input as InetAddress else null)
                        logd(TAGKClass,
                            tag,
                            "$inputType localIpAddress: $localIpAddress")
                        channelSend(
                            ChannelId.RCToIpComm,
                            ChannelMessageType.RCLocalIp,
                            localIpAddress
                        )
                        if (null != localIpAddress) {
                            wtMesh.addPeer(
                                node.uid(),
                                WTCommPeerInfo(
                                    node.id(),
                                    node.unique,
                                    WTMedium.WifiIp,
                                    umCI = listOf(localIpAddress.toString(), wtIPComm().serverPort()!!.toString()),
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
                    ChannelMessageType.RCGroupServerPort -> {

                    }
                    ChannelMessageType.RCGroupInfo -> {
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
                                    "\nchannelId: $channelId " +
                                    "\ninputType: $inputType " +
                                    "\ninput: $input "
                        ))
                    }
                }
            }
            else -> {
                throw (NotImplementedError("$tag channelId: $channelId: inputType: $inputType Not Implemented "))
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun WTPRMComm.peersUpdateSendDebugInfo() {
    var str = ""
    str += "Peers List: ${directNodes()}"
    directNodes().forEach { k ->
        str += "\n\tDest: " + directUnderlay(k)?.uid  + " IP Address: " + directUnderlay(k)?.umCI
    }
    channelSend(
        ChannelId.RCToWalkieTalkie,
        ChannelMessageType.RCWTMeshDebugInfoMessage,
        str)
}