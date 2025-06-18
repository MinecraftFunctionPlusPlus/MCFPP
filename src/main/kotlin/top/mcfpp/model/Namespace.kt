package top.mcfpp.model

import top.mcfpp.Project
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.ClassPointer
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.model.compound.UnsolvedClass
import top.mcfpp.model.compound.UnsolvedTemplate
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.field.IField
import top.mcfpp.model.field.NamespaceField
import top.mcfpp.model.field.SimpleFieldWithType
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPGenericParamType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper.splitMNIParam
import top.mcfpp.util.StringHelper.splitNamespaceID
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.io.Serializable
import java.lang.reflect.Modifier

class Namespace(val identifier: String): Serializable, FieldContainer {

    val field : NamespaceField = NamespaceField()

    override val prefix get() =  "namespace_$identifier"

    /**
     * 合并命名空间
     */
    fun merge(namespace: Namespace, force: Boolean = false){
        namespace.field.forEachFunction { field.addFunction(it, force) }
        namespace.field.forEachClass { field.addClass(it.identifier, it, force) }
        namespace.field.forEachInterface { field.addInterface(it.identifier, it, force) }
        namespace.field.forEachTemplate { field.addTemplate(it.identifier, it, force) }
    }

    fun resolve(){
        field.forEachClass { c ->
            run {
                c.field.forEachVar { resolveVar(it) }
                c.constructors.forEach { constructor -> run{
                    constructor.normalParams.forEach {
                        it.type.tryResolve()
                    }
                } }
                c.field.forEachFunction { resolveFunction(it) }
            }
        }
        field.forEachTemplate { t ->
            run {
                t.field.forEachVar { resolveVar(it) }
                t.field.forEachFunction { resolveFunction(it) }
            }
        }
        field.forEachObject { o ->
            run {
                o.field.forEachVar { resolveVar(it) }
                o.field.forEachFunction { resolveFunction(it) }
            }
        }
        field.forEachFunction { resolveFunction(it) }
    }

    private fun resolveFunction(f: Function){
        for (np in f.normalParams){
            np.type.tryResolve()
            f.field.putVar(np.identifier, np.buildVar())
        }
        if(f is GenericFunction){
            for (rp in f.readOnlyParams){
                rp.type.tryResolve()
                f.field.putVar(rp.identifier, rp.buildVar())
            }
        }
        if(f is NativeFunction){
            for (rp in f.readOnlyParams){
                rp.type.tryResolve()
                f.field.putVar(rp.identifier, rp.buildVar())
            }
        }
        f.returnType.tryResolve()
        f.buildReturnVar(f.returnType)
    }

    private fun resolveVar(v: Var<*>){
        v.type.tryResolve()
        if(v is DataTemplateObject && v.templateType is UnsolvedTemplate){
            v.templateType.resolve()
        }
        if(v is ClassPointer && v.clazz is UnsolvedClass){
            (v.clazz as UnsolvedClass).resolve()
        }
    }

