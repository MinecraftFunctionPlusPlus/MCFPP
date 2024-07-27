package top.mcfpp.lib

import java.io.Serializable

class NamespaceID(var namespace: String?, var identifier: String): Serializable {
    override fun toString(): String {
        if(namespace == null){
            return identifier
        }
        return "$namespace:$identifier"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is NamespaceID) return false
        return namespace == other.namespace && identifier == other.identifier
    }

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    companion object {
        fun parseFromString(str: String) : NamespaceID{
            val split = str.split(":", limit = 2)
            if(split.size != 2){
                return NamespaceID(null,str)
            }
            return NamespaceID(split[0],split[1])
        }
    }
}