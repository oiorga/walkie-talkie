package walkie.talkie.ui.nav

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import walkie.glue.wtchat.ChatGroupType
import walkie.glue.wtmisc.WTNavigation
import walkie.talkie.WalkieTalkie
import walkie.talkie.ui.nav.WTNavGraph.Companion.TAG
import walkie.talkie.ui.nav.WTNavGraph.Companion.TAGKClass
import walkie.talkie.ui.screens.MenuTextInfo
import walkie.talkie.ui.screens.WTChat
import walkie.talkie.ui.screens.WalkieTalkieDebugUI
import walkie.talkie.ui.screens.WalkieTalkieMainUI
import walkie.talkie.ui.screens.WalkieTalkiePeersUI
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString

data class WTNavNode (
    val route: WTNavigation,
    val value: Any? = null,
    val onClick: (() -> Unit)? = null,
    val changeOnclick: Boolean = false,
    val preview: (@Composable (Modifier?) -> Unit)? = null,
    val navController: NavHostController? = null
) {
    /* private var _prev: WTNavNode? = null */
    private var execOnClick: (() -> Unit)? = null

    companion object {
        const val TAG = "WTNavNode"
        val TAGKClass = WTNavNode::class
    }

    /*
    fun prev(prev: WTNavNode? = null): WTNavNode? {
        if (null != prev) _prev = prev
        return _prev
    }
    */

    init {
        logging(true)

        execOnClick = onClick

        if (changeOnclick) {
            execOnClick = {
                onClick?.let { onclick -> onclick() }
                navController?.navigate(route.name)
            }
        }
    }

    @Composable
    fun Preview(modifier: Modifier = Modifier) {
        val tag = "Preview/${randomString(2U)}"
        val logF = (route == WTNavigation.MainDebug)

        logd(TAGKClass, tag,"$route Calling Preview: execOnClick: ${null != execOnClick} executeOnClick: executeOnClick", logF)

        val mod = if (null != execOnClick) {
            modifier.clickable {
                logd(
                    TAGKClass,
                    tag,
                    "$route execOnClick",
                    logF
                )
                execOnClick?.invoke()
            }
        } else {
            modifier
        }
        preview?.invoke(mod)

        logd (TAGKClass, tag,"$route Exiting Preview: onclick: ${null != execOnClick}", logF)
    }
}

