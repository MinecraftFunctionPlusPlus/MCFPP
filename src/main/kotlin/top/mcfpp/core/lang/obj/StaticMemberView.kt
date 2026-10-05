package top.mcfpp.core.lang.obj

import top.mcfpp.core.lang.ConcreteVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Member
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPObjectDataTemplateType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

class StaticMemberView(type: CanSelectMember, identifier: String = TempPool.getVarIdentify()) :
    ConcreteVar<StaticMemberView, CanSelectMember>(identifier, type) {

    override var type: MCFPPType = MCFPPPrivateType.StaticMemberViewType

    override fun toDynamic(replace: Boolean): Var<*> {
        return this
    }

    override fun doAssignedBy(b: Var<*>): StaticMemberView {
        LogProcessor.error("Cannot assign value to object type variable")
        return this
    }

    override fun clone(): StaticMemberView = this

    override fun getTempVar(): StaticMemberView = this

    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        if (value is MCFPPObjectDataTemplateType) {
            val template = (value as MCFPPObjectDataTemplateType).template as ObjectDataTemplate
            if (template.deferredFields.containsKey(key)) {
                LogProcessor.error("Cannot infer object field '$key' before its initializer is evaluated (forward or self reference)")
                return UnknownVar(key).apply { isError = true } to true
            }
        }
        return value.getMemberVar(key, accessModifier).apply {
            first?.parent = value
            if(value is MCFPPObjectDataTemplateType) {
                first?.nbtPath = ((value as MCFPPObjectDataTemplateType).template as ObjectDataTemplate).nbtPath.memberIndex(key)
            }
        }
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return value.getMemberFunction(key, readOnlyArgs, normalArgs, accessModifier)
    }

    override fun getAccess(function: Function): Member.AccessModifier {
        return value.getAccess(function)
    }

    override fun replaceMemberVar(v: Var<*>) {
        value.replaceMemberVar(v)
    }

}
