package walkie.talkie.ui.screens

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
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
import walkie.comm.WTCommPeerInfo
import walkie.comm.uid
import walkie.glue.wtchat.ChatGroupType
import walkie.glue.wtmisc.WTNavigation
import walkie.talkie.WalkieTalkie
import walkie.talkie.WalkieTalkie.Companion.TAGKClass
import walkie.talkie.node.NodeId
import walkie.talkie.ui.nav.WTNavNode
import walkie.talkie.ui.util.BottomBarUI
import walkie.talkie.ui.util.LazyScreen
import walkie.talkie.ui.util.ScaffoldScreen
import walkie.talkie.ui.util.TopBarBack
import walkie.talkie.ui.util.TopBarUI
import walkie.talkie.viewmodel.WTViewModel
import walkie.util.logd
import walkie.util.randomString
import kotlin.random.Random

private val WTPeersUITheme: WTUITheme = WTUITheme(
    topTitle = "Nearby Devices",
    bottomTitle = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.WalkieTalkiePeersUI(wtNavNode: WTNavNode? = null) {
    val tag = "WalkieTalkiePeersUI/${randomString(2u)}"
    val mainVModel = this.wtVModel()
    val switchScreen: Boolean by remember { derivedStateOf { mainVModel.switchScreen() } }

    logd(tag, "$tag Entry 0 triggerUpdate: ${mainVModel.triggerUIUpdate} switchScreen: $switchScreen")

    LaunchedEffect (switchScreen) {
        logd(TAGKClass, tag, "$tag LaunchedEffect: ${mainVModel.currentScreen()} nextScreen: ${mainVModel.nextScreen()}")
        Log.d(tag, "$tag LaunchedEffect: ${mainVModel.currentScreen()} nextScreen: ${mainVModel.nextScreen()}")

        /* mainVModel.switchScreen(mainVModel.nextScreen()) */
    }

    mainVModel.wtViewModelUpdate(WTNavigation.MainPeers)

    ScaffoldScreen (
        modifier = Modifier.fillMaxSize(),
        topBar = { TopBarUI(
            navigationIcon = { TopBarBack(modifier = Modifier, mainVModel = mainVModel) },
            wtUITheme = WTPeersUITheme) },
        bottomBar = { BottomBarUI(WTPeersUITheme) },
        contentColor = Color.Transparent
    ) {
        logd(tag, "ScaffoldScreen: ${mainVModel.triggerUIUpdate}")

        PeersMainScreen(
            mainVModel = mainVModel,
            modifier = Modifier
                .fillMaxWidth(1F)
                .background(WTPeersUITheme.bgColor)
                .imePadding(),
            wtUITheme = WTPeersUITheme,
            mainVModel.triggerUIUpdate
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.PeersUiMainEntry(
    modifier: Modifier,
    mainVModel: WTViewModel,
    wtUITheme: WTUITheme = WTPeersUITheme,
    onClick: (() -> Unit)? = null
) {
    val tag = "PeersUiMainEntry/${randomString(2u)}"

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
                /* .clickable { mainVModel.changeScreen(WTNavigation.MainPeers) } */
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
                    text = "Nearby Devices",
                    modifier = modifier
                        .background(
                            wtUITheme.bgChatColor,
                            RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp)
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
internal fun WalkieTalkie.PeersUiEntry(
    modifier: Modifier,
    mainVModel: WTViewModel,
    peer: WTCommPeerInfo,
    wtUITheme: WTUITheme = WTPeersUITheme,
    onClick: (() -> Unit)? = null
) {
    val tag = "PeersUiEntry/${randomString(2u)}"

    logd(TAGKClass, tag, "Entry")

    Box(
        modifier = modifier
            .padding(wtUITheme.lazyListItemPadding)
            .wrapContentSize(align = Alignment.TopStart)
            .background(
                wtUITheme.bgColor,
                RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp)
            )
            .fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = modifier
                .wrapContentSize()
        )
        {
            logd(TAGKClass, tag, "RowScope")
            Text(
                text = peer.id,
                modifier = modifier
                    .clickable { }
                    .background(
                        wtUITheme.bgChatColor,
                        RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp),
                    )
                    .padding(wtUITheme.horizontalTextPadding, wtUITheme.verticalTextPadding)
                    .fillMaxWidth(.5F),
                color = wtUITheme.textColor,
                fontSize = wtUITheme.lazyListItemSize,
                textAlign = TextAlign.Left
            )
            /*
            Text(
                /* text = if (peer.connected) "Connected" else "Disconnected", */
                text = "Connected",
                modifier = modifier.clickable { }
                    .background(
                        wtUITheme.bgChatColor,
                        RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp),                    )
                   .fillMaxWidth(.5F),
                color = wtUITheme.textColor,
                fontSize = wtUITheme.lazyListItemSize,
                textAlign = TextAlign.Left
            )
            */
            val mod = if (null != onClick) modifier.clickable { onClick() } else modifier
            Text(
                text = "Open Chat",
                modifier = mod
                    /* .clickable { openChatOnClick(mainVModel, peer = peer) } */
                    .background(
                        wtUITheme.bgChatColor,
                        RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp),
                    )
                    .padding(wtUITheme.horizontalTextPadding, wtUITheme.verticalTextPadding)
                    /* .padding(wtUITheme.lazyListItemPadding) */
                    .fillMaxWidth(1F),
                color = wtUITheme.textColor,
                fontSize = wtUITheme.lazyListItemSize,
                textAlign = TextAlign.Right
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun WalkieTalkie.openChatOnClickPrep(
    mainVModel: WTViewModel,
    peer: WTCommPeerInfo
) {
    val tag = "openChatOnClick/${randomString(2U)}"
    val chatGroupId = ChatGroupId(peer.uid(), peer.id, type = ChatGroupType.RemoteChat)

    logd(tag, "(0): ${mainVModel.currentScreen()} dId: ${mainVModel.nextDiscussionId}")

    wtCommonData().wtGlobalGroupMap.addNode(
        chatGroupId,
        NodeId.Builder().id(peer.id).unique(peer.unique!!).build()
    )
    wtCommonData().wtGlobalDiscussionMap.createDiscussion(chatGroupId)

    mainVModel.nextDiscussionId = chatGroupId
    mainVModel.changeScreen(WTNavigation.RemoteChat)

    logd(tag, "(1): ${mainVModel.currentScreen()} dId: ${mainVModel.nextDiscussionId}")
}


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun WalkieTalkie.openChatOnClick(
    mainVModel: WTViewModel,
    peer: WTCommPeerInfo
) {
    openChatOnClickPrep(mainVModel, peer)
    mainVModel.switchScreen(WTNavigation.RemoteChat)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.PeersMainScreen(
    mainVModel: WTViewModel,
    modifier: Modifier,
    wtUITheme: WTUITheme = WTPeersUITheme,
    triggerUpdate : Boolean) {
    val navList = mutableListOf<WTNavNode>()
    val searching: WTNavNode = WTNavNode(
        route = WTNavigation.None,
        /* value = null, */
        preview = { mod ->
            Searching(
                modifier = mod!!,
                mainVModel = mainVModel,
                wtUITheme
            )
        })

    run { triggerUpdate }

    val randomTrue = Random.nextBoolean()
    wtComm().directNodesInfo().forEach { peerInfo ->
        val navNode = WTNavNode(
            route = WTNavigation.RemoteChat,
            /* value = peerInfo, */
            onClick = if (randomTrue) { { openChatOnClick(mainVModel, peer = peerInfo) } } else null,
            preview = { mod ->
                PeersUiEntry(
                    modifier = mod!!,
                    mainVModel = mainVModel,
                    peer = peerInfo,
                    wtUITheme = wtUITheme,
                    onClick = if (!randomTrue) { { openChatOnClick(mainVModel, peer = peerInfo) } } else null,
                )
            }
        )
        navList.add(navNode)
    }

    navList.add(searching)

    LazyScreen(
        modifier = modifier,
        lazyList = navList,
        autoScroll = true,
        lazyContent = { mod, item ->
            item.Preview(modifier = mod)
        })
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.Searching(
    modifier: Modifier,
    mainVModel: WTViewModel,
    wtUITheme: WTUITheme = WTPeersUITheme
) {
    val tag = "SearchingPeers/${randomString(2u)}"
    /*
    val tick: MutableState<Int> = remember { mutableIntStateOf(0) }
    val dots: MutableState<String> = remember { mutableStateOf("") }
    val mill = "|/-\\|/-\\"
    val darkShade =  "\u2588"
    val shade = darkShade + darkShade + darkShade + darkShade + darkShade + darkShade + darkShade + darkShade
    val textA: WTLineTextAnimation = remember { WTLineTextAnimation(shade) }
    val textA: WTLineTextAnimation = remember { WTLineTextAnimation("...........") }

    val textA: WTLineTextAnimation = remember { WTLineTextAnimation(mill, outputLength = 1) }
    LaunchedEffect(dots.value) {
        delay(200L)
        dots.value = textA.next()
        if ("-" == dots.value) dots.value = "--"
        logd(TAGKClass, tag, "LaunchedEffect. tick: ${tick.value} dots: [${dots.value}]")
    }
    */

    logd(TAGKClass, tag, "Entry")
    Row(
        modifier = modifier
            .wrapContentSize()
            .background(
                wtUITheme.bgChatColor,
                RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp))
    )
    {
        logd(TAGKClass, tag, "RowScope")
        Box(modifier = modifier
            .background(wtUITheme.bgChatColor)
            .padding(wtUITheme.horizontalTextPadding, wtUITheme.verticalTextPadding)
        ) {
            Text(
                text = "Searching",
                modifier = modifier
                    .clickable { }
                    .background(
                        wtUITheme.bgChatColor,
                        RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp)
                    )
                    /* .padding(wtUITheme.horizontalTextPadding, wtUITheme.verticalTextPadding) */
                    .wrapContentSize(),
                color = wtUITheme.textColor,
                fontSize = wtUITheme.lazyListItemSize,
                textAlign = TextAlign.Left
            )
        }

        Box(modifier = modifier
            .background(wtUITheme.bgChatColor)
            .fillMaxHeight()
            .align(Alignment.CenterVertically)
        ) {
            Searching01(
                modifier = modifier
                    .wrapContentSize()
                    .fillMaxHeight()
                    .background(wtUITheme.bgChatColor)
                    /* .padding(wtUITheme.horizontalTextPadding, wtUITheme.verticalTextPadding) */,
                mainVModel = mainVModel)
        }
        /*
        Text(
            text = dots.value,
            modifier = modifier
                .clickable { }
                .background(
                    wtUITheme.bgChatColor,
                    RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp),
                )
                .fillMaxWidth(1F),
            color = wtUITheme.textColor,
            fontSize = wtUITheme.lazyListItemSize,
            textAlign = TextAlign.Left
        )
        */
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.Searching01(
    modifier: Modifier,
    mainVModel: WTViewModel,
    wtUITheme: WTUITheme = WTPeersUITheme
) {
    Box(modifier = modifier
        .background(wtUITheme.bgChatColor)
    ) {
        LinearProgressIndicator(
            modifier = modifier.fillMaxWidth(),
            color = wtUITheme.textColor,
            trackColor = wtUITheme.bgChatColor,
        )
    }
}