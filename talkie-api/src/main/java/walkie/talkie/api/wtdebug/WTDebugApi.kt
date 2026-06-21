package walkie.talkie.api.wtdebug

interface WTDebugInt {
    fun wtDebug(onOff: Boolean? = null): Boolean
}

/* To implement fully later */
fun wtError(tag: String? = null, errStr: String? = null) {
    error("$tag $errStr")
}