package walkie.talkie.ui.util

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import walkie.glue.wtchat.ChatMessageAbs
import walkie.glue.wtchat.ChatMessageItemInt
import walkie.glue.wtmisc.WTNavigation
import walkie.talkie.WalkieTalkie
import walkie.talkie.ui.screens.WTUITheme
import walkie.talkie.ui.theme.UiTheme
import walkie.talkie.viewmodel.WTViewModel
import walkie.util.logd
import walkie.util.randomString

@Composable
fun ChatDivider(modifier: Modifier) {
    Box {
        HorizontalDivider(
            modifier = modifier
                .wrapContentSize()
                .background(Color.Transparent),
            thickness = 1.dp,
            color = Color.Transparent
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun WalkieTalkie.ComposableChatItem(
    chatMessage: ChatMessageAbs,
    modifier: Modifier,
    wtUITheme: WTUITheme,
    contentAlign: Alignment,
    textAlign: TextAlign) {
    val tag = "ComposableChatItem/${randomString(2U)}"
    val horizontalAlignment = if (contentAlign == Alignment.TopEnd)  Alignment.End else Alignment.Start

    logd(tag, "Entry")

    Column(
        modifier = modifier
            .wrapContentSize()
            .fillMaxWidth()
            .background(Color.Transparent)
    ) {
        Box(
            modifier = modifier
                .sizeIn(4.dp, 4.dp)
                .wrapContentSize(align = contentAlign)
                .background(Color.Transparent)
                .align(horizontalAlignment)
        )
        {
            Text(
                text = if (wtDebug() || chatMessage.groupId.debug) "[${chatMessage.sender.node.uid()} -> ${chatMessage.groupId.groupId}]" else chatMessage.sender.node.id(),
                modifier = modifier
                    .background(
                        wtUITheme.bgChatColor,
                        RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp)
                    ),
                textAlign = textAlign,
                fontSize = wtUITheme.chatFontSize,
                color = wtUITheme.chatColor
            )
        }

        Box(
            modifier = modifier
                .wrapContentSize(align = contentAlign)
                .background(Color.Transparent)
                .align(horizontalAlignment)
        )
        {
            Column (
                modifier = modifier.align(contentAlign),
                horizontalAlignment = horizontalAlignment
            )
            {
                chatMessage.forEach { chatItem ->
                    ComposableChatItem(modifier, chatItem, debug = wtDebug() || chatMessage.groupId.debug)
                }
            }
        }
    }

    WalkieTalkie.logd(tag, "Exit")
}

@Composable
fun WalkieTalkie.ComposableChatItem(modifier: Modifier, item: ChatMessageItemInt, debug: Boolean) {
    when (item.type) {
        "String" -> ComposableChatItemString(
            modifier = modifier,
            item = if (debug) "[${item.timeStampCreation} -> ${item.timeStampReceived}]\n ${item.valueString.toString()}" else item.valueString.toString())
        else -> {
            ComposableChatItemString(modifier,"KBOOM!!!")
        }
    }
}

@Composable
fun ComposableChatItemString(
    modifier: Modifier,
    item: String,
    wtUITheme: WTUITheme = WTUITheme()) {
    Text(
        text = item,
        modifier = modifier
            .background(
                wtUITheme.bgChatColor,
                RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp)
            )
            .padding(wtUITheme.horizontalTextPadding, wtUITheme.verticalTextPadding)
        ,
        fontSize = wtUITheme.chatFontSize,
        color = wtUITheme.chatColor
    )
}

@Composable
fun <T : Any> LazyScreen(
    modifier: Modifier,
    lazyList: List<T>,
    autoScroll: Boolean = true,
    lazyContent: @Composable() ((Modifier, T) -> Unit)? = null,
) {
    val tag = "LazyScreen"
    val tagKClass = LazyListState::class
    val listState = rememberLazyListState()
    val reachedBottom: Boolean by remember { derivedStateOf { listState.reachedBottom(1) } }

    LaunchedEffect(reachedBottom && autoScroll) {
        Log.d(
            tag,
            "$tag: Scroll down 0: reachedBottom: $reachedBottom")
        if (!reachedBottom) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount)
        Log.d(
            tag,
            "$tag: Scroll down 1: reachedBottom: $reachedBottom")
    }

    Log.d(tag, "Calling ConstraintLayout:" +
            "\n\t\t\t\tlist: $lazyList")

    ConstraintLayout(modifier = modifier
        .fillMaxSize()
        .background(Color.Transparent)
    ) {
        Box(modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(1F)
            /* .padding() */
            .wrapContentSize(align = Alignment.TopStart)
            .background(Color.Transparent)
            .alpha(1F)
        ) {
            LazyColumn(
                modifier = modifier
                    .fillMaxHeight(),
                state = listState,
            ) {
                items(lazyList) { item ->
                    lazyContent?.invoke(
                        Modifier.fillMaxHeight(),
                        item
                    )
                }
            }
        }
    }
}

