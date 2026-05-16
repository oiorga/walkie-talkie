package walkie.talkie

import walkie.talkie.api.wtdebug.WTDebugInt
import walkie.util.api.ChannelMessageType
import walkie.util.api.RemoteCallMuxInt
import walkie.util.generic.ChannelMux
import walkie.util.generic.ChannelMuxInt
import walkie.util.generic.RemoteCallMux

import android.app.Application
import androidx.activity.viewModels
import walkie.talkie.common.WTCommonData
import walkie.talkie.viewmodel.WTViewModel
import kotlin.getValue

class WalkieTalkie() : Application() {
    companion object {
        const val TAG = "WalkieTalkie"
        val TAGKClass = WalkieTalkie::class
    }
    val tag = TAG

    val wtCommonData: WTCommonData = WTCommonData.ONE

    override fun onCreate() {
        super.onCreate()
    }

    override fun onTerminate() {
        super.onTerminate()
    }

    override fun onLowMemory() {
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
    }
}