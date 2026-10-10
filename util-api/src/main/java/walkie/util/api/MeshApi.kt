package walkie.util.api;

enum class MeshDispatchEventId: DispatchEventIdInt {
    CBMeshNewPeer,
    CBMeshLostPeer,
    CBMeshResetPeers,
    CBMeshGetGroupOwner
}
