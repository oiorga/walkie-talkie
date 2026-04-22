package walkie.talkie.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import walkie.util.VMCollection
import kotlinx.coroutines.runBlocking
import walkie.chat.ChatMessage
import walkie.glue.wtchat.ChatGroupIdInt
import walkie.glue.wtchat.DiscussionAbs
import walkie.glue.wtchat.DiscussionMapAbs
import walkie.glue.wtmisc.WTNavigation
import walkie.talkie.common.WTCommonData
import walkie.talkie.node.NodeId
import walkie.talkie.ui.nav.WTNavGraph
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString

enum class UIMessageAction {Send, Receive}

class WTViewModel (
    var coroutineScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    var savedStateHandle: SavedStateHandle? = null
): ViewModel() {
    companion object {
        const val TAG = "WTViewModel"
    }
    private val uiDummyObj: VMCollection = VMCollection()

    private val wtCommonData: WTCommonData = WTCommonData.ONE

    /* Needed by WTMainUI */
    private var _triggerUIUpdate: Boolean by mutableStateOf(false)
    private var _changed: Boolean = true

    private var switchScreen: Boolean by mutableStateOf(false)
    private var currentScreen: WTNavigation = WTNavigation.WT
    private var nextScreen: WTNavigation = WTNavigation.WT
    private lateinit var _discussionMap: DiscussionMapAbs
    var nextDiscussionId: ChatGroupIdInt? = null
    lateinit var navGraph: WTNavGraph

    var textInfoId = ""

    /* Needed by WTChatUi */
    lateinit var chatDiscussion: DiscussionAbs
    var chatDiscussionId: ChatGroupIdInt? = null
    lateinit var wtSystemNode: NodeId

    val discussionMap
        get() = _discussionMap as Map<ChatGroupIdInt, DiscussionAbs>

    init {
        logging(true)
        logd(TAG, "init()")
        _triggerUIUpdate = true
        _changed = true
        currentScreen(WTNavigation.WT)
    }

    fun currentScreen(): WTNavigation {
        return currentScreen
    }

    private fun currentScreen(navDest: WTNavigation) {
        val tag = "currentScreen/${randomString(2U)}"
        logd(tag, "current: $currentScreen new: $navDest switchScreen: $switchScreen")
        this.currentScreen = navDest
    }

    private fun nextScreen(navDest: WTNavigation) {
        val tag = "nextScreen/${randomString(2U)}"
        logd(tag, "current: $currentScreen new: $navDest switchScreen: $switchScreen")
        this.nextScreen = navDest
    }

    fun nextScreen(): WTNavigation {
        return nextScreen
    }

    fun switchScreen(): Boolean {
        return switchScreen
    }

    fun lateInit() {
        wtCommonData.wtVModel = this
        _discussionMap = wtCommonData.wtGlobalDiscussionMap.discussionMap()
        wtSystemNode = wtCommonData.wtSystemNodeId as NodeId
    }

    val triggerUIUpdate: Boolean
        get() = _triggerUIUpdate

    fun changed() {
        val tag = "changed/${randomString(2U)}"
        logd(tag, "changed: $_triggerUIUpdate -> ${!_triggerUIUpdate} current: ${currentScreen()} switchScreen: $switchScreen")
        _changed = true
        _triggerUIUpdate = !_triggerUIUpdate
    }

    fun addUIObj(key: String, value: Any) {
        uiDummyObj.add(key, value)
        savedStateHandle?.set(key, value)
    }

    fun getUIObj(key: String, type: String): Any {
        return (uiDummyObj.get(key, type))
    }

    fun getUIObjFromSavedState(key: String): Any {
        return savedStateHandle?.get(key)!!
    }

    fun processMessage(message: ChatMessage, action: UIMessageAction) {
        if (UIMessageAction.Send == action) {
            runBlocking { wtCommonData.sendChatMessage(message) }
        }
    }

    fun switchScreen(screen: WTNavigation) {
        val tag = "switchScreen/${randomString(2U)}"
        logd(tag, "current: ${currentScreen()} newScreen: $screen switchScreen: $switchScreen")
        if (switchScreen) {
            logd(tag, "current: ${currentScreen()} newScreen: $screen switchScreen: $switchScreen")
            switchScreen = false
            logd(tag, "current: ${currentScreen()} newScreen: $screen switchScreen: $switchScreen")
            navGraph.navigate(screen)
            logd(tag, "current: ${currentScreen()} newScreen: $screen switchScreen: $switchScreen")
        }
        logd(tag, "current: ${currentScreen()} newScreen: $screen switchScreen: $switchScreen")
    }

    fun switchToScreen(screen: WTNavigation? = null) {
        val tag = "switchToScreen/${randomString(2U)}"

        logd(tag, "current: ${currentScreen()} newScreen: $screen switchScreen: $switchScreen")
        switchScreen = false
        logd(tag, "current: ${currentScreen()} newScreen: $screen switchScreen: $switchScreen")
        screen?.let { nxtScreen -> navGraph.navigate(nxtScreen) } ?: navGraph.navigate(nextScreen)
        logd(tag, "current: ${currentScreen()} newScreen: $screen switchScreen: $switchScreen")
    }

    fun changeScreen(screen: WTNavigation) {
        val tag = "changeScreen/${randomString(2U)}"
        var count = 0
        logd(tag, "($count): current: ${currentScreen()} newScreen: $screen switchScreen: $switchScreen").also { count++ }
        nextScreen = screen
        switchScreen = true
        logd(tag, "($count): current: ${currentScreen()} newScreen: $screen switchScreen: $switchScreen").also { count++ }
    }

    fun wtViewModelUpdate(navDest: WTNavigation) {
        val tag = "wtViewModelUpdate/${randomString(2U)}"
        logd(tag, "navDest: ${this.currentScreen} -> $navDest switchScreen: $switchScreen")

        currentScreen(navDest)
        when (navDest) {
            WTNavigation.RemoteChat,
            WTNavigation.RemoteChatTesting,
            WTNavigation.LocalChatTesting,
            WTNavigation.LocalDebugItem,
            WTNavigation.LocalDebugItem -> {
                chatDiscussionId = nextDiscussionId
                wtCommonData.wtCurrentDiscussionId = chatDiscussionId!!
                chatDiscussion = wtCommonData.wtGlobalDiscussionMap.discussionMap()[chatDiscussionId]!!
            }
            WTNavigation.WT -> { }
            WTNavigation.Main -> { }
            WTNavigation.MainDebug -> { }
            WTNavigation.MainPeers -> { }
            WTNavigation.TextInfo -> { }
            WTNavigation.Back,
            WTNavigation.None,
            WTNavigation.Root -> { }
        }
    }

    override fun onCleared() {
        val tag = "onCleared/${randomString(2U)}"
        logd(tag, "$this:$tag onCleared()")
        coroutineScope.cancel()
    }
}
