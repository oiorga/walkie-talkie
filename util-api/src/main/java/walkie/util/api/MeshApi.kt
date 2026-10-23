package walkie.util.api;

import kotlinx.serialization.Serializable

enum class MeshDispatchEventId: DispatchEventIdInt {
    CBMeshNewPeer,
    CBMeshLostPeer,
    CBMeshResetPeers,
    CBMeshGetGroupOwner
}

@Serializable
sealed interface MeshKey<out K> {
    @Serializable
    data class Key<K>(val id: K) : MeshKey<K>
    @Serializable
    data object Root : MeshKey<Nothing>
}