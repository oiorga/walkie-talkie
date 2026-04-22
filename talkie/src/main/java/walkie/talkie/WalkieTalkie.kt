package walkie.talkie

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.NEARBY_WIFI_DEVICES
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ACTION_WIFI_P2P_LISTEN_STATE_CHANGED
import android.net.wifi.p2p.WifiP2pManager.ACTION_WIFI_P2P_REQUEST_RESPONSE_CHANGED
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.os.SystemClock.sleep
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import walkie.chat.ChatDiscussion
import walkie.chat.ChatDiscussionMap
import walkie.chat.ChatGroupId
import walkie.chat.ChatGroupList
import walkie.chat.ChatGroupMap
import walkie.chat.ChatMessage
import walkie.chat.ChatMessageItem
import walkie.chat.ChatMessageItemList
import walkie.chat.Receiver
import walkie.chat.Sender
import walkie.comm.WTComm
import walkie.util.generic.ChannelMux
import walkie.util.generic.ChannelMuxInt
import walkie.util.generic.registerAsReceiver
import walkie.util.generic.registerSenders
import walkie.glue.wtchat.ChatGroupType
import walkie.glue.wtdebug.WTDebugInt
import walkie.util.generic.genericListOf
import walkie.glue.wtmisc.InfoMap
import walkie.glue.wtmisc.WTNavigation
import walkie.glue_inc.ChannelId
import walkie.glue_inc.ChannelIdInt
import walkie.glue_inc.ChannelMessageType
import walkie.talkie.common.UpdateUiLiveData
import walkie.talkie.common.WTCommonData
import walkie.talkie.globalmap.DiscussionMap
import walkie.talkie.node.NodeId
import walkie.talkie.playground.CounterLive
import walkie.talkie.playground.commSquirrelWheel
import walkie.talkie.ui.nav.WTNavInit
import walkie.talkie.viewmodel.WTViewModel
import walkie.util.LifeCycleObserver
import walkie.util.Logging
import walkie.util.generateBinaryRec
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString
import walkie.wifidirect.WTWiFiDirect
import walkie.wifidirect.WiFiDirectBroadcastReceiver
import walkie.wifidirect.wtWifiDirectMain
import walkie.wifidirect.wtWifiDirectStop
import kotlin.random.Random
import kotlin.system.exitProcess

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class WalkieTalkie(
    private val _channelMux: ChannelMuxInt<Any, ChannelMessageType> = ChannelMux<Any, ChannelMessageType>()
    ) :
    ComponentActivity(),
    WTDebugInt,
    ChannelMuxInt<Any, ChannelMessageType> by _channelMux
{
    companion object {
        const val TAG = "WalkieTalkie"
        val TAGKClass = WalkieTalkie::class
        const val PERMISSION_REQUEST_CODE = 2
    }

    val intentFilter = IntentFilter().apply {
        addAction(WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        addAction(WIFI_P2P_DISCOVERY_CHANGED_ACTION)
        addAction(ACTION_WIFI_P2P_REQUEST_RESPONSE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            addAction(ACTION_WIFI_P2P_LISTEN_STATE_CHANGED)
        }
    }


    val tag = TAG

    private var wtCommonData: WTCommonData = WTCommonData.ONE
    private val wtVModel by viewModels<WTViewModel>()

    internal fun wtComm() : WTComm {
        return wtCommonData().wtComm
    }

    internal fun wtWifiD() : WTWiFiDirect {
        return wtCommonData().wtWifiD
    }

    internal fun wtVModel() : WTViewModel {
        return wtVModel
    }

    internal fun wtCommonData() : WTCommonData {
        return wtCommonData
    }

    override fun wtDebug (onOff: Boolean?): Boolean {
        return wtCommonData.wtDebug(onOff)
    }

    init {
        Logging.ONE.setGlobal(true)
        logging(true)
        Logging.enable(enclosingClass = LazyListState::class, true)
        Logging.enable(enclosingClass = CoroutineScope::class, true)
    }

    private suspend fun wifiCleanUp() {
        val tag = "WalkieTalkie.Overrides/${randomString(2U)}"
        logd(tag, "wifiCleanUp")
        wtCommonData().wtScope.cancel()
        wtCommonData().wtWifiD.wtWifiDirectStop()
    }

    override fun onStart() {
        val tag = "WalkieTalkie.Overrides/${randomString(2U)}"
        logd(tag, "onStart")
        super.onStart()
    }

    override fun onRestart() {
        val tag = "WalkieTalkie.Overrides/${randomString(2U)}"
        logd(tag, "onRestart")
        super.onRestart()
    }

    override fun onStop() {
        val tag = "WalkieTalkie.Overrides/${randomString(2U)}"
        logd(tag, "onStop")
        super.onStop()

        if (wtDebug()) {
            kill()
        }
    }

    override fun onDestroy() {
        val tag = "WalkieTalkie.Overrides/${randomString(2U)}"
        logd(tag, "onDestroy")

        super.onDestroy()
        if (wtDebug()) {
            kill()
        }
    }

    private fun kill() {
        wtCommonData().wtScope.launch {
            wifiCleanUp()
        }
        sleep(1000L)
        this.finish()
        this.finishAffinity()
        this.finishAndRemoveTask()
        exitProcess(0);
    }

    override fun onResume() {
        val tag = "WalkieTalkie.Overrides/${randomString(2U)}"
        logd(tag, "onResume")

        super.onResume()
        wtCommonData().wtBcastReceiver.also { bcastReceiver ->
            this.registerReceiver(bcastReceiver, intentFilter, RECEIVER_EXPORTED)
        }
    }

    override fun onPause() {
        val tag = "WalkieTalkie.Overrides/${randomString(2U)}"
        logd(tag, "onPause")

        super.onPause()
        wtCommonData().wtBcastReceiver.also { bcastReceiver ->
            this.unregisterReceiver(bcastReceiver)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val tag = "WalkieTalkie.Overrides/${randomString(2U)}"
        logd(tag, "onCreate")

        installSplashScreen()

        var count = 0

        super.onCreate(savedInstanceState)

        /* In order to be able to use Java sockets from this activity */
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().permitAll().build())

        wtCommDataInit(stage = 0)
        wtCommDataInit(stage = 1)

        wtVModel.lateInit()
        wtVModel.coroutineScope = wtCommonData.wtScope
        wtVModel.savedStateHandle = SavedStateHandle()

        wtCommDataInit(stage = 2)

        val uiObserver = Observer<Long> { wtVModel.changed() }
        wtCommonData.updateUiLiveData.counter.observe(this, uiObserver)

        /* Need ComponentActivity, not Activity */
        lifecycle.addObserver(wtCommonData.wtLCObs)

        /* application.registerActivityLifecycleCallbacks(WTLifeCycleLogs); */

        commSquirrelWheel(scope = wtCommonData.wtScope, delay = 6000L, addRandom = 5L)

        enableEdgeToEdge()

        /* In order to have the topBar not to scroll up when textInput. To Revisit */
        /* Redundant with
           <activity android:windowSoftInputMode="adjustResize" </activity>
           in AndroidManifest.xml
        */
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        //WindowCompat.setDecorFitsSystemWindows(window, false)


        logd(tag, "($count) navDest: ${wtVModel.currentScreen()}").also {count++}

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {

                logd(tag, "($count) navDest: ${wtVModel.currentScreen()}").also {count++}
                WTNavInit(
                    navController = rememberNavController(),
                    startDestination = WTNavigation.WT
                )
            }
        }

        wtCommonData().wtWifiD.wtWifiDirectMain(wtCommonData.wtScope, scanInterval = 1000L)
    }

    private val groupIdWDI = "WIFI Direct Info"
    private val info1 = InfoMap(groupIdWDI)
    override suspend fun channelOnReceive(
        channelId: ChannelIdInt,
        inputType: ChannelMessageType?,
        input: Any?
    ) {
        when (channelId) {
            ChannelId.RCToWalkieTalkie -> {
                when (inputType) {
                    ChannelMessageType.RCWifiDebugInfoMessage -> {
                        val groupId = groupIdWDI
                        if (info1["b"] != input as String) {
                            info1["b"] = input
                            wtCommonData().wtGlobalDiscussionMap.replaceDiscussion(
                                ChatGroupId(
                                    groupId = groupId,
                                    type = ChatGroupType.LocalDebug
                                ), ChatDiscussion(ChatGroupId(groupId = groupIdWDI, type = ChatGroupType.LocalDebug))
                            )
                            val message = ChatMessage(
                                sender = Sender(wtCommonData().wtSystemNodeId),
                                receiver = Receiver(
                                    wtCommonData().wtSystemNodeId,
                                    ChatGroupId(groupId = groupId, type = ChatGroupType.LocalDebug)
                                ),
                                groupId = ChatGroupId(groupId = groupId, type = ChatGroupType.LocalDebug),
                                chatMessageItemList = ChatMessageItemList(
                                    mutableListOf(
                                        ChatMessageItem.Builder().value(info1.toString).build()
                                    )
                                )
                            )
                            wtCommonData().sendChatMessage(message)
                        }
                    }

                    ChannelMessageType.RCWTMeshDebugInfoMessage -> {
                        val groupId = groupIdWDI
                        if (info1["a"] != input as String) {
                            info1["a"] = input
                            wtCommonData().wtGlobalDiscussionMap.replaceDiscussion(
                                ChatGroupId(
                                    groupId = groupId,
                                    type = ChatGroupType.LocalDebug
                                ), ChatDiscussion(ChatGroupId(groupId = groupId, type = ChatGroupType.LocalDebug))
                            )
                            val message = ChatMessage(
                                sender = Sender(wtCommonData().wtSystemNodeId),
                                receiver = Receiver(
                                    wtCommonData().wtSystemNodeId,
                                    ChatGroupId(groupId = groupId, type = ChatGroupType.LocalDebug)
                                ),
                                groupId = ChatGroupId(groupId = groupId, type = ChatGroupType.LocalDebug),
                                chatMessageItemList = ChatMessageItemList(
                                    mutableListOf(
                                        ChatMessageItem.Builder().value(info1.toString).build()
                                    )
                                )
                            )
                            wtCommonData().sendChatMessage(message)
                        }
                    }

                    ChannelMessageType.RCWifiRestartChannel -> {
                        logd(tag, "RCWifiRestartChannel")
                        wifiDRestartChannel()
                        wtCommonData().wtComm.wtPRMComm().wtIPComm().stop()
                    }

                    else -> {

                    }
                }
            }

            else -> {

            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun WalkieTalkie.wifiDInit() {
    requestWifiDPermission()

    val manager: WifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    val channel: WifiP2pManager.Channel? =
        manager.initialize(this.applicationContext, mainLooper, null /* channelListener - to pass a channel listener to shadow */)
    channel?.also { chanel ->
        wtCommonData().wtWifiD = WTWiFiDirect(manager, chanel, wtCommonData().wtSystemNodeId )
        wtCommonData().wtBcastReceiver = WiFiDirectBroadcastReceiver()
        this.registerReceiver(wtCommonData().wtBcastReceiver, intentFilter, RECEIVER_EXPORTED)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun WalkieTalkie.wifiDRestartChannel() {
    val tag = "wifiDRestartChannel/${randomString(2u)}"

    logd(tag, "Entry")

    val manager = wtCommonData().wtWifiD.manager

    val channel: WifiP2pManager.Channel? =
        manager.initialize(this.applicationContext, mainLooper, null /* channelListener - to pass a channel listener to shadow */)

    logd(tag, "Requested new channel ${channel.toString()}")
    channel?.also {
        wtCommonData().wtWifiD.channel(it)
    }

    wtCommonData().wtBcastReceiver.also { bcastReceiver ->
        this.unregisterReceiver(bcastReceiver)
        this.registerReceiver(bcastReceiver, intentFilter, RECEIVER_EXPORTED)
    }

    logd(tag, "Restarting peers scanning")
    wtCommonData().wtWifiD.wtWifiDirectMain(wtCommonData().wtScope, scanInterval = 1000L)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun WalkieTalkie.wtDeviceName() : String{
    return Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun WalkieTalkie.requestWifiDPermission() {
    ActivityCompat.requestPermissions(
        this,
        arrayOf(ACCESS_FINE_LOCATION, NEARBY_WIFI_DEVICES, /*ACCESS_BACKGROUND_LOCATION */),
        WalkieTalkie.PERMISSION_REQUEST_CODE
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun WalkieTalkie.requestWifiDPermissionLocation() {
    ActivityCompat.requestPermissions(
        this,
        arrayOf(ACCESS_FINE_LOCATION, NEARBY_WIFI_DEVICES),
        WalkieTalkie.PERMISSION_REQUEST_CODE
    );
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun WalkieTalkie.wtCommDataInit(stage: Int) : WTCommonData {
    val wtCommonData: WTCommonData = WTCommonData.ONE

    logd(tag,"WalkieTalkie.wtCommDataInit called stage: $stage init")

    for (i in 0..<stage) {
        if (!WTCommonData.initStage[i]) {
            logd(
                tag,
                "WalkieTalkie.wtCommDataInit called stage: $stage init before  stage: $i: ${WTCommonData.initStage[i]}?"
            )
            return wtCommonData
        }
    }

    if (WTCommonData.initStage[stage]) {
        logd(
            tag,
            "WalkieTalkie.wtCommDataInit stage: $stage already Inited?"
        )
        return wtCommonData
    }

    when (stage) {
        0 -> {
            wtDebug(onOff = BuildConfig.DEBUG)
            wtCommonData.wtDeviceName = Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
            wtCommonData.wtSystemNodeId = NodeId(wtCommonData.wtDeviceName, randomString(4U))
            wtCommonData.wtLCObs = LifeCycleObserver(this, lifecycle)
            customComposablesInit()
        }

        1 -> {
            wifiDInit()
            wtCommonData.wtScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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

            this.registerSenders(
                channelId = ChannelId.RCToWalkieTalkie,
                wtCommonData.wtWifiD,
                wtCommonData.wtComm.wtPRMComm()
            )
        }

        2 -> {
            /*
            /* WalkieTalkie capable devices/running this app */
            wtCommonData.wtGlobalGroupMap[ChatGroupId(groupId = "WalkieTalkie", groupName = "WalkieTalkie", type = ChatGroupType.WalkieTalkie)] =
                ChatGroupList(ChatGroupId(groupId = "WalkieTalkie", groupName = groupId, type = ChatGroupType.WalkieTalkie), mutableListOf())

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

            wtCommonData().wtComm.start()
        }
        else -> {
            logd(
                tag,
                "WalkieTalkie.wtCommDataInit stage: $stage out of range?"
            )
            return wtCommonData
        }
    }

    WTCommonData.initStage[stage] = true
    return wtCommonData
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun WalkieTalkie.customComposablesInit() {
    wtCommonData().customComposables["About"] = { mod, theme -> WTInfo(mod, theme) }
    wtCommonData().customComposables["Help"] = { mod, theme -> WTHelp(mod, theme) }
}

class S private constructor() {
    private var _s: Int = 0
    val s: Int
        get() = _s

    companion object {
        val INSTANCE: S by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { S() }
    }

    fun inc() {
        _s++
    }

    fun dec() {
        _s--
    }
}
