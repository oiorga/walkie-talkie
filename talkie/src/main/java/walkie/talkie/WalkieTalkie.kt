package walkie.talkie

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.CoroutineScope
import walkie.chat.ChatDiscussionMap
import walkie.chat.ChatGroupId
import walkie.chat.ChatGroupList
import walkie.chat.ChatGroupMap
import walkie.comm.WTComm
import walkie.talkie.api.wtchat.ChatGroupType
import walkie.talkie.api.wtdebug.WTDebugInt
import walkie.talkie.common.UpdateUiLiveData
import walkie.talkie.common.WTCommonData
import walkie.talkie.globalmap.DiscussionMap
import walkie.talkie.node.NodeId
import walkie.talkie.playground.CounterLive
import walkie.util.CoroutineRuntime
import walkie.util.Logging
import walkie.util.api.ChannelId
import walkie.util.generateBinaryRec
import walkie.util.generic.genericListOf
import walkie.util.generic.registerAsReceiver
import walkie.util.generic.registerSenders
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString
import kotlin.random.Random

class WalkieTalkie:
    Application(),
    WTDebugInt {
    companion object {
        const val TAG = "WalkieTalkie"
        val TAGKClass = WalkieTalkie::class
    }
    val tag = TAG

    val wtHub: WTCommonData = WTCommonData.ONE

    init {
        Logging.ONE.setGlobal(true)
        logging(true)
        Logging.enable(enclosingClass = LazyListState::class, true)
        Logging.enable(enclosingClass = CoroutineScope::class, true)
    }

    override fun onCreate() {
        super.onCreate()

    }

    override fun onTerminate() {
        super.onTerminate()
    }

    override fun onLowMemory() {
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
    }

    override fun wtDebug(onOff: Boolean?): Boolean {
        return wtHub.wtDebug(onOff)
    }
}

