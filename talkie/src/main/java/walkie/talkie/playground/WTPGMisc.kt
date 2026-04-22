package walkie.talkie.playground

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import walkie.chat.ChatGroupId
import walkie.chat.ChatMessage
import walkie.chat.ChatMessageItem
import walkie.chat.ChatMessageItemList
import walkie.chat.Receiver
import walkie.chat.Sender
import walkie.comm.uid
import walkie.glue.wtchat.ChatGroupType
import walkie.talkie.WalkieTalkie
import walkie.talkie.node.NodeId
import walkie.util.randomString
import java.util.Timer
import java.util.TimerTask
import kotlin.random.Random

class WalkiePlayGroundTimer(private val delay: Long = 1000L) {
    private var counter: Counter = Counter()
    private val timer: Timer = Timer()

    init {
        timer.schedule(object : TimerTask() {
            override fun run() {
                tick()
            }
        }, 0L, this.delay)
    }

    private fun tick() {
        counter.inc()
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun WalkieTalkie.jobby (scope: CoroutineScope = MainScope(),
                        counter: CounterLive = wtCommonData().counterLive,
                        delay: Long = 1000L,
                        index: Int,
                        sharedInt: IntArray
): Job {
    val tag = "squirrelWheel"
    val job = scope.launch() {
        while (true) {
            val c = counter.counter
            counter.inc()
            sharedInt[index+1]++
            if (0 == index % 2) {
                delay(1L)
                sharedInt[0]++
            } else {
                sharedInt[0]--
                delay(1L)
            }
            delay(Random.nextLong(3 * delay))
            Log.d(tag, "$tag jobby: sharedInt[${index+1}]: ${sharedInt[index+1]} sharedInt[0]: ${sharedInt[0]} ${counter.counter.value}")
        }
    }
    return job
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun WalkieTalkie.squirrelWheel (scope: CoroutineScope = MainScope(), counter: CounterLive = wtCommonData().counterLive, delay: Long = 1000L) = runBlocking {
    val tag: String = "squirrelWheel"
    val jobList = mutableListOf<Job>()
    val max = 4
    val sharedInt: IntArray = intArrayOf(0, 0, 0, 0, 0)

    for (i in 0 ..<max) {
        jobList.add(
            jobby(
            scope,
            counter,
            delay,
            i,
            sharedInt
        )
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun WalkieTalkie.commSquirrelWheel (scope: CoroutineScope = MainScope(), delay: Long = 1003L, addRandom: Long = 10L) = runBlocking {
    val tag: String = "commSquirrelWheel"
    var delaY = 0

    Log.d(tag, "commSquirrelWheel: Entry")

    val generateRandomMessageJob = scope.launch {
        Log.d(tag, "commSquirrelWheel: generateRandomMessageJob: 0")
        delay(delay * Random.nextLong(addRandom))
        while (true) {
            if (wtDebug()) {
                wifiLocalRndMsgs(scope, delay)
                wifiDirectIpPeersRndMsgs(scope, delay)
            }

            delay(Random.nextLong(1 + delay * addRandom))
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun WalkieTalkie.wifiLocalRndMsgs(scope: CoroutineScope, delay: Long) {
    val message = ChatMessage(
        sender = generateRandomSender(),
        receiver = generateRandomReceiver(receiverType = ChatGroupType.LocalChatTesting),
        groupId = generateRandomGroupId(type = ChatGroupType.LocalChatTesting),
        chatMessageItemList = ChatMessageItemList(
            mutableListOf(
                ChatMessageItem.Builder().value(Random.nextLong().toString()).build()
            )
        )
    )
    wtCommonData().sendChatMessage(message)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
suspend fun WalkieTalkie.wifiDirectIpPeersRndMsgs(scope: CoroutineScope, delay: Long) {
    delay(0L)
    var delaY = 0
    var i = 0
    val dPeersGroup = ChatGroupId("Direct Peers", type = ChatGroupType.RemoteChatTesting)

    wtComm().directNodesInfo().forEach { peer ->
        val peerGId = ChatGroupId(groupId = peer.uid(), groupName = peer.id, type = ChatGroupType.RemoteChatTesting)
        val peerNodeId = NodeId.Builder().id(peer.id).unique(peer.unique!!).build()
        wtCommonData().wtGlobalGroupMap.addNode(
            peerGId,
            NodeId.Builder().id(peer.id).unique(peer.unique!!).build()
        )
        wtCommonData().wtGlobalGroupMap.addNode(
            dPeersGroup,
            peerNodeId
        )
        i++
        val c = randomString(2U)
        val maxK = 10.toLong()
        for (k in 0..maxK) {
            delaY = 1 + (delaY + 1) % 7
            delay(10L)
            scope.launch {
                delay(delay * Random.nextLong((k + 1) * delaY))
                val message = ChatMessage(
                    sender = Sender(wtCommonData().wtSystemNodeId),
                    receiver = Receiver(peerNodeId, peerGId),
                    groupId = peerGId,
                    chatMessageItemList = ChatMessageItemList(
                        mutableListOf(
                            ChatMessageItem.Builder()
                                .value(
                                    "${wtCommonData().wtSystemNodeId.uid()} -> ${peer.uid()}" +
                                            " $c$maxK $k " + "[" + randomString(8U) + "]"
                                )
                                .build()
                        )
                    )
                )
                wtCommonData().sendChatMessage(message)
            }

            scope.launch {
                delay(delay * Random.nextLong(delaY.toLong()))
                val message = ChatMessage(
                    sender = Sender(wtCommonData().wtSystemNodeId),
                    receiver = Receiver(peerNodeId, dPeersGroup),
                    groupId = dPeersGroup,
                    chatMessageItemList = ChatMessageItemList(
                        mutableListOf(
                            ChatMessageItem.Builder()
                                .value(
                                    "${wtCommonData().wtSystemNodeId.uid()} -> ${peer.uid()} " + "" +
                                            "[" + randomString(8U) + "]")
                                .build()
                        )
                    )
                )
                wtCommonData().sendChatMessage(message)
            }
        }
    }
}

fun generateRandomSender() : Sender {
    return Sender(NodeId("${Random.nextLong(2L)}.${Random.nextLong(2L)}.${Random.nextLong(2L)}.${Random.nextLong(1L)}", "TESTing"))
}

fun generateRandomReceiver(receiverType: ChatGroupType = ChatGroupType.Etc) : Receiver {
    val rnd = "${Random.nextLong(2L)}.${Random.nextLong(2L)}.${Random.nextLong(2L)}.${Random.nextLong(2L)}"
    val node = NodeId(rnd, "TESTing")
    return Receiver(
        node = node,
        ChatGroupId(node.uid(), type = ChatGroupType.LocalChatTesting)
    )
}

fun generateRandomGroupId(type: ChatGroupType = ChatGroupType.Etc) : ChatGroupId {
    return ChatGroupId(
        groupId = "${Random.nextLong(2L)}.${Random.nextLong(2L)}.${Random.nextLong(2L)}.${Random.nextLong(2L)}",
        type = type
    )
}

data class CounterLive(
    private val _counter: MutableLiveData<Long> = MutableLiveData<Long>()
) {
    init {
        _counter.value = 0
    }

    val counter: LiveData<Long>
        get() = _counter

    fun inc() {
        _counter.value = _counter.value?.plus(1)
    }
}

data class Counter(private var counter: Long = 0L) {
    fun inc(): Long {
        counter += 1
        return counter
    }

    fun set (input: Long) {
        counter = input
    }

    fun get(): Long {
        return counter
    }
}
