package walkie.talkie.ui.screens

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import walkie.glue.wtchat.ChatGroupType
import walkie.glue.wtmisc.WTNavigation
import walkie.talkie.WalkieTalkie
import walkie.talkie.WalkieTalkie.Companion.TAGKClass
import walkie.talkie.ui.nav.WTNavNode
import walkie.talkie.ui.util.BottomBarUI
import walkie.talkie.ui.util.LazyScreen
import walkie.talkie.ui.util.ScaffoldScreen
import walkie.talkie.ui.util.TopBarBack
import walkie.talkie.ui.util.TopBarUI
import walkie.talkie.viewmodel.WTViewModel
import walkie.talkie.ui.nav.wtChatTypeToNavScreen
import walkie.util.logd
import walkie.util.randomString
import kotlin.random.Random

private val WTDebugUITheme: WTUITheme = WTUITheme(
    topTitle = "Walkie Talkie Debug",
    bottomTitle = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.WalkieTalkieDebugUI(wtNavNode: WTNavNode? = null) {
    val tag = "WalkieTalkieDebugUI"
    val mainVModel = this.wtVModel()
    val switchScreen: Boolean by remember { derivedStateOf { mainVModel.switchScreen() } }

    logd(tag, "$tag Entry 0 triggerUpdate: ${mainVModel.triggerUIUpdate} switchScreen: $switchScreen")

    LaunchedEffect (switchScreen) {
        logd(TAGKClass, tag, "$tag LaunchedEffect: ${mainVModel.currentScreen()} nextScreen: ${mainVModel.nextScreen()}")
        Log.d(tag, "$tag LaunchedEffect: ${mainVModel.currentScreen()} nextScreen: ${mainVModel.nextScreen()}")

        /* mainVModel.switchScreen(mainVModel.nextScreen()) */
    }

    mainVModel.wtViewModelUpdate(WTNavigation.MainDebug)

    ScaffoldScreen(
        modifier = Modifier
            .fillMaxSize(),
        topBar = { TopBarUI(
            navigationIcon = { TopBarBack(modifier = Modifier, mainVModel = mainVModel) },
            wtUITheme = WTDebugUITheme) },
        bottomBar = { BottomBarUI(WTDebugUITheme) },
        contentColor = WTDebugUITheme.bgColor
    ) {
        logd(tag, "ScaffoldScreen: ${mainVModel.triggerUIUpdate}")

        DebugMainScreen(
            mainVModel = mainVModel,
            modifier = Modifier
                .fillMaxWidth(1F)
                .background(Color.Transparent),
            mainVModel.triggerUIUpdate
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.DebugUiMainEntry(
    modifier: Modifier,
    mainVModel: WTViewModel,
    wtUITheme: WTUITheme = WTUITheme(),
    onClick: (() -> Unit)? = null
) {
    val tag = "DebugUiMainEntry"

    logd(TAGKClass, tag, "Entry")

    Column(
        modifier = modifier
            .wrapContentSize()
    )
    {
        logd(TAGKClass, tag, "ColumnScope")
        val mod = if (null != onClick) modifier.clickable { onClick() } else modifier
        Box(
            modifier = mod
                .padding(wtUITheme.lazyListItemPadding)
                .align(Alignment.Start)
                .wrapContentSize(align = Alignment.TopStart)
                .background(
                    wtUITheme.bgChatColor,
                    RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp)
                )
                .fillMaxWidth()
        )
        {
            Row() {
                Text(
                    text = "Debug Menu",
                    modifier = modifier
                        .background(
                            wtUITheme.bgChatColor,
                            RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp),
                        )
                        .padding(wtUITheme.horizontalTextPadding, wtUITheme.verticalTextPadding)
                        .fillMaxWidth(.4F) ,
                    color = wtUITheme.textColor,
                    fontSize = wtUITheme.lazyListItemSize,
                    textAlign = TextAlign.Left
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.DebugMainScreen(
    mainVModel: WTViewModel,
    modifier: Modifier,
    triggerUpdate : Boolean) {
    val tag = "DebugMainScreen/${randomString(2U)}"

    logd(TAGKClass, tag, "Entry")

    if (Random.nextBoolean()) {
        val navList = mutableListOf<WTNavNode>()
        mainVModel.discussionMap.keys.forEach { chatGroupId ->
            if (chatGroupId.type == ChatGroupType.LocalDebug ||
                chatGroupId.type == ChatGroupType.LocalChatTesting ||
                chatGroupId.type == ChatGroupType.RemoteChatTesting
            ) {
                logd(TAGKClass, tag, "Creating navNode: chatGroupId: ${chatGroupId.groupId.toString()}")
                val randomTrue = Random.nextBoolean()
                val navNode = WTNavNode(
                    route = wtChatTypeToNavScreen(chatGroupId.type),
                    /* value = chatGroupId, */
                    onClick = if (randomTrue) { {
                            logd(TAGKClass, tag, "onClick: chatGroupId: ${chatGroupId.groupId.toString()}")
                            onDiscussionItemClick(mainVModel, chatGroupId as ChatGroupId)
                            mainVModel.switchToScreen(mainVModel.nextScreen())
                        } } else null
                    ,
                    preview = { mod ->
                        DiscussionItemEntry(
                            mod!!,
                            mainVModel,
                            discussionItemId = chatGroupId,
                            onClick = if (!randomTrue) { {
                                    onDiscussionItemClick(mainVModel, chatGroupId)
                                    mainVModel.switchToScreen(mainVModel.nextScreen())
                            } } else null
                        )
                    },
                    /* navController = mainVModel.navGraph.navController */
                )
                navList.add(navNode)
            }
        }

        LazyScreen(
            modifier = modifier,
            lazyList = navList,
            autoScroll = true,
            lazyContent = { mod, item ->
                item.Preview(modifier = mod)
            })
    } else {
        LazyScreen(
            modifier,
            mainVModel.discussionMap.filter {
                it.key.type == ChatGroupType.LocalDebug ||
                it.key.type == ChatGroupType.LocalChatTesting ||
                it.key.type == ChatGroupType.RemoteChatTesting
            }.keys.toList(),
            autoScroll = true,
            lazyContent = { mod, chatGroupId ->
                DiscussionItemEntry(
                    modifier = mod,
                    mainVModel = mainVModel,
                    discussionItemId = chatGroupId,
                    onClick = {
                        onDiscussionItemClick(mainVModel, chatGroupId)
                        mainVModel.switchToScreen(mainVModel.nextScreen())
                    }
                )
            }
        )
    }
    logd(TAGKClass, tag, "Exit")
}