    fun injectedBy(cls: Class<*>){
        val l = Project.currNamespace
        Project.currNamespace = this.identifier
        val methods = cls.methods
        //获取import方法
        val simpleFieldWithType = SimpleFieldWithType.getTypeScope()
        for(method in methods){
            if(method.name == "importToMNI"){
                if(method.parameterCount != 0){
                    LogProcessor.error("Method importToMNI in class ${cls.name} must have no parameter")
                }else if(!Modifier.isStatic(method.modifiers)){
                    LogProcessor.error("Method importToMNI in class ${cls.name} must be static")
                }else{
                    @Suppress("UNCHECKED_CAST") val imports = method.invoke(null) as Array<String>
                    for(import in imports){
                        val nsp = import.splitNamespaceID()
                        val qwq = nsp.first?.let { GlobalField.getNamespace(nsp.first!!)}
                        if(qwq == null){
                            LogProcessor.error("Namespace '${nsp.first}' not found")
                            continue
                        }
                        if(nsp.second == "*"){
                            qwq.field.forEachType {
                                val d = simpleFieldWithType.putType(it.simpleName, it)
                                if(!d){
                                    simpleFieldWithType.putType(it.simpleName, it, true)
                                    LogProcessor.error("Already have import '${it.simpleName}'")
                                }
                            }
                        }else{
                            val owo = qwq.field.getDeclaredType(nsp.second)?.getType()
                            if(owo == null){
                                LogProcessor.error("Declared type '$import' not found")
                                continue
                            }
                            val result = simpleFieldWithType.putType(owo.simpleName, owo)
                            if(!result){
                                simpleFieldWithType.putType(owo.simpleName, owo, true)
                                LogProcessor.error("Already have import '${owo.simpleName}")
                            }
                        }
                    }
                }
                break
            }
        }
        //获取所有带有注解MNIMethod的Java方法
        for(method in methods){
            val mniRegister = method.getAnnotation(MNIFunction::class.java)
            if(mniRegister != null){
                if(!Modifier.isStatic(method.modifiers)) {
                    LogProcessor.error("MNIMethod ${method.name} in class ${cls.name} must be static")
                    continue
                }
                val nf = NativeFunction(method.name, javaMethod = method)
                //解析MNIMethod注解成员
                val callerType = MCFPPType.parseFromString(mniRegister.caller, simpleFieldWithType)
                nf.caller = callerType?: run {
                    LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(mniRegister.caller) + " in method ${method.name} in MNIClass ${cls.name}")
                    MCFPPPrivateType.Void
                }
                mniRegister.genericType.map {
                    nf.field.putType(it, MCFPPGenericParamType(it, arrayListOf()))
                }
                //解析MNIMethod注解成员
                val readOnlyType = mniRegister.readOnlyParams.map {
                    val qwq = it.splitMNIParam().first.split(" ", limit = 2)
                    val type = MCFPPType.parseFromString(qwq.last(), simpleFieldWithType)?: run {
                        LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(qwq[0]) + " in method ${method.name} in MNIClass ${cls.name}")
                        MCFPPBaseType.Any
                    }
                    type to it.startsWith("static")
                }
                val normalType = mniRegister.normalParams.map {
                    val qwq = it.splitMNIParam().first.split(" ", limit = 2)
                    val type = MCFPPType.parseFromString(qwq.last(), simpleFieldWithType)?: run {
                        LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(qwq[0]) + " in method ${method.name} in MNIClass ${cls.name}")
                        MCFPPBaseType.Any
                    }
                    type to it.startsWith("static")
                }
                val returnType = MCFPPType.parseFromString(mniRegister.returnType, simpleFieldWithType)?: run {
                    LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(mniRegister.returnType) + " in method ${method.name} in MNIClass ${cls.name}")
                    MCFPPBaseType.Any
                }
                nf.returnType = returnType
                //检查method的参数s
                if(method.parameterCount != readOnlyType.size + normalType.size + 1){
                    LogProcessor.error("Method ${method.name} in class ${cls.name} has wrong parameter count")
                    continue
                }
                for(rt in readOnlyType){
                    nf.appendReadOnlyParam(rt.first, "p${nf.paramCount()}", rt.second)
                }
                for(nt in normalType){
                    nf.appendNormalParam(nt.first, "p${nf.paramCount()}", nt.second)
                }
                //有继承
                if(mniRegister.override){
                    val result = field.hasFunction(nf, true)
                    if(!result){
                        LogProcessor.error("Method ${nf.identifier} in class ${cls.name} overrides nothing")
                        continue
                    }else{
                        nf.isOverride = true
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

    override fun toString(): String {
        return "namespace($identifier)"
    }

    companion object {
        val currNamespaceField: NamespaceField
            get() = GlobalField.localNamespaces[Project.currNamespace]?.field?:
                GlobalField.importedLibNamespaces[Project.currNamespace]?.field?:
                GlobalField.stdNamespaces[Project.currNamespace]!!.field
    }
}