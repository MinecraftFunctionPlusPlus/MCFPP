package top.mcfpp.model

import top.mcfpp.Project
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.io.info.MacroFunctionInfo
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.compound.UnsolvedTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.mni.NativeSignatureValidator
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.scope.NamespaceScope
import top.mcfpp.model.scope.SimpleScopeWithType
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPGenericParamType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPNotCompiledGenericType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper.splitMNIParam
import top.mcfpp.util.StringHelper.splitNamespaceID
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.io.Serializable
import java.lang.reflect.Modifier

class Namespace(val identifier: String): Serializable, FieldContainer {

    val scope : NamespaceScope = NamespaceScope(identifier)

    // Libraries are read before source declarations are complete. Keep only reflection metadata here;
    // MacroLoadingSnapshot.prepare projects it into the declaration phase's shared immutable catalog.
    private val macros = ArrayList<MacroFunctionInfo>()

    /** Project-owned discovery and source requests; processors run only at the declaration barrier. */
    internal val templateProcessors = top.mcfpp.mni.expand.TemplateProcessorRegistry(identifier)

    @JvmOverloads
    fun registerTemplateProcessor(annotation: String, className: String, loader: ClassLoader = Project.classLoader): Boolean =
        templateProcessors.register(annotation, className, loader)

    fun registerTemplateProcessor(annotation: String, processorClass: Class<*>): Boolean =
        templateProcessors.register(annotation, processorClass)

    /** Raw compiler relation; restoration must not replay discovery or duplicate diagnostics. */
    internal val journalMacroDeclarations: ArrayList<MacroFunctionInfo> get() = macros

    internal fun checkpointMacroDeclarations(): top.mcfpp.model.scope.NamespaceRelationCheckpoint<MacroFunctionInfo> =
        top.mcfpp.model.scope.NamespaceRelationJournal.list({ macros }, {}, { it is MacroFunctionInfo })

    internal fun macroDeclarations(): List<MacroFunctionInfo> = java.util.Collections.unmodifiableList(ArrayList(macros))

    internal fun restoreMacroDeclarations(declarations: Collection<MacroFunctionInfo>) {
        for (declaration in declarations) {
            val previous = macros.firstOrNull { it.methodDescriptor == declaration.methodDescriptor }
            if (previous == null) macros.add(declaration)
            else if (previous != declaration) LogProcessor.error("Conflicting macro library descriptors: ${declaration.methodDescriptor}")
        }
    }

    /** Class discovery uses the same configured loader as legacy MNI and library restoration. */
    @JvmOverloads
    fun injectedBy(className: String, loader: ClassLoader = Project.classLoader) {
        injectedBy(Class.forName(className, false, loader))
    }

    override val prefix get() =  "namespace_$identifier"

    /**
     * 合并命名空间
     */
    fun merge(namespace: Namespace, force: Boolean = false){
        restoreMacroDeclarations(namespace.macroDeclarations())
        if (namespace.identifier == identifier) templateProcessors.mergeFrom(namespace.templateProcessors)
        namespace.scope.forEachFunction { scope.addFunction(it, force) }
        namespace.scope.forEachTemplate {
            if (!scope.addTemplate(it.identifier, it, force))
                LogProcessor.error("Template has been defined: ${it.identifier} in namespace $identifier")
        }
        namespace.scope.forEachObject {
            if (!scope.addObject(it.identifier, it, force))
                LogProcessor.error("Object has been defined: ${it.identifier} in namespace $identifier")
        }
        namespace.scope.forEachEnum {
            if (!scope.addEnum(it.identifier, it, force))
                LogProcessor.error("Enum has been defined: ${it.identifier} in namespace $identifier")
        }
        namespace.scope.resolvedAliases().forEach { (name, target) ->
            if (!scope.putResolvedAlias(name, target, force))
                LogProcessor.error("Type alias has been defined: $name in namespace $identifier")
        }
    }

    fun resolve(){
        scope.forEachTemplate { t ->
            run {
                t.scope.forEachVar { resolveVar(it) }
                t.scope.forEachFunction { resolveFunction(it) }
                if(t.companionObject != null){
                    //find companion object
                    top.mcfpp.model.compound.pairCompanion(t, scope.getObject(t.identifier) as? DataTemplate)
                }
            }
        }
        scope.forEachObject { o ->
            run {
                o.scope.forEachVar { resolveVar(it) }
                o.scope.forEachFunction { resolveFunction(it) }
                if(o is DataTemplate) {
                    o.companionObject = o
                }
            }
        }
        scope.forEachFunction { resolveFunction(it) }
        //继承关系处理
        scope.forEachTemplate { if (it.parentID.isEmpty() || it.parent.isNotEmpty()) it.flatExtends() }
        scope.forEachObject { if(it is ObjectDataTemplate && (it.parentID.isEmpty() || it.parent.isNotEmpty())) it.flatExtends() }
    }

