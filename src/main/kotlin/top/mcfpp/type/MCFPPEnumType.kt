package top.mcfpp.type

import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.EnumVar
import top.mcfpp.model.Member
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.Enum
import top.mcfpp.model.compound.EnumMember

open class MCFPPEnumType(
    var enum: Enum
): MCFPPType(arrayListOf(MCFPPBaseType.Object)) {

    override val typeId: TypeId get() = TypeId.Declaration("enum", enum.namespace, enum.identifier)

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
        val re = EnumVar(enum, member.value, member.identifier)
        return re to true
    }

    override fun defaultValue() = enum.getMember(0)!!

    override fun build(identifier: String, value: Any?): Var<*> = EnumVar(enum, value as EnumMember, identifier)
    override fun buildUnConcrete(identifier: String): Var<*> = EnumVar(enum, identifier)

}
