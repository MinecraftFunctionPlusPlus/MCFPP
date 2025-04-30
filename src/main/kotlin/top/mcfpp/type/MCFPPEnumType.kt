package top.mcfpp.type

import top.mcfpp.core.lang.obj.EnumVar
import top.mcfpp.core.lang.obj.EnumVarConcrete
import top.mcfpp.core.lang.Var
import top.mcfpp.model.*
import top.mcfpp.model.compound.Enum
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.Class

open class MCFPPEnumType(
    var enum: Enum
): MCFPPType(arrayListOf(MCFPPBaseType.Any)) {

    override val objectData: CompoundData
        get() = enum

    override val typeName: String
        get() = "enum(${enum.namespace}:${enum.identifier})"

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        if(!enum.members.containsKey(key)){
            return null to true
        }
        val member = enum.members[key]!!
        val re = EnumVarConcrete(enum, member.value, member.identifier)
        re.sbObject = enum.sbObject
        re.isConst = true
        re.hasAssigned = true
        return re to true
    }
    override fun build(identifier: String, container: FieldContainer): Var<*> = EnumVarConcrete(enum, 0, identifier)
    override fun build(identifier: String): Var<*> = EnumVarConcrete(enum,0, identifier)
    override fun build(identifier: String, clazz: Class): Var<*> = EnumVarConcrete(enum, 0, identifier)
    override fun build(value: Any): Var<*> = EnumVarConcrete(enum, value as Int)
    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = EnumVar(enum, identifier)
    override fun buildUnConcrete(identifier: String): Var<*> = EnumVar(enum, identifier)
    override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = EnumVar(this.enum, identifier)

}