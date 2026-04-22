package walkie.comm

import android.os.Build
import androidx.annotation.RequiresApi
import walkie.comm.ip.wtIPCommMain
import walkie.comm.prm.WTPRMComm
import walkie.util.generic.ChannelMux
import walkie.util.generic.ChannelMuxInt
import walkie.glue.wtchat.ChatGroupType
import walkie.glue.wtcomm.CommPacket
import walkie.glue.wtcomm.CommPacketInt
import walkie.glue.wtcomm.WTCommChatMessageIn
import walkie.glue.wtcomm.WTCommChatMessageOut
import walkie.glue.wtcomm.WTCommPacketIn
import walkie.glue.wtcomm.WTCommPacketOut
import walkie.glue.wtsystem.NodeIdInt
import walkie.glue_inc.CallBackId
import walkie.glue_inc.ChannelId
import walkie.glue_inc.ChannelIdInt
import walkie.glue_inc.ChannelMessageType
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class WTComm (
    private val nodeId: NodeIdInt,
    private val _channelMux: ChannelMuxInt<Any, ChannelMessageType> = ChannelMux<Any, ChannelMessageType>(),
    /* private val _remoteCallMux: WTRemoteCallMuxInt<Any, Any> = WTRemoteCallMux<Any, Any>(), */
) : ChannelMuxInt<Any, ChannelMessageType> by _channelMux,
    /* WTRemoteCallMuxInt<Any, Any> by _remoteCallMux, */
    WTCommChatMessageIn,
    WTCommChatMessageOut,
    WTCommPacketOut,
    WTCommPacketIn
{
    private val wtPRMComm: WTPRMComm = WTPRMComm(nodeId)

    companion object {
        const val TAG = "WTComm"
        val TAGKClass = WTComm::class
    }

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
        logd("Init Entry")

        this.registerReceiver(ChannelId.RCToPRMComm, wtPRMComm)
        wtPRMComm.wtIPComm().registerReceiver(ChannelId.RCToComm, this)

        wtPRMComm.registerCallBack(CallBackId.CBMeshNewPeer) { _ ->
            channelSend(ChannelId.RCTOCommonData, ChannelMessageType.RCUpdatePeersUI)
        }
        wtPRMComm.registerCallBack(CallBackId.CBServerPort) { serverPort ->
            logd("$this sending serverPort: $serverPort to RCToWifi")
            channelSend(ChannelId.RCToWifi, ChannelMessageType.RCLocalServerPort, serverPort!!)
        }
        logd("Init Exit")
    }

    fun start() {
        wtPRMComm().wtIPComm().wtIPCommMain()
    }

    private suspend fun chatMessageLoopback(commPacket: CommPacketInt) {
        chatMessageOut(commPacket)
    }

    override suspend fun chatMessageIn(commPacket: CommPacketInt) {
        commPacketOut(commPacket)
    }

    override suspend fun chatMessageOut(commPacket: CommPacketInt) {
        channelSend(ChannelId.RCCommToChat, null, commPacket)
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

    override suspend fun channelOnReceive(channelId: ChannelIdInt, inputType: ChannelMessageType?, input: Any?) {
        val tag = "channelOnReceive/${randomString(2U)}"
        val logF = (inputType == ChannelMessageType.RCChatMessage || inputType == ChannelMessageType.RCChatMessageLoopback)
        logd(tag, "channelOnReceive: channelId: $channelId inputType: $inputType")

        when (channelId) {
            ChannelId.RCChatToComm -> {
                chatMessageIn(input as CommPacketInt)
            }
            ChannelId.RCWifiToComm -> {
                commPacketIn(input as CommPacketInt)
            }
            ChannelId.RCToComm -> {
                when (inputType) {
                    ChannelMessageType.RCWifiMessage -> {
                        commPacketIn(input as CommPacketInt)
                    }
                    ChannelMessageType.RCChatMessage -> {
                        commPacketOut(input as CommPacketInt)
                    }
                    ChannelMessageType.RCChatMessageLoopback -> {
                        chatMessageLoopback(input as CommPacketInt)
                    }
                    ChannelMessageType.RCLocalIp -> {
                        channelSend(ChannelId.RCToPRMComm, ChannelMessageType.RCLocalIp, input)
                    }
                    ChannelMessageType.RCGroupInfo -> {
                        channelSend(ChannelId.RCToPRMComm, ChannelMessageType.RCGroupInfo, input)
                    }
                    else -> {
                        throw (NotImplementedError("$TAG: channelOnReceive: channelId: $channelId: inputType: $inputType Not Implemented "))
                    }
                }
            }
            else -> {
                throw (NotImplementedError("$TAG: channelOnReceive: channelId: $channelId: inputType: $inputType Not Implemented "))
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
