package top.mcfpp.core.lang

import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPUnionType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

/**
 * 联合类型变量。仅能用于数据模板中。声明语法如下：
 *
 * ```mcfpp
 * data Test{
 *     int|float a;
 *     Data1|Data2 data;
 * }
 *
 * 在赋值给其他变量的时候，联合变量将会尝试隐式转换为目标变量，根据联合类型的声明顺序来尝试进行隐式转换
 *
 * 在被其他变量赋值的时候，联合变量将会尝试寻找一个类型相同的变量进行赋值，如果找不到，将会尝试寻找一个能隐式转换为目标变量的变量进行赋值。如果依然
 *找不到，则会寻找一个能赋值给目标变量的变量进行赋值。寻找的顺序和联合类型的声明顺序一致。
 */
open class UnionTypeVar(identifier: String, vararg vars: Var<*>): Var<UnionTypeVar>(identifier) {

    val vars: ArrayList<Var<*>> = ArrayList(vars.toList())

    override var type: MCFPPType = MCFPPUnionType(*vars.map { it.type }.toTypedArray())

    override var parent: CanSelectMember?
        get() = super.parent
        set(value) {
            super.parent = value
            vars.forEach { it.parent = value }
        }

    override fun implicitCast(type: MCFPPType): Var<*> {
        //寻找类型相同的变量
        for (v in vars) {
            if (v.type == type) {
                return v
            }
        }
        //寻找能强制转换为此变量的变量
        for (v in vars) {
            val qwq = v.implicitCast(type)
            if(qwq.isError){
                continue
            }
        }
        return super.implicitCast(type)
    }


    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun explicitCast(type: MCFPPType): Var<*> {
        //寻找类型相同的变量
        for (v in vars) {
            if (v.type == type) {
                return v
            }
        }
        //寻找能强制转换为此变量的变量
        for (v in vars) {
            val qwq = v.implicitCast(type)
            if(qwq.isError){
                continue
            }
        }
        return super.explicitCast(type)
    }

    override fun doAssignedBy(b: Var<*>): UnionTypeVar {
        for (v in vars) {
            if (v.type == b.type) {
                val qwq = v.assignedBy(b)
                if(qwq is MCFPPValue<*>) qwq.toDynamic(false)
                return this
            }
        }
        for (v in vars){
            val qwq = v.implicitCast(b.type)
            if(qwq.isError){
                continue
            }
            val qwe = v.assignedBy(b)
            if(qwe is MCFPPValue<*>) {
                vars[vars.indexOf(v)] = qwe
                return UnionTypeVarConcrete(this, qwe.value)
            }
            return this
        }
        for (v in vars) {
            if (v.canAssignedBy(b)) {
                val qwq = v.assignedBy(b)
                if(qwq is MCFPPValue<*>) qwq.toDynamic(false)
                return this
            }
        }
        LogProcessor.error("Cannot assign ${b.type} to $type")
        return this
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        return !b.implicitCast(type).isError
    }

    override fun clone(): UnionTypeVar {
        return UnionTypeVar(identifier, *vars.map { it.clone() }.toTypedArray())
    }

    override fun getTempVar(): UnionTypeVar = clone().apply { isTemp = true; vars.forEach { it.isTemp = true } }

    //TODO 到底需不需要入栈出栈
    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        for (v in vars) {
            val qwq = v.getMemberVar(key, accessModifier)
            if(qwq.first != null){
                return qwq
            }
        }
        return Pair(null, false)
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        for (v in vars) {
            val qwq = v.getMemberFunction(key, readOnlyParams, normalParams, accessModifier)
            if(qwq.first !is UnknownFunction){
                return qwq
            }
        }
        return Pair(UnknownFunction(key), false)
    }
}

class UnionTypeVarConcrete(identifier: String, override var value: Any?, vararg vars: Var<*>): UnionTypeVar(identifier, *vars), MCFPPValue<Any?> {

    constructor(unionTypeVar: UnionTypeVar, value: Any?): this(unionTypeVar.identifier, value, *unionTypeVar.vars.toTypedArray())

    override fun toDynamic(replace: Boolean): Var<*> {
        for (v in vars) {
            if (v is MCFPPValue<*> && v.value == value) {
                return v.toDynamic(false)
            }
        }
        val re = UnionTypeVar(identifier, *vars.toTypedArray())
        if(replace){
            replacedBy(re)
        }
        return re
    }

}