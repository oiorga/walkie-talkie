package walkie.talkie.globalmap

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import walkie.chat.ChatDiscussion
import walkie.chat.ChatGroupId
import walkie.chat.ChatGroupMap
import walkie.chat.ChatMessage
import walkie.chat.ChatMessageItem
import walkie.chat.ChatMessageItemList
import walkie.chat.Receiver
import walkie.chat.Sender
import walkie.util.generic.PipeMux
import walkie.util.generic.PipeMuxInt
import walkie.talkie.api.wtchat.ChatGroupType
import walkie.talkie.api.wtchat.ChatMessageAbs
import walkie.talkie.api.wtchat.DiscussionAbs
import walkie.talkie.api.wtchat.DiscussionMapAbs
import walkie.talkie.api.wtcomm.CommPacket
import walkie.talkie.api.wtsystem.NodeIdInt
import walkie.talkie.common.UpdateUiLiveData
import walkie.talkie.node.NodeId
import walkie.util.api.PipeId
import walkie.util.api.PipeIdInt
import walkie.util.api.PipeMessageType
import walkie.util.api.RemoteCallMuxInt
import walkie.util.generic.RemoteCallMux
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
    val discussionMap: DiscussionMapAbs,
    private val groupMap: ChatGroupMap,
    private val systemNode: NodeIdInt,
    val scope: CoroutineScope,
    private val _remoteCallMux: RemoteCallMuxInt = RemoteCallMux(),
    private val _channelMux: PipeMuxInt<Any, PipeMessageType> = PipeMux<Any, PipeMessageType>(),
) : RemoteCallMuxInt by _remoteCallMux,
    PipeMuxInt<Any, PipeMessageType> by _channelMux
{
    lateinit var updateUiLiveData: UpdateUiLiveData

    companion object {
        const val TAG = "GlobalDiscussionMap"
    }

    val mutex = Mutex()

    init {
        /* registerRemoteCall(RemoteCallId.RCChatMessageFromComm) { run { recvFromComm(it as CommPacket) } } */

        logging(true)
        
        logd(TAG, "init")
    }

    private suspend fun addMessage(chatMessage: ChatMessageAbs) {
        val tag = "addMessage/${randomString(2U)}"
        val logF = (chatMessage.groupId.type == ChatGroupType.RemoteChatTesting || chatMessage.groupId.type == ChatGroupType.RemoteChat)

        val type = chatMessage.groupId.type
        val gIdSender = ChatGroupId(chatMessage.sender.node.uid(), chatMessage.sender.node.id(), type = type)
        val gIdGroup = ChatGroupId(chatMessage.groupId.groupId, chatMessage.groupId.groupName, type = type)
        val gIdReceiver = ChatGroupId(chatMessage.receiver.node.uid(), chatMessage.receiver.node.id(), type = type)
        val gIdMe = ChatGroupId(systemNode.uid(), systemNode.id(), type = type)
        val gIdBogus = ChatGroupId("Bogus", "Bogus", type = ChatGroupType.Etc)

        mutex.lock()

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

        mutex.unlock()

        pipeSend(PipeId.RCTOCommonData,  scope, PipeMessageType.RCUpdateChatUI, chatMessage.groupId.type)
    }

    suspend fun createDiscussion(chatGroupId: ChatGroupId) : ChatDiscussion {
        var chatDiscussion = ChatDiscussion(chatGroupId)

        mutex.lock()
        if (null != discussionMap[chatGroupId]) {
            chatDiscussion = discussionMap[chatGroupId] as ChatDiscussion
        } else {
            discussionMap[chatGroupId] = chatDiscussion
        }
        mutex.unlock()
        return chatDiscussion
    }

    suspend fun createDiscussion(chatGroupId: ChatGroupId, nodeId: NodeIdInt) : ChatDiscussion {
        var chatDiscussion = ChatDiscussion(chatGroupId)

        mutex.lock()
        groupMap.addNode(chatGroupId, nodeId)

        if (null != discussionMap[chatGroupId]) {
            chatDiscussion = discussionMap[chatGroupId] as ChatDiscussion
        } else {
            discussionMap[chatGroupId] = chatDiscussion
        }
        mutex.unlock()
        return chatDiscussion
    }

    suspend fun removeDiscussion(chatGroupId: ChatGroupId) : DiscussionAbs? {
        mutex.lock()
        val chat = discussionMap.remove(chatGroupId)
        mutex.unlock()

        return chat
    }

    suspend fun replaceDiscussion(chatGroupId: ChatGroupId, chatDiscussion: ChatDiscussion) : DiscussionAbs? {
        mutex.lock()
        discussionMap[chatGroupId] = chatDiscussion
        mutex.unlock()
        return discussionMap[chatGroupId]
    }

    suspend fun sendMessage(chatMessage: ChatMessageAbs) {
        val tag = "sendMessage/${randomString(2U)}"
        val logF = (chatMessage.groupId.type == ChatGroupType.RemoteChat ||
                chatMessage.groupId.type == ChatGroupType.RemoteChatTesting)

        /*
        logd(tag,"group: ${chatMessage.groupId.groupId.toString()} is " +
                    (if (null == group) "" else "not") + " null" + " type: " + group?.groupId?.type,
            logF)
        group?.logD(tag, logF)
        logd(tag, "$tag 0 group?.groupId?.type == " + group?.groupId?.type, logF)
        */

        mutex.lock()
        val group = groupMap[chatMessage.groupId]
        mutex.unlock()

        group?.logD(tag, logF)
        chatMessage.logD(tag, logF)
        sendToComm(chatMessage)

        if (group?.groupId?.type != ChatGroupType.Local) {
            group?.logD(tag, logF)
            group?.forEach { receiverNode ->
                if (receiverNode.uid() != systemNode.uid()) {
                    logd(
                        tag,
                        "$tag: sendMessage: to group: ${chatMessage.groupId.groupId} to: " + receiverNode.uid(),
                        logF
                    )
                    receiverNode.logD(tag, logF)
                    chatMessage.logD(tag, logF)
                    sendToComm(chatMessage, receiver = Receiver(receiverNode, group.groupId))
                }
            }
        }
    }

    override suspend fun pipeOnReceive(
        pipeId: PipeIdInt,
        type: PipeMessageType?,
        input: Any?
        ) {
        when (pipeId) {
            PipeId.RCCommToChat -> {
                recvFromComm(input as CommPacket)
            }
            else -> {
                logd(TAG, "$TAG: channelOnReceive: $pipeId not handled")
                throw (NotImplementedError("$TAG: channelOnReceive: $pipeId not handled"))
            }
        }
    }

    private fun sendToComm(chatMessage: ChatMessageAbs, receiver: Receiver? = null) {
        val tag = "sendToComm/${randomString(2U)}"
        val logF = chatMessage.groupId.type == ChatGroupType.RemoteChat || chatMessage.groupId.type == ChatGroupType.RemoteChatTesting

        val messageType = if (null == receiver) PipeMessageType.RCChatMessageLoopback else PipeMessageType.RCChatMessage
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

        pipeSend(PipeId.RCToComm, scope, messageType, commPacket)
    }

    private suspend fun processCommPacketIn(commPacket: CommPacket): ChatMessage {
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

        mutex.lock()

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

        mutex.unlock()

        return chatMessage
    }

    private suspend fun recvFromComm(commPacket: CommPacket){
        val tag = "recvFromComm/${randomString(2U)}"
        val logF = (commPacket.groupIdType == ChatGroupType.RemoteChatTesting || commPacket.groupIdType == ChatGroupType.RemoteChat)

        commPacket.logD(tag, logF)

        addMessage(processCommPacketIn(commPacket))
    }
}
