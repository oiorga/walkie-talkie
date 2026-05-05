package  walkie.glue.wtmisc

import kotlinx.serialization.Serializable
import walkie.util.generic.GenericMapAbs

/*
@Serializable
object WTNavRoot
*/

@Serializable
sealed class WTNavigation(open val route: Node) {
    @Serializable
    enum class Node {
        None,
        WT,
        MAIN,
        /*
        Chat,
        */
        RemoteChat,
        RemoteChatTesting,
        LocalChatTesting,
        LocalDebugItem,
        MainDebug,
        Peers,
        Back,
        Root,
        TextInfo,
        Up
    }
    @Serializable
    data object None : WTNavigation(Node.None)
    @Serializable
    data object WT : WTNavigation(Node.WT)
    @Serializable
    data object Main : WTNavigation(Node.MAIN)
    /*
    @Serializable
    data object Chat : WTNavigation(Node.Chat)
    */
    @Serializable
    data object RemoteChatTesting : WTNavigation(Node.RemoteChatTesting)
    @Serializable
    data object LocalChatTesting : WTNavigation(Node.LocalChatTesting)
    @Serializable
    data object RemoteChat : WTNavigation(Node.RemoteChat)
    @Serializable
    data object MainDebug : WTNavigation(Node.MainDebug)
    @Serializable
    data object LocalDebugItem : WTNavigation(Node.LocalDebugItem)
    @Serializable
    data object Back : WTNavigation(Node.Back)
    @Serializable
    data object MainPeers : WTNavigation(Node.Peers)
    @Serializable
    data object Root : WTNavigation(Node.Root)
    @Serializable
    data object TextInfo : WTNavigation(Node.TextInfo)
    val name: String
        get() = this.route.name
}

data class InfoMap(val id: String) : GenericMapAbs<String, String>() {
    val toString: String
        get() {
            var str = ""

            this.keys.sorted().forEach {
                str += this[it] + "\n"
            }
            return str
        }
}
