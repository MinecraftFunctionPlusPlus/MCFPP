package top.mcfpp.type

import top.mcfpp.model.Class
import top.mcfpp.core.lang.EnumVarConcrete
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Enum
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.core.lang.EnumVar

open class MCFPPEnumType(
    var enum: Enum
): MCFPPType(enum, listOf(MCFPPBaseType.Any)) {

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
    override fun build(identifier: String, container: FieldContainer): Var<*> = EnumVarConcrete(enum, container,0, identifier)
    override fun build(identifier: String): Var<*> = EnumVarConcrete(enum,0, identifier)
    override fun build(identifier: String, clazz: Class): Var<*> = EnumVarConcrete(enum, clazz,0, identifier)
    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = EnumVar(enum, container, identifier)
    override fun buildUnConcrete(identifier: String): Var<*> = EnumVar(enum, identifier)
    override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = EnumVar(this.enum, clazz, identifier)

}