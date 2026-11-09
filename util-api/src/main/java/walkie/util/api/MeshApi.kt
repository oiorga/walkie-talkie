package walkie.util.api;

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