/**
 * From https://medium.com/@giorgos.patronas1/endless-scrolling-in-android-with-jetpack-compose-af1f55a03d1a - saved a few hours of my life
 *
 */
internal fun LazyListState.reachedBottom(buffer: Int = 1): Boolean {
    val tag = "reachedBottom"
    val tagKClass = LazyListState::class
    var ret = false

    val lastVisibleItem = this.layoutInfo.visibleItemsInfo.lastOrNull()

    if (null != lastVisibleItem)
        ret = ((0 != lastVisibleItem.index) && ((this.layoutInfo.totalItemsCount - buffer) == lastVisibleItem.index))

    logd (tagKClass, tag,"$tag: buffer: $buffer: $ret")

    return ret
}

@Composable
fun <T : Any> LazyChatScreen(
    modifier: Modifier,
    lazyList: List<T>,
    autoScroll: Boolean = true,
    chatItemContent: @Composable() ((Modifier, T) -> Unit)? = null,
    inputContent: (@Composable() (Modifier) -> Unit)? = null
) {
    TwoComposablesStack(
        modifier = modifier,
        top = { mod ->
            LazyScreen(
                modifier = mod,
                lazyList = lazyList,
                autoScroll = autoScroll,
                lazyContent = chatItemContent)
        },
        bottom = { mod ->
            inputContent?.invoke(mod)
        }
    )
}

