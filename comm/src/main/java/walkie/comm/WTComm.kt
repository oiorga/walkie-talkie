package walkie.comm

import kotlinx.coroutines.CoroutineScope
import walkie.comm.ip.wtIPCommMain
import walkie.comm.prm.WTPRMComm
import walkie.talkie.api.wtModule.ModuleOpImpl
import walkie.talkie.api.wtModule.ModuleOpInt
import walkie.util.generic.MessageBus
import walkie.talkie.api.wtchat.ChatGroupType
import walkie.talkie.api.wtcomm.CommPacket
import walkie.talkie.api.wtcomm.CommPacketInt
import walkie.talkie.api.wtcomm.WTCommChatMessageIn
import walkie.talkie.api.wtcomm.WTCommChatMessageOut
import walkie.talkie.api.wtcomm.WTCommPacketIn
import walkie.talkie.api.wtcomm.WTCommPacketOut
import walkie.talkie.api.wtsystem.NodeIdInt
import walkie.talkie.api.wtModule.MessageBusId
import walkie.talkie.api.wtModule.PipeMessageType
import walkie.talkie.api.wtModule.WTModOpArg
import walkie.util.api.DispatchEventId
import walkie.util.api.MessageBusIdInt
import walkie.util.api.BusMessageInt
import walkie.util.api.MessageBusInt
import walkie.util.generic.BusMessage
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString

class WTComm (
    private val nodeId: NodeIdInt,
    val scope: CoroutineScope,
    private val _pipeMux: MessageBusInt<PipeMessageType, Any> = MessageBus(),
    private val _moduleOp: ModuleOpInt = ModuleOpImpl(_pipeMux)

) : MessageBusInt<PipeMessageType, Any> by _pipeMux,
    ModuleOpInt by _moduleOp,
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

        /*
        pipeSubscribe(PipeId.ToComm, scope, true, ::onPipeMessage)
        */

        subscribe(
            to = WTModOpArg.To.Comm,
            onEventInfo = WTModOpArg.OnEventInfo(::onBusMessage, scope)
        )

        wtPRMComm.registerToEvent(DispatchEventId.CBMeshNewPeer) { _ ->
            send(
                to = WTModOpArg.To.CommonData,
                msg = WTModOpArg.Msg(
                    BusMessage(
                        PipeMessageType.UpdatePeersUI,
                        null
                    )
                )
            )
        }
        wtPRMComm.registerToEvent(DispatchEventId.CBServerPort) { serverPort ->
            logd(tag, "$this sending serverPort: $serverPort to RCToWifi")

            send(
                to = WTModOpArg.To.Wifi,
                msg = WTModOpArg.Msg(
                    BusMessage(
                        PipeMessageType.LocalServerPort,
                        serverPort!!
                    )
                )
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
        send(
            to = WTModOpArg.To.Chat,
            msg = WTModOpArg.Msg(
                BusMessage(
                    PipeMessageType.CommToChatPacket,
                    commPacket
                )
            )
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

    override suspend fun onBusMessage(busId: MessageBusIdInt, msg: BusMessageInt<PipeMessageType, Any>) {
        val tag = "channelOnReceive/${randomString(2U)}"
        val type = msg.type
        val data = msg.data
        val logF = (type == PipeMessageType.ChatMessage || type == PipeMessageType.ChatMessageLoopback)
        logd(tag, "channelOnReceive: channelId: $busId inputType: $type")

        when (busId) {
            MessageBusId.ToComm -> {
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
                        send(
                            to = WTModOpArg.To.PRMComm,
                            msg = WTModOpArg.Msg(
                                BusMessage(
                                    PipeMessageType.LocalIp,
                                    data
                                )
                            )
                        )
                    }
                    PipeMessageType.GroupInfo -> {
                        send(
                            to = WTModOpArg.To.PRMComm,
                            msg = WTModOpArg.Msg(
                                BusMessage(
                                    PipeMessageType.GroupInfo,
                                    data
                                )
                            )
                        )
                    }
                    else -> {
                        throw (NotImplementedError("$TAG: channelOnReceive: channelId: $busId: inputType: $type Not Implemented "))
                    }
                }
            }
            else -> {
                throw (NotImplementedError("$TAG: channelOnReceive: channelId: $busId: inputType: $type Not Implemented "))
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
