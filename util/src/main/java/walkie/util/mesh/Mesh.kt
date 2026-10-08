package walkie.util.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import walkie.util.Gate
import walkie.util.api.DispatchEventId
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

    @Serializable
    private val kToKTable: MutableMap<K?, K> = mutableMapOf<K?, K>()

    private val kToVTable: MutableMap<K?, V> = mutableMapOf<K?, V>()
    private val kAgeTable: MutableMap<K?, Long> = mutableMapOf<K?, Long>()
    private val inPeersQ: BlockingQueue<Pair<K?, MutableMap<K?, V>>> = BlockingQueue<Pair<K?, MutableMap<K?, V>>>(name = "$TAG/inPeersQ", permits = 100)
    private val meshMutex: Mutex = Mutex()
    private var sendCall: (suspend (v: V, info: String) -> Unit)? = null
    private val inPeersGate: Gate = Gate()

    suspend fun resetPeersInfo() {
        val tag = "resetPeersInfo/${randomString(2u)}"
        logd(tag, "resetPeersInfo")

        meshMutex.withLock {
            kToKTable.clear()
            kToVTable.clear()
            kAgeTable.clear()
        }
        dispatchEvent(DispatchEventId.CBMeshResetPeers)
    }

    init {
        val tag = "init/${randomString(2u)}"
        logging()
        logd(tag, "Init Entry")

        main()

        logd(tag, "Init Exit")
    }

    abstract fun decodeFromString(input: String): Pair<K?, MutableMap<K?, V>>?
    suspend fun addPeersJson(jsonString: String) {
        val tag = "addPeersJson/${randomString(2u)}"

        logd(tag, "$kToVTable")

        val kToVTable = decodeFromString(jsonString) ?: return

        meshMutex.withLock {
            inPeersQ.enqueue(kToVTable)
            val k = kToVTable.first
            kAgeTable[k] = 0
        }
    }

    abstract fun encodeToString(kToVTable: Pair<K?, MutableMap<K?, V>>): String

    private suspend fun sendPeers(dest: V, kToVTable: Pair<K?, MutableMap<K?, V>>) {
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

        logd(tag, "$k -> $v")

        meshMutex.withLock {
            if (null != k) {
                kToKTable[k] = k
                kToVTable[k] = v
                kAgeTable[k] = 0L
                dispatchEvent(DispatchEventId.CBMeshNewPeer, k)
            } else {
                kToVTable[k] = v
                kAgeTable[k] = 0L
            }
        }
    }

    /*
    fun getPeer(k: K?): V? {
        return kToVTable[k]
    }

    suspend fun updatePeer(k: K?, v:V) {
        val tag = "updatePeer/${randomString(2u)}"
        val toReplace = kToKTable[k]
        logd(tag, "$k -> $v")

        if (null == toReplace || v != toReplace) {
            addPeer(k, v)
        } else {
            if (null != k) {
                kToKTable[k] = k
                kToVTable[k] = v
                kAgeTable[k] = 0L
                dispatchEvent(walkie.glue_inc.DispatchEventId.CBMeshNewPeer, k)
            } else {
                kToVTable[k] = v
                kAgeTable[k] = 0L
            }
        }
    }
    */

    private suspend fun broadcastPeers() {
        val tag = "broadcastPeers/${randomString(2u)}"

        var count = 0

        val uId = uniqueId ?: run {
            logd(
                TAGKClass,
                tag,
                "Local init not ready: uniqueID is NULL")
            return
        }

        if (kToVTable.isEmpty()) {
            logd(tag, "Local init not ready: kToVTable is empty")
            return
        }

        meshMutex.withLock {
            val kToVTable = this.kToVTable.toMutableMap()

            if (null == kToVTable[null]) {
                dispatchEvent(DispatchEventId.CBMeshGetGroupOwner)
                /* return */
            }

            kToVTable.forEach { (k, v) ->
                logd(
                    tag, "($count): " +
                            (if (uId == k) "Skipping: " else "Sending: ") +
                            Pair(k, kToVTable).toString()
                )
                if (k != uId) {
                    sendPeers(v, Pair(uId, kToVTable))
                    val vv = kAgeTable[k] ?: 0; kAgeTable[k] = vv + 1
                }
                count++
            }
        }
    }

    private suspend fun processInPeers() {
        val tag = "processInPeers/${randomString(2u)}"
        var count = 0
        var bcastPeersNow = false

        if (kToVTable[uniqueId] == null) {
            logd(
                TAGKClass,
                tag,
                "(0): Local init not ready")
            return
        }

        meshMutex.withLock {
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
                            if (null == this.kToVTable[kToV.key]) bcastPeersNow = true
                            this.kToVTable[kToV.key] = kToV.value
                            this.kToKTable[kToV.key] = node
                            logd(
                                TAGKClass,
                                tag,
                                "($count): Got new peer: $kToV"
                            ).also { count++ }
                            dispatchEvent(DispatchEventId.CBMeshNewPeer, kToV.key!!)
                        }
                        logd(
                            TAGKClass,
                            tag,
                            "($count): ${this.kToVTable} ${this.kToKTable}"
                        ).also { count++ }
                    }
                }
            }
            if (bcastPeersNow) {
                logd(tag, "Got new Peer.ers now Broadcasting peers now")
                inPeersGate.open()
            }
        }
    }

    fun directUnderlay (node: K): V? {
        return (kToVTable[node])
    }
}
