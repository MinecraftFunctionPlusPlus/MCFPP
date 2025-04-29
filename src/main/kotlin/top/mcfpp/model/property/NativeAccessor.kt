package top.mcfpp.model.property

import top.mcfpp.Project
import top.mcfpp.annotations.MNIAccessor
import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.util.LogProcessor

class NativeAccessor: AbstractAccessor {

    val function: NativeFunction

    constructor(function: NativeFunction){
        this.function = function
    }

    constructor(javaRefer: String, d: CompoundData, field: Var<*>) {
        function = NativeFunction("get_${field.identifier}", d.namespace)
        function.returnType = field.type
        function.owner = d
        try {
            //根据JavaRefer找到类
            val clazz = Project.classLoader.loadClass(javaRefer)
            val methods = clazz.methods
            var hasFind = false
            for(method in methods){
                val mniAccessor = method.getAnnotation(MNIAccessor::class.java) ?: continue
                if(mniAccessor.name == field.identifier){
                    hasFind = true
                    function.javaMethod = method
                    break
                }
            }
            if(!hasFind){
                LogProcessor.error("Cannot find accessor ${field.identifier} in class $javaRefer")
            }
        } catch (e: ClassNotFoundException) {
            LogProcessor.error("Cannot find java class: $javaRefer")
        }
    }

    override fun getter(caller: CanSelectMember, field: Var<*>): Var<*> {
        function.invoke(ArrayList(), caller)
        return function.returnVar
    }

}