/*
internal fun WalkieTalkie.wtCommonDataInit(stage: Int) : WTCommonData {
    val wtCommonData: WTCommonData = WTCommonData.ONE

    logd(tag,"WTActivity.wtCommDataInit called stage: $stage init")

    for (i in 0..<stage) {
        if (!WTCommonData.initStage[i]) {
            logd(
                tag,
                "WTActivity.wtCommDataInit called stage: $stage init before  stage: $i: ${WTCommonData.initStage[i]}?"
            )
            return wtCommonData
        }
    }

    if (WTCommonData.initStage[stage]) {
        logd(
            tag,
            "WTActivity.wtCommDataInit stage: $stage already initialized?"
        )
        return wtCommonData
    }

    when (stage) {
        0 -> {
            wtDebug(onOff = BuildConfig.DEBUG)
            wtCommonData.wtDeviceName = Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
            wtCommonData.wtSystemNodeId = NodeId(wtCommonData.wtDeviceName, randomString(4U))
            wtCommonData.wtRuntime = CoroutineRuntime.Custom(
                CoroutineRuntime.RunJob.Supervisor,
                CoroutineRuntime.RunDispatcher.Main)

            /* Move from Activity
            customComposablesInit()
            */
        }

        1 -> {
            wifiDInit()
            wtCommonData.counterLive = CounterLive()
            wtCommonData.updateUiLiveData = UpdateUiLiveData()

            wtCommonData.wtComm = WTComm(wtCommonData.wtSystemNodeId)

            wtCommonData.wtGlobalGroupMap = ChatGroupMap()
            wtCommonData.wtGlobalDiscussionMap =
                DiscussionMap(
                    discussionMap = ChatDiscussionMap(),
                    groupMap = wtCommonData.wtGlobalGroupMap,
                    systemNode = wtCommonData.wtSystemNodeId)
            wtCommonData.wtGlobalDiscussionMap.updateUiLiveData =
                wtCommonData.updateUiLiveData

            wtCommonData.registerAsReceiver(
                ChannelId.RCTOCommonData,
                wtCommonData.wtGlobalDiscussionMap,
                wtCommonData.wtComm
            )

            wtCommonData.wtComm.registerSenders(
                ChannelId.RCToComm,
                wtCommonData.wtGlobalDiscussionMap,
                wtCommonData.wtWifiD
            )

            wtCommonData.wtGlobalDiscussionMap.registerSenders(
                channelId = ChannelId.RCCommToChat,
                wtCommonData.wtComm
            )

            wtCommonData.wtWifiD.registerSenders(
                ChannelId.RCToWifi,
                wtCommonData.wtBcastReceiver,
                wtCommonData.wtComm,
                wtCommonData.wtWifiD
            )

            /* Move from Activity
            this.registerSenders(
                channelId = ChannelId.RCToWTActivity,
                wtCommonData.wtWifiD,
                wtCommonData.wtComm.wtPRMComm()
            )
            */
        }

        2 -> {
            /*
            /* WTActivity capable devices/running this app */
            wtCommonData.wtGlobalGroupMap[ChatGroupId(groupId = "WTActivity", groupName = "WTActivity", type = ChatGroupType.WTActivity)] =
                ChatGroupList(ChatGroupId(groupId = "WTActivity", groupName = groupId, type = ChatGroupType.WTActivity), mutableListOf())

            /* Where all the unsolicited messages go to group */
            wtCommonData.wtGlobalGroupMap[ChatGroupId("Bogus", ChatGroupType.Etc)] =
                ChatGroupList(ChatGroupId("Bogus", ChatGroupType.Etc), mutableListOf())
            */

            /* Peers List - testing */
            wtCommonData.wtGlobalGroupMap[ChatGroupId(groupId = "WIFI Direct Info", groupName = "WIFI Direct Info", type = ChatGroupType.LocalDebug)] =
                ChatGroupList(ChatGroupId(groupId = "WIFI Direct Info", groupName = "WIFI Direct Info", ChatGroupType.LocalDebug), mutableListOf())

            /* WIFI Direct Initial Testing - testing */
            wtCommonData.wtGlobalGroupMap[ChatGroupId(groupId = "WIFI Direct All", groupName = "WIFI Direct All", ChatGroupType.LocalDebug)] =
                ChatGroupList(ChatGroupId(groupId = "WIFI Direct All", groupName = "WIFI Direct All", type = ChatGroupType.LocalDebug), mutableListOf())

            wtCommonData.wtGlobalGroupMap[ChatGroupId("Direct Peers", type = ChatGroupType.RemoteChatTesting)] =
                ChatGroupList(ChatGroupId("Direct Peers", type = ChatGroupType.RemoteChatTesting), mutableListOf())

            for (i in 0..17) {
                val groupId =
                    "${Random.nextInt(2)}.${Random.nextInt(2)}.${Random.nextInt(2)}.${
                        Random.nextInt(
                            2
                        )
                    }"
                wtCommonData.wtGlobalGroupMap[ChatGroupId(groupId, type = ChatGroupType.LocalChatTesting)] =
                    ChatGroupList(
                        ChatGroupId(groupId, type = ChatGroupType.LocalChatTesting),
                        mutableListOf()
                    )
                generateBinaryRec(
                    baseList = genericListOf(
                        Random.nextInt(2),
                        Random.nextInt(2),
                        Random.nextInt(2),
                        Random.nextInt(2)
                    ),
                    base = 2,
                    length = 4,
                    count = 7
                ).toList().forEach {
                    wtCommonData.wtGlobalGroupMap[ChatGroupId(groupId = groupId, type = ChatGroupType.LocalChatTesting)]?.add(NodeId(it, ""))
                }
            }

            wtCommonData.wtComm.start()
        }
        else -> {
            logd(
                tag,
                "WTActivity.wtCommDataInit stage: $stage out of range?"
            )
            return wtCommonData
        }
    }

    WTCommonData.initStage[stage] = true
    return wtCommonData
}
*/