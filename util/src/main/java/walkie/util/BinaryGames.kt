package walkie.util

import android.util.Log
import walkie.util.generic.GenericList
import kotlin.math.pow

fun incBinary(listIn: GenericList<Int>, base: Int = 2, length: Int = 4) : GenericList<Int> {
    var listOut: GenericList<Int> = GenericList<Int>()

    var last: Int = listIn.removeLast()
    var carrier = if (last + 1 >= base) 1 else 0
    last = (last + 1) % base
    listIn.reversed().forEach {
        listOut.add((it + carrier) % base)
        carrier = if ((it + carrier) >= base) 1 else 0
    }

    listOut = listOut.reversed() as GenericList<Int>
    listOut.add(last)

    return listOut
}

fun incBinaryRec(listIn: GenericList<Int>, carrier: Int = 1, base: Int) : GenericList<Int> {
    var listOut: GenericList<Int> = GenericList<Int>()

    if (listIn.isNotEmpty()) {
        var last = listIn.removeLast()

        //Log.d("generateBinary", "incBinaryRec ----: carrier: $carrier last: $last")

        last += carrier
        val c = (if (last >= base) 1 else 0)
        last %= base

        //Log.d("generateBinary", "incBinaryRec ++++: carrier: $carrier - $c last: $last")
        listOut = incBinaryRec(listIn, c, base)

        listOut.add(last)
    }

    return listOut
}

fun generateBinaryRec (
    baseList: GenericList<Int> = GenericList<Int>(),
    base: Int = 2,
    length: Int = 4,
    count: Int = base.toFloat().pow(length).toInt()
): MutableList<String> {
    val tag = "generateBinary"
    val list = GenericList<String>()
    var list0 = GenericList<Int>()

    list0 = baseList
    for (i in list0.size ..< length) list0.add(0)

    for (i in 0 ..<count) {
        list0 = incBinaryRec(list0, 1, base)
        //Log.d("generateBinary", "generateBinary: ${list0.toString()}")
        list.add(list0.joinToString(prefix = "", postfix = "", separator = "."))
    }

    Log.d("generateBinary", "generateBinary: ${list.toString()}")

    return list
}

fun generateBinary (
    baseList: GenericList<Int> = GenericList<Int>(),
    base: Int = 2,
    length: Int = 4,
    count: Int = base.toFloat().pow(length).toInt()): MutableList<String> {
    val tag = "generateBinary"
    val list = GenericList<String>()
    var list0 = baseList

    list0 = baseList
    for (i in list0.size ..< length) list0.add(0)

    list.add(list0.joinToString(prefix = "", postfix = "", separator = "."))
    for (i in 1 ..<count) {
        list0 = incBinary(list0, base, length)
        list.add(list0.joinToString(prefix = "", postfix = "", separator = "."))
    }

    Log.d("generateBinary", "generateBinary: ${list.toString()}")

    return list
}
