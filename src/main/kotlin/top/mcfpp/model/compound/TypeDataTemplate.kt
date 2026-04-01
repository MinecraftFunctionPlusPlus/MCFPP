package top.mcfpp.model.compound

import top.mcfpp.Project
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.TypeDataTemplateObject
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPType

class TypeDataTemplate(var typeAs: MCFPPType, identifier: String, namespace: String = Project.currNamespace) : DataTemplate(identifier, namespace) {

    init {
        isFinal = true
    }

    override fun getType(): MCFPPDataTemplateType {
        return super.getType()
    }

    companion object{
        fun defaultConstructor(value: Var<*>, caller: TypeDataTemplateObject){
            caller.delegateVar = caller.delegateVar.assignedBy(value)
        }
    }

}