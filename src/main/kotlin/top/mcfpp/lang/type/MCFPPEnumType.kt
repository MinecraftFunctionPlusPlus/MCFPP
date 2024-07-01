package top.mcfpp.lang.type

import top.mcfpp.lang.EnumVarConcrete
import top.mcfpp.lang.Var
import top.mcfpp.model.Class
import top.mcfpp.model.Enum
import top.mcfpp.model.Member

open class MCFPPEnumType(
    var enum: Enum
):MCFPPType(enum , listOf(MCFPPBaseType.Any)) {
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
}