package  walkie.comm

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import walkie.glue.wtcomm.WTMedium
import walkie.glue.wtcomm.WTPeerInt
import walkie.glue.wtsystem.nodeUniqueId
import walkie.glue.wtsystem.uidToId
import walkie.glue.wtsystem.uidToUnique
import walkie.util.`try`
import walkie.util.logd
import walkie.util.logging
import walkie.util.mesh.Mesh
import walkie.util.randomString

@Serializable
data class WTCommPeerInfo (
    override var id: String,
    override var unique: String? = null,
    override val underlyingMedium: WTMedium = WTMedium.WifiIp,
    override val umCI: List<String> = listOf(),
    //override val connected: Boolean = false,
) : WTPeerInt {
    companion object {
        private const val TAG = "WTCommPeerInfo"
        val TAGKClass = WTCommPeerInfo::class
    }

    init {
        logging(true)
        logd(TAG, "(0) id: $id unique: $unique")
        if (null == unique) {
            unique = uidToUnique(id)
            id = uidToId(id)!!
        }
        unique!!
        logd(TAG, "(1) id: $id unique: $unique")
    }

    val name: String
        get() = id

    val uid: String
        get() = nodeUniqueId(id, unique!!)
}

fun WTCommPeerInfo.uid(): String {
    return nodeUniqueId(id, unique!!)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class WTIPMesh(uniqueId: String) : Mesh<String, WTCommPeerInfo>(uniqueId) {
    companion object {
        const val TAG = "WTIPMesh"
        val TAGKClass = WTIPMesh::class
    }

    init {
        logging(true)
    }

    override fun encodeToString(kToVTable: Pair<String?, MutableMap<String?, WTCommPeerInfo>>): String {
        val json = Json { encodeDefaults = true }
        val tag = "encodeToString/${randomString(2U)}"
        var jSon: String? = null

        `try`(TAGKClass, tag) {
            logd(TAGKClass, tag, "Encoding kToVTable to json")
            jSon = json.encodeToString(kToVTable)
        }

        return jSon!!
    }

    override fun decodeFromString(input: String): Pair<String?, MutableMap<String?, WTCommPeerInfo>>? {
        val json = Json { encodeDefaults = true }
        val tag = "decodeFromString/${randomString(2U)}"
        var kToVTable: Pair<String?, MutableMap<String?, WTCommPeerInfo>>? = null

        val exc = `try`(TAGKClass, tag, throwExc = false) {
            logd(TAGKClass, tag, "Decoding input jSon to kToVTable")
            kToVTable = json.decodeFromString<Pair<String?, MutableMap<String?, WTCommPeerInfo>>>(input)
        }

        return if (null == exc) kToVTable!! else null
    }
}