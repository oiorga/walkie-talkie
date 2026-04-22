package walkie.comm.ip

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString
import walkie.util.`try`

@Serializable
enum class WTIPCommPacketType {
    Control,
    ControlMesh,
    ControlBroadcast,
    ControlUnicast,
    Data
}

@Serializable
class WTIPPacketComm (
    val ipCommType: WTIPCommPacketType,
    val jsonString: String
) {
    companion object {
        const val TAG = "WTIPPacketComm"
        val TAGKClass = WTIPPacketComm::class
    }

    init {
        logging()
    }

    fun toByteArray(): ByteArray {
        val tag = "toByteArray/${randomString(2U)}"
        logd(tag, "toByteArray input Json WTIPPacketComm")
        return toJson().toByteArray()
    }

    fun toJson() : String {
        val tag = "toJson/${randomString(2U)}"
        var str: String? = null
        val json = Json { encodeDefaults = true }

        logd(tag, "encodeToString input Json WTIPPacketComm")
        `try`(TAGKClass, tag) {
            str = json.encodeToString(this)
        }
        return str!!
    }
}
