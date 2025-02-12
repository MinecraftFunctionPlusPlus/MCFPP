package top.mcfpp.lib

import top.mcfpp.util.StringHelper.toSnakeCase

class NamespaceID {

    var namespace: String
        private set

    var identifier: String
        private set

    constructor(namespace: String?, identifier: String) {
        this.namespace = namespace?.toSnakeCase() ?:"minecraft"
        this.identifier = identifier.toSnakeCase()
    }

    constructor(namespace: String?, path: String, identifier: String){
        this.namespace = namespace?.toSnakeCase()?:"minecraft"
        this.identifier = path.split("/").joinToString("/") { it.toSnakeCase() } + "/" + identifier
    }

    override fun toString(): String {
        return "$namespace:$identifier"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is NamespaceID) return false
        return namespace == other.namespace && identifier == other.identifier
    }

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    fun changeNamespace(namespace: String, check: Boolean = true){
        if(check){
            this.namespace = namespace.toSnakeCase()
        }else {
            this.namespace = namespace
        }
    }

    fun changeIdentifier(identifier: String, check: Boolean = true){
        if(check){
            this.identifier = identifier.toSnakeCase()
        }else{
            this.identifier = identifier
        }
    }

    fun appendIdentifier(identifier: String, check: Boolean = true): NamespaceID{
        if(check){
            this.identifier = this.identifier + "/" + identifier.toSnakeCase()
        }else{
            this.identifier = this.identifier + "/" + identifier
        }
        return this
    }

    fun prependIdentifier(identifier: String, check: Boolean = true): NamespaceID{
        if(check){
            this.identifier = identifier.toSnakeCase() + "/" + this.identifier
        }else{
            this.identifier = identifier + "/" + this.identifier
        }
        return this
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