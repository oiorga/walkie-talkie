package walkie.talkie.node

import walkie.glue.wtsystem.NodeIdInt
import walkie.glue.wtsystem.nodeUniqueId
import walkie.glue.wtsystem.uidToId
import walkie.glue.wtsystem.uidToUnique
import walkie.util.logd
import walkie.util.logging
import walkie.util.randomString

data class NodeId (
    override val id: String,
    override val unique: String? = null
) : NodeIdInt {
    private var uid: String
    private var uniq: String

    companion object {
        private const val TAG = "NodeId"
        val TAGKClass = NodeId::class
    }

    init {
        logging(true)

        uniq = unique ?: randomString(2U)
        uid = this.uid(id, uniq)

        logd(TAGKClass,
            TAG,
            "$TAG init: " +
                "\n\t\t\t\tid: $id " +
                "\n\t\t\t\tuniq: $uniq " +
                "\n\t\t\t\tuid: $uid")

        if (id == uidToId(uniq)) {
            throw(Exception("id == Builder.uidToId(uid)"))
        }
    }

    override fun id(): String {
        return id.toString()
    }
    override fun unique(): String {
        return uniq.toString()
    }

    override fun uid(id: String?, unique: String?): String {
        if (null == id && null == unique) return uid.toString()
        id!!
        unique!!
        return nodeUniqueId(id, unique)
    }

    override fun logD(tag: String, logF: Boolean) {
        if (logF) logd(TAGKClass,
            TAG,
            "$TAG " +
                    "\n\t\t\t\tid: $id " +
                    "\n\t\t\t\tuniq: $uniq " +
                    "\n\t\t\t\tuid: $uid")
    }

    data class Builder(private var id: String? = null, private var unique: String? = null, private var uid: String? = null) {
        fun id(id: String) = apply {
            this.id = id
        }
        fun unique(unique: String) = apply {
            this.unique = unique
        }
        fun build(): NodeId {
            logd(TAGKClass,
                TAG,
                "Builder: id: $id unique: $unique")
            if (null != uid) {
                this.id = uidToId(uid!!)
                this.unique = uidToUnique(uid!!)
            }
            this.id!!
            return NodeId(
                this.id!!,
                this.unique
            )
        }
    }
}
