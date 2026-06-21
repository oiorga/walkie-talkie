package walkie.comm

import kotlinx.coroutines.CoroutineScope
import walkie.comm.ip.wtIPCommMain
import walkie.comm.prm.WTPRMComm
import walkie.util.generic.PipeMux
import walkie.util.generic.PipeMuxInt
import walkie.talkie.api.wtchat.ChatGroupType
import walkie.talkie.api.wtcomm.CommPacket
import walkie.talkie.api.wtcomm.CommPacketInt
import walkie.talkie.api.wtcomm.WTCommChatMessageIn
import walkie.talkie.api.wtcomm.WTCommChatMessageOut
import walkie.talkie.api.wtcomm.WTCommPacketIn
import walkie.talkie.api.wtcomm.WTCommPacketOut
import walkie.talkie.api.wtsystem.NodeIdInt

import walkie.util.api.PipeId
import walkie.util.api.PipeIdInt
import walkie.util.api.PipeMessageType
import walkie.util.api.DispatchEventId
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString

class WTComm (
    private val nodeId: NodeIdInt,
    val scope: CoroutineScope,
    private val _channelMux: PipeMuxInt<Any, PipeMessageType> = PipeMux<Any, PipeMessageType>(),
    /* private val _remoteCallMux: WTRemoteCallMuxInt<Any, Any> = WTRemoteCallMux<Any, Any>(), */
) : PipeMuxInt<Any, PipeMessageType> by _channelMux,
    /* WTRemoteCallMuxInt<Any, Any> by _remoteCallMux, */
    WTCommChatMessageIn,
    WTCommChatMessageOut,
    WTCommPacketOut,
    WTCommPacketIn
{
    private val wtPRMComm: WTPRMComm = WTPRMComm(nodeId, scope)

    companion object {
        const val TAG = "WTComm"
        val TAGKClass = WTComm::class
    }

    val tag = TAG

    fun wtPRMComm() : WTPRMComm {
        return wtPRMComm
    }

    private fun directNodes(): List<String> {
        return wtPRMComm().directNodes().toList()
    }

    fun directNodesInfo(): List<WTCommPeerInfo> {
        val tag = "directNodesInfo/${randomString(2U)}"
        val directNodesList = mutableListOf<WTCommPeerInfo>()

        directNodes().filter { peer ->
            peer != nodeId.uid()
        }.forEach { peer ->
            val directUnderlay = wtPRMComm().directUnderlay(peer)
            if (null != directUnderlay) {
                directNodesList.add(directUnderlay)
            } else {
                logd(tag, "$peer has directUnderlay NULL")
                throw (NullPointerException("$peer has directUnderlay NULL"))
            }
        }
        return directNodesList
    }

    init {
        logging(true)
        logd(tag, "Init Entry")

        this.registerReceiver(PipeId.RCToPRMComm, wtPRMComm.scope, wtPRMComm)
        wtPRMComm.wtIPComm.registerReceiver(PipeId.RCToComm, scope, this)

        wtPRMComm.registerToEvent(DispatchEventId.CBMeshNewPeer) { _ ->
            pipeSend(PipeId.RCTOCommonData, scope, PipeMessageType.RCUpdatePeersUI)
        }
        wtPRMComm.registerToEvent(DispatchEventId.CBServerPort) { serverPort ->
            logd(tag, "$this sending serverPort: $serverPort to RCToWifi")
            pipeSend(PipeId.RCToWifi, scope, PipeMessageType.RCLocalServerPort, serverPort!!)
        }
        logd(tag, "Init Exit")
    }

    fun start() {
        wtPRMComm().wtIPComm.wtIPCommMain(scope)
    }

    private suspend fun chatMessageLoopback(commPacket: CommPacketInt) {
        chatMessageOut(commPacket)
    }

    override suspend fun chatMessageIn(commPacket: CommPacketInt) {
        commPacketOut(commPacket)
    }

    override suspend fun chatMessageOut(commPacket: CommPacketInt) {
        pipeSend(PipeId.RCCommToChat, scope, null, commPacket)
    }

    override suspend fun commPacketOut(commPacket: CommPacketInt) {
        val tag = "commPacketOut/${randomString(2U)}"
        val logF = (commPacket.groupIdType == ChatGroupType.RemoteChat || commPacket.groupIdType == ChatGroupType.RemoteChatTesting)
        logd(tag, "Entry", logF)
        commPacket.logD(tag, logF)
        if (commPacket.groupIdType == ChatGroupType.RemoteChat ||
            commPacket.groupIdType == ChatGroupType.RemoteChatTesting) {
            wtPRMComm.sendCommPacket(commPacket as CommPacket)
        }
    }

    override suspend fun pipeOnReceive(pipeId: PipeIdInt, inputType: PipeMessageType?, input: Any?) {
        val tag = "channelOnReceive/${randomString(2U)}"
        val logF = (inputType == PipeMessageType.RCChatMessage || inputType == PipeMessageType.RCChatMessageLoopback)
        logd(tag, "channelOnReceive: channelId: $pipeId inputType: $inputType")

        when (pipeId) {
            PipeId.RCChatToComm -> {
                chatMessageIn(input as CommPacketInt)
            }
            PipeId.RCWifiToComm -> {
                commPacketIn(input as CommPacketInt)
            }
            PipeId.RCToComm -> {
                when (inputType) {
                    PipeMessageType.RCWifiMessage -> {
                        commPacketIn(input as CommPacketInt)
                    }
                    PipeMessageType.RCChatMessage -> {
                        commPacketOut(input as CommPacketInt)
                    }
                    PipeMessageType.RCChatMessageLoopback -> {
                        chatMessageLoopback(input as CommPacketInt)
                    }
                    PipeMessageType.RCLocalIp -> {
                        pipeSend(PipeId.RCToPRMComm, scope, PipeMessageType.RCLocalIp, input)
                    }
                    PipeMessageType.RCGroupInfo -> {
                        pipeSend(PipeId.RCToPRMComm, scope, PipeMessageType.RCGroupInfo, input)
                    }
                    else -> {
                        throw (NotImplementedError("$TAG: channelOnReceive: channelId: $pipeId: inputType: $inputType Not Implemented "))
                    }
                }
            }
            else -> {
                throw (NotImplementedError("$TAG: channelOnReceive: channelId: $pipeId: inputType: $inputType Not Implemented "))
            }
        }
    }

    override suspend fun commPacketIn(commPacket: CommPacketInt) {
        val tag = "commPacketIn/${randomString(2U)}"
        /* val logF = (commPacket.groupIdType == ChatGroupType.RemoteChat || commPacket.groupIdType == ChatGroupType.RemoteChatTesting) */
        commPacket.logD(tag)
        chatMessageOut(commPacket)
    }
}
