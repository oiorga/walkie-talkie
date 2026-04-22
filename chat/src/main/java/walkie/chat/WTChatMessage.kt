package walkie.chat

import android.util.Log
import walkie.util.generic.GenericListAbs
import walkie.glue.wtchat.ChatGroupIdInt
import walkie.glue.wtchat.ChatMessageAbs
import walkie.glue.wtchat.ChatMessageItemInt
import walkie.glue.wtchat.ReceiverInt
import walkie.glue.wtchat.SenderInt
import walkie.glue.wtcomm.CommPacket
import walkie.glue.wtcomm.CommPacketInt
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString
import walkie.util.timeNow

data class ChatMessage(
    override val receiver: ReceiverInt,
    override val sender: SenderInt,
    override val groupId: ChatGroupIdInt,
    val chatMessageItemList: GenericListAbs<ChatMessageItemInt> = ChatMessageItemList()
    ) : ChatMessageAbs(receiver, sender, groupId, chatMessageItemList) {
    private val tag = "ChatMessage"
    override val timeStampCreation: Long

    init {
        if (chatMessageItemList.isEmpty()) {
            Log.d(tag, "ChatMessage called empty on init")
        }
        timeStampCreation = timeNow()
    }

    override fun toCommPacket(): CommPacketInt {
        val commPacket = CommPacket(
            /* commType = WTCommType.Data, */
            receiverId = receiver.node.id(),
            receiverUnique = receiver.node.unique(),
            groupId = groupId.groupId,
            groupName = groupId.groupName!!,
            groupIdType = groupId.type,
            senderId = sender.node.id(),
            senderUnique = sender.node.unique(),
            payloadType = getPayloadLast()?.type.toString(),
            payloadTimeStampCreation = getPayloadLast()?.timeStampCreation!!,
            payloadValue = getPayloadLast()?.valueString.toString().toByteArray()
        )
        return commPacket
    }
}

data class ChatMessageItem(
    private var _byteArrayValue : ByteArray? = null,
    private var _type: String? = null,
    private var _timeStampCreation: Long = 0,
    private var _timeStampReceived: Long = 0
) : ChatMessageItemInt {

    companion object {
        val nonDefaultValueType = mapOf(
            "Bubiko" to ("Bubiko"),
        )
    }

    init {
        //_timeStampReceived =
        timeStampCreation()
    }

    private fun timeStampCreation(timeStamp: Long = 0L): Long {
        var temp: Long = (if (0L == timeStamp) _timeStampCreation else timeStamp)
        if (0L == temp) {
            temp = timeNow()
            _timeStampCreation = temp
        }
        return temp
    }

    data class Builder(
        var value: Any? = null,
        var type: String? = null,
        var timeStampCreation: Long = 0
    ) {
        fun value(value: Any) = apply {
            this.type = (value::class.simpleName!!)
            this.value = value
        }
        fun type(type: String) = apply {
            if (null == this.type) this.type = type
        }
        fun timeStamp(timeStamp: Long = 0) = apply {
            this.timeStampCreation = (if (0L == timeStamp) timeNow() else timeStamp)
        }
        fun build() = ChatMessageItem(
            _byteArrayValue = value.toString().encodeToByteArray(),
            _type = this.type,
            _timeStampCreation = this.timeStampCreation)
    }

    override var timeStampReceived: Long
        get() = _timeStampReceived
        set(value) { _timeStampReceived = value }

    override val timeStampCreation: Long
        get() = _timeStampCreation

    override val type: String?
        get() = _type

    override var valueString: String? =
        if (null == type) {
            null
        } else {
            when (type) {
                "String" -> _byteArrayValue?.decodeToString()
                else -> {
                    null
                }
            }
        }

    override val byteArrayValue: ByteArray?
        get() = _byteArrayValue

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChatMessageItem

        if (_byteArrayValue != null) {
            if (other._byteArrayValue == null) return false
            if (!_byteArrayValue.contentEquals(other._byteArrayValue)) return false
        } else if (other._byteArrayValue != null) return false
        if (type != other.type) return false
        if (_timeStampCreation != other._timeStampCreation) return false
        if (valueString != other.valueString) return false

        return true
    }

    override fun hashCode(): Int {
        var result = _byteArrayValue?.contentHashCode() ?: 0
        result = 31 * result + (type?.hashCode() ?: 0)
        result = 31 * result + _timeStampCreation.hashCode()
        result = 31 * result + (valueString?.hashCode() ?: 0)
        return result
    }
}

data class ChatMessageItemList(
    private val itList: MutableList<ChatMessageItemInt> = mutableListOf<ChatMessageItemInt>()
) : GenericListAbs<ChatMessageItemInt>(itList) {

    companion object {
        const val TAG = "ChatMessageItemList"
        val TAGKClass = ChatMessageItemList::class
    }

    init {
       logging()
    }

    override fun add(element: ChatMessageItemInt): Boolean {
        val tag = "add/${randomString(2U)}"
        val chatMessageItem = element as ChatMessageItem

        chatMessageItem.timeStampReceived = timeNow()

        logd(tag, "Adding chatMessage with timeStampReceived: ${chatMessageItem.timeStampReceived}")

        return super.add(chatMessageItem)
    }

    init {
        if (itList.isEmpty()) {
            logd(TAG, "ChatMessageItemList called empty on init")
        } else {
            val now = timeNow()
            itList.forEach {item ->
                item.timeStampReceived = now
            }
        }
    }
}
