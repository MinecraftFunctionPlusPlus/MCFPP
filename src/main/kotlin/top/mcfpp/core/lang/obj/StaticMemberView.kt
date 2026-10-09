package top.mcfpp.core.lang.obj

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.PropertyVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Member
import top.mcfpp.model.compound.ObjectCompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

class StaticMemberView(val declaration: CanSelectMember, identifier: String = TempPool.getVarIdentify()) :
    Var<StaticMemberView>(identifier) {

    override var type: MCFPPType = MCFPPPrivateType.StaticMemberViewType

    override fun doAssignedBy(b: Var<*>): StaticMemberView {
        LogProcessor.error("Cannot assign value to object type variable")
        return this
    }

    override fun clone(): StaticMemberView = this

    override fun getTempVar(): StaticMemberView = this

    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val objectOwner = (declaration as? MCFPPType)?.objectData as? ObjectCompoundData
        (objectOwner as? DataTemplate)?.let(top.mcfpp.Project::prepareObjectInitializer)
        if (objectOwner is DataTemplate) {
            val template = objectOwner
            if (template.deferredFields.containsKey(key)) {
                LogProcessor.error("Cannot infer object field '$key' before its initializer is evaluated (forward or self reference)")
                return UnknownVar(key).apply { isError = true } to true
            }
        }
        return declaration.getMemberVar(key, accessModifier).apply {
            first?.parent = declaration
            if(objectOwner != null) {
                first?.let { member ->
                    val field = if (member is PropertyVar) member.field else member
                    field.nbtPath = objectOwner.nbtPath.memberIndex(key)
                    StorageAccess.bindStaticField(field)
                }
            }
        }
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        ((declaration as? MCFPPType)?.objectData as? DataTemplate)?.let(top.mcfpp.Project::prepareObjectInitializer)
        return declaration.getMemberFunction(key, readOnlyArgs, normalArgs, accessModifier)
    }

    override fun getAccess(function: Function): Member.AccessModifier {
        return declaration.getAccess(function)
    }

    override fun replaceMemberVar(v: Var<*>) {
        declaration.replaceMemberVar(v)
    }

}
