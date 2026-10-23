package  walkie.comm

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import walkie.talkie.api.wtcomm.WTMedium
import walkie.talkie.api.wtcomm.WTPeerInt
import walkie.talkie.api.wtsystem.nodeUniqueId
import walkie.talkie.api.wtsystem.uidToId
import walkie.talkie.api.wtsystem.uidToUnique
import walkie.util.api.MeshKey
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

class WTIPMesh(uniqueId: String, scope: CoroutineScope) : Mesh<String, WTCommPeerInfo>(uniqueId, scope) {
    companion object {
        const val TAG = "WTIPMesh"
        val TAGKClass = WTIPMesh::class
    }

    val json = Json {
        encodeDefaults = true
        allowStructuredMapKeys = true
    }

    init {
        logging(true)
    }

    override fun encodeToString(kToVTable: Pair<MeshKey<String>, Map<MeshKey<String>, WTCommPeerInfo>>): String {

        val tag = "encodeToString/${randomString(2U)}"
        var jSon: String? = null

        `try`(TAGKClass, tag) {
            logd(TAGKClass, tag, "Encoding kToVTable to json")
            jSon = json.encodeToString(kToVTable)
        }

        return checkNotNull(jSon) {
            logd(TAGKClass, tag, "Encoding completed without producing JSON")
            "$TAGKClass/$tag Encoding completed without producing JSON"
        }
    }

    override fun decodeFromString(input: String): Pair<MeshKey<String>, MutableMap<MeshKey<String>, WTCommPeerInfo>>? {
        val tag = "decodeFromString/${randomString(2U)}"
        var kToVTable: Pair<MeshKey<String>, MutableMap<MeshKey<String>, WTCommPeerInfo>>? = null

        val exc = `try`(TAGKClass, tag, throwExc = false) {
            logd(TAGKClass, tag, "Decoding input jSon to kToVTable")
            kToVTable = json.decodeFromString<Pair<MeshKey<String>, MutableMap<MeshKey<String>, WTCommPeerInfo>>>(input)
        }

        return if (exc == null) {
            checkNotNull(kToVTable) {
                "$TAGKClass/$tag: Decoding completed without producing a result"
            }
        } else {
            null
        }
    }
}