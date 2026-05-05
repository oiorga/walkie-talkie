package walkie.glue.wtchat

import android.util.Log
import kotlinx.serialization.Serializable
import walkie.util.generic.GenericListAbs
import walkie.util.generic.GenericMapAbs
import walkie.glue.wtsystem.NodeIdInt

@Serializable
sealed interface CGType {
    val tag: String
}

@Serializable
sealed class ChatGroupType (override val tag: String) : CGType {
    @Serializable
    data object Local : ChatGroupType("Local")
    @Serializable
    data object LocalDebug : ChatGroupType("LocalDebug")
    @Serializable
    data object LocalChatTesting : ChatGroupType("LocalChatTesting")
    @Serializable
    data object RemoteChat : ChatGroupType("RemoteChat")
    @Serializable
    data object RemoteChatTesting : ChatGroupType("RemoteChatTesting")
    /*
    @Serializable
    data object WalkieTalkie : ChatGroupType("WalkieTalkie")
    */
    @Serializable
    data object Etc : ChatGroupType("Etc")
}

interface ChatGroupIdInt {
    val groupId: String
    var groupName: String?
    val type: ChatGroupType
    val debug: Boolean
        get() = (ChatGroupType.LocalDebug == type ||
                ChatGroupType.LocalChatTesting == type ||
                ChatGroupType.RemoteChatTesting == type)
    fun logD(tag: String = "ChatGroupIdInt", logF: Boolean = true)
}

abstract class ChatGroupMapAbs (
    groupIdMap: MutableMap<ChatGroupIdInt, ChatGroupListAbs>
) : GenericMapAbs<ChatGroupIdInt, ChatGroupListAbs>(groupIdMap)

abstract class ChatGroupListAbs (
    open val groupId: ChatGroupIdInt,
    private val groupIdList: MutableList<NodeIdInt>
) : GenericListAbs<NodeIdInt>(groupIdList) {

    fun logD(tag: String = "ChatGroupListAbs", logF: Boolean = true) {
        if (logF) Log.d(tag, "\n$tag" +
                "\n\t\t\t\tgroupId: " + groupId +
                "\n\t\t\t\tgroupList: " + groupIdList
        )
    }
}
