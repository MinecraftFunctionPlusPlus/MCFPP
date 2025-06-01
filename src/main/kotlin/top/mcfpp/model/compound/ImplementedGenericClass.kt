package top.mcfpp.model.compound

import top.mcfpp.Project
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.util.LogProcessor

class ImplementedGenericClass(identifier: String, namespace: String = Project.currNamespace, val readOnlyArgs: List<Var<*>>, parentGenericClass: GenericClass) : CompiledGenericClass(identifier, namespace, parentGenericClass, readOnlyArgs.map { it as MCFPPValue<*> }) {

    init {
        extends(parentGenericClass.compile(readOnlyArgs))
        if(parentGenericClass.compiledClasses.containsKey(readOnlyArgs)){
            LogProcessor.error("Duplicate generic class implementation: $identifier ${readOnlyArgs.map { (it as MCFPPValue<*>).value }.joinToString(",")}")
        }
        parentGenericClass.compiledClasses[readOnlyArgs] = this
    }

}