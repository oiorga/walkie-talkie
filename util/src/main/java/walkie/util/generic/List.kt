package walkie.util.generic

import android.os.Build
import android.util.Log
import kotlinx.serialization.Serializable

/**
 * abstract class GenericListAbs
 *
 * Working/Alias against:
 *        itemList: MutableList<T> = mutableListOf()
 **/
abstract class GenericListAbs<T> (
    private val itemList: MutableList<T> = mutableListOf<T>()
) : MutableList<T> by itemList {
    private val tag = "GenericListAbs"

    init {
        if (itemList.isEmpty()) {
            Log.d(tag, "GenericListAbs called empty on init")
        }
    }

    override fun removeLast(): T {
        return if (Build.VERSION.SDK_INT >= 35) {
            this.removeLast()
        } else {
            this.removeAt(this.lastIndex)
        }
    }

    override fun removeFirst(): T {
        return if (Build.VERSION.SDK_INT >= 35) {
            this.removeLast()
        } else {
            this.removeAt(0)
        }
    }

    fun toMutableList(): MutableList<T> {
        return itemList.toMutableList()
    }
}

class GenericList<T>: GenericListAbs<T>()

public fun <T> genericListOf(
    vararg elements: T
): GenericList<T> {
    val genericList = GenericList<T>()

    elements.forEach {
        genericList.add(it)
    }

    return genericList
}