data class WTNavGraph (
    val navController: NavHostController,
    val destination: WTNavigation,
    val modifier: Modifier = Modifier,
    private val _composables: MutableMap<WTNavigation, @Composable () -> Unit> = mutableMapOf(),
) : MutableMap<WTNavigation, @Composable () ->  Unit> by _composables {
    companion object {
        const val TAG = "WTNav"
        val TAGKClass = WTNavGraph::class
    }

    init {
        logging(true)
    }

    fun node(route: WTNavigation,
             exec: @Composable () -> Unit) {
        _composables[route] = exec
    }

    @Composable
    fun Build() {
        val tag = "Build/${randomString(2U)}"
        logd(TAGKClass, tag,  "NavHost Build _composables.forEach Build: ")

        NavHost(
            modifier = Modifier.fillMaxSize(),
            navController = navController,
            startDestination = destination.name
        ) {
            logd(TAGKClass, tag, "NavHost Build _composables Begin NavHost")

            _composables.forEach { (dest, exec) ->
                logd(TAGKClass, tag,  "$dest")
                composable(dest.name) {
                    exec.invoke()
                }
            }
        }
    }

    fun navigate(dest: WTNavigation = destination) {
        when (dest) {
            WTNavigation.Back -> {
                navController.popBackStack()
            }
            else -> {
                navController.navigate(route = dest.name)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.WTUI(route: WTNavigation) {
    when (route) {
        WTNavigation.Root,
        WTNavigation.WT,
        WTNavigation.Main -> { WalkieTalkieMainUI() }
        WTNavigation.RemoteChat,
        WTNavigation.RemoteChatTesting,
        WTNavigation.LocalDebugItem,
        WTNavigation.LocalChatTesting -> { WTChat() }
        WTNavigation.MainDebug -> { WalkieTalkieDebugUI() }
        WTNavigation.MainPeers -> { WalkieTalkiePeersUI() }
        WTNavigation.Back -> { }
        WTNavigation.None -> { }
        WTNavigation.TextInfo -> { MenuTextInfo() }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.WTPreUI(route: WTNavigation) {
    when (route) {
        WTNavigation.Root,
        WTNavigation.WT,
        WTNavigation.Main,
        WTNavigation.RemoteChat,
        WTNavigation.RemoteChatTesting,
        WTNavigation.LocalDebugItem,
        WTNavigation.LocalChatTesting,
        WTNavigation.MainDebug,
        WTNavigation.MainPeers,
        WTNavigation.Back,
        WTNavigation.TextInfo,
        WTNavigation.None -> { logd (TAGKClass, TAG, "$route Default PRE") }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.WTPostUI(route: WTNavigation) {
    when (route) {
        WTNavigation.Root,
        WTNavigation.WT,
        WTNavigation.Main,
        WTNavigation.RemoteChat,
        WTNavigation.RemoteChatTesting,
        WTNavigation.LocalDebugItem,
        WTNavigation.LocalChatTesting,
        WTNavigation.MainDebug,
        WTNavigation.MainPeers,
        WTNavigation.Back,
        WTNavigation.TextInfo,
        WTNavigation.None -> { logd (TAGKClass, TAG, "$route Default POST ") }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.WTNavInit(
    navController: NavHostController = rememberNavController(),
    startDestination: WTNavigation
) {
    wtVModel().navGraph = WTNavGraph(navController, startDestination)
    val wtNavGraph = wtVModel().navGraph

    wtNavGraph.node(WTNavigation.WT) { WTUI(WTNavigation.WT) }
    wtNavGraph.node(WTNavigation.Main) { WTUI(WTNavigation.WT) }
    wtNavGraph.node(WTNavigation.RemoteChat) { WTUI(WTNavigation.RemoteChat) }
    wtNavGraph.node(WTNavigation.RemoteChatTesting) { WTUI(WTNavigation.RemoteChatTesting) }
    wtNavGraph.node(WTNavigation.LocalDebugItem) { WTUI(WTNavigation.LocalDebugItem) }
    wtNavGraph.node(WTNavigation.LocalChatTesting) { WTUI(WTNavigation.LocalChatTesting) }
    wtNavGraph.node(WTNavigation.MainDebug) { WTUI(WTNavigation.MainDebug) }
    wtNavGraph.node(WTNavigation.MainPeers) { WTUI(WTNavigation.MainPeers) }
    wtNavGraph.node(WTNavigation.TextInfo) { WTUI(WTNavigation.TextInfo) }

    wtNavGraph.Build()
}

fun wtChatTypeToNavScreen(chatType: ChatGroupType): WTNavigation {
    val navScreen: WTNavigation = when (chatType) {
        ChatGroupType.RemoteChat -> { WTNavigation.RemoteChat }
        ChatGroupType.RemoteChatTesting -> { WTNavigation.RemoteChatTesting }
        ChatGroupType.LocalDebug -> { WTNavigation.LocalDebugItem }
        ChatGroupType.LocalChatTesting -> { WTNavigation.LocalChatTesting }
        else -> {
            WTNavigation.None
        }
    }
    return navScreen
}

fun wtChatUpdateUI(chatType: ChatGroupType, navScreen: WTNavigation): Boolean {
    val updateUI: Boolean = when(chatType) {
        ChatGroupType.RemoteChat -> {
            navScreen == WTNavigation.Main || navScreen == WTNavigation.RemoteChat
        }
        ChatGroupType.RemoteChatTesting -> {
            navScreen == WTNavigation.MainDebug || navScreen == WTNavigation.RemoteChatTesting || navScreen == WTNavigation.Main
        }
        ChatGroupType.LocalDebug -> {
            navScreen == WTNavigation.MainDebug || navScreen == WTNavigation.LocalDebugItem || navScreen == WTNavigation.Main
        }
        ChatGroupType.LocalChatTesting -> {
            navScreen == WTNavigation.MainDebug || navScreen == WTNavigation.LocalChatTesting || navScreen == WTNavigation.Main
        }
        else -> { false }
    }
    return updateUI
}