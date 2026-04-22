package walkie.util.generic

import android.os.Build
import android.util.Log
import kotlinx.serialization.Serializable

/**
 * abstract class GenericMapAbs
 *
 * Working/Alias against:
 *        itemList: MutableList<T> = mutableListOf()
 **/
@Serializable
abstract class GenericMapAbs<K, V> (
    private val map: MutableMap<K, V> = mutableMapOf()
) : MutableMap<K, V> by map {
    private val tag = "GenericListAbs"

    init {
        if (map.isEmpty()) {
            Log.d(tag, "GenericListAbs called empty on init")
        }
    }
}

class GenericMap<K, V>: GenericMapAbs<K, V>()
