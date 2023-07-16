package top.mcfpp.lang

import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lib.Class
import top.mcfpp.lib.ClassMember
import top.mcfpp.lib.Function

class Selector : CanSelectMember {

    @get:Override
    override var type = "selector"
    var text: String? = null

    override var clsType: Class
        get() = TODO("Not yet implemented")
        set(value) {}

    constructor(b:Var):super(b)
    constructor():super()

    constructor(string: String)

    @Override
    override fun assign(b: Var?) {
        if (b is MCString) {
            text = b.toString()
        } else {
            throw VariableConverseException()
        }
    }

    @Override
    override fun cast(type: String): Var? {
        return null
    }

    @Override
    override fun clone(): Selector {
        TODO()
    }

    override fun getTempVar(): Var {
        TODO("Not yet implemented")
    }

    override fun getMemberVar(key: String, accessModifier: ClassMember.AccessModifier): Pair<Var?, Boolean> {
        TODO("Not yet implemented")
    }

    override fun getMemberFunction(key: String, params: List<String>, accessModifier: ClassMember.AccessModifier): Pair<Function?, Boolean> {
        TODO("Not yet implemented")
    }
}