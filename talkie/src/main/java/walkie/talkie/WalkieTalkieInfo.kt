package walkie.talkie

import android.content.pm.PackageInfo
import android.icu.text.DateFormat.getDateInstance
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import walkie.talkie.ui.screens.WTUITheme
import walkie.talkie.ui.util.EmptyLine
import walkie.util.logd
import walkie.util.randomString

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun WalkieTalkie.WTInfo(modifier: Modifier = Modifier, wtUITheme: WTUITheme = WTUITheme()) {
    val pInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)

    val hostInfoMap = mapOf(
        /* "Host" to Build.HOST, */
        /* "Base OS" to Build.VERSION.BASE_OS, */
        /* "Release" to Build.VERSION.RELEASE, */
        /* "Codename" to Build.VERSION.CODENAME, */
        /* "Copyright" to WTCopyright, */
        "Manufacturer" to Build.MANUFACTURER + " " + Build.MODEL,
        "Host SDK Info" to Build.VERSION.SDK_INT,
        /* "BASE OS" to Build.VERSION.BASE_OS, */
        "Host Build Time" to getDateInstance().format(Build.TIME),
    )
    val buildInfoMap = mapOf(
        "" to "",
        "versionName" to pInfo.versionName,
        "versionCode" to pInfo.longVersionCode,
        "buildTime" to getDateInstance().format(BuildConfig.BUILD_TIME),
        "gitCommit" to BuildInfo.COMMIT,
        "gitBranch" to BuildInfo.BRANCH,
        "gitRemote" to BuildInfo.REPO
    )

    Box (modifier = modifier
        .fillMaxSize()
        .background(color = wtUITheme.bgColor)
    ) {
        Column (
            modifier = modifier
                .align(Alignment.Center)
                .background(wtUITheme.bgColor)
                .wrapContentSize()
        ) {
            Text(
                text = "Walky Talky Chat App",
                modifier = modifier
                    .fillMaxWidth()
                    .background(
                        color = wtUITheme.bgColor,
                        RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp)
                    )
                    .align(Alignment.CenterHorizontally),
                color = wtUITheme.textColor,
                fontSize = wtUITheme.menuItemSize,
                textAlign = TextAlign.Center
            )
            EmptyLine()
            Text(
                text = "Version: " + buildInfoMap["versionName"] + (if (!wtDebug() && !BuildConfig.DEBUG) "" else "(${buildInfoMap["versionCode"]})"),
                modifier = modifier
                    .fillMaxWidth()
                    .background(
                        color = wtUITheme.bgColor,
                        RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp)
                    ),
                color = wtUITheme.textColor,
                fontSize = wtUITheme.chatFontSize,
                textAlign = TextAlign.Center
            )
            if (wtDebug()) {
                Text(
                    text = "Build Time: " + "${buildInfoMap["buildTime"]}",
                    modifier = modifier
                        .fillMaxWidth()
                        .background(
                            color = wtUITheme.bgColor,
                            RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp)
                        ),
                    color = wtUITheme.textColor,
                    fontSize = wtUITheme.chatFontSize,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Commit: " + "${buildInfoMap["gitCommit"]}",
                    modifier = modifier
                        .fillMaxWidth()
                        .background(
                            color = wtUITheme.bgColor,
                            RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp)
                        ),
                    color = wtUITheme.textColor,
                    fontSize = wtUITheme.chatFontSize,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Branch: " + "${buildInfoMap["gitBranch"]}",
                    modifier = modifier
                        .fillMaxWidth()
                        .background(
                            color = wtUITheme.bgColor,
                            RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp)
                        ),
                    color = wtUITheme.textColor,
                    fontSize = wtUITheme.chatFontSize,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Repo: " + "${buildInfoMap["gitRemote"]}",
                    modifier = modifier
                        .fillMaxWidth()
                        .background(
                            color = wtUITheme.bgColor,
                            RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp)
                        ),
                    color = wtUITheme.textColor,
                    fontSize = wtUITheme.chatFontSize,
                    textAlign = TextAlign.Center
                )
                EmptyLine()
            }
            Text(
                text = "" + WTCopyright,
                modifier = modifier
                    .fillMaxWidth()
                    .background(
                        color = wtUITheme.bgColor,
                        RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp)
                    ),
                color = wtUITheme.textColor,
                fontSize = wtUITheme.chatFontSize,
                textAlign = TextAlign.Center
            )
            EmptyLine()
            WTDebug(
                modifier = modifier
                    .fillMaxWidth()
                    .background(
                        color = wtUITheme.bgColor,
                        RoundedCornerShape(8.dp, 8.dp, 8.dp, 8.dp)
                    )
                    .align(Alignment.CenterHorizontally)
            )
            for (i in 0..5) { EmptyLine() }
            Box (
                modifier = modifier
                    .align(Alignment.CenterHorizontally)
                    .wrapContentSize(Alignment.Center)
            ) {
                Column (
                    modifier = modifier
                        .align(Alignment.Center)
                        .background(wtUITheme.bgColor)
                ) {
                    hostInfoMap.forEach { (key, value) ->
                        Text(
                            text = if ("" == key) "" else "$key: $value",
                            modifier = modifier
                                .wrapContentSize()
                                .align(Alignment.CenterHorizontally)
                                .background(
                                    color = wtUITheme.bgColor,
                                    RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp)
                                ),
                            color = wtUITheme.textColor,
                            fontSize = wtUITheme.chatFontSize,
                            textAlign = TextAlign.Justify
                        )
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun WalkieTalkie.WTDebug(modifier: Modifier = Modifier, wtUITheme: WTUITheme = WTUITheme()) {
    val tag = "WTDebug/${randomString(2U)}"
    var clickCount = remember { 0 }
    val spaces = "                        "
    val text = remember { mutableStateOf(if (wtDebug()) "Disable App Debug" else spaces) }

    logd(tag, "clickCount: $clickCount ${wtDebug()}")

    Text(
        text = text.value,
        modifier = modifier
            .wrapContentSize()
            .background(color = wtUITheme.bgColor, RoundedCornerShape(8.dp, 8.dp, 8.dp, 8.dp))
            .clickable {
                if (wtDebug()) {
                    wtDebug(false)
                    text.value = spaces
                } else {
                    clickCount++
                    if (clickCount >= 7) {
                        wtDebug(true)
                        text.value = "Disable Debug"
                    }
                }
            },
        color = wtUITheme.textColor,
        fontSize = wtUITheme.chatFontSize,
        textAlign = TextAlign.Justify
    )
}

private const val WTHelp = "Welcome to the Walky Talky chat app!" +
        "\n" +
        "\nHave up to four phones within wireless distance, running the app." +
        "\nHave wireless enabled on all phones. No connection to an existing network is required." +
        "\nLook into the \"Nearby Devices\" section. Once nearby devices appear in the list, chat sessions can pe open." +
        "\n" +
        "\nTo be continued..."

private const val WTCopyright = "Copyright (c) 2024–2026 Ovidiu Iorga" + "\nLicensed under the MIT License"

@Composable
fun WalkieTalkie.WTHelp(modifier: Modifier = Modifier, wtUITheme: WTUITheme = WTUITheme()) {

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(color = wtUITheme.bgColor)
    ) {
        Column(
            modifier = modifier
                .align(Alignment.Center)
                .background(wtUITheme.bgColor)
                .wrapContentSize()
        ) {
            Text(
                text = WTHelp,
                modifier = modifier
                    .fillMaxWidth()
                    .background(
                        color = wtUITheme.bgColor,
                        RoundedCornerShape(4.dp, 4.dp, 4.dp, 4.dp)
                    )
                    .align(Alignment.CenterHorizontally),
                color = wtUITheme.textColor,
                fontSize = wtUITheme.chatFontSize,
                textAlign = TextAlign.Center
            )
        }
    }
}

