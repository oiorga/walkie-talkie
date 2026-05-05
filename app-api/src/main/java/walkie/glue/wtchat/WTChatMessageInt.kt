package walkie.glue.wtchat

import android.util.Log
import walkie.util.generic.GenericListAbs
import walkie.glue.wtcomm.CommPacketInt
import walkie.glue.wtsystem.NodeIdInt

abstract class ChatMessageAbs (
    open val receiver: ReceiverInt,
    open val sender: SenderInt,
    open val groupId: ChatGroupIdInt,
    private val chatMessageItemList: GenericListAbs<ChatMessageItemInt>
) : GenericListAbs<ChatMessageItemInt>(chatMessageItemList) {
    abstract val timeStampCreation: Long
    abstract fun toCommPacket(): CommPacketInt

    open fun logD(tag: String = "ChatMessage", logF: Boolean = true) {
        if (logF) Log.d(tag, tag +
                "\n receiver: " + this.receiver.toString() +
                "\n sender: " + this.sender.toString() +
                "\n groupId: " + this.groupId.toString() + " type: " + this.groupId.type +
                "\n chatMessage: " +
                "\n  value: ${this.chatMessageItemList.last().valueString}" +
                "\n  type: ${this.chatMessageItemList.last().type}" +
                "\n  timeStamp: ${this.chatMessageItemList.last().timeStampCreation}")
    }

    open fun getPayload(): GenericListAbs<ChatMessageItemInt> {
        return chatMessageItemList
    }

    open fun getPayloadLast(): ChatMessageItemInt? {
        return (chatMessageItemList.last())
    }
}

interface AuthorInt {
    val firstName: String
    val lastName: String
    val name: String
}

interface SenderInt {
    val node: NodeIdInt
}

interface ReceiverInt {
    val node: NodeIdInt
    val discussionId: ChatGroupIdInt?
}

interface ChatMessageItemInt {
    var valueString: String?
    val byteArrayValue: ByteArray?
    val type: String?
    val timeStampCreation: Long
    var timeStampReceived: Long
}
