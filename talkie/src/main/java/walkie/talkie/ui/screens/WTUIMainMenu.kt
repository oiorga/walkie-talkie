package walkie.talkie.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import walkie.glue.wtmisc.WTNavigation
import walkie.talkie.WalkieTalkie
import walkie.talkie.ui.nav.WTNavNode
import walkie.talkie.ui.util.BottomBarUI
import walkie.talkie.ui.util.DropDownMenu
import walkie.talkie.ui.util.ScaffoldScreen
import walkie.talkie.ui.util.TopBarBack
import walkie.talkie.ui.util.TopBarUI
import walkie.talkie.viewmodel.WTViewModel
import walkie.util.logd
import walkie.util.randomString


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.MainScreenTopBarActionMenu(
    modifier: Modifier,
    mainVModel: WTViewModel,
    wtUITheme: WTUITheme = WTUITheme()
) {
    val tag = "MainScreenTopBarAction/${randomString(2U)}"
    logd(tag, "Entry")
    val menuList = mutableListOf<WTNavNode>()

    menuList.add(
        WTNavNode(
            route = WTNavigation.TextInfo,
            onClick = {
                mainVModel.textInfoId = "Help"
                mainVModel.switchToScreen(WTNavigation.TextInfo)
                      },
            value = "Help",
            preview = { mod ->
                MenuItemInfo(entryId = "Help",  mod!!, mainVModel, wtUITheme, )
            },
            /* navController = mainVModel.navGraph.navController */
        )
    )

    menuList.add(
        WTNavNode(
            route = WTNavigation.TextInfo,
            /*
            onClick = {
                mainVModel.textInfoId = "About"
                mainVModel.switchToScreen(WTNavigation.TextInfo)
                      },
            */
            value = "About",
            preview = { mod ->
                MenuItemInfo(entryId = "About", mod!!, mainVModel, wtUITheme)
            },
            /* navController = mainVModel.navGraph.navController */
        )
    )

    DropDownMenu(modifier, menuList) { menuItem, mod ->
        menuItem.Preview(mod)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.MenuItemInfo(
    entryId: String,
    modifier: Modifier,
    mainVModel: WTViewModel,
    wtUITheme: WTUITheme = WTUITheme(),
    onClick: (() -> Unit)? = null
) {
    val mod =
        if (null != onClick) modifier.clickable { onClick() }
        else modifier.clickable {
            if (null !== wtCommonData().customComposables[entryId]) {
                mainVModel.textInfoId = entryId
                mainVModel.switchToScreen(WTNavigation.TextInfo)
            }
        }

    Text("$entryId       ",
        modifier = mod
            .wrapContentSize()
            .fillMaxWidth(1F)
            .padding(wtUITheme.horizontalTextPadding, wtUITheme.verticalTextPadding)
            .background(wtUITheme.topBgColor, RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp)),
        color = wtUITheme.textColor,
        fontSize = wtUITheme.menuItemSize
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun WalkieTalkie.MenuTextInfo() {
    val tag = "WalkieTalkieMenuItem"
    val mainVModel = this.wtVModel()

    mainVModel.wtViewModelUpdate(WTNavigation.TextInfo)

    ScaffoldScreen (
        modifier = Modifier
            .fillMaxSize(1F)
            .background(color = WTMenuUITheme.bgColor)
        ,
        topBar = {
            TopBarUI(
                navigationIcon = { TopBarBack(modifier = Modifier, mainVModel = mainVModel) },
                wtUITheme = WTMenuUITheme
            )
        },
        bottomBar = { BottomBarUI(WTMenuUITheme) },
        containerColor = WTMenuUITheme.bgColor
    ) {
        wtCommonData().customComposables[mainVModel.textInfoId]?.invoke(Modifier, WTUITheme())
    }
}