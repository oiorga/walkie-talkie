package walkie.util

import android.util.Log

/**
* ViewModel Collection
* Add, Get simple types to/from ViewModels
*
*/
class VMCollection () {
    private val tag = "UICollection"

    companion object {
        val defaultValueType = mapOf(
            "Int" to (0 as Any),
            "Float" to (0F as Any),
            "String" to ("" as Any),
            "Double" to (0F as Any),
            "Long" to (0L as Any)
        )
    }

    private var uiCool: MutableMap<String, MutableMap<String, Any>> = mutableMapOf("uiCool" to mutableMapOf("uiCool" to "uiCool" as Any))

    fun add (key: String, value: Any) {
        Log.d(tag, "add 0: $key $value ${value::class.simpleName as String}")

        uiCool[value::class.simpleName as String] = mutableMapOf(key to value)

        /*
        val obj3 = uiCool[value::class.simpleName as String]
        val obj4 = obj3?.get(key) ?: 4000000.toLong()
        Log.d(tag, "add 1: $obj3 $obj4 ${value::class.simpleName as String}")
        */
    }

    fun get (key: String, type: String) : Any {
        var mapObjType: Any?

        Log.d(tag, "get start: $key $type ${uiCool[type]} ${uiCool[type]?.get(key)}")

        mapObjType = uiCool[type]
        if (null == mapObjType) {
            uiCool[type] = mutableMapOf(key to (defaultValueType[type] as Any))
            mapObjType = uiCool[type]
        }

        var mapObjValue: Any? = mapObjType?.get(key)
        if (null == mapObjValue) {
            uiCool[type] = mutableMapOf(key to defaultValueType[type] as Any)
            mapObjValue = uiCool[type]?.get(key)
        }

        /*
        Log.d(tag, "get end: $key $type ${uiCool[type]} ${uiCool[type]?.get(key)}")
        */

        return (mapObjValue as Any)
    }
}