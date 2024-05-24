package top.mcfpp.lang

/**
 * 一个储存对象
 *
 * @constructor Create empty Storage
 */
class Storage {

    var namespace: String
    var identifier: String

    constructor(namespace: String, identifier: String) {
        this.namespace = namespace
        this.identifier = identifier
    }

    override fun toString(): String {
        return "$namespace:$identifier"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Storage) return false
        return namespace == other.namespace && identifier == other.identifier
    }

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    companion object {
        val NbtData = Storage("mcfpp", "data")
        val MCFPP_SYSTEM = Storage("mcfpp", "system")
        val MCFPP_TEMP = Storage("mcfpp", "temp")
        val MCFPP_ARG = Storage("mcfpp", "arg")
        val MCFPP_OUTPUT = Storage("mcfpp", "output")
    }

}