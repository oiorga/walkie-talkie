package walkie.talkie

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.NEARBY_WIFI_DEVICES
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import walkie.talkie.api.wtdebug.WTDebugInt
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
import walkie.talkie.api.wtchat.ChatGroupType
import walkie.talkie.api.wtmisc.InfoMap
import walkie.talkie.api.wtmisc.WTNavigation
import walkie.util.generic.ChannelMux
import walkie.util.generic.ChannelMuxInt
import walkie.util.generic.registerAsReceiver
import walkie.util.generic.registerSenders
import walkie.util.generic.genericListOf
import walkie.talkie.common.UpdateUiLiveData
import walkie.talkie.common.WTCommonData
import walkie.talkie.globalmap.DiscussionMap
import walkie.talkie.node.NodeId
import walkie.talkie.playground.CounterLive
import walkie.talkie.playground.commSquirrelWheel
import walkie.talkie.ui.nav.WTNavInit
import walkie.talkie.viewmodel.WTViewModel
import walkie.talkie.viewmodel.WTViewModelFactory
import walkie.util.CoroutineRuntime
import walkie.util.LifeCycleObserver
import walkie.util.Logging
import walkie.util.api.ChannelId
import walkie.util.api.ChannelIdInt
import walkie.util.api.ChannelMessageType
import walkie.util.api.RemoteCallId
import walkie.util.api.RemoteCallMuxInt
import walkie.util.generateBinaryRec
import walkie.util.generic.RemoteCallMux
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString
import walkie.wifidirect.WTWiFiDirect
import walkie.wifidirect.WiFiDirectBroadcastReceiver
import walkie.wifidirect.wtWifiDirectMain
import walkie.wifidirect.wtWifiDirectStop
import kotlin.getValue
import kotlin.random.Random
import kotlin.system.exitProcess

