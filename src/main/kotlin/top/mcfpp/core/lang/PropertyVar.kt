package top.mcfpp.core.lang

import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.Property
import top.mcfpp.type.MCFPPType

class PropertyVar(val property: Property, var field: Var<*>, val caller: Var<*>): Var<PropertyVar>(field.identifier) {

    init {
        property.parent = caller
    }

    override var type: MCFPPType = field.type

    override fun explicitCast(type: MCFPPType): Var<*> {
        throw IllegalStateException("Property cannot be casted")
    }

    override fun canExplicitCast(type: MCFPPType): Boolean {
        return field.canExplicitCast(type)
    }

    override fun implicitCast(type: MCFPPType): Var<*> {
        throw IllegalStateException("Property cannot be casted")
    }

    override fun canImplicitCast(type: MCFPPType): Boolean {
        return field.canImplicitCast(type)
    }

    override fun doAssignedBy(b: Var<*>): PropertyVar {
        val qwq = property.setter(caller, field, b)
        qwq.parent = caller
        this.field = qwq
        return this
    }

    override fun clone(): PropertyVar = this

    override fun getTempVar(): PropertyVar = this

    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return field.getMemberVar(key, accessModifier)
    }

    fun getter(): Var<*> {
        return property.getter(caller, field)
    }

    fun setter(b: Var<*>){
        property.setter(caller, field, b)
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return field.getMemberFunction(key, readOnlyArgs, normalArgs, accessModifier)
    }

    override fun replacedBy(v: Var<*>) {
        field.replacedBy((v as PropertyVar).field)
    }


}