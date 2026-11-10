package walkie.util.api;

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable

enum class MeshDispatchEventId: DispatchEventId {
    DEMeshNewPeer,
    DEMeshLostPeer,
    DEMeshResetPeers,
    DEMeshGetGroupOwner
}

@Serializable
sealed interface MeshKey<out K> {
    @Serializable
    data class Key<K>(val id: K) : MeshKey<K>
    @Serializable
    data object Root : MeshKey<Nothing>
}

interface MeshApi<K , V> {
    val uniqueId: K
    val scope: CoroutineScope
    val heartbeat: Long
        get() = 3000L

    fun registerUpdateCall (updatePeersCall: suspend (V, String) -> Unit)
    fun decodeFromString(input: String): Pair<MeshKey<K>, MutableMap<MeshKey<K>, V>>?
    fun encodeToString(kToVTable: Pair<MeshKey<K>, Map<MeshKey<K>, V>>): String
    suspend fun resetPeersInfo()
    suspend fun addPeer(k: MeshKey<K>, v: V)
    fun directPeer (node: MeshKey<K>): V?
    fun directPeerAgeSeconds (node: MeshKey<K>): Long?
    fun directPeerAgeMills (node: MeshKey<K>): Long?
}