package top.mcfpp.core.lang

import top.mcfpp.type.MCFPPType
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor

/**
 * 一个MCFPP中的访问器。访问器是对类、模板等复合结构成员访问的封装。
 *
 * 访问器拥有一个成员[value]，表示该访问器所访问的成员。对访问器进行的操作，效果和直接对变量进行操作完全一致
 *
 * 访问器可以控制对成员的访问权限，例如只读，只写等
 *
 * 原则上，访问器不应该直接被创建，而是通过[Var.getMemberVar]等方法获得
 */
class Accessor(override var value: Var<*>, identifier: String = value.identifier, var isReadOnly: Boolean = false) : Var<Accessor>(identifier), MCFPPValue<Var<*>> {

    init {
        this.parent = value.parent
    }

    /**
     * 访问器的类型和[value]的类型一致
     */
    override var type: MCFPPType
        get() = value.type
        set(value) {}

    override fun implicitCast(type: MCFPPType): Var<*> {
        return value.implicitCast(type)
    }

    override fun clone(): Accessor {
        return Accessor(value.clone(), identifier)
    }

    override fun getTempVar(): Accessor {
        return Accessor(value.getTempVar())
    }

    override fun storeToStack() {
        value.storeToStack()
    }

    override fun getFromStack() {
        value.getFromStack()
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return value.getMemberVar(key, accessModifier)
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return value.getMemberFunction(key, readOnlyParams, normalParams, accessModifier)
    }

    override fun explicitCast(type: MCFPPType): Var<*> {
        return value.explicitCast(type)
    }

    override fun doAssign(b: Var<*>): Accessor {
        if(isReadOnly){
            LogProcessor.error("$identifier is read only")
            return this
        }
        return Accessor(value.assign(b), this.identifier, isReadOnly)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        return if(value is MCFPPValue<*>) {
            (value as MCFPPValue<*>).toDynamic(replace)
        } else {
            value
        }
    }

}