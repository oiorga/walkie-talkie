package walkie.util.mesh

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.Serializable
import walkie.glue_inc.CallBackId
import walkie.util.TimedSemaphore
import walkie.util.generic.BlockingQueue
import walkie.util.generic.CallBack
import walkie.util.generic.CallBackInt
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
abstract class Mesh<K , V> (
    private var uniqueId: K,
    private var _heartbeat: Long = 3000L,
    private var _scope: CoroutineScope = MainScope(),
    private val _callBackList: CallBackInt<Any, Any> = CallBack()
) : CallBackInt<Any, Any> by _callBackList
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
    val sendPeersS: ((K, MutableMap<K, V>) -> Unit)? = null
    val receivePeersS: ((K, MutableMap<K, V>) -> Unit)? = null
    private val meshSemaphore: Semaphore = Semaphore(1, 0)
    private var sendCall: (suspend (v: V, info: String) -> Unit)? = null
    private val inPeersQSem: TimedSemaphore = TimedSemaphore(_heartbeat)

    fun heartBeat(value: Long = 0L): Long {
        if (0L != value) _heartbeat = value
        return _heartbeat
    }

    fun scope(scope: CoroutineScope? = null): CoroutineScope {
        if (null != scope) _scope = scope
        return _scope
    }

    suspend fun resetPeersInfo() {
        val tag = "resetPeersInfo/${randomString(2u)}"
        logd(tag, "resetPeersInfo")

        meshSemaphore.acquire()
        kToKTable.clear()
        kToVTable.clear()
        kAgeTable.clear()
        inPeersQ.qClear()
        meshSemaphore.release()
        callBack(CallBackId.CBMeshResetPeers)
    }

    init {
        val tag = "init/${randomString(2u)}"
        logging()
        logd(tag, "Init Entry")

        main()

        logd(tag, "Init Exit")
    }

    /*
    override suspend fun channelOnReceive(channelId: walkie.glue_inc.ChannelIdInt, inputType: walkie.glue_inc.ChannelMessageInt?, input: Any?) {
        val tag = "channelOnReceive/${randomString(2u)}"
        logd(tag, "channelId: $channelId inputType: $inputType")
    }
    */

    abstract fun decodeFromString(input: String): Pair<K?, MutableMap<K?, V>>?

    suspend fun addPeersJson(jsonString: String) {
        val tag = "addPeersJson/${randomString(2u)}"

        logd(tag, "$kToVTable")

        val kToVTable = decodeFromString(jsonString) ?: return

        meshSemaphore.acquire()
        inPeersQ.enqueue(kToVTable)

        val k = kToVTable.first
        val vv = kAgeTable[k] ?: 0
        /* Avoid sending spam useless mesh packets to peers
        if (vv >= 3) {
            inPeersQSem.release()
        }
        */
        kAgeTable[k] = 0
        meshSemaphore.release()
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

        _scope.launch {
            while (true) {
                logd(TAGKClass, tag,"mainLoop: $count", logF).also { count++ }
                inPeersQSem.acquire()
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

        meshSemaphore.acquire()
        if (null != k) {
            kToKTable[k] = k
            kToVTable[k] = v
            kAgeTable[k] = 0L
            callBack(CallBackId.CBMeshNewPeer, k)
        } else {
            kToVTable[k] = v
            kAgeTable[k] = 0L
        }
        meshSemaphore.release()
    }

    fun getPeer(k: K?): V? {
        return kToVTable[k]
    }

    /*
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
                callBack(walkie.glue_inc.CallBackId.CBMeshNewPeer, k)
            } else {
                kToVTable[k] = v
                kAgeTable[k] = 0L
            }
        }
    }
    */

    private suspend fun broadcastPeers() {
        val tag = "broadcastPeers/${randomString(2u)}"
        val logF = false

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

        meshSemaphore.acquire()
        val kToVTable = this.kToVTable.toMutableMap()

        if (null == kToVTable[null]) {
            callBack(CallBackId.CBMeshGetGroupOwner)
            /* return */
        }

        kToVTable.forEach { (k, v) ->
            logd(tag, "($count): " +
                    (if (uId == k) "Skipping: " else "Sending: ") +
                    Pair(k, kToVTable).toString())
            if (k != uId) {
                sendPeers(v, Pair(uId, kToVTable))
                val vv = kAgeTable[k] ?: 0; kAgeTable[k] = vv + 1
            }
            count++
        }
        meshSemaphore.release()
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

        meshSemaphore.acquire()
        while (inPeersQ.isNotEmpty()) {
            val pair = inPeersQ.dequeue() ?: continue
            val node = pair.first

            logd(
                TAGKClass,
                tag,
                "($count): $pair").also { count++ }

            if (null != node && uniqueId != node) {
                val kToVTable = pair.second

                kToVTable.forEach { kToV ->
                    logd(
                        TAGKClass,
                        tag,
                        "($count): $kToV").also { count++ }
                    if (null != kToV.key && uniqueId != kToV.key) {
                        if (null == this.kToVTable[kToV.key]) bcastPeersNow = true
                        this.kToVTable[kToV.key] = kToV.value
                        this.kToKTable[kToV.key] = node
                        logd(
                            TAGKClass,
                            tag,
                            "($count): Got new peer: $kToV").also { count++ }
                        callBack(CallBackId.CBMeshNewPeer, kToV.key!!)
                    }
                    logd(
                        TAGKClass,
                        tag,
                        "($count): ${this.kToVTable} ${this.kToKTable}").also { count++ }
                }
            }
        }
        if (bcastPeersNow){
            logd(tag, "Got new Peer.ers now Broadcasting peers now")
            inPeersQSem.release()
        } 
        meshSemaphore.release()
    }

    fun directUnderlay (node: K): V? {
        return (kToVTable[node])
    }

    /*
    private fun directNode (node: K): K? {
        return kToKTable[node]
    }

    private fun directNodes() : List<K> {
        return kToKTable.keys.filterNotNull().filter { it != uniqueId }.toList()
    }

    private fun allNodes() : List<K> {
        return kToKTable.keys.filterNotNull().toList()
    }
    */
}
