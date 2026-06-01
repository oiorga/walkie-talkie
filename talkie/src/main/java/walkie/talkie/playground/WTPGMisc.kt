package walkie.talkie.playground

import android.util.Log
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
import walkie.talkie.WTActivity
import walkie.talkie.WalkieTalkie
import walkie.talkie.api.wtchat.ChatGroupType
import walkie.talkie.node.NodeId
import walkie.util.logd
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

fun WalkieTalkie.jobby (scope: CoroutineScope = MainScope(),
                        delay: Long = 1000L,
                        index: Int,
                        sharedInt: IntArray
): Job {
    val tag = "squirrelWheel"
    val job = scope.launch() {
        while (true) {
            sharedInt[index+1]++
            if (0 == index % 2) {
                delay(1L)
                sharedInt[0]++
            } else {
                sharedInt[0]--
                delay(1L)
            }
            delay(Random.nextLong(3 * delay))
            Log.d(tag, "$tag jobby: sharedInt[${index+1}]: ${sharedInt[index+1]} sharedInt[0]: ${sharedInt[0]}")
        }
    }
    return job
}

fun WalkieTalkie.squirrelWheel (scope: CoroutineScope = MainScope(), delay: Long = 1000L) = runBlocking {
    val tag: String = "squirrelWheel"
    val jobList = mutableListOf<Job>()
    val max = 4
    val sharedInt: IntArray = intArrayOf(0, 0, 0, 0, 0)

    for (i in 0 ..<max) {
        jobList.add(
            jobby(
            scope,
            delay,
            i,
            sharedInt
        )
        )
    }
}

fun WalkieTalkie.commSquirrelWheel (scope: CoroutineScope, delay: Long = 1003L, addRandom: Long = 10L): Job {
    val tag: String = "commSquirrelWheel/${randomString(2U)}"

    logd(tag, "Entry")

    return scope.launch {
        logd(tag, "generateRandomMessageJob: 0")
        while (true) {
            if (wtDebug()) {
                wifiLocalRndMsgs()
                wifiDirectIpPeersRndMsgs(scope, delay)
            }

            delay(delay * (1 + Random.nextLong(addRandom)))
        }
    }
}

suspend fun WalkieTalkie.wifiLocalRndMsgs() {
    if (!wtDebug())
        return

    val tag = "wifiLocalRndMsgs/${randomString(2U)}"
    val messagePayload = Random.nextLong().toString()

    logd(tag, messagePayload)

    val message = ChatMessage(
        sender = generateRandomSender(),
        receiver = generateRandomReceiver(receiverType = ChatGroupType.LocalChatTesting),
        groupId = generateRandomGroupId(type = ChatGroupType.LocalChatTesting),
        chatMessageItemList = ChatMessageItemList(
            mutableListOf(
                ChatMessageItem.Builder().value(messagePayload).build()
            )
        )
    )
    wtHub.sendChatMessage(message)
}

suspend fun WalkieTalkie.wifiDirectIpPeersRndMsgs(scope: CoroutineScope, delay: Long) {
    if (!wtDebug())
        return

    val tag = "wifiDirectIpPeersRndMsgs/${randomString(2U)}"
    var delaY = 0
    var i = 0
    val dPeersGroup = ChatGroupId("Direct Peers", type = ChatGroupType.RemoteChatTesting)

    logd(tag, "Entry")

    wtHub.wtComm.directNodesInfo().forEach { peer ->
        val peerGId = ChatGroupId(groupId = peer.uid(), groupName = peer.id, type = ChatGroupType.RemoteChatTesting)
        val peerNodeId = NodeId.Builder().id(peer.id).unique(peer.unique!!).build()
        wtHub.wtGlobalGroupMap.addNode(
            peerGId,
            NodeId.Builder().id(peer.id).unique(peer.unique!!).build()
        )
        wtHub.wtGlobalGroupMap.addNode(
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

                val messagePayload = "${wtHub.wtSystemNodeId.uid()} -> ${peer.uid()}" +
                        " $c$maxK $k " + "[" + randomString(8U) + "]"

                val message = ChatMessage(
                    sender = Sender(wtHub.wtSystemNodeId),
                    receiver = Receiver(peerNodeId, peerGId),
                    groupId = peerGId,
                    chatMessageItemList = ChatMessageItemList(
                        mutableListOf(
                            ChatMessageItem.Builder()
                                .value(messagePayload)
                                .build()
                        )
                    )
                )

                logd(tag, "${message.sender.node.unique} -> ${message.receiver.node.unique}: $messagePayload")
                wtHub.sendChatMessage(message)
            }

            scope.launch {
                delay(delay * Random.nextLong(delaY.toLong()))
                val messagePayload = "${wtHub.wtSystemNodeId.uid()} -> ${peer.uid()} " + "" +
                        "[" + randomString(8U) + "]"
                val message = ChatMessage(
                    sender = Sender(wtHub.wtSystemNodeId),
                    receiver = Receiver(peerNodeId, dPeersGroup),
                    groupId = dPeersGroup,
                    chatMessageItemList = ChatMessageItemList(
                        mutableListOf(
                            ChatMessageItem.Builder()
                                .value(messagePayload)
                                .build()
                        )
                    )
                )

                logd(tag, "${message.sender.node.unique} -> ${message.receiver.node.unique}: $messagePayload")
                wtHub.sendChatMessage(message)
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
