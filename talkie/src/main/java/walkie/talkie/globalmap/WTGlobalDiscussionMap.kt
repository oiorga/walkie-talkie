package walkie.talkie.globalmap

import walkie.chat.ChatDiscussion
import walkie.chat.ChatGroupId
import walkie.chat.ChatGroupMap
import walkie.chat.ChatMessage
import walkie.chat.ChatMessageItem
import walkie.chat.ChatMessageItemList
import walkie.chat.Receiver
import walkie.chat.Sender
import walkie.util.generic.ChannelMux
import walkie.util.generic.ChannelMuxInt
import walkie.glue.wtchat.ChatGroupType
import walkie.glue.wtchat.ChatMessageAbs
import walkie.glue.wtchat.DiscussionAbs
import walkie.glue.wtchat.DiscussionMapAbs
import walkie.glue.wtcomm.CommPacket
import walkie.glue.wtsystem.NodeIdInt
import walkie.glue_inc.ChannelId
import walkie.glue_inc.ChannelIdInt
import walkie.glue_inc.ChannelMessageType
import walkie.talkie.common.UpdateUiLiveData
import walkie.talkie.node.NodeId
import walkie.util.generic.RemoteCallMux
import walkie.util.generic.RemoteCallMuxInt
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString

/**
 *
 *
 *
 *
 */
