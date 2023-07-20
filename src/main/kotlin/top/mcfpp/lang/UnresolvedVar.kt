package top.mcfpp.lang

import top.mcfpp.exception.VariableNotResolvedException
import top.mcfpp.lib.FieldContainer

class UnresolvedVar : Var {

    private val varType: String

    override val type: String
        get() = varType

    constructor(identifier: String, type: String){
        varType = type
        this.identifier = identifier
    }

    fun resolve(fieldContainer: FieldContainer): Var?{
        return build(identifier, type, fieldContainer)
    }

    override fun assign(b: Var?) {
        throw VariableNotResolvedException()
    }

    override fun cast(type: String): Var? {
        throw VariableNotResolvedException()
    }

    override fun clone(): Any {
        return UnresolvedVar(identifier,varType)
    }

    override fun getTempVar(): Var {
        throw VariableNotResolvedException()
    }
}