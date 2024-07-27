package top.mcfpp.lib

import top.mcfpp.lang.resource.StorageConcrete
import java.io.Serializable

/**
 * 一个储存对象
 *
 * @constructor Create empty top.mcfpp.lang.resource.Storage
 */
class Storage: Serializable {

    var namespace : String
    var identifier : String

    constructor(namespace: String, identifier: String){
        this.namespace = namespace
        this.identifier = identifier
    }

    override fun toString():String{
        return "$namespace:$identifier"
    }

    override fun equals(other: Any?): Boolean {
        if(other !is Storage) return false
        return namespace == other.namespace && identifier == other.identifier
    }

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    fun getResourceID() : top.mcfpp.lang.resource.Storage{
        return StorageConcrete("$namespace:$identifier")
    }

    companion object {
        val NbtData = Storage("mcfpp","data")
        val MCFPP_SYSTEM = Storage("mcfpp","system")
        val MCFPP_TEMP = Storage("mcfpp","temp")
        val MCFPP_ARG = Storage("mcfpp","arg")
        val MCFPP_OUTPUT = Storage("mcfpp","output")
    }

}