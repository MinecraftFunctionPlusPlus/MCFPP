package top.mcfpp.model.accessor

import top.mcfpp.annotations.MNIAccessor
import top.mcfpp.annotations.MNIMutator
import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.CompoundData
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.util.LogProcessor

class NativeMutator(javaRefer: String, d: CompoundData, field: Var<*>): AbstractMutator(field) {

    val function: NativeFunction = NativeFunction("set_${field.identifier}", field.type, d.namespace)

    init {
        function.field.putVar("field", field)
        function.appendNormalParam(field.type, "value")
        function.field.putVar("value", field.type.build("value"))
        function.owner = d
        try {
            //根据JavaRefer找到类
            val clsName = javaRefer.substring(0,javaRefer.lastIndexOf('.'))
            val clazz = Class.forName(clsName)
            val methods = clazz.methods
            var hasFind = false
            for(method in methods){
                val mniAccessor = method.getAnnotation(MNIMutator::class.java) ?: continue
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

    override fun setter(caller: CanSelectMember, b: Var<*>): Var<*> {
        function.invoke(arrayListOf(b), caller)
        return function.returnVar
    }
}