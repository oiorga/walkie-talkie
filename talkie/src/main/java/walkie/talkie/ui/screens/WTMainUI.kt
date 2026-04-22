package walkie.talkie.ui.screens

import android.icu.text.DateFormat.getDateTimeInstance
import android.icu.text.DateFormat.getTimeInstance
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import walkie.chat.ChatGroupId
import walkie.glue.wtchat.ChatGroupIdInt
import walkie.glue.wtchat.ChatGroupType
import walkie.glue.wtmisc.WTNavigation
import walkie.talkie.WalkieTalkie
import walkie.talkie.WalkieTalkie.Companion.TAGKClass
import walkie.talkie.ui.nav.WTNavNode
import walkie.talkie.ui.nav.wtChatTypeToNavScreen
import walkie.talkie.ui.util.BottomBarUI
import walkie.talkie.ui.util.Compose
import walkie.talkie.ui.util.LazyScreen
import walkie.talkie.ui.util.ScaffoldScreen
import walkie.talkie.ui.util.TopBarUI
import walkie.talkie.viewmodel.WTViewModel
import walkie.util.logd
import walkie.util.randomString

private val WTMainUITheme: WTUITheme = WTUITheme(
    topTitle = "Walky Talky",
    bottomTitle = ""
)

val WalkieTalkie.WTMenuUITheme: WTUITheme
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    get() =
        WTUITheme(
            topTitle = this.wtVModel().textInfoId,
            bottomTitle = ""
        )

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.WalkieTalkieMainUI(wtNavNode: WTNavNode? = null) {
    val tag = "WalkieTalkieMain"
    val mainVModel = this.wtVModel()
    val switchScreen: Boolean by remember { derivedStateOf { mainVModel.switchScreen() } }

    logd(tag,"$tag: ${mainVModel.currentScreen()} nextScreen: ${mainVModel.nextScreen()} switchScreen: $switchScreen")

    mainVModel.wtViewModelUpdate(WTNavigation.Main)

    LaunchedEffect (switchScreen) {
        logd(TAGKClass, tag, "$tag LaunchedEffect: currentScreen: ${mainVModel.currentScreen()} nextScreen: ${mainVModel.nextScreen()}")
        /* mainVModel.switchScreen(mainVModel.nextScreen()) */
    }

    logd(tag, "$tag navDest: ${mainVModel.currentScreen()} nextScreen: ${mainVModel.nextScreen()}")
    ScaffoldScreen (
        modifier = Modifier
            .fillMaxSize(1F)
            .background(Color.Transparent)
        ,
        topBar = {
            TopBarUI(
                wtUITheme = WTMainUITheme,
                actions = { MainScreenTopBarActionMenu(modifier = Modifier, mainVModel = mainVModel) }
            )
                 },
        bottomBar = { BottomBarUI(WTMainUITheme) },
        containerColor = WTMainUITheme.bgColor
    ) {
        Compose(mainVModel.triggerUIUpdate) {
            MainScreen(
                modifier = Modifier
                    .fillMaxWidth(1F)
                    .background(WTMainUITheme.bgColor),
                mainVModel = mainVModel,
                /* triggerUpdate = mainVModel.triggerUIUpdate */
            )
        }
    }
}

