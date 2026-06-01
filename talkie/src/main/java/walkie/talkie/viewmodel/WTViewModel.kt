package walkie.talkie.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import walkie.chat.ChatGroupId
import walkie.chat.ChatMessage
import walkie.comm.WTCommPeerInfo
import walkie.comm.uid
import walkie.talkie.WTActivity
import walkie.talkie.api.wtchat.ChatGroupIdInt
import walkie.talkie.api.wtchat.ChatGroupType
import walkie.talkie.api.wtchat.DiscussionAbs
import walkie.talkie.api.wtchat.DiscussionMapAbs
import walkie.talkie.api.wtdebug.WTDebugInt
import walkie.talkie.api.wtmisc.WTNavigation
import walkie.talkie.common.WTCommonData
import walkie.talkie.node.NodeId
import walkie.talkie.ui.nav.WTNavGraph
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString

enum class UIMessageAction {Send, Receive}

class WTViewModelFactory(private val wtHub: WTCommonData) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return WTViewModel(wtHub) as T
    }
}

class WTViewModel (val wtHub: WTCommonData): ViewModel(), WTDebugInt {
    companion object {
        const val TAG = "WTViewModel"
    }

    /* Needed by WTMainUI */
    var triggerUIUpdate: Boolean by mutableStateOf(false)
        private set
    private var changed: Boolean = true

    private var switchScreen: Boolean by mutableStateOf(false)
    private var currentScreen: WTNavigation = WTNavigation.WT
    private var nextScreen: WTNavigation = WTNavigation.WT
    lateinit var discussionMap: DiscussionMapAbs
        private set
    var nextDiscussionId: ChatGroupIdInt? = null
    lateinit var navGraph: WTNavGraph

    var textInfoId = ""

    /* Needed by WTChatUi */
    lateinit var chatDiscussion: DiscussionAbs
    var chatDiscussionId: ChatGroupIdInt? = null
    lateinit var wtSystemNode: NodeId

    init {
        logging(true)
        logd(TAG, "init()")
        triggerUIUpdate = true
        changed = true
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
        wtHub.wtVModel = this
        discussionMap = wtHub.wtGlobalDiscussionMap.discussionMap
        wtSystemNode = wtHub.wtSystemNodeId as NodeId
    }
    
    fun changed() {
        val tag = "changed/${randomString(2U)}"
        logd(tag, "changed: $triggerUIUpdate -> ${!triggerUIUpdate} current: ${currentScreen()} switchScreen: $switchScreen")
        changed = true
        triggerUIUpdate = !triggerUIUpdate
    }
    fun processMessage(message: ChatMessage, action: UIMessageAction) {
        if (UIMessageAction.Send == action) {
            runBlocking { wtHub.sendChatMessage(message) }
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
                wtHub.wtCurrentDiscussionId = chatDiscussionId!!
                chatDiscussion = wtHub.wtGlobalDiscussionMap.discussionMap[chatDiscussionId]!!
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
        viewModelScope.cancel()
        if (wtHub.wtVModel === this) {
            wtHub.wtVModel = null
        }
    }

    override fun wtDebug(onOff: Boolean?): Boolean {
        return wtHub.wtDebug(onOff)
    }
}

internal fun WTViewModel.openChatOnClick(
    peer: WTCommPeerInfo
) {
    val tag = "openChatOnClick/${randomString(2U)}"
    val chatGroupId = ChatGroupId(peer.uid(), peer.id, type = ChatGroupType.RemoteChat)

    logd(tag, "Entry: ${currentScreen()} dId: $nextDiscussionId")
    wtHub.wtScope.launch {
        wtHub.wtGlobalDiscussionMap.createDiscussion(
            chatGroupId,
            NodeId.Builder().id(peer.id).unique(peer.unique!!).build()
        )
        nextDiscussionId = chatGroupId
        changeScreen(WTNavigation.RemoteChat)
        switchScreen(WTNavigation.RemoteChat)
    }
    logd(tag, "Exit: ${currentScreen()} dId: $nextDiscussionId")
}