@Composable
fun TwoComposablesStack(
    modifier: Modifier,
    top: @Composable() ((Modifier) -> Unit)? = null,
    bottom: (@Composable() (Modifier) -> Unit)? = null
) {
    val tag = "TwoStackComposables"

    Log.d(tag, "Entry")

    ConstraintLayout(modifier = modifier
        .fillMaxSize()
    ) {
        val (lazyContentRef, bottomContentRef) = createRefs()

        Box(modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(1F)
            .wrapContentSize(align = Alignment.TopStart)
            .background(Color.Transparent)
            .constrainAs(lazyContentRef) {
                this.top.linkTo(parent.top, margin = 5.dp)
                this.bottom.linkTo(bottomContentRef.top, margin = 5.dp)
                start.linkTo(parent.start, margin = 5.dp)
                end.linkTo(parent.end, margin = 5.dp)
                height = Dimension.fillToConstraints
            }
            .alpha(1F),
        ) {
            top?.invoke(modifier.fillMaxHeight())
        }

        Box(modifier = modifier
            .fillMaxWidth()
            .wrapContentSize(align = Alignment.BottomCenter)
            .background(Color.Transparent)
            .constrainAs(bottomContentRef) {
                this.top.linkTo(lazyContentRef.bottom, margin = 5.dp)
                this.bottom.linkTo(parent.bottom, margin = 5.dp)
                start.linkTo(parent.start, margin = 5.dp)
                end.linkTo(parent.end, margin = 5.dp)
            }
            .alpha(1F)
        ) {
            bottom?.invoke(
                Modifier
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
fun ScaffoldScreen(
    modifier: Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    snackBarHost: (@Composable () -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = Color.Transparent,
    contentColor: Color = Color.Transparent,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: (@Composable() () -> Unit)
    ) {
    val tag = "ScaffoldScreen"

    UiTheme {
        Log.d(tag, "Calling Scaffold:")
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Transparent),
            topBar = { topBar?.invoke() },
            bottomBar = { bottomBar?.invoke() },
            snackbarHost = { snackBarHost?.invoke() },
            floatingActionButton = { floatingActionButton?.invoke() },
            floatingActionButtonPosition = floatingActionButtonPosition,
            containerColor = containerColor,
            contentColor = contentColor,
            contentWindowInsets = contentWindowInsets
        ) { paddingValues ->
            Log.d(tag, "Scaffold calling Surface:")

            Surface(
                modifier = modifier
                    .background(contentColor)
                    .fillMaxSize()
                    .wrapContentSize()
                    .padding(paddingValues)
                    /*
                     * This is in order to clear whatever padding values from now on, for the subsequent called @composables.
                     * Fixes doubling up the OutlinedTextField field opening the virtual keyboard
                     */
                    .consumeWindowInsets(paddingValues)
            )
            {
                Log.d(tag, "Surface calling Box:")
                Box(
                    modifier = modifier
                        .background(contentColor)
                        .fillMaxWidth()
                        .fillMaxHeight(1F)
                        .wrapContentSize(align = Alignment.TopStart)
                        .alpha(1F),
                    contentAlignment = Alignment.BottomStart,
                    propagateMinConstraints = false
                ) {
                    Log.d(tag, "Box calling Content:")
                    content.invoke()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopBarUI(
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable() (RowScope.() -> Unit))? = null,
    expandedHeight: Dp? = null,
    windowInsets: WindowInsets? = null,
    colors: TopAppBarColors? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    wtUITheme: WTUITheme = WTUITheme()
) {
    TopAppBar(
        modifier = modifier
            .background(wtUITheme.topBgColor, shape = RoundedCornerShape(5)),
        title = title ?: {
            Text(text = wtUITheme.topTitle.toString(),
                modifier = modifier
                    .fillMaxWidth()
                    .background(wtUITheme.topBgColor),
                textAlign = TextAlign.Center,
                color = wtUITheme.topTitleColor)
        },
        navigationIcon = navigationIcon ?: { },
        actions = actions ?: { },
        expandedHeight = expandedHeight ?: TopAppBarDefaults.TopAppBarExpandedHeight,
        windowInsets = windowInsets ?: TopAppBarDefaults.windowInsets,
        scrollBehavior = scrollBehavior ?: TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
        colors = colors ?: TopAppBarColors(containerColor = wtUITheme.topBgColor, wtUITheme.topBgColor, wtUITheme.topBgColor, wtUITheme.topBgColor, wtUITheme.topBgColor,)
    )
}

@Composable
internal fun BottomBarUI(wtUITheme: WTUITheme) {
    BottomAppBar (
        modifier = Modifier.background(Color.Transparent),
        containerColor = wtUITheme.bottomBgColor
    ) {
        Text(text = wtUITheme.bottomTitle.toString(),
            modifier = Modifier.background(Color.Transparent),
            color = wtUITheme.bottomTitleColor)
    }
}

@Composable
inline fun<reified T, reified K> T.Compose(key: K? = null, content: @Composable() T.() -> Unit) {
    val kKey: MutableState<K?>? = remember { mutableStateOf(key) } ?: null
    run {
        run { kKey }
        content()
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.TopBarBack(
    modifier: Modifier = Modifier,
    mainVModel: WTViewModel,
    wtUITheme: WTUITheme = WTUITheme()
) {
    val tag = "TopBarBack/${randomString(2U)}"
    val sendTextIcon: Painter = painterResource(id = wtUITheme.backIcon)

    logd(tag, "Entry: ${mainVModel.currentScreen()}")
    Icon(
        painter = sendTextIcon,
        contentDescription = null,
        modifier = modifier
            .scale(.8F)
            .background(wtUITheme.bgColor)
            .wrapContentSize()
            .clickable {
                logd(tag, "Clicking Back. ${mainVModel.currentScreen()}")
                mainVModel.switchToScreen(WTNavigation.Back)
            },
        tint = wtUITheme.textColor
    )
}

@Composable
internal fun<T> DropDownMenu(
    modifier: Modifier,
    itemsList: List<T>? = null,
    wtUITheme: WTUITheme = WTUITheme(),
    itemMenuContent: (@Composable (T, Modifier) -> Unit)
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier
        .background(wtUITheme.topBgColor, RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp))) {
        IconButton(onClick = { expanded = !expanded }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "DropDownMenu",
                tint = wtUITheme.textColor
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier.background(wtUITheme.topBgColor, RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp))
        ) {
            itemsList?.forEach { item ->
                itemMenuContent(item, modifier)
                if (item != itemsList.last()) HorizontalDivider()
            }
        }
    }
}


@Composable
fun EmptyLine(modifier: Modifier = Modifier, spacesNo: Int = 16, wtUITheme: WTUITheme = WTUITheme()) {
    var spaceLine = ""
    for (i in 0..spacesNo) spaceLine += " "
    Text(
        text = spaceLine,
        modifier = modifier
            .fillMaxWidth()
            .background(color = wtUITheme.bgColor, RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp)),
        color = wtUITheme.textColor,
        fontSize = wtUITheme.chatFontSize,
        textAlign = TextAlign.Center
    )
}