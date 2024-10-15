package top.mcfpp.model

import top.mcfpp.Project
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.core.lang.UnresolvedVar
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.UnresolvedType
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.field.NamespaceField
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.generic.GenericFunction
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPGenericParamType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
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
            val mniRegister = method.getAnnotation(MNIFunction::class.java)
            if(mniRegister != null){
                if(!Modifier.isStatic(method.modifiers)) {
                    LogProcessor.error("MNIMethod ${method.name} in class ${cls.name} must be static")
                    continue
                }
                val nf = NativeFunction(method.name, javaMethod = method)
                //解析MNIMethod注解成员
                val callerType = MCFPPType.parseFromIdentifier(mniRegister.caller, Function.field)
                nf.caller = callerType?: run {
                    LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(mniRegister.caller))
                    MCFPPBaseType.Void
                }
                mniRegister.genericType.map {
                    nf.field.putType(it, MCFPPGenericParamType(it, listOf()))
                }
                //解析MNIMethod注解成员
                val readOnlyType = mniRegister.readOnlyParams.map {
                    var qwq = it.split(" ", limit = 3)
                    if(qwq.size == 3) qwq = qwq.subList(1, 3)
                    val type = MCFPPType.parseFromIdentifier(qwq[0], Namespace.currNamespaceField)?: run {
                        LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(qwq[0]))
                        MCFPPBaseType.Any
                    }
                    qwq[1] to type to it.startsWith("static")
                }
                val normalType = mniRegister.normalParams.map {
                    var qwq = it.split(" ", limit = 3)
                    if(qwq.size == 3) qwq = qwq.subList(1, 3)
                    val type = MCFPPType.parseFromIdentifier(qwq[0], Namespace.currNamespaceField)?: run {
                        LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(qwq[0]))
                        MCFPPBaseType.Any
                    }
                    qwq[1] to type to it.startsWith("static")
                }
                val returnType = MCFPPType.parseFromIdentifier(mniRegister.returnType, Namespace.currNamespaceField)?: run {
                    LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(mniRegister.returnType))
                    MCFPPBaseType.Any
                }
                nf.returnType = returnType
                //检查method的参数s
                if(method.parameterCount != readOnlyType.size + normalType.size + 1){
                    LogProcessor.error("Method ${method.name} in class ${cls.name} has wrong parameter count")
                    continue
                }
                for(rt in readOnlyType){
                    nf.appendReadOnlyParam(rt.first.second, rt.first.first, rt.second)
                }
                for(nt in normalType){
                    nf.appendNormalParam(nt.first.second, nt.first.first, nt.second)
                }
                //有继承
                if(mniRegister.override){
                    val result = field.hasFunction(nf, true)
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