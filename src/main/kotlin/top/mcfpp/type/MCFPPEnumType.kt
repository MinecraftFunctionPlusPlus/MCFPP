package top.mcfpp.type

import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.EnumVar
import top.mcfpp.core.lang.obj.EnumVarConcrete
import top.mcfpp.model.Member
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.Enum
import top.mcfpp.model.compound.EnumMember

open class MCFPPEnumType(
    var enum: Enum
): MCFPPType(arrayListOf(MCFPPBaseType.Any)) {

    override val objectData: CompoundData
        get() = enum

    override val typeName: String
        get() = "enum(${enum.namespace}:${enum.identifier})"

    override val simpleName: String
        get() = enum.identifier

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

    override fun defaultValue() = enum.getMember(0)!!

    override fun build(identifier: String, value: Any?): Var<*> = EnumVarConcrete(enum, value as EnumMember, identifier)
    override fun buildUnConcrete(identifier: String): Var<*> = EnumVar(enum, identifier)

}