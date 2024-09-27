package top.mcfpp.model.accessor

import top.mcfpp.annotations.MNIAccessor
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.CompoundData
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor

class NativeAccessor(javaRefer: String, d: CompoundData, field: Var<*>): AbstractAccessor(field) {

    val function: NativeFunction

    init {
        function = NativeFunction("get_${field.identifier}", field.type, d.namespace)
        function.field.putVar("field", field)
        function.owner = d
        try {
            //根据JavaRefer找到类
            val clsName = javaRefer.substring(0,javaRefer.lastIndexOf('.'))
            val clazz = Class.forName(clsName)
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
                throw NoSuchMethodException("Cannot find accessor ${field.identifier} in class $clsName")
            }
        } catch (e: ClassNotFoundException) {
            LogProcessor.error("Cannot find java class: " + e.message)
        }
    }

    override fun getter(caller: CanSelectMember): Var<*> {
        function.invoke(ArrayList(), caller)
        return function.returnVar
    }

}