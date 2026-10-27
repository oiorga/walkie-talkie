package walkie.util.mesh

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import walkie.util.Gate
import walkie.util.api.BlockingQueueResult
import walkie.util.api.MeshDispatchEventId
import walkie.util.api.MeshKey
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

    private val kAgeTable: MutableMap<MeshKey<K>, Long> = mutableMapOf()
    @Volatile
    private var kAgeTableSnapshot: Map<MeshKey<K>, Long> = emptyMap()

    private val kToVTable: MutableMap<MeshKey<K>, V> = mutableMapOf()
    @Volatile
    private var kToVTableSnapshot: Map<MeshKey<K>, V> = emptyMap<MeshKey<K>, V>()

    private val inPeersQ: BlockingQueue<Pair<MeshKey<K>, MutableMap<MeshKey<K>, V>>> = BlockingQueue<Pair<MeshKey<K>, MutableMap<MeshKey<K>, V>>>(name = "$TAG/inPeersQ", capacity = 100, scope)
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

    abstract fun decodeFromString(input: String): Pair<MeshKey<K>, MutableMap<MeshKey<K>, V>>?
    fun addPeersJson(jsonString: String) {
        val tag = "addPeersJson/${randomString(2u)}"

        val kToVTable = decodeFromString(jsonString) ?: return

        inPeersQ.enqueueRetryBestEffort(kToVTable)
    }

    abstract fun encodeToString(kToVTable: Pair<MeshKey<K>, Map<MeshKey<K>, V>>): String

    private suspend fun sendPeers(dest: V, kToVTable: Pair<MeshKey<K>, Map<MeshKey<K>, V>>) {
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
            var timeStamp = SystemClock.elapsedRealtime()
            while (isActive) {
                logd(TAGKClass, tag,"mainLoop: $count", logF).also { count++ }
                inPeersGate.await(heartbeat)
                processInPeers()
                timeStamp = updatePeersAge(timeStamp)
                broadcastPeers()
            }
        }
    }

    /**
     * Add first Direct Peer (Group Owner) or Self
     **/
    suspend fun addPeer(k: MeshKey<K>, v: V) {
        val tag = "addPeer/${randomString(2u)}"

        logd(tag, "$k -> $v")

        meshMutex.withLock {
            kToVTable[k] = v
            kAgeTable[k] = 0
            kToVTableSnapshot = kToVTable.toMap()
            kAgeTableSnapshot = kAgeTable.toMap()
        }

        if (k is MeshKey.Key) {
            dispatchEvent(MeshDispatchEventId.CBMeshNewPeer, k.id)
        }
    }

    private suspend fun updatePeersAge(timeStamp: Long = 0): Long {
        val tag = "updatePeersAge/${randomString(2u)}"
        val currentTimeStamp = SystemClock.elapsedRealtime()

        val delta = if (0L == timeStamp) {
            return currentTimeStamp
        } else currentTimeStamp - timeStamp

        meshMutex.withLock {
            kAgeTable.forEach { (key, age) ->
                kAgeTable[key] = age + delta
            }
            kAgeTableSnapshot = kAgeTable.toMap()
        }

        return currentTimeStamp
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
            this.kToVTableSnapshot
        }

        if (kToVTable.isEmpty()) {
            logd(tag, "Local init not ready: kToVTable is empty")
            return
        }

        if (null == kToVTable[MeshKey.Root]) {
            dispatchEvent(eventId = MeshDispatchEventId.CBMeshGetGroupOwner)
        }

        kToVTable.forEach { (k, v) ->
            logd(
                tag, "($count): " +
                        (if (uId == k) "Skipping: " else "Sending: ") +
                        Pair(k, kToVTable).toString()
            )
            if (k != MeshKey.Key(uId)) {
                sendPeers(v, Pair(MeshKey.Key(uId), kToVTable))
            }
            count++
        }
    }

    private suspend fun processInPeers() {
        val tag = "processInPeers/${randomString(2u)}"
        var count = 0
        var bcastPeersNow = false
        val newPeers = mutableListOf<MeshKey<K>>()

        meshMutex.withLock {
            if (kToVTable[MeshKey.Key(uniqueId)] == null) {
                logd(
                    TAGKClass,
                    tag,
                    "(0): Local init not ready"
                )
                return
            }

            while (true) {
                val pair = when (val res = inPeersQ.tryDequeue()) {
                    is BlockingQueueResult.Success -> {
                        res.value
                    }
                    else -> {
                        logd(
                            TAGKClass,
                            tag,
                            "($count): inPeersQ.dequeue failed"
                        ).also { count++ }
                        break
                    }
                }

                val node = pair.first

                logd(
                    TAGKClass,
                    tag,
                    "($count): $pair"
                ).also { count++ }

                if (MeshKey.Key(uniqueId) != node) {
                    val kToVTable = pair.second

                    kToVTable.forEach { kToV ->
                        logd(
                            TAGKClass,
                            tag,
                            "($count): $kToV"
                        ).also { count++ }
                        if (MeshKey.Key(uniqueId) != kToV.key) {
                            if (null == this.kToVTable[kToV.key]) {
                                newPeers.add(kToV.key)
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
            if (bcastPeersNow)
                this.kAgeTableSnapshot = this.kAgeTable.toMap()
        }

        newPeers.forEach { key ->
            if (key is MeshKey.Key)
                dispatchEvent(MeshDispatchEventId.CBMeshNewPeer, key.id)
        }

        if (bcastPeersNow) {
            logd(tag, "Got new Peers. Broadcasting peers.")
            inPeersGate.open()
        }
    }

    fun directPeer (node: MeshKey<K>): V? = (kToVTableSnapshot[node])

    fun directPeerAgeMills (node: MeshKey<K>): Long? = (kAgeTableSnapshot[node])

    fun directPeerAgeSeconds (node: MeshKey<K>): Long? = (kAgeTableSnapshot[node]?.div(1000L))
}
