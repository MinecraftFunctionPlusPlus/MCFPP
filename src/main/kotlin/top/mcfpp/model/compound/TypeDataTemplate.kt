package top.mcfpp.model.compound

import top.mcfpp.Project
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.TypeDataTemplateObject
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPTypeDataTemplateType

class TypeDataTemplate(var typeAs: MCFPPType, identifier: String, namespace: String = Project.currNamespace) : DataTemplate(identifier, namespace) {

    init {
        isFinal = true
    }

    override fun getType(): MCFPPDataTemplateType {
        return MCFPPTypeDataTemplateType(this)
    }

    companion object{
        fun defaultConstructor(value: Var<*>, caller: TypeDataTemplateObject){
            top.mcfpp.analysis.StorageAccess.ensure(caller)
            val delegated = top.mcfpp.analysis.StorageAccess.view(caller, caller.templateType.let {
                (it as TypeDataTemplate).typeAs
            })
            if (!delegated.isError) top.mcfpp.analysis.StorageAccess.write(delegated, value)
        }
    }

}
