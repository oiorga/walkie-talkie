package walkie.chat

import android.util.Log
import walkie.glue.wtchat.AuthorInt
import walkie.glue.wtchat.ChatGroupIdInt
import walkie.glue.wtchat.ChatGroupListAbs
import walkie.glue.wtchat.ChatGroupMapAbs
import walkie.glue.wtchat.ChatGroupType
import walkie.glue.wtchat.ReceiverInt
import walkie.glue.wtchat.SenderInt
import walkie.glue.wtsystem.NodeIdInt
import walkie.util.logd
import walkie.util.logging

data class ChatGroupMap(
    private val groupMap: MutableMap<ChatGroupIdInt, ChatGroupListAbs> = mutableMapOf()
) : ChatGroupMapAbs(groupMap) {
    private val tag = "ChatMessageItemList"

    init {
        if (groupMap.isEmpty()) {
            Log.d(tag, "ChatMessageItemList called empty on init")
        }
    }

    private fun createGroup (chatGroupId: ChatGroupIdInt) : ChatGroupListAbs? {
        val chatGroupL: ChatGroupListAbs?

        if (null == groupMap[chatGroupId]) {
            chatGroupL = ChatGroupList(chatGroupId)
            groupMap[chatGroupId] = chatGroupL
        } else {
            chatGroupL = groupMap[chatGroupId]
        }

        return chatGroupL
    }

    fun addNode (chatGroupId: ChatGroupId, nodeId: NodeIdInt) {
        val chatGroupL = createGroup(chatGroupId)

        if (chatGroupL != null) {
            if (nodeId !in chatGroupL) {
                chatGroupL.add(nodeId)
            }
        }
    }

    fun removeGroup (chatGroupId: ChatGroupIdInt) : ChatGroupListAbs? {
        return groupMap.remove(chatGroupId)
    }

    fun groupIsEmpty (chatGroupId: ChatGroupIdInt) : Boolean {
        return ((groupMap[chatGroupId] != null) && (groupMap[chatGroupId]?.isEmpty() == true))
    }

    fun groupExists(chatGroupId: ChatGroupIdInt) : Boolean {
        return groupMap[chatGroupId] != null
    }

    fun getGroupList (chatGroupId: ChatGroupIdInt) : ChatGroupListAbs? {
        return groupMap[chatGroupId]
    }
}

data class ChatGroupList(
    override val groupId: ChatGroupIdInt,
    private var groupIdList: MutableList<NodeIdInt> = mutableListOf()
) : ChatGroupListAbs(groupId, groupIdList) {
    private val tag = "ChatMessageItemList"

    init {
        logging(true)
        if (groupIdList.isEmpty()) {
            logd(tag, "ChatMessageItemList called empty on init")
        }
    }
}

data class ChatGroupId(
    override val groupId: String,
    override var groupName: String? = null,
    override val type: ChatGroupType
) : ChatGroupIdInt {
    private val tag = "ChatDiscussionId"

    init {
        Log.d(tag, "$tag: $groupId")
        if (debug && (null == groupName)) groupName = groupId
        groupName!!
    }

    override fun logD(tag: String, logF: Boolean) {
        if (logF) Log.d(tag, "tag\ngroupId: $groupId\ntype: $type")
    }
}

data class Sender (
    override val node: NodeIdInt
) : SenderInt

data class Receiver (
    override val node: NodeIdInt,
    override val discussionId: ChatGroupIdInt
) : ReceiverInt {

}

data class Author (
    override val firstName: String = "",
    override val lastName: String = "",
) : AuthorInt {
    override val name = "$firstName.$lastName"
}
