package walkie.talkie

import android.app.Application
import walkie.talkie.common.WTCommonData

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
