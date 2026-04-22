package walkie.glue.wtchat

import walkie.util.generic.GenericListAbs
import walkie.util.generic.GenericMapAbs

abstract class DiscussionAbs (
    open val discussionId: ChatGroupIdInt,
    private val discussionMessageList: GenericListAbs<ChatMessageAbs>
) : GenericListAbs<ChatMessageAbs>(discussionMessageList) {
    abstract val timeStampCreation: Long
    abstract val timeStampLastUpdated: Long
    var read: Boolean = false

    abstract fun addChatMessage(chatMessage: ChatMessageAbs)
}

abstract class DiscussionMapAbs (
    private val discussionMessageMap: MutableMap<ChatGroupIdInt, DiscussionAbs>
) : GenericMapAbs<ChatGroupIdInt, DiscussionAbs>(discussionMessageMap)
