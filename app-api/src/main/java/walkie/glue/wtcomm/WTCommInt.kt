package walkie.glue.wtcomm

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import walkie.glue.wtchat.ChatGroupType
import walkie.glue.wtsystem.nodeUniqueId

enum class CommTransmissionMedium {BlueTooth, WIFIDirect}
enum class CommLoopback {Internal, External}

@Serializable
enum class WTCommType {
    Control,
    ControlBroadcast,
    ControlUnicast,
    Data
}

interface CommPacketInt {
    /* val commType: WTCommType */
    val directReceiver: String?
    val receiverId: String?
    val receiverUnique: String?
    val groupId: String?
    val groupName: String?
    val groupIdType: ChatGroupType?
    val senderId: String?
    val senderUnique: String?
    val payloadType: String?
    val payloadTimeStampCreation: Long?
    val payloadValue: ByteArray?
    fun logD(tag: String = "CommPacket", logF: Boolean = true)
    fun senderUId(): String?
    fun receiverUId(): String?
}

@Serializable
data class CommPacket (
    /* override val commType: WTCommType, */
    override val directReceiver: String? = null,
    override val receiverId: String,
    override val receiverUnique: String,
    override val groupId: String,
    override val groupName: String,
    override val groupIdType: ChatGroupType,
    override val senderId: String,
    override val senderUnique: String,
    override val payloadType: String,
    override val payloadTimeStampCreation: Long,
    override val payloadValue: ByteArray
) : CommPacketInt {

    fun toJson() : String {
        val json = Json { encodeDefaults = true }
        return (json.encodeToString(this))
    }

    override fun senderUId(): String {
        return nodeUniqueId(senderId, senderUnique)
    }

    override fun receiverUId(): String {
        return nodeUniqueId(receiverId, receiverUnique)
    }

    override fun logD(tag: String, logF: Boolean) {
        if (logF) Log.d(tag, tag +
                "\n receiver: " + this.receiverUId()+
                "\n sender: " + this.senderUId() +
                "\n groupId: " + this.groupId.toString() +
                "\n groupType: " + this.groupIdType +
                "\n chatMessage: " +
                "\n  value: ${this.payloadValue.decodeToString()}" +
                "\n  type: ${this.payloadType.toString()}" +
                "\n  timeStamp: ${this.payloadTimeStampCreation.toString()}"
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CommPacket

        if (directReceiver != other.directReceiver) return false
        if (receiverId != other.receiverId) return false
        if (receiverUnique != other.receiverUnique) return false
        if (groupId != other.groupId) return false
        if (groupIdType != other.groupIdType) return false
        if (senderId != other.senderId) return false
        if (senderUnique != other.senderUnique) return false
        if (!payloadValue.contentEquals(other.payloadValue)) return false
        if (payloadType != other.payloadType) return false
        if (payloadTimeStampCreation != other.payloadTimeStampCreation) return false

        return true
    }

    override fun hashCode(): Int {
        var result = directReceiver?.hashCode() ?: 0
        result = 31 * result + (receiverId.hashCode() ?: 0)
        result = 31 * result + (receiverUnique.hashCode() ?: 0)
        result = 31 * result + (groupId.hashCode() ?: 0)
        result = 31 * result + (groupIdType.hashCode() ?: 0)
        result = 31 * result + (senderId.hashCode() ?: 0)
        result = 31 * result + (senderUnique.hashCode() ?: 0)
        result = 31 * result + payloadValue.contentHashCode()
        result = 31 * result + (payloadType.hashCode() ?: 0)
        result = 31 * result + payloadTimeStampCreation.hashCode()
        return result
    }
}

interface WTCommChatMessageOut {
    suspend fun chatMessageOut(commPacket: CommPacketInt)
}

interface WTCommChatMessageIn {
    suspend fun chatMessageIn(commPacket: CommPacketInt)
}

interface WTCommPacketOut {
    suspend fun commPacketOut(commPacket: CommPacketInt)
}

interface WTCommPacketIn {
    suspend fun commPacketIn(commPacket: CommPacketInt)
}

@Serializable
sealed class WTMedium(open val type: Medium) {
    @Serializable
    enum class Medium {
        Dummy,
        WifiDirect,
        WifiAware,
        Bluetooth,
        WifiIp,
        Air,
        Water,
        Fire,
        Earth
    }
/*
    @Serializable
    data object WD : WTMedium(Medium.WifiDirect)
    @Serializable
    data object WA : WTMedium(Medium.WifiAware)
    @Serializable
    data object BT : WTMedium(Medium.Bluetooth)
*/

    companion object {

    }

    @Serializable
    data object WifiIp : WTMedium(Medium.WifiIp)

    val name: String
        get() = this.type.name
}

interface WTPeerInt {
    val id: String
    val unique: String?
    val underlyingMedium: WTMedium
    /*
     * [u]nderlying [m]edium [C]onnection [I]nfo
     *
     * IE: IP Address
     */
    val umCI: List<String>
    //val connected: Boolean
}
