package walkie.comm

import kotlinx.coroutines.CoroutineScope
import walkie.comm.ip.wtIPCommMain
import walkie.comm.prm.WTPRMComm
import walkie.util.generic.PipeMux
import walkie.talkie.api.wtchat.ChatGroupType
import walkie.talkie.api.wtcomm.CommPacket
import walkie.talkie.api.wtcomm.CommPacketInt
import walkie.talkie.api.wtcomm.WTCommChatMessageIn
import walkie.talkie.api.wtcomm.WTCommChatMessageOut
import walkie.talkie.api.wtcomm.WTCommPacketIn
import walkie.talkie.api.wtcomm.WTCommPacketOut
import walkie.talkie.api.wtsystem.NodeIdInt
import walkie.talkie.api.wtModule.PipeId
import walkie.talkie.api.wtModule.PipeMessageType
import walkie.util.api.DispatchEventId
import walkie.util.api.PipeIdInt
import walkie.util.api.PipeMessageInt
import walkie.util.api.PipeMuxInt
import walkie.util.generic.PipeMessage
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString

class WTComm (
    private val nodeId: NodeIdInt,
    val scope: CoroutineScope,
    private val _pipeMux: PipeMuxInt<PipeMessageType, Any> = PipeMux(),
    /* private val _remoteCallMux: WTRemoteCallMuxInt<Any, Any> = WTRemoteCallMux<Any, Any>(), */
) : PipeMuxInt<PipeMessageType, Any> by _pipeMux,
    /* WTRemoteCallMuxInt<Any, Any> by _remoteCallMux, */
    WTCommChatMessageIn,
    WTCommChatMessageOut,
    WTCommPacketOut,
    WTCommPacketIn
{
    val wtPRMComm: WTPRMComm = WTPRMComm(nodeId, scope)

    companion object {
        const val TAG = "WTComm"
        val TAGKClass = WTComm::class
    }

    val tag = TAG

    private fun directNodes(): List<String> {
        return wtPRMComm.directNodes().toList()
    }

    fun directNodesInfo(): List<WTCommPeerInfo> {
        val tag = "directNodesInfo/${randomString(2U)}"
        val directNodesList = mutableListOf<WTCommPeerInfo>()

        directNodes().filter { peer ->
            peer != nodeId.uid()
        }.forEach { peer ->
            val directUnderlay = wtPRMComm.directUnderlay(peer)
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

        /*
        this.registerReceiver(PipeId.ToPRMComm, wtPRMComm.scope, wtPRMComm)
        wtPRMComm.wtIPComm.registerReceiver(PipeId.ToComm, scope, this)
        */

        subscribe(PipeId.ToComm, scope, true, ::onPipeMessage)

        wtPRMComm.registerToEvent(DispatchEventId.CBMeshNewPeer) { _ ->
            pipeSendAsync(PipeId.TOCommonData, scope,
                PipeMessage(
                    PipeMessageType.UpdatePeersUI,
                    null
                )
            )
        }
        wtPRMComm.registerToEvent(DispatchEventId.CBServerPort) { serverPort ->
            logd(tag, "$this sending serverPort: $serverPort to RCToWifi")
            pipeSendAsync(PipeId.ToWifi, scope,
                PipeMessage(
                    PipeMessageType.LocalServerPort,
                    serverPort!!)
            )
        }
        logd(tag, "Init Exit")
    }

    fun start() {
        wtPRMComm.wtIPComm.wtIPCommMain(scope)
    }

    private suspend fun chatMessageLoopback(commPacket: CommPacketInt) {
        chatMessageOut(commPacket)
    }

    override suspend fun chatMessageIn(commPacket: CommPacketInt) {
        commPacketOut(commPacket)
    }

    override suspend fun chatMessageOut(commPacket: CommPacketInt) {
        pipeSendAsync(PipeId.ToChat, scope,
            PipeMessage(
                PipeMessageType.CommToChatPacket,
                commPacket)
        )
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

    override suspend fun onPipeMessage(pipeId: PipeIdInt, msg: PipeMessageInt<PipeMessageType, Any>) {
        val tag = "channelOnReceive/${randomString(2U)}"
        val type = msg.type
        val data = msg.data
        val logF = (type == PipeMessageType.ChatMessage || type == PipeMessageType.ChatMessageLoopback)
        logd(tag, "channelOnReceive: channelId: $pipeId inputType: $type")

        when (pipeId) {
            PipeId.ToComm -> {
                when (type) {
                    PipeMessageType.WifiMessage -> {
                        commPacketIn(data as CommPacketInt)
                    }
                    PipeMessageType.ChatMessage -> {
                        commPacketOut(data as CommPacketInt)
                    }
                    PipeMessageType.ChatMessageLoopback -> {
                        chatMessageLoopback(data as CommPacketInt)
                    }
                    PipeMessageType.LocalIp -> {
                        pipeSendAsync(PipeId.ToPRMComm, scope,
                            PipeMessage(
                                PipeMessageType.LocalIp,
                                data
                            )
                        )
                    }
                    PipeMessageType.GroupInfo -> {
                        pipeSendAsync(PipeId.ToPRMComm, scope,
                            PipeMessage(
                                PipeMessageType.GroupInfo,
                                data
                            )
                        )
                    }
                    else -> {
                        throw (NotImplementedError("$TAG: channelOnReceive: channelId: $pipeId: inputType: $type Not Implemented "))
                    }
                }
            }
            else -> {
                throw (NotImplementedError("$TAG: channelOnReceive: channelId: $pipeId: inputType: $type Not Implemented "))
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