data class DiscussionMap(
    private val discussionMap: DiscussionMapAbs,
    private val groupMap: ChatGroupMap,
    private val systemNode: NodeIdInt,
    private val _remoteCallMux: RemoteCallMuxInt<Any, Any> = RemoteCallMux<Any, Any>(),
    private val _channelMux: ChannelMuxInt<Any, ChannelMessageType> = ChannelMux<Any, ChannelMessageType>(),
) : RemoteCallMuxInt<Any, Any> by _remoteCallMux,
    ChannelMuxInt<Any, ChannelMessageType> by _channelMux
{
    lateinit var updateUiLiveData: UpdateUiLiveData

    companion object {
        const val TAG = "GlobalDiscussionMap"
    }

    init {
        /* registerRemoteCall(RemoteCallId.RCChatMessageFromComm) { run { recvFromComm(it as CommPacket) } } */

        logging(true)
        
        logd(TAG, "init")
    }

    private fun addMessage(chatMessage: ChatMessageAbs) {
        val tag = "addMessage/${randomString(2U)}"
        val logF = (chatMessage.groupId.type == ChatGroupType.RemoteChatTesting || chatMessage.groupId.type == ChatGroupType.RemoteChat)

        val type = chatMessage.groupId.type
        val gIdSender = ChatGroupId(chatMessage.sender.node.uid(), chatMessage.sender.node.id(), type = type)
        val gIdGroup = ChatGroupId(chatMessage.groupId.groupId, chatMessage.groupId.groupName, type = type)
        val gIdReceiver = ChatGroupId(chatMessage.receiver.node.uid(), chatMessage.receiver.node.id(), type = type)
        val gIdMe = ChatGroupId(systemNode.uid(), systemNode.id(), type = type)
        val gIdBogus = ChatGroupId("Bogus", "Bogus", type = ChatGroupType.Etc)

        val gId: ChatGroupId =
            if (gIdMe == gIdSender && groupMap.groupExists(gIdGroup)) gIdGroup
            else if (groupMap.groupExists(gIdSender) && gIdGroup == gIdReceiver) gIdSender
            else if (groupMap.groupExists(gIdGroup)) gIdGroup
            else gIdBogus
        logd(
            tag,
            "\ngIdSender: $gIdSender " + groupMap.groupExists(gIdSender) + " " +
                    "\ngIdReceiver: $gIdReceiver " + groupMap.groupExists(gIdReceiver) + " " +
                    "\ngIdGroup: $gIdGroup " + groupMap.groupExists(gIdGroup) + " " +
                    "\ngIdBogus: $gIdBogus " + groupMap.groupExists(gIdBogus) + " " +
                    "\ngId ELECTED: $gId",
            logF
        )
        chatMessage.logD(tag, logF)

        var tempVar: DiscussionAbs? = discussionMap[gId]

        if (null == tempVar) {
            tempVar = ChatDiscussion(gId)
            logd(tag, "(1) ${discussionMap[gId]} was null", logF)
        } else {
            discussionMap.remove(gId)
        }

        tempVar.addChatMessage(chatMessage)
        discussionMap[gId] = tempVar

        channelSend(ChannelId.RCTOCommonData, ChannelMessageType.RCUpdateChatUI, chatMessage.groupId.type)
    }

    fun discussionMap() = discussionMap

    fun createDiscussion(chatGroupId: ChatGroupId, chatDiscussion: ChatDiscussion = ChatDiscussion(chatGroupId)) : ChatDiscussion {
        var ret: ChatDiscussion = chatDiscussion
        if (null != discussionMap[chatGroupId]) {
            ret = discussionMap[chatGroupId] as ChatDiscussion
        } else {
            discussionMap[chatGroupId] = chatDiscussion
        }
        return ret
    }

    fun removeDiscussion(chatGroupId: ChatGroupId) : DiscussionAbs? {
        return discussionMap.remove(chatGroupId)
    }

    fun replaceDiscussion(chatGroupId: ChatGroupId, chatDiscussion: ChatDiscussion) : DiscussionAbs? {
        discussionMap[chatGroupId] = chatDiscussion
        return discussionMap[chatGroupId]
    }

    fun sendMessage(chatMessage: ChatMessageAbs) {
        val tag = "sendMessage/${randomString(2U)}"
        val logF = (chatMessage.groupId.type == ChatGroupType.RemoteChat ||
                chatMessage.groupId.type == ChatGroupType.RemoteChatTesting)
        val group = groupMap[chatMessage.groupId]

        /*
        logd(tag,"group: ${chatMessage.groupId.groupId.toString()} is " +
                    (if (null == group) "" else "not") + " null" + " type: " + group?.groupId?.type,
            logF)
        group?.logD(tag, logF)
        logd(tag, "$tag 0 group?.groupId?.type == " + group?.groupId?.type, logF)
        */

        group?.logD(tag, logF)
        chatMessage.logD(tag, logF)
        sendToComm(chatMessage)

        if (group?.groupId?.type != ChatGroupType.Local) {
            group?.logD(tag, logF)
            group?.forEach { receiverNode ->
                if (receiverNode.uid() != systemNode.uid()) {
                    logd(
                        tag,
                        "$tag: sendMessage: to group: ${chatMessage.groupId.groupId.toString()} to: " + receiverNode.uid(),
                        logF
                    )
                    receiverNode.logD(tag, logF)
                    chatMessage.logD(tag, logF)
                    sendToComm(chatMessage, receiver = Receiver(receiverNode, group.groupId))
                }
            }
        }
    }

    override suspend fun channelOnReceive(
        channelId: ChannelIdInt,
        inputType: ChannelMessageType?,
        input: Any?
        ) {
        when (channelId) {
            ChannelId.RCCommToChat -> {
                recvFromComm(input as CommPacket)
            }
            else -> {
                logd(TAG, "$TAG: channelOnReceive: $channelId not handled")
                throw (NotImplementedError("$TAG: channelOnReceive: $channelId not handled"))
            }
        }
    }

    private fun sendToComm(chatMessage: ChatMessageAbs, receiver: Receiver? = null) {
        val tag = "sendToComm/${randomString(2U)}"
        val logF = chatMessage.groupId.type == ChatGroupType.RemoteChat || chatMessage.groupId.type == ChatGroupType.RemoteChatTesting

        val messageType = if (null == receiver) ChannelMessageType.RCChatMessageLoopback else ChannelMessageType.RCChatMessage
        val receiverId = receiver?.node?.id() ?: chatMessage.sender.node.id()
        val receiverUnique = receiver?.node?.unique() ?: chatMessage.sender.node.unique()

        val commPacket = CommPacket(
            /* commType = WTCommType.Data, */
            receiverId = receiverId,
            receiverUnique = receiverUnique,
            groupId = chatMessage.groupId.groupId,
            groupName = chatMessage.groupId.groupName!!,
            groupIdType = chatMessage.groupId.type,
            senderId = chatMessage.sender.node.id(),
            senderUnique = chatMessage.sender.node.unique(),
            payloadType = chatMessage.getPayloadLast()?.type.toString(),
            payloadTimeStampCreation = chatMessage.getPayloadLast()?.timeStampCreation!!,
            payloadValue = chatMessage.getPayloadLast()?.valueString.toString().toByteArray()
        )

        commPacket.logD(tag, logF)

        channelSend(ChannelId.RCToComm, messageType, commPacket)
    }

    private fun processCommPacketIn(commPacket: CommPacket): ChatMessage {
        val tag = "processCommPacketIn/${randomString(2U)}"
        val logF = commPacket.groupIdType == ChatGroupType.RemoteChat || commPacket.groupIdType == ChatGroupType.RemoteChatTesting

        val gId : ChatGroupId =
            if (commPacket.receiverUId() == commPacket.groupId) { /* Check whether this is from an individual peer */
                ChatGroupId(commPacket.senderUId(), commPacket.senderId, type = commPacket.groupIdType)
            } else if (commPacket.receiverUId() == systemNode.uid()) {
                ChatGroupId(commPacket.groupId, commPacket.groupName, type = commPacket.groupIdType)
            } else {

                ChatGroupId(commPacket.groupId, commPacket.groupName, type = commPacket.groupIdType)
            }

        if ((commPacket.groupIdType == ChatGroupType.RemoteChat || commPacket.groupIdType == ChatGroupType.RemoteChatTesting)) {
            groupMap.addNode(gId, NodeId.Builder().id(commPacket.senderId).unique(commPacket.senderUnique).build())
            groupMap.addNode(gId, systemNode)
            commPacket.logD(tag, logF)
            groupMap[gId]?.logD(tag, logF)
        }

        val chatItem = ChatMessageItem.Builder().
            value(commPacket.payloadValue.decodeToString()).
            type(commPacket.payloadType).
            timeStamp(commPacket.payloadTimeStampCreation).
            build()
        val chatMessage = ChatMessage(
            receiver = Receiver(NodeId.Builder().id(commPacket.receiverId).unique(commPacket.receiverUnique).build(), gId),
            groupId = gId,
            sender = Sender(NodeId.Builder().id(commPacket.senderId).unique(commPacket.senderUnique).build()),
            chatMessageItemList = ChatMessageItemList(mutableListOf(chatItem))
        )
        return chatMessage
    }

    private fun recvFromComm(commPacket: CommPacket){
        val tag = "recvFromComm/${randomString(2U)}"
        val logF = (commPacket.groupIdType == ChatGroupType.RemoteChatTesting || commPacket.groupIdType == ChatGroupType.RemoteChat)

        commPacket.logD(tag, logF)

        addMessage(processCommPacketIn(commPacket))
    }
}
