package walkie.talkie

import android.app.Application
import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
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
import walkie.wifidirect.WTWiFiDirect
import walkie.wifidirect.WiFiDirectBroadcastReceiver
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

        wtHubInit(stage = 0)
        wtHubInit(stage = 1)
        wtHubInit(stage = 2)
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

internal fun WalkieTalkie.wtHubInit(stage: Int) : WTCommonData {
    logd(tag,"WTActivity.wtCommDataInit called stage: $stage init")

    for (i in 0..<stage) {
        if (!wtHub.initStage[i]) {
            logd(
                tag,
                "WTActivity.wtCommDataInit called stage: $stage init before  stage: $i: ${wtHub.initStage[i]}?"
            )
            return wtHub
        }
    }

    if (wtHub.initStage[stage]) {
        logd(
            tag,
            "WTActivity.wtCommDataInit stage: $stage already initialized?"
        )
        return wtHub
    }

    when (stage) {
        0 -> {
            wtDebug(onOff = BuildConfig.DEBUG)
            wtHub.wtDeviceName = Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
            wtHub.wtSystemNodeId = NodeId(wtHub.wtDeviceName, randomString(4U))
            wtHub.wtRuntimee = CoroutineRuntime.Custom(
                CoroutineRuntime.RunJob.Supervisor,
                CoroutineRuntime.RunDispatcher.Main)
            wtHub.wtScope = wtHub.wtRuntimee.scope
        }

        1 -> {
            wifiDInit()
            wtHub.counterLive = CounterLive()
            wtHub.updateUiLiveData = UpdateUiLiveData()

            wtHub.wtComm = WTComm(wtHub.wtSystemNodeId, wtHub.wtScope)

            wtHub.wtGlobalGroupMap = ChatGroupMap()
            wtHub.wtGlobalDiscussionMap =
                DiscussionMap(
                    discussionMap = ChatDiscussionMap(),
                    groupMap = wtHub.wtGlobalGroupMap,
                    systemNode = wtHub.wtSystemNodeId)
            wtHub.wtGlobalDiscussionMap.updateUiLiveData =
                wtHub.updateUiLiveData

            wtHub.registerAsReceiver(
                ChannelId.RCTOCommonData,
                wtHub.wtGlobalDiscussionMap,
                wtHub.wtComm
            )

            wtHub.wtComm.registerSenders(
                ChannelId.RCToComm,
                wtHub.wtGlobalDiscussionMap,
                wtHub.wtWifiD
            )

            wtHub.wtGlobalDiscussionMap.registerSenders(
                channelId = ChannelId.RCCommToChat,
                wtHub.wtComm
            )

            wtHub.wtWifiD.registerSenders(
                ChannelId.RCToWifi,
                wtHub.wtBcastReceiver,
                wtHub.wtComm,
                wtHub.wtWifiD
            )

            /* Move from Activity
            this.registerSenders(
                channelId = ChannelId.RCToWTActivity,
                wtHub.wtWifiD,
                wtHub.wtComm.wtPRMComm()
            )
            */
        }

        2 -> {
            /*
            /* WTActivity capable devices/running this app */
            wtHub.wtGlobalGroupMap[ChatGroupId(groupId = "WTActivity", groupName = "WTActivity", type = ChatGroupType.WTActivity)] =
                ChatGroupList(ChatGroupId(groupId = "WTActivity", groupName = groupId, type = ChatGroupType.WTActivity), mutableListOf())

            /* Where all the unsolicited messages go to group */
            wtHub.wtGlobalGroupMap[ChatGroupId("Bogus", ChatGroupType.Etc)] =
                ChatGroupList(ChatGroupId("Bogus", ChatGroupType.Etc), mutableListOf())
            */

            /* Peers List - testing */
            wtHub.wtGlobalGroupMap[ChatGroupId(groupId = "WIFI Direct Info", groupName = "WIFI Direct Info", type = ChatGroupType.LocalDebug)] =
                ChatGroupList(ChatGroupId(groupId = "WIFI Direct Info", groupName = "WIFI Direct Info", ChatGroupType.LocalDebug), mutableListOf())

            /* WIFI Direct Initial Testing - testing */
            wtHub.wtGlobalGroupMap[ChatGroupId(groupId = "WIFI Direct All", groupName = "WIFI Direct All", ChatGroupType.LocalDebug)] =
                ChatGroupList(ChatGroupId(groupId = "WIFI Direct All", groupName = "WIFI Direct All", type = ChatGroupType.LocalDebug), mutableListOf())

            wtHub.wtGlobalGroupMap[ChatGroupId("Direct Peers", type = ChatGroupType.RemoteChatTesting)] =
                ChatGroupList(ChatGroupId("Direct Peers", type = ChatGroupType.RemoteChatTesting), mutableListOf())

            for (i in 0..17) {
                val groupId =
                    "${Random.nextInt(2)}.${Random.nextInt(2)}.${Random.nextInt(2)}.${
                        Random.nextInt(
                            2
                        )
                    }"
                wtHub.wtGlobalGroupMap[ChatGroupId(groupId, type = ChatGroupType.LocalChatTesting)] =
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
                    wtHub.wtGlobalGroupMap[ChatGroupId(groupId = groupId, type = ChatGroupType.LocalChatTesting)]?.add(NodeId(it, ""))
                }
            }

            /* To do it properly

            wtHub.wtComm.start() */
        }
        else -> {
            logd(
                tag,
                "WTActivity.wtCommDataInit stage: $stage out of range?"
            )
            return wtHub
        }
    }

    wtHub.initStage[stage] = true
    return wtHub
}

internal fun WalkieTalkie.wifiDInit() {
    val manager: WifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    wtHub.wtWifiD = WTWiFiDirect(manager, wtHub.wtSystemNodeId, wtHub.wtScope)
    wtHub.wtBcastReceiver = WiFiDirectBroadcastReceiver()
}

