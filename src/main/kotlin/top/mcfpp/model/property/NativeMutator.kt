package top.mcfpp.model.property

import top.mcfpp.Project
import top.mcfpp.annotations.MNIMutator
import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.util.LogProcessor

class NativeMutator: AbstractMutator {

    var function: NativeFunction

    constructor(function: NativeFunction){
        this.function = function
    }

    constructor(javaRefer: String, d: CompoundData, field: Var<*>) {
        function = NativeFunction("set_${field.identifier}", d.namespace)
        function.returnType = field.type
        function.field.putVar("field", field)
        function.appendNormalParam(field.type, "value")
        function.field.putVar("value", field.type.build("value"))
        function.owner = d
        try {
            //根据JavaRefer找到类
            val clazz = Project.classLoader.loadClass(javaRefer)
            val methods = clazz.methods
            var hasFind = false
            for(method in methods){
                val mniMutator = method.getAnnotation(MNIMutator::class.java) ?: continue
                if(mniMutator.value == field.identifier){
                    hasFind = true
                    function.javaMethod = method
                    break
                }
            }
            if(!hasFind){
                LogProcessor.error("Cannot find mutator ${field.identifier} in class $javaRefer")
            }
        } catch (e: ClassNotFoundException) {
            LogProcessor.error("Cannot find java class: $javaRefer")
        }
    }

    override fun setter(caller: CanSelectMember, field: Var<*>, b: Var<*>): Var<*>{
        function.invoke(arrayListOf(b), caller)
        return function.returnVar
    }
}

class AnonymousNativeMutator(val native: (CanSelectMember, Var<*>)->Var<*>): AbstractMutator(){
    override fun setter(caller: CanSelectMember, field: Var<*>, b: Var<*>): Var<*> {
        return native(caller, b)
    }

}