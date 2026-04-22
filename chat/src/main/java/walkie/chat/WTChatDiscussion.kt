package walkie.chat

import android.util.Log
import walkie.util.generic.GenericListAbs
import walkie.glue.wtchat.ChatGroupIdInt
import walkie.glue.wtchat.ChatMessageAbs
import walkie.glue.wtchat.DiscussionAbs
import walkie.glue.wtchat.DiscussionMapAbs

data class ChatDiscussion(
    override val discussionId: ChatGroupIdInt,
    private val discussionMessageList: DiscussionItemList = DiscussionItemList(),
) : DiscussionAbs(discussionId, discussionMessageList) {
    private val tag = "ChatDiscussion"

    private val _timeStampCreation: Long =
        java.time.Instant.now().toEpochMilli()
    private var _timeStampLastUpdated = _timeStampCreation

    override val timeStampCreation: Long
        get() = _timeStampCreation

    override val timeStampLastUpdated: Long
        get() = _timeStampLastUpdated

    init {
        Log.d(tag, "$tag:init")

    }

    override fun addChatMessage(chatMessage: ChatMessageAbs) {
        Log.d(tag, "$tag:add 0")

        if (discussionMessageList.isNotEmpty()) discussionMessageList.removeLast()

        if (discussionMessageList.isNotEmpty() && (discussionMessageList.last().sender.node.uid() == chatMessage.sender.node.uid())) {
            Log.d(tag, "$tag:add 1 0")
            discussionMessageList.last().add(chatMessage.last())
        } else {
            discussionMessageList.add(chatMessage)
            Log.d(tag, "$tag:add 1 1")
        }

        /* Dummy. Why? */
        discussionMessageList.add(ChatMessage(
            chatMessage.receiver,
            chatMessage.sender,
            chatMessage.groupId))

        _timeStampLastUpdated =
            java.time.Instant.now().toEpochMilli()

        read = false

        Log.d(tag, "$tag:add 1")
    }
}

data class DiscussionItemList(
    private val itemList: MutableList<ChatMessageAbs> = mutableListOf()
) : GenericListAbs<ChatMessageAbs>(itemList) {
    private val tag = "DiscussionItemList"

    init {
        if (itemList.isEmpty()) {
            Log.d(tag, "ChatMessageItemList called empty on init")
        }
    }
}

data class ChatDiscussionMap(
    private val discussionMessageMap: MutableMap<ChatGroupIdInt, DiscussionAbs> = mutableMapOf<ChatGroupIdInt, DiscussionAbs>()
) : DiscussionMapAbs(discussionMessageMap) {
    private val tag = "ChatDiscussionMap"

    init {
        Log.d(tag, "$tag:init")
    }
}