class WTActivity(
    private val _channelMux: ChannelMuxInt<Any, ChannelMessageType> = ChannelMux<Any, ChannelMessageType>(),
    private val _remoteCallMux: RemoteCallMuxInt = RemoteCallMux()
    ) :
    ComponentActivity(),
    WTDebugInt,
    ChannelMuxInt<Any, ChannelMessageType> by _channelMux,
    RemoteCallMuxInt by _remoteCallMux {

        companion object {
        const val TAG = "WTActivity"
        val TAGKClass = WTActivity::class
        const val PERMISSION_REQUEST_CODE = 2
    }

    val intentFilter = IntentFilter().apply {
        addAction(WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        addAction(WIFI_P2P_DISCOVERY_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addAction(ACTION_WIFI_P2P_REQUEST_RESPONSE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            addAction(ACTION_WIFI_P2P_LISTEN_STATE_CHANGED)
        }
    }

    val tag = TAG

    private val walkieTalkie by lazy {
        application as WalkieTalkie
    }

    val wtHub by lazy {
        walkieTalkie.wtHub
    }

    val wtVModel by viewModels<WTViewModel> {
        WTViewModelFactory(wtHub)
    }

    val wtComm: WTComm
        get() = wtHub.wtComm

    override fun wtDebug (onOff: Boolean?): Boolean {
        return wtHub.wtDebug(onOff)
    }

    init {
        /*
        Logging.ONE.setGlobal(true)
        logging(true)
        Logging.enable(enclosingClass = LazyListState::class, true)
        Logging.enable(enclosingClass = CoroutineScope::class, true)
        */
    }

    private suspend fun wifiCleanUp() {
        val tag = "wifiCleanUp/${randomString(2U)}"
        logd(tag, "wifiCleanUp")
        wtHub.wtWifiD.wtWifiDirectStop()
    }

    override fun onStart() {
        val tag = "wifiCleanUp/${randomString(2U)}"
        logd(tag, "onStart")
        super.onStart()
    }

    override fun onRestart() {
        val tag = "wifiCleanUp/${randomString(2U)}"
        logd(tag, "onRestart")
        super.onRestart()
    }

    override fun onStop() {
        val tag = "wifiCleanUp/${randomString(2U)}"
        logd(tag, "onStop")
        super.onStop()

        if (wtDebug()) {
            kill()
        }
    }

    override fun onDestroy() {
        val tag = "wifiCleanUp/${randomString(2U)}"
        logd(tag, "onDestroy")

        super.onDestroy()
        if (wtDebug()) {
            kill()
        }
    }

    private fun kill() {
        lifecycleScope.launch() {
            withContext(NonCancellable) {
                wifiCleanUp()
                wtHub.wtRuntime.cancel()
                finishAffinity()
                finishAndRemoveTask()
                exitProcess(0)
            }
        }
    }

    override fun onResume() {
        val tag = "wifiCleanUp/${randomString(2U)}"
        logd(tag, "onResume")

        super.onResume()
        wtHub.wtBcastReceiver.also { bcastReceiver ->
            ContextCompat.registerReceiver(this, bcastReceiver, intentFilter, ContextCompat.RECEIVER_EXPORTED)
        }
    }

    override fun onPause() {
        val tag = "wifiCleanUp/${randomString(2U)}"
        logd(tag, "onPause")

        super.onPause()
        wtHub.wtBcastReceiver.also { bcastReceiver ->
            this.unregisterReceiver(bcastReceiver)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val tag = "wifiCleanUp/${randomString(2U)}"
        logd(tag, "onCreate")

        installSplashScreen()

        var count = 0

        super.onCreate(savedInstanceState)

        /* In order to be able to use Java sockets from this activity */
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().permitAll().build())

        wtHubInit(stage = 0)
        wtHubInit(stage = 1)

        wtVModel.lateInit()

        wtHubInit(stage = 2)

        val uiObserver = Observer<Long> { wtVModel.changed() }
        wtHub.updateUiLiveData.counter.observe(this, uiObserver)

        /* Do I need this? */
        /* lifecycle.addObserver(wtHub.wtLCObs) */
        /* application.registerActivityLifecycleCallbacks(WTLifeCycleLogs); */

        commSquirrelWheel(scope = wtHub.wtRuntime.scope(), delay = 6000L, addRandom = 5L)

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

        wtHub.wtWifiD.wtWifiDirectMain(scanInterval = 1000L)
    }

    private val groupIdWDI = "WIFI Direct Info"
    private val info1 = InfoMap(groupIdWDI)
    override suspend fun channelOnReceive(
        channelId: ChannelIdInt,
        inputType: ChannelMessageType?,
        input: Any?
    ) {
        when (channelId) {
            ChannelId.RCToWTActivity -> {
                when (inputType) {
                    ChannelMessageType.RCWifiDebugInfoMessage -> {
                        val groupId = groupIdWDI
                        if (info1["b"] != input as String) {
                            info1["b"] = input
                            wtHub.wtGlobalDiscussionMap.replaceDiscussion(
                                ChatGroupId(
                                    groupId = groupId,
                                    type = ChatGroupType.LocalDebug
                                ), ChatDiscussion(ChatGroupId(groupId = groupIdWDI, type = ChatGroupType.LocalDebug))
                            )
                            val message = ChatMessage(
                                sender = Sender(wtHub.wtSystemNodeId),
                                receiver = Receiver(
                                    wtHub.wtSystemNodeId,
                                    ChatGroupId(groupId = groupId, type = ChatGroupType.LocalDebug)
                                ),
                                groupId = ChatGroupId(groupId = groupId, type = ChatGroupType.LocalDebug),
                                chatMessageItemList = ChatMessageItemList(
                                    mutableListOf(
                                        ChatMessageItem.Builder().value(info1.toString).build()
                                    )
                                )
                            )
                            wtHub.sendChatMessage(message)
                        }
                    }

                    ChannelMessageType.RCWTMeshDebugInfoMessage -> {
                        val groupId = groupIdWDI
                        if (info1["a"] != input as String) {
                            info1["a"] = input
                            wtHub.wtGlobalDiscussionMap.replaceDiscussion(
                                ChatGroupId(
                                    groupId = groupId,
                                    type = ChatGroupType.LocalDebug
                                ), ChatDiscussion(ChatGroupId(groupId = groupId, type = ChatGroupType.LocalDebug))
                            )
                            val message = ChatMessage(
                                sender = Sender(wtHub.wtSystemNodeId),
                                receiver = Receiver(
                                    wtHub.wtSystemNodeId,
                                    ChatGroupId(groupId = groupId, type = ChatGroupType.LocalDebug)
                                ),
                                groupId = ChatGroupId(groupId = groupId, type = ChatGroupType.LocalDebug),
                                chatMessageItemList = ChatMessageItemList(
                                    mutableListOf(
                                        ChatMessageItem.Builder().value(info1.toString).build()
                                    )
                                )
                            )
                            wtHub.sendChatMessage(message)
                        }
                    }

                    ChannelMessageType.RCWifiRestartChannel -> {
                        logd(tag, "RCWifiRestartChannel")
                        wifiDRestartChannel()
                        wtHub.wtComm.wtPRMComm().wtIPComm().stop()
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

internal fun WTActivity.wifiDInit() {
    requestWifiDPermission()

    val manager: WifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    val channel: WifiP2pManager.Channel? =
        manager.initialize(this.applicationContext, mainLooper, null /* channelListener - to pass a channel listener to shadow */)
    channel?.also { chanel ->
        wtHub.wtWifiD = WTWiFiDirect(manager)
        wtHub.wtWifiD.channel(channel)
        wtHub.wtWifiD.nodeId(wtHub.wtSystemNodeId)
        wtHub.wtWifiD.runtime(wtHub.wtRuntime)

        wtHub.wtBcastReceiver = WiFiDirectBroadcastReceiver()
        ContextCompat.registerReceiver(this, wtHub.wtBcastReceiver, intentFilter, ContextCompat.RECEIVER_EXPORTED)
        registerRemoteCall(RemoteCallId.RCCheckWifiDPermission) { _ -> hasWifiDPermission() }
        wtHub.wtWifiD.registerRemoteCallTo(RemoteCallId.RCCheckWifiDPermission, this)
        registerRemoteCall(RemoteCallId.RCRequestWifiDPermission) { _ -> requestWifiDPermission() }
        wtHub.wtWifiD.registerRemoteCallTo(RemoteCallId.RCRequestWifiDPermission, this)
    }
}

internal fun WTActivity.wifiDRestartChannel() {
    val tag = "wifiDRestartChannel/${randomString(2u)}"

    logd(tag, "Entry")

    val manager = wtHub.wtWifiD.manager

    val channel: WifiP2pManager.Channel? =
        manager.initialize(this.applicationContext, mainLooper, null /* channelListener - to pass a channel listener to shadow */)

    logd(tag, "Requested new channel ${channel.toString()}")
    channel?.also {
        wtHub.wtWifiD.channel(it)
    }

    wtHub.wtBcastReceiver.also { bcastReceiver ->
        this.unregisterReceiver(bcastReceiver)
        ContextCompat.registerReceiver(this, wtHub.wtBcastReceiver, intentFilter, ContextCompat.RECEIVER_EXPORTED)

    }

    logd(tag, "Restarting peers scanning")
    wtHub.wtWifiD.wtWifiDirectMain(scanInterval = 1000L)
}

internal fun WTActivity.wtDeviceName() : String{
    return Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
}

internal fun WTActivity.requestWifiDPermission() {
    logd(tag, "WTActivity.requestWifiDPermission")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(ACCESS_FINE_LOCATION, NEARBY_WIFI_DEVICES /* ACCESS_BACKGROUND_LOCATION */),
            WTActivity.PERMISSION_REQUEST_CODE
        )
    } else {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION),
            WTActivity.PERMISSION_REQUEST_CODE
        )
    }
}

internal fun WTActivity.hasWifiDPermission(): Boolean {
    val ret = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        (ActivityCompat.checkSelfPermission(
            this,
            ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
            this,
            NEARBY_WIFI_DEVICES
        ) == PackageManager.PERMISSION_GRANTED
                )
    } else {
        (ActivityCompat.checkSelfPermission(
            this,
            ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
            this,
            ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
                )    }

    logd(tag, "WTActivity.wifiDPermission: $ret")
    return ret
}

/*
internal fun WTActivity.requestWifiDPermissionLocation() {
    ActivityCompat.requestPermissions(
        this,
        arrayOf(ACCESS_FINE_LOCATION, NEARBY_WIFI_DEVICES),
        WTActivity.PERMISSION_REQUEST_CODE
    );
}
*/

internal fun WTActivity.wtHubInit(stage: Int) : WTCommonData {
    val wtHub: WTCommonData = WTCommonData.ONE

    logd(tag,"WTActivity.wtCommDataInit called stage: $stage init")

    for (i in 0..<stage) {
        if (!WTCommonData.initStage[i]) {
            logd(
                tag,
                "WTActivity.wtCommDataInit called stage: $stage init before  stage: $i: ${WTCommonData.initStage[i]}?"
            )
            return wtHub
        }
    }

    if (WTCommonData.initStage[stage]) {
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
            wtHub.wtRuntime = CoroutineRuntime.Custom(
                CoroutineRuntime.RunJob.Supervisor,
                CoroutineRuntime.RunDispatcher.Main)

            /* Do I need this? */
            /* wtHub.wtLCObs = LifeCycleObserver(this, lifecycle) */
            customComposablesInit()
        }

        1 -> {
            wifiDInit()
            wtHub.counterLive = CounterLive()
            wtHub.updateUiLiveData = UpdateUiLiveData()

            wtHub.wtComm = WTComm(wtHub.wtSystemNodeId)

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

            this.registerSenders(
                channelId = ChannelId.RCToWTActivity,
                wtHub.wtWifiD,
                wtHub.wtComm.wtPRMComm()
            )
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

            wtHub.wtComm.start()
        }
        else -> {
            logd(
                tag,
                "WTActivity.wtCommDataInit stage: $stage out of range?"
            )
            return wtHub
        }
    }

    WTCommonData.initStage[stage] = true
    return wtHub
}

fun WTActivity.customComposablesInit() {
    wtHub.customComposables["About"] = { mod, theme -> WTInfo(mod, theme) }
    wtHub.customComposables["Help"] = { mod, theme -> WTHelp(mod, theme) }
}