internal fun WalkieTalkie.onDiscussionItemClick(
    mainVModel: WTViewModel,
    discussionItemId: ChatGroupIdInt
) {
    val tag = "WalkieTalkieMainUI"
    logd(tag, "$tag/onDiscussionItemClick(0): ${mainVModel.currentScreen()} dId: ${mainVModel.nextDiscussionId}")
    mainVModel.nextDiscussionId = discussionItemId
    mainVModel.changeScreen(wtChatTypeToNavScreen(discussionItemId.type))
    logd(tag, "$tag/onDiscussionItemClick(1): ${mainVModel.currentScreen()} dId: ${mainVModel.nextDiscussionId}")
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.DiscussionItemEntry(
    modifier: Modifier,
    mainVModel: WTViewModel,
    wtUITheme: WTUITheme = WTUITheme(),
    discussionItemId: ChatGroupIdInt? = null,
    onClick: (() -> Unit)? = null
) {
    val tag = "DiscussionItemEntry/${randomString(2U)}"
    val discussionMap = mainVModel.discussionMap
    val color =
        if (null == discussionMap[discussionItemId]?.read) wtUITheme.textColor else if (true == discussionMap[discussionItemId]?.read) wtUITheme.lazyListItemColorSeen else wtUITheme.lazyListItemColorNew
    val bgColor =
        if (null == discussionMap[discussionItemId]?.read) wtUITheme.bgColor else if (true == discussionMap[discussionItemId]?.read) wtUITheme.lazyListItemBgColorSeen else wtUITheme.lazyListItemBgColorNew

    logd(
        TAGKClass, tag,
        "\nEntry ${discussionItemId?.groupId} ${discussionMap[discussionItemId]?.read} " +
                "\ncolor: $color bgColor: $bgColor"
    )

    logd(
        TAGKClass,
        tag,
        "ColumnScope ${discussionItemId?.groupId} ${discussionMap[discussionItemId]?.read}"
    )
    val mod = if (null != onClick) modifier.clickable { onClick() } else modifier
    Box(
        modifier = mod
            /* .clickable { onDiscussionItemClick(mainVModel, discussionItemId!!) } */
            .padding(wtUITheme.lazyListItemPadding)
            /* .align(Alignment.Start) */
            .wrapContentSize(align = Alignment.TopStart)
            .background(
                bgColor,
                RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp)
            )
            .fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        logd(
            TAGKClass,
            tag,
            "BoxScope ${discussionItemId?.groupId} ${discussionMap[discussionItemId]?.read}"
        )
        val gId = if (wtDebug()) discussionItemId?.groupId else (discussionItemId?.groupName)!!
        if (null != gId) {
            val timeStamp = discussionMap[discussionItemId]?.timeStampLastUpdated
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                logd(
                    TAGKClass,
                    tag,
                    "RowScope ${discussionItemId?.groupId} ${discussionMap[discussionItemId]?.read} ${gId}"
                )
                Text(
                    text = gId,
                    modifier = modifier
                        .fillMaxHeight()
                        .padding(wtUITheme.horizontalTextPadding, wtUITheme.verticalTextPadding)
                        .align(Alignment.CenterVertically)
                        .background(
                            bgColor,
                            RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp),
                        ),
                    color = color,
                    fontSize = wtUITheme.lazyListItemSize,
                    textAlign = TextAlign.Left
                )
                logd(
                    TAGKClass,
                    tag,
                    "RowScope ${discussionItemId?.groupId} ${discussionMap[discussionItemId]?.read} ${
                        /* SimpleDateFormat("MM/dd HH:mm").format(timeStamp) */
                        getDateTimeInstance().format(timeStamp)
                    }"
                )
                Text(
                    /* text = SimpleDateFormat("MM/dd HH:mm").format(timeStamp) */
                    text = getDateTimeInstance().format(timeStamp),
                    modifier = modifier
                        .background(
                            bgColor,
                            RoundedCornerShape(1.dp, 1.dp, 1.dp, 1.dp),
                        )
                        .fillMaxWidth(1F)
                        .padding()
                        .align(Alignment.Bottom),
                    color = color,
                    fontSize = wtUITheme.dateFontSize,
                    textAlign = TextAlign.Right
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.MainScreen(
    modifier: Modifier,
    mainVModel: WTViewModel,
    /* triggerUpdate: Any? = null */
) {
    val tag = "WalkieTalkieMain.Screen"

    /* run { triggerUpdate } */

    val navList: MutableList<WTNavNode> = mainScreenList(mainVModel)

    LazyScreen(
        modifier = modifier,
        lazyList = navList.reversed(),
        autoScroll = true,
        lazyContent = { mod, item ->
            item.Preview(modifier = mod)
        })
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun WalkieTalkie.mainScreenList(
    mainVModel: WTViewModel
) : MutableList<WTNavNode> {
    val tag = "mainScreenList/${randomString(2U)}"
    val screenList = mutableListOf<WTNavNode>()
    var addDebug = false

    logd(tag, "Entry")
    mainVModel.discussionMap.keys.forEach { gId ->
        if (gId.type == ChatGroupType.RemoteChat) {
            val navNode = WTNavNode(
                route = WTNavigation.RemoteChat,
                /* value = gId, */
                onClick = { onDiscussionItemClick(mainVModel, gId as ChatGroupId); mainVModel.switchScreen(mainVModel.nextScreen()) },
                preview = { mod ->
                    DiscussionItemEntry(
                        mod!!,
                        mainVModel,
                        discussionItemId = gId as ChatGroupId,
                        /* onClick = { onDiscussionItemClick(mainVModel, gId as ChatGroupId); mainVModel.switchScreen(mainVModel.nextScreen()) } */
                        )
                },
                /* navController = mainVModel.navGraph.navController */
            )
            screenList.add(navNode)
        }
        if (gId.type == ChatGroupType.LocalDebug ||
            gId.type == ChatGroupType.LocalChatTesting ||
            gId.type == ChatGroupType.RemoteChatTesting) {
            addDebug = true
        }
    }

    screenList.add(
        WTNavNode(
            route = WTNavigation.MainPeers,
            /* value = null, */
            onClick = { mainVModel.switchToScreen(WTNavigation.MainPeers) },
            preview = { mod ->
                PeersUiMainEntry(modifier = mod!!, mainVModel = mainVModel,
                    /* onClick = { mainVModel.changeScreen(WTNavigation.MainPeers) } */
                )
            },
            /* navController = mainVModel.navGraph.navController */
        )
    )

    if (addDebug && wtDebug()) screenList.add(
        WTNavNode(
            route = WTNavigation.MainDebug,
            /* value = null, */
            onClick = { mainVModel.switchToScreen(WTNavigation.MainDebug) },
            preview = { mod ->
                DebugUiMainEntry(modifier = mod!!, mainVModel = mainVModel,
                    /* onClick = { mainVModel.changeScreen(WTNavigation.MainDebug) } */
                    )
            },
            /* navController = mainVModel.navGraph.navController */
        )
    )

    return screenList
}
