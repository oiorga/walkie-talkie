package walkie.glue.wtdebug

import android.util.Log
import kotlinx.serialization.Serializable

interface WTDebugInt {
    fun wtDebug(onOff: Boolean? = null): Boolean
}