package top.mcfpp.lang.value

import top.mcfpp.lang.Var
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPGenericType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lib.function.Function
import top.mcfpp.lib.Member

/**
 * type T;
 * 这里T既是值也是类型
 * 作为值的时候，存储的是一个类型
 */

class MCTypeValue(
    identifier:String,
    var parentType: List<MCFPPType>
): Var<MCFPPType>(identifier) {

    /**
     * **只读，不可更改**。此值的类型，为MCFPPBaseType.Type
     */
    override var type: MCFPPType = MCFPPBaseType.Type

    /**
     * 它的值，应当是一个MCFPPType，可能不存在
     */
    override var javaValue: MCFPPType? = MCFPPBaseType.Any

    fun toType():MCFPPGenericType{
        return MCFPPGenericType(identifier,parentType)
    }
    override fun assign(b: Var<*>?) {
        TODO("Not yet implemented")
    }

    override fun cast(type: MCFPPType): Var<*> {
        TODO("Not yet implemented")
    }

    override fun clone(): MCTypeValue {
        TODO("Not yet implemented")
    }

    override fun getTempVar(): Var<*> {
        TODO("Not yet implemented")
    }

    override fun storeToStack() {
        TODO("Not yet implemented")
    }

    override fun getFromStack() {
        TODO("Not yet implemented")
    }

    override fun toDynamic() {
        TODO("Not yet implemented")
    }

    override fun getVarValue(): Any? {
        TODO("Not yet implemented")
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        TODO("Not yet implemented")
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        TODO("Not yet implemented")
    }
}