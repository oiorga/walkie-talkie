package walkie.glue.wtsystem

interface NodeIdInt {
    val id: String
    val unique: String?

    fun id(): String
    fun unique(): String
    fun uid(id: String? = null, unique: String? = null): String
    fun logD(tag: String, logF: Boolean = true)
}

fun nodeUniqueId (id: String, unique: String): String {
    return ("[$id].[$unique]")
}

fun uidToId(uId: String): String? {
    var ret: String? = null
    var id = ""

    var step = 0
    uId.forEach {
        when (step) {
            0 -> {
                if (it == '[') {
                    step++
                }
            }
            1 -> {
                if (it == ']') {
                    step++
                } else {
                    id += it
                }
            }
            else -> { }
        }
    }

    if ("" != id) ret = id

    /*
    logd(
        TAGKClass,
        TAG,
        "$TAG: uidToId: uId: $uId id: $id"
    )
    */

    return ret
}

fun uidToUnique(uId: String): String? {
    var ret: String? = null
    var unique = ""

    var step = 0
    uId.forEach {
        when (step) {
            0, 2 -> {
                if (it == '[') {
                    step++
                }
            }
            1 -> {
                if (it == ']') {
                    step++
                }
            }
            3 -> {
                if (it == ']') {
                    step++
                } else {
                    unique += it
                }
            }
            else -> { }
        }
    }

    if ("" != unique) ret = unique

    /*
    logd(
        TAGKClass,
        TAG,
        "$TAG: uidToId: uId: $uId unique: $unique"
    )
    */

    return ret
}
