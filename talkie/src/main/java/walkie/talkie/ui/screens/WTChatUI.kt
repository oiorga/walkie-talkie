package walkie.talkie.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import walkie.chat.ChatMessage
import walkie.chat.ChatMessageItem
import walkie.chat.Receiver
import walkie.chat.Sender
import walkie.glue.wtchat.ChatGroupType
import walkie.glue.wtchat.ChatMessageAbs
import walkie.glue.wtmisc.WTNavigation
import walkie.talkie.WalkieTalkie
import walkie.talkie.ui.nav.WTNavNode
import walkie.talkie.ui.nav.wtChatTypeToNavScreen
import walkie.talkie.ui.util.BottomBarUI
import walkie.talkie.ui.util.ChatDivider
import walkie.talkie.ui.util.ComposableChatItem
import walkie.talkie.ui.util.LazyChatScreen
import walkie.talkie.ui.util.ScaffoldScreen
import walkie.talkie.ui.util.TopBarBack
import walkie.talkie.ui.util.TopBarUI
import walkie.talkie.viewmodel.UIMessageAction
import walkie.talkie.viewmodel.WTViewModel
import walkie.util.logd
import walkie.util.randomString

val WalkieTalkie.WTChatUITheme: WTUITheme
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    get() =
        WTUITheme(
            topTitle = this.wtVModel().chatDiscussionId?.groupName,
            bottomTitle = ""
        )

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun WalkieTalkie.WTChat (wtNavNode: WTNavNode? = null) {
    val tag = "WTChat/${randomString(2U)}"
    val chatVModel = this.wtVModel()
    val navDest: WTNavigation = wtChatTypeToNavScreen(chatVModel.nextDiscussionId!!.type)

    logd(tag, "$tag Entry 0")

    chatVModel.wtViewModelUpdate(navDest)

    logd(tag, "$tag Entry 1 triggerUpdate: ${chatVModel.triggerUIUpdate} ")

    ScaffoldScreen(
        modifier = Modifier
            .fillMaxSize()
            .consumeWindowInsets(PaddingValues())
        ,
        topBar = { TopBarUI(
            navigationIcon = { TopBarBack(modifier = Modifier, mainVModel = chatVModel) },
            wtUITheme = WTChatUITheme) },
        bottomBar = { BottomBarUI(WTChatUITheme) },
        contentColor = Color.Transparent
    ) {
        logd(tag, "ScaffoldScreen: ${chatVModel.triggerUIUpdate}")

        ChatScreen(
            chatVModel = chatVModel,
            modifier = Modifier
                .fillMaxWidth(1F)
                .consumeWindowInsets(PaddingValues())
                .background(WTChatUITheme.bgColor),
            wtUITheme = WTChatUITheme
        )
    }

    chatVModel.chatDiscussion.read = true
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.ChatBox(
    chatVModel: WTViewModel,
    modifier: Modifier,
    wtUITheme: WTUITheme = WTChatUITheme
) {
    val tag = "ChatBox"
    var textInput by remember { mutableStateOf("") }
    val receiver = Receiver(chatVModel.wtSystemNode, chatVModel.chatDiscussionId!!)
    val sender = Sender(node = chatVModel.wtSystemNode)
    var hasFocus by remember { mutableStateOf(false) }

    logd(tag, "ChatBox Entry")

    val message = ChatMessage(
        receiver = receiver,
        sender = sender,
        groupId = chatVModel.chatDiscussionId!!
    )

    OutlinedTextField(
        value = textInput,
        modifier = modifier
            .background(wtUITheme.bgColor)
            /*
            .border(
                width = 1.dp,
                color = wtUITheme.chatColor,
                shape = RoundedCornerShape(8.dp)
            )
            */
            .windowInsetsPadding(WindowInsets(0, 0, 0, 0))
            .fillMaxWidth()
            .consumeWindowInsets(PaddingValues())
            .imePadding()
            .onFocusChanged { focusState -> hasFocus = focusState.hasFocus },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = wtUITheme.chatColor,
            focusedBorderColor = wtUITheme.chatColor,
            focusedLabelColor = wtUITheme.chatColor,
            unfocusedBorderColor = wtUITheme.chatColor,
            unfocusedTextColor = wtUITheme.chatColor,
            unfocusedLabelColor = wtUITheme.chatColor,
            cursorColor = wtUITheme.chatColor),
        shape = RoundedCornerShape(8.dp),
        onValueChange = { input->
            textInput = input
        },
        trailingIcon = {
            if (textInput.isNotEmpty())
                IconButton( onClick = {
                    message.add(ChatMessageItem.Builder().value(textInput).build())
                    chatVModel.processMessage(message, UIMessageAction.Send)
                    textInput = ""
                },
                    colors = IconButtonColors(
                        containerColor = wtUITheme.bgColor,
                        contentColor = wtUITheme.textColor,
                        disabledContainerColor = wtUITheme.bgColor,
                        disabledContentColor = wtUITheme.textColor)
                ) {
                    val sendTextIcon: Painter = painterResource(id = wtUITheme.sendTextIcon)
                    Icon(
                        painter = sendTextIcon,
                        contentDescription = null,
                        modifier = modifier
                            .scale(.8F)
                            .background(wtUITheme.bgColor),
                        tint = wtUITheme.textColor
                    )
                }
        },
        label = { Text (
            text = "Message",
            /*
            modifier = modifier
                .background(wtUITheme.bgColor)
                .border(
                    width = 1.dp,
                    color = wtUITheme.chatColor,
                    shape = RoundedCornerShape(8.dp)
                )
                .wrapContentSize(Alignment.TopStart),
            */
            color = wtUITheme.textColor,
            textAlign = TextAlign.Left
        ) }
    )

    logd(tag, "ChatBox Sending: $textInput")
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.ChatItem(
    modifier: Modifier,
    chatVModel: WTViewModel,
    chatMessage: ChatMessageAbs,
    wtUITheme: WTUITheme = WTChatUITheme
) {
    val tag = "ChatItem/${randomString(2U)}"
    val isMine: Boolean = chatMessage.sender.node.uid() == chatVModel.wtSystemNode.uid()
    val contentAlign = if (isMine) Alignment.TopEnd else Alignment.TopStart
    val textAlign = if (contentAlign == Alignment.TopEnd) TextAlign.Right else TextAlign.Left

    logd(tag, "ChatItem Entry: from: ${chatMessage.sender.node.uid()} isMine: $isMine")

    /* Force a last non-visible item on the lazy list for auto scroll on new remote messages */
    if (chatMessage.isEmpty()) {
        logd(tag, "$tag 0: Received empty message from: ${chatMessage.sender.node.uid()}")
        ChatDivider(modifier)
        return
    }

    ComposableChatItem(
        chatMessage = chatMessage,
        modifier = modifier,
        wtUITheme = wtUITheme,
        contentAlign = contentAlign,
        textAlign = textAlign)

    logd(tag, "$tag Exit")
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.ChatScreen(
    modifier: Modifier,
    chatVModel: WTViewModel,
    wtUITheme: WTUITheme = WTChatUITheme
    ) {
    val type = chatVModel.chatDiscussion.discussionId.type
    val tag = "WalkieTalkieChat.Screen"
    val navList = mutableListOf<WTNavNode>()

    logd(tag, "Entry:")
    chatVModel.chatDiscussion.forEach {
        if (it.groupId.type == type) {
            val navNode = WTNavNode(
                route = WTNavigation.None,
                /* value = it, */
                preview = { mod ->
                    ChatItem(
                        mod!!,
                        chatVModel,
                        it,
                        wtUITheme
                    )
                }
            )
            navList.add(navNode)
        }
    }

    LazyChatScreen(
        modifier = modifier.background(Color.Transparent),
        lazyList = navList,
        chatItemContent = { mod, item ->
            item.Preview(mod)
        },
        inputContent =
        if (type == ChatGroupType.LocalDebug) null
        else { mod ->
            ChatBox(
                modifier = mod
                    .wrapContentSize()
                    .fillMaxWidth()
                ,
                chatVModel = chatVModel,
                wtUITheme = wtUITheme
            )
        }
    )
}
