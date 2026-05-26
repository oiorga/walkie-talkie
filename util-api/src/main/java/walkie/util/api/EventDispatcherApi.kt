package walkie.util.api

interface DispatchEventIdInt

enum class DispatchEventId: DispatchEventIdInt {
    CBDummy0, CBDummy1, CBDummy2, CBDummy3, CBDummy4, CBDummy5, CBDummy6, CBDummy7, CBDummy8, CBDummy9,
    CBNA,
    CBServerPort,
    CBMeshNewPeer,
    CBMeshLostPeer,
    CBMeshResetPeers,
    CBMeshGetGroupOwner,
}
