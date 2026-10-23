package walkie.util.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import walkie.util.Gate
import walkie.util.api.MeshDispatchEventId
import walkie.util.generic.BlockingQueue
import walkie.util.generic.EventDispatcher
import walkie.util.generic.EventDispatcherInt
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString

abstract class Mesh<K , V> (
    private var uniqueId: K,
    private var scope: CoroutineScope,
    private var heartbeat: Long = 3000L,
    private val _callBackList: EventDispatcherInt<Any> = EventDispatcher()
) : EventDispatcherInt<Any> by _callBackList
{
    companion object {
        const val TAG = "Mesh"
        val TAGKClass = Mesh::class
    }

    private val kAgeTable: MutableMap<K?, Long> = mutableMapOf<K?, Long>()
    @Volatile
    private var kAgeTableSnapshot: Map<K?, Long> = emptyMap()

    private val kToVTable: MutableMap<K?, V> = mutableMapOf<K?, V>()
    @Volatile
    private var kToVTableSnapshot: Map<K?, V> = emptyMap<K?, V>()

    private val inPeersQ: BlockingQueue<Pair<K?, MutableMap<K?, V>>> = BlockingQueue<Pair<K?, MutableMap<K?, V>>>(name = "$TAG/inPeersQ", permits = 100)
    private val meshMutex: Mutex = Mutex()
    private var sendCall: (suspend (v: V, info: String) -> Unit)? = null
    private val inPeersGate: Gate = Gate()

    suspend fun resetPeersInfo() {
        val tag = "resetPeersInfo/${randomString(2u)}"
        logd(tag, "resetPeersInfo")

        meshMutex.withLock {
            kToVTable.clear()
            kToVTableSnapshot = kToVTable.toMap()
            kAgeTable.clear()
            kAgeTableSnapshot = kAgeTable.toMap()
        }
        dispatchEvent(MeshDispatchEventId.CBMeshResetPeers)
    }

    init {
        val tag = "init/${randomString(2u)}"
        logging()
        logd(tag, "Init Entry")

        main()

        logd(tag, "Init Exit")
    }

    abstract fun decodeFromString(input: String): Pair<K?, MutableMap<K?, V>>?
    fun addPeersJson(jsonString: String) {
        val tag = "addPeersJson/${randomString(2u)}"

        val kToVTable = decodeFromString(jsonString) ?: return

        inPeersQ.enqueueRetryBestEffort(kToVTable)

        /*
        if (!inPeersQ.enqueue(kToVTable)) {
            logd(tag, "inPeersQ.enqueue failed")
        }
        */
    }

    abstract fun encodeToString(kToVTable: Pair<K?, Map<K?, V>>): String

    private suspend fun sendPeers(dest: V, kToVTable: Pair<K?, Map<K?, V>>) {
        val tag = "sendPeers/${randomString(2u)}"

        logd(TAGKClass, tag, "(0): $dest -> $kToVTable")
        val jSon = encodeToString(kToVTable)
        logd(TAGKClass, tag, "(1): $dest -> $jSon")

        sendCall?.invoke(dest, jSon)
            ?: logd(TAGKClass, tag, "sendPeersCall is NULL")
    }

    fun registerSend (sendCall: suspend (V, String) -> Unit) {
        val tag = "registerSend/${randomString(2u)}"

        if (null != this.sendCall) {
            logd(tag, "this.sendCall is already set!!!")
        }
        this.sendCall = sendCall
    }

    private fun main() {
        val tag = "main/${randomString(2u)}"
        val logF = false
        var count: Long = 0

        scope.launch() {
            while (isActive) {
                logd(TAGKClass, tag,"mainLoop: $count", logF).also { count++ }
                inPeersGate.await(heartbeat)
                processInPeers()
                broadcastPeers()
            }
        }
    }

    /**
     * Add first Direct Peer (Group Owner) or Self
     **/
    suspend fun addPeer(k: K?, v: V) {
        val tag = "addPeer/${randomString(2u)}"
        var newKey = false

        logd(tag, "$k -> $v")

        meshMutex.withLock {
            if (null != k) {
                kToVTable[k] = v
                kAgeTable[k] = 0
                newKey = true
            } else {
                kToVTable[k] = v
            }
            kToVTableSnapshot = kToVTable.toMap()
            kAgeTableSnapshot = kAgeTable.toMap()
        }

        if (newKey) {
            dispatchEvent(MeshDispatchEventId.CBMeshNewPeer, k)
        }
    }

    private suspend fun broadcastPeers() {
        val tag = "broadcastPeers/${randomString(2u)}"
        var count = 0

        val uId = uniqueId ?: run {
            logd(
                TAGKClass,
                tag,
                "Local init not ready: uniqueID is NULL"
            )
            return
        }

        val kToVTable = meshMutex.withLock {
            kAgeTable.forEach { (key, age) ->
                if (null != key) {
                    kAgeTable[key] = (age + heartbeat)
                }
            }
            kAgeTableSnapshot = kAgeTable.toMap()
            this.kToVTableSnapshot
        }

        if (kToVTable.isEmpty()) {
            logd(tag, "Local init not ready: kToVTable is empty")
            return
        }

        if (null == kToVTable[null]) {
            dispatchEvent(eventId = MeshDispatchEventId.CBMeshGetGroupOwner)
        }

        kToVTable.forEach { (k, v) ->
            logd(
                tag, "($count): " +
                        (if (uId == k) "Skipping: " else "Sending: ") +
                        Pair(k, kToVTable).toString()
            )
            if (k != uId) {
                sendPeers(v, Pair(uId, kToVTable))
            }
            count++
        }
    }

    private suspend fun processInPeers() {
        val tag = "processInPeers/${randomString(2u)}"
        var count = 0
        var bcastPeersNow = false
        val newPeers = mutableListOf<K>()

        meshMutex.withLock {
            if (kToVTable[uniqueId] == null) {
                logd(
                    TAGKClass,
                    tag,
                    "(0): Local init not ready"
                )
                return
            }

            while (!inPeersQ.isEmpty) {
                val pair = inPeersQ.dequeue()
                val node = pair.first

                logd(
                    TAGKClass,
                    tag,
                    "($count): $pair"
                ).also { count++ }

                if (null != node && uniqueId != node) {
                    val kToVTable = pair.second

                    kToVTable.forEach { kToV ->
                        logd(
                            TAGKClass,
                            tag,
                            "($count): $kToV"
                        ).also { count++ }
                        if (null != kToV.key && uniqueId != kToV.key) {
                            if (null == this.kToVTable[kToV.key]) {
                                newPeers.add(kToV.key!!)
                                bcastPeersNow = true
                            }
                            this.kToVTable[kToV.key] = kToV.value
                            logd(
                                TAGKClass,
                                tag,
                                "($count): Got new peer: $kToV"
                            ).also { count++ }
                        }
                        this.kAgeTable[kToV.key] = 0
                        logd(
                            TAGKClass,
                            tag,
                            "($count): " +
                                    "\n\tkToVTable => ${this.kToVTable} " +
                                    "\n\tkAgeTable =>${this.kAgeTable}"
                        ).also { count++ }
                    }
                }
            }

            this.kToVTableSnapshot = this.kToVTable.toMap()
            this.kAgeTableSnapshot = this.kAgeTable.toMap()
        }

        newPeers.forEach { key ->
            dispatchEvent(MeshDispatchEventId.CBMeshNewPeer, key)
        }

        if (bcastPeersNow) {
            logd(tag, "Got new Peers. Broadcasting peers.")
            inPeersGate.open()
        }
    }

    fun directPeer (node: K): V? = (kToVTableSnapshot[node])

    fun directPeerAge (node: K): Long? = (kAgeTableSnapshot[node])
}
