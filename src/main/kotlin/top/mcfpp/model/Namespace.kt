package top.mcfpp.model

import top.mcfpp.Project
import top.mcfpp.annotations.MNIRegister
import top.mcfpp.lang.UnresolvedVar
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lang.type.UnresolvedType
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.field.NamespaceField
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.generic.GenericFunction
import top.mcfpp.util.LogProcessor
import java.io.Serializable
import java.lang.Class
import java.lang.reflect.Modifier

class Namespace(val identifier: String): Serializable {

    val field : NamespaceField

    init {
        this.field = NamespaceField()
    }

    /**
     * 合并命名空间
     */
    fun merge(namespace: Namespace){
        namespace.field.forEachFunction { field.addFunction(it, false) }
        namespace.field.forEachClass { field.addClass(it.identifier, it) }
        namespace.field.forEachInterface { field.addInterface(it.identifier, it) }
        namespace.field.forEachTemplate { field.addTemplate(it.identifier, it) }
    }

    fun resolve(){
        field.forEachClass { c ->
            run {
                for (v in c.field.allVars){
                    if(v is UnresolvedVar){
                        c.field.putVar(c.identifier, v.resolve(c), true)
                    }
                }
                c.constructors.forEach { constructor -> run{
                    constructor.normalParams.forEach {
                        if(it.type is UnresolvedType){
                            it.type = (it.type as UnresolvedType).resolve(constructor.field)
                        }
                    }
                } }
                c.field.forEachFunction { resolveFunction(it) }
            }
        }
        field.forEachTemplate { t ->
            run {
                for (v in t.field.allVars){
                    if(v is UnresolvedVar){
                        t.field.putVar(t.identifier, v.resolve(t), true)
                    }
                }
                t.field.forEachFunction { resolveFunction(it) }
            }
        }
        field.forEachObject { o ->
            run {
                for (v in o.field.allVars){
                    if(v is UnresolvedVar){
                        o.field.putVar(o.identifier, v.resolve(o), true)
                    }
                }
                o.field.forEachFunction { resolveFunction(it) }
            }
        }
        field.forEachFunction { resolveFunction(it) }
    }

    private fun resolveFunction(f: Function){
        for ((index, np) in f.normalParams.withIndex()){
            if(np.type is UnresolvedType){
                f.normalParams[index].type = (np.type as UnresolvedType).resolve(f.field)
            }
            f.field.putVar(np.identifier, np.buildVar())
        }
        if(f is GenericFunction){
            for ((index, rp) in f.readOnlyParams.withIndex()){
                if(rp.type is UnresolvedType){
                    f.readOnlyParams[index].type = (rp.type as UnresolvedType).resolve(f.field)
                }
                f.field.putVar(rp.identifier, rp.buildVar())
            }
        }
        if(f is NativeFunction){
            for ((index, rp) in f.readOnlyParams.withIndex()){
                if(rp.type is UnresolvedType){
                    f.readOnlyParams[index].type = (rp.type as UnresolvedType).resolve(f.field)
                }
                f.field.putVar(rp.identifier, rp.buildVar())
            }
        }
        if(f.returnType is UnresolvedType){
            f.returnType = (f.returnType as UnresolvedType).resolve(f.field)
        }
        f.buildReturnVar(f.returnType)
    }


    fun getNativeFunctionFromClass(cls: Class<*>){
        val l = Project.currNamespace
        Project.currNamespace = this.identifier
        //获取所有带有注解MNIMethod的Java方法
        val methods = cls.methods
        for(method in methods){
            val mniRegister = method.getAnnotation(MNIRegister::class.java)
            if(mniRegister != null){
                if(!Modifier.isStatic(method.modifiers)) {
                    LogProcessor.error("MNIMethod ${method.name} in class ${cls.name} must be static")
                    continue
                }
                //解析MNIMethod注解成员
                val readOnlyType = mniRegister.readOnlyParams.map {
                    val qwq = it.split(" ", limit = 2)
                    qwq[1] to MCFPPType.parseFromIdentifier(qwq[0], Namespace.currNamespaceField)
                }
                val normalType = mniRegister.normalParams.map {
                    val qwq = it.split(" ", limit = 2)
                    qwq[1] to MCFPPType.parseFromIdentifier(qwq[0], Namespace.currNamespaceField)
                }
                val returnType = MCFPPType.parseFromIdentifier(mniRegister.returnType, Namespace.currNamespaceField)
                //检查method的参数s
                if(method.parameterCount != readOnlyType.size + normalType.size + 1){
                    LogProcessor.error("Method ${method.name} in class ${cls.name} has wrong parameter count")
                    continue
                }
                val nf = NativeFunction(method.name, returnType, javaMethod = method)
                nf.caller = mniRegister.caller
                for(rt in readOnlyType){
                    nf.appendReadOnlyParam(rt.second, rt.first)
                }
                for(nt in normalType){
                    nf.appendNormalParam(nt.second, nt.first)
                }
                //有继承
                if(mniRegister.override){
                    val result = field.hasFunction(nf)
                    if(!result){
                        LogProcessor.error("Method ${nf.identifier} in class ${cls.name} overrides nothing")
                        continue
                    }else{
                        this.field.addFunction(nf, true)
                    }
                }else {
                    val result = this.field.addFunction(nf, false)
                    if(!result){
                        LogProcessor.warn("Duplicate method ${nf.identifier} in class ${cls.name}. If you want to override it, please add @MNIRegister(override = true) to the method")
                        this.field.addFunction(nf, true)
                    }
                }
            }
        }
        Project.currNamespace = l
    }

    companion object {
        val currNamespaceField: NamespaceField
            get() = GlobalField.localNamespaces[Project.currNamespace]?.field?:
                GlobalField.importedLibNamespaces[Project.currNamespace]?.field?:
                GlobalField.stdNamespaces[Project.currNamespace]!!.field
    }
}