    private fun resolveFunction(f: Function){
        for (np in f.normalParams){
            np.type.tryResolve()
            if (f is NativeFunction && np.type is MCFPPNotCompiledGenericType) continue
            f.scope.putVar(np.identifier, np.buildVar())
        }
        if(f is GenericFunction){
            for (rp in f.readOnlyParams){
                rp.type.tryResolve()
                f.scope.putVar(rp.identifier, rp.buildVar())
            }
        }
        if(f is NativeFunction){
            for (rp in f.readOnlyParams){
                rp.type.tryResolve()
                f.scope.putVar(rp.identifier, rp.buildVar())
            }
        }
        f.returnType.tryResolve()
        f.buildReturnVar(f.returnType)
    }

    private fun resolveVar(v: Var<*>){
        v.type.tryResolve()
        if (v is DataTemplateObject) {
            val template = v.templateType
            if (template is UnsolvedTemplate) v.templateType = template.resolve()
        }
    }

    fun injectedBy(cls: Class<*>){
        val l = Project.currNamespace
        Project.currNamespace = this.identifier
        try {
            injectMethods(cls)
        } finally {
            Project.currNamespace = l
        }
    }

    private fun injectMethods(cls: Class<*>) {
        val methods = NativeSignatureValidator.methods(cls)
        restoreMacroDeclarations(methods.mapNotNull { MacroFunctionInfo.from(it) })
        val registration = NativeSignatureValidator.Registration()
        //获取import方法
        val simpleFieldWithType = SimpleScopeWithType.getTypeScope()
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
                        val qwq = nsp.first?.let { GlobalScope.getNamespace(nsp.first!!)}
                        if(qwq == null){
                            LogProcessor.error("Namespace '${nsp.first}' not found")
                            continue
                        }
                        if(nsp.second == "*"){
                            qwq.scope.forEachType {
                                val d = simpleFieldWithType.putType(it.simpleName, it)
                                if(!d){
                                    simpleFieldWithType.putType(it.simpleName, it, true)
                                    LogProcessor.error("Already have import '${it.simpleName}'")
                                }
                            }
                        }else{
                            val owo = qwq.scope.getDeclaredType(nsp.second)?.getType()
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
                val nf = NativeFunction(mniRegister.identifier.ifEmpty { method.name }, javaMethod = method)
                //解析MNIMethod注解成员
                val callerType = MCFPPType.parseFromString(mniRegister.caller, simpleFieldWithType)
                nf.caller = callerType?: run {
                    LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(mniRegister.caller) + " in method ${method.name} in MNIClass ${cls.name}")
                    MCFPPPrivateType.Void
                }
                mniRegister.genericType.map {
                    nf.scope.putType(it, MCFPPGenericParamType(it, arrayListOf()))
                }
                //解析MNIMethod注解成员
                val readOnlyType = mniRegister.readOnlyParams.map {
                    val typeName = it.splitMNIParam().first
                    MCFPPType.parseFromString(typeName, simpleFieldWithType)?: run {
                        LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(typeName) + " in method ${method.name} in MNIClass ${cls.name}")
                        MCFPPBaseType.Any
                    }
                }
                val normalType = mniRegister.normalParams.map {
                    val typeName = it.splitMNIParam().first
                    MCFPPType.parseFromString(typeName, simpleFieldWithType)?: run {
                        LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(typeName) + " in method ${method.name} in MNIClass ${cls.name}")
                        MCFPPBaseType.Any
                    }
                }
                val returnType = MCFPPType.parseFromString(mniRegister.returnType, simpleFieldWithType)?: run {
                    LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(mniRegister.returnType) + " in method ${method.name} in MNIClass ${cls.name}")
                    MCFPPBaseType.Any
                }
                nf.returnType = returnType
                for(rt in readOnlyType){
                    nf.appendReadOnlyParam(rt, "p${nf.paramCount()}")
                }
                for(nt in normalType){
                    nf.appendNormalParam(nt, "p${nf.paramCount()}")
                }
                if (!registration.accept(nf)) continue
                nf.returnsConstWhenArgsConst = mniRegister.returnsConstWhenArgsConst
                //有继承
                if(mniRegister.override){
                    val result = scope.hasFunction(nf, true)
                    if(!result){
                        LogProcessor.error("Method ${nf.identifier} in class ${cls.name} overrides nothing")
                        continue
                    }else{
                        nf.isOverride = true
                        this.scope.addFunction(nf, true)
                    }
                }else {
                    val result = this.scope.addFunction(nf, false)
                    if(!result){
                        LogProcessor.warn("Duplicate method ${nf.identifier} in class ${cls.name}. If you want to override it, please add @MNIRegister(override = true) to the method")
                        this.scope.addFunction(nf, true)
                    }
                }
            }
        }
    }

    override fun toString(): String {
        return "namespace($identifier)"
    }

    companion object {
        val currNamespaceField: NamespaceScope
            get() = GlobalScope.localNamespaces[Project.currNamespace]?.scope?:
                GlobalScope.importedLibNamespaces[Project.currNamespace]?.scope?:
                GlobalScope.stdNamespaces[Project.currNamespace]!!.scope
    }
}
