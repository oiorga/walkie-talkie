package walkie.talkie.ui.screens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import walkie.talkie.R
import walkie.talkie.ui.theme.DarkGreen
import walkie.talkie.ui.theme.DarkGreen01
import walkie.talkie.ui.theme.PaleGreen
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString

@Immutable
class WTUITheme (
    val bgColor: Color = DarkGreen,
    val textColor: Color = PaleGreen,
    val bgChatColor: Color = DarkGreen01,
    val chatColor: Color = textColor,
    val chatFontSize: TextUnit = 18.sp,
    val dateFontSize: TextUnit = 12.sp,
    val topTitle: String? = null,
    val topTitleColor: Color = textColor,
    val topBgColor: Color = bgColor,
    val bottomTitle: String? = null,
    val bottomTitleColor: Color = textColor,
    val bottomBgColor: Color = bgColor,
    val lazyListItemBgColorNew: Color = textColor,
    val lazyListItemColorNew: Color = bgChatColor,
    val lazyListItemBgColorSeen: Color = bgChatColor,
    val lazyListItemColorSeen: Color = textColor,
    val lazyListItemSize: TextUnit = 24.sp,
    val menuItemSize: TextUnit = 22.sp,
    val lazyListItemPadding: Dp = 2.dp,
    val verticalTextPadding: Dp = 6.dp,
    val horizontalTextPadding: Dp = 4.dp,
    val sendTextIcon: Int = R.drawable.arrow_forward_24dp_wght400_opsz24,
    val backIcon: Int = R.drawable.arrow_back_24dp_wght400_opsz24
)

class WTLineTextAnimation(
    private val textArray: String = ".",
    val color: Color = Color.Green,
    val colorStep: Int? = null,
    private val outputLength: Int = textArray.length
) {
    private var tick: Int = 0
    private var output: String = ""
    private var tLength: Int = textArray.length

    companion object {
        const val TAG = "WTLineTextAnimation"
        val TAGKClass = WTLineTextAnimation::class
    }

    init {
        val tag = "init{}/${randomString(2U)}"
        logging()
        logd(tag, "textArray: $textArray outputLength: $outputLength")
    }

    fun reset() {
        tick = 0
        output = ""
    }

    fun tick(): Int {
        return tick
    }

    fun next(): String {
        val tag = "next/${randomString(2U)}"
        if (0 == (tick % outputLength)) {
            output = ""
        }
        output += textArray[(++tick) % tLength]
        logd(tag, "tick: $tick output: $output")
        return output
    }
}