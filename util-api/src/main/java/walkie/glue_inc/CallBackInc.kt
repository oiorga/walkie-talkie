package walkie.glue_inc

interface CallBackIdInt
enum class CallBackId: CallBackIdInt {
    CBDummy0, CBDummy1, CBDummy2, CBDummy3, CBDummy4, CBDummy5, CBDummy6, CBDummy7, CBDummy8, CBDummy9,
    CBNA,
    CBServerPort,
    CBMeshNewPeer,
    CBMeshLostPeer,
    CBMeshResetPeers,
    CBMeshGetGroupOwner,
}
