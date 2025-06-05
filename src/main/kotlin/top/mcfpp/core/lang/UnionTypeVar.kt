package top.mcfpp.core.lang

import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
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
 * ```
 *
 * 在赋值给其他变量的时候，联合变量将会尝试隐式转换为目标变量，根据联合类型的声明顺序来尝试进行隐式转换
 *
 * 在被其他变量赋值的时候，联合变量将会尝试寻找一个类型相同的变量进行赋值，如果找不到，将会尝试寻找一个能隐式转换为目标变量的变量进行赋值。如果依然
 * 找不到，则会寻找一个能赋值给目标变量的变量进行赋值。寻找的顺序和联合类型的声明顺序一致。
 */
open class UnionTypeVar: Var<UnionTypeVar> {

    protected lateinit var frontVar: Var<*>

    override lateinit var type: MCFPPType

    protected constructor(identifier: String): super(identifier)

    @Suppress("LeakingThis")
    constructor(identifier: String, vararg type: MCFPPType): super(identifier) {
        frontVar = type[0].build(identifier)
        this.type = MCFPPUnionType(*type)
    }

    @Suppress("LeakingThis")
    constructor(v: UnionTypeVar): super(v) {
        frontVar = v.frontVar.clone()
        type = v.type
    }

    val unionTypes: Array<out MCFPPType>
        get() = (type as MCFPPUnionType).types

    override var parent: CanSelectMember?
        get() = super.parent
        set(value) {
            super.parent = value
            frontVar.parent = value
        }

    override fun implicitCast(type: MCFPPType): Var<*> {
        if(type == this.type) return this
        val v = frontVar.implicitCast(type)
        if(!v.isError){
            return v
        }
        //寻找类型相同的变量
        for (t in unionTypes) {
            if (t == type) {
                return t.buildUnConcrete(identifier)
            }
        }
        //寻找能强制转换为此变量的变量
        for (t in unionTypes) {
            if(t.isSubOf(type)){
                return t.buildUnConcrete(identifier)
            }
        }
        return super.implicitCast(type)
    }

    override fun canImplicitCast(type: MCFPPType): Boolean {
        return frontVar.canImplicitCast(type) || unionTypes.any { it.isSubOf(type) } || super.canImplicitCast(type)
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun explicitCast(type: MCFPPType): Var<*> {
        if(type == this.type) {
            LogProcessor.warn(TextTranslator.REDUNDANT_CAST_WARN.translate(this.type.typeName, type.typeName))
            return this
        }
        if(type == frontVar.type) return frontVar
        val v = frontVar.explicitCast(type)
        if(!v.isError){
            return v
        }
        //寻找类型相同的变量
        for (t in unionTypes) {
            if (t == type) {
                return t.buildUnConcrete(identifier)
            }
        }
        //寻找能强制转换为此变量的变量
        for (t in unionTypes) {
            if(t.isSubOf(type)){
                return t.buildUnConcrete(identifier)
            }
        }
        return super.explicitCast(type)
    }

    override fun canExplicitCast(type: MCFPPType): Boolean {
        return frontVar.canExplicitCast(type) || unionTypes.any { it.isSubOf(type) }
    }

    override fun doAssignedBy(b: Var<*>): UnionTypeVar {
        if(frontVar.canAssignedBy(b)){
            frontVar = frontVar.assignedBy(b)
            if(frontVar is MCFPPValue<*>){
                return UnionTypeVarConcrete(this, (frontVar as MCFPPValue<*>).value)
            }
            return this
        }
        //寻找能赋值给此变量的变量
        for (t in unionTypes) {
            val temp = t.build(identifier)
            if(temp.canAssignedBy(b)){
                frontVar = temp.assignedBy(b)
                if(frontVar is MCFPPValue<*>){
                    return UnionTypeVarConcrete(this, (frontVar as MCFPPValue<*>).value)
                }
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
        return UnionTypeVar(this)
    }

    override fun getTempVar(): UnionTypeVar {
        return clone().apply {
            isTemp = true
            frontVar = frontVar.assignedBy(this@UnionTypeVar.frontVar)
        }
    }

    override fun storeToStack() {
        frontVar.storeToStack()
    }

    override fun getFromStack() {
        frontVar.getFromStack()
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return frontVar.getMemberVar(key, accessModifier)
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return frontVar.getMemberFunction(key, readOnlyArgs, normalArgs, accessModifier)
    }
}

class UnionTypeVarConcrete: UnionTypeVar, MCFPPValue<Any?> {

    override var value: Any?
    constructor(identifier: String, value: Any?, vararg types: MCFPPType): super(identifier){
        frontVar = types[0].build(identifier, value)
        this.type = MCFPPUnionType(*types)
        this.value = value
    }

    constructor(identifier: String, type: MCFPPType, value: Any?, vararg types: MCFPPType): super(identifier){
        frontVar = type.build(identifier, value)
        this.type = MCFPPUnionType(*types)
        this.value = value
    }

    constructor(unionTypeVar: UnionTypeVar, value: Any?): super(unionTypeVar){
        this.value = value
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        val re = UnionTypeVar(identifier, *unionTypes)
        if(replace){
            replacedBy(re)
        }
        return re
    }

    override fun doAssignedBy(b: Var<*>): UnionTypeVar {
        if(frontVar.canAssignedBy(b)){
            frontVar = frontVar.assignedBy(b)
            if(frontVar is MCFPPValue<*>){
                value = (frontVar as MCFPPValue<*>).value
                return this
            }else{
                return UnionTypeVar(this)
            }
        }
        //寻找能赋值给此变量的变量
        for (t in unionTypes) {
            val temp = t.build(identifier)
            if(temp.canAssignedBy(b)){
                frontVar = temp.assignedBy(b)
                if(frontVar is MCFPPValue<*>){
                    value = (frontVar as MCFPPValue<*>).value
                    return this
                }else{
                    return UnionTypeVar(this)
                }
            }
        }
        LogProcessor.error("Cannot assign ${b.type} to $type")
        return this
    }

    override fun clone(): UnionTypeVar {
        return UnionTypeVarConcrete(this, value)
    }


    override fun implicitCast(type: MCFPPType): Var<*> {
        val v = frontVar.implicitCast(type)
        if(!v.isError){
            return v
        }
        //寻找类型相同的变量
        for (t in unionTypes) {
            if (t == type) {
                return t.build(identifier)
            }
        }
        //寻找能强制转换为此变量的变量
        for (t in unionTypes) {
            if(t.isSubOf(type)){
                return t.build(identifier)
            }
        }
        return super.implicitCast(type)
    }


    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun explicitCast(type: MCFPPType): Var<*> {
        if(type == frontVar.type) return frontVar
        val v = frontVar.explicitCast(type)
        if(!v.isError){
            return v
        }
        //寻找类型相同的变量
        for (t in unionTypes) {
            if (t == type) {
                return t.build(identifier)
            }
        }
        //寻找能强制转换为此变量的变量
        for (t in unionTypes) {
            if(t.isSubOf(type)){
                return t.build(identifier)
            }
        }
        return super.explicitCast(type)
    }


}