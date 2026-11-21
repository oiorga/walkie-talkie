package walkie.comm

import kotlinx.coroutines.CoroutineScope
import walkie.comm.ip.wtIPCommMain
import walkie.comm.prm.WTPRMComm
import walkie.talkie.api.wtModule.WTModuleOpMod
import walkie.talkie.api.wtModule.WTModuleOpModApi
import walkie.talkie.api.WTBusMessageType
import walkie.talkie.api.WTMessageBus
import walkie.talkie.api.WTMessageBusApi
import walkie.talkie.api.wtModule.WTModOpMod
import walkie.talkie.api.WTBusMessage
import walkie.talkie.api.WTBusMessageApi
import walkie.talkie.api.WTMessageBusId
import walkie.talkie.api.wtchat.ChatGroupType
import walkie.talkie.api.wtcomm.CommPacket
import walkie.talkie.api.wtcomm.CommPacketInt
import walkie.talkie.api.wtcomm.WTCommChatMessageIn
import walkie.talkie.api.wtcomm.WTCommChatMessageOut
import walkie.talkie.api.wtcomm.WTCommPacketIn
import walkie.talkie.api.wtcomm.WTCommPacketOut
import walkie.talkie.api.wtsystem.NodeIdInt
import walkie.util.api.MeshDispatchEventId
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString

class WTComm (
    private val nodeId: NodeIdInt,
    val scope: CoroutineScope,
    private val _busMux: WTMessageBus = WTMessageBus(),
    private val _moduleOp: WTModuleOpModApi = WTModuleOpMod(
        messageBus = _busMux,
        remoteCall = null)

) : WTMessageBusApi by _busMux,
    WTModuleOpModApi by _moduleOp,
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
        return wtPRMComm.directNodesSnapshot
    }

    fun directNodesInfo(): List<WTCommPeerInfo> {
        val tag = "directNodesInfo/${randomString(2U)}"
        val directNodesList = mutableListOf<WTCommPeerInfo>()

        directNodes().filter { peer ->
            peer != nodeId.uid()
        }.forEach { peer ->
            val directPeer = wtPRMComm.directPeer(peer)
            if (null != directPeer) {
                directNodesList.add(directPeer)
            } else {
                logd(tag, "$peer has directPeer NULL")
                throw (NullPointerException("$peer has directPeer NULL"))
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
            to = WTModOpMod.To.Comm,
            onEvent = WTModOpMod.OnEvent(
                scope = scope,
                onEventCall = { busId, message ->
                    onBusMessage(busId, WTBusMessage(message.type, message.value))
                })
        )

        wtPRMComm.subscribeEvent(MeshDispatchEventId.DEMeshNewPeer) { _ ->
            send(
                to = WTModOpMod.To.CommonData,
                modMsg = WTModOpMod.Msg(
                    type = WTBusMessageType.UpdatePeersUI,
                    value = null
                )
            )
        }
        wtPRMComm.subscribeEvent(WTCommDispatchEventId.DEServerPort) { serverPort ->
            logd(tag, "$this sending serverPort: $serverPort to RCToWifi")

            send(
                to = WTModOpMod.To.Wifi,
                modMsg = WTModOpMod.Msg(
                    WTBusMessageType.LocalServerPort,
                    serverPort!!
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
            to = WTModOpMod.To.Chat,
            modMsg = WTModOpMod.Msg(
                WTBusMessageType.CommToChatPacket,
                commPacket
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

    override suspend fun onBusMessage(busId: WTMessageBusId, msg: WTBusMessageApi) {
        val tag = "channelOnReceive/${randomString(2U)}"
        val type = msg.type
        val data = msg.data
        val logF = (type == WTBusMessageType.ChatMessage || type == WTBusMessageType.ChatMessageLoopback)
        logd(tag, "channelOnReceive: channelId: $busId inputType: $type")

        when (busId) {
            WTMessageBusId.ToComm -> {
                when (type) {
                    WTBusMessageType.WifiMessage -> {
                        commPacketIn(data as CommPacketInt)
                    }
                    WTBusMessageType.ChatMessage -> {
                        commPacketOut(data as CommPacketInt)
                    }
                    WTBusMessageType.ChatMessageLoopback -> {
                        chatMessageLoopback(data as CommPacketInt)
                    }
                    WTBusMessageType.LocalIp -> {
                        send(
                            to = WTModOpMod.To.PRMComm,
                            modMsg = WTModOpMod.Msg(
                                WTBusMessageType.LocalIp,
                                data
                            )
                        )
                    }
                    WTBusMessageType.GroupInfo -> {
                        send(
                            to = WTModOpMod.To.PRMComm,
                            modMsg = WTModOpMod.Msg(
                                WTBusMessageType.GroupInfo,
                                data
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
