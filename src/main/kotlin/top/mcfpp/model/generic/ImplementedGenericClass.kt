package top.mcfpp.model.generic

import top.mcfpp.Project
import top.mcfpp.lang.Var
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.model.Class
import top.mcfpp.util.LogProcessor

class ImplementedGenericClass(identifier: String, namespace: String = Project.currNamespace, val readOnlyArgs: List<Var<*>>, val parentGenericClass: GenericClass) : Class(identifier, namespace) {

    init {
        extends(parentGenericClass.compile(readOnlyArgs))
        if(parentGenericClass.compiledClasses.containsKey(readOnlyArgs)){
            LogProcessor.error("Duplicate generic class implementation: $identifier ${readOnlyArgs.map { (it as MCFPPValue<*>).value }.joinToString(",")}")
        }
        parentGenericClass.compiledClasses[readOnlyArgs] = this
    }

}