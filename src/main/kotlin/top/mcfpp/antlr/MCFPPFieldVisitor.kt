package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.Project.withCompilationContext
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.antlr.mcfppParser.TemplateDeclarationContext
import top.mcfpp.compiletime.CompileTimeFunction
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.UnionTypeVar
import top.mcfpp.core.lang.UnionTypeVarConcrete
import top.mcfpp.core.lang.Var
import top.mcfpp.exception.UndefinedException
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.Member
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.compound.TypeDataTemplate
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.Property
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.scope.IScopeWithType
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper.splitNamespaceID
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*
import kotlin.reflect.jvm.javaMethod

/**
 * 在编译工程之前，应当首先将所有文件中的资源全部遍历一次并写入缓存。
 * TODO 存在优化空间，因为部分代码和McfppTypeVisitor有高度重合和相关性
 */
open class MCFPPFieldVisitor : mcfppParserBaseVisitor<Any?>() {

    protected var isInObject = false

    protected lateinit var typeScope : IScopeWithType

    /**
     * 遍历整个文件。一个文件包含了命名空间的声明，函数的声明，类的声明以及全局变量的声明。全局变量是可以跨文件调用的。
     * @param ctx the parse tree
     * @return null
     */
    override fun visitCompilationUnit(ctx: mcfppParser.CompilationUnitContext): Any? = withCompilationContext(ctx) {
        typeScope = GlobalScope.localNamespaces[Project.currNamespace]!!.scope
        //文件结构，类和函数
        for (t in ctx.typeDeclaration()) {
            visit(t)
        }
        return null
    }

//region function
    /**
     * 函数的声明
     * @param ctx the parse tree
     * @return null
     */
    
    override fun visitFunctionDeclaration(ctx: mcfppParser.FunctionDeclarationContext): Any? = withCompilationContext(ctx) {
        //创建函数对象
        val identifier = ctx.functionDeclarationPart().Identifier().text
        val f = if(ctx.functionDeclarationPart().functionParams()?.readOnlyParams() != null && ctx.functionDeclarationPart().functionParams().readOnlyParams().parameterList().parameter().size != 0){
            GenericFunction(identifier, Project.currNamespace, ctx.curlBlock())
        }else {
            Function(identifier, Project.currNamespace, ctx.curlBlock())
        }
        f.returnType = if(ctx.functionDeclarationPart().functionReturnType()?.type() != null){
            MCFPPType.parseFromContextNotNull(ctx.functionDeclarationPart().functionReturnType().type(), typeScope)
        }else{
            MCFPPPrivateType.Void
        }
        //解析参数
        ctx.functionDeclarationPart().functionParams()?.let { f.addParamsFromContext(it) }
        //不是类的成员
        f.ownerType = Function.Companion.OwnerType.NONE
        //写入域
        val namespace = GlobalScope.localNamespaces[f.namespace]!!
        if (namespace.scope.hasFunction(f, true)) {
            LogProcessor.error("Already defined function: " + f.namespaceID)
            Function.currFunction = Function.nullFunction
        } else if(namespace.scope.hasDeclaredType(f.identifier)) {
            LogProcessor.error("Function name conflicted with type name: " + f.identifier)
            Function.currFunction = Function.nullFunction
        } else{
            namespace.scope.addFunction(f,false)
        }
        if (f.isEntrance
            && ctx.functionDeclarationPart().functionParams().normalParams().parameterList().parameter().size != 0
            && (ctx.functionDeclarationPart().functionParams().readOnlyParams() == null || ctx.functionDeclarationPart().functionParams().readOnlyParams().parameterList().parameter().size != 0)
            ) {
            LogProcessor.error("Entrance function shouldn't have parameter:" + f.namespaceID)
        }
        return null
    }

    override fun visitInlineFunctionDeclaration(ctx: mcfppParser.InlineFunctionDeclarationContext): Any? = withCompilationContext(ctx) {
        //创建函数对象
        val f: Function
        //是否是内联函数
        val identifier : String = ctx.functionDeclarationPart().Identifier().text
        f = InlineFunction(identifier, Project.currNamespace, ctx.curlBlock())
        //解析参数
        f.addParamsFromContext(ctx.functionDeclarationPart().functionParams())
        //不是类的成员
        f.ownerType = Function.Companion.OwnerType.NONE
        //写入域
        val namespace = GlobalScope.localNamespaces[f.namespace]!!
        if (!namespace.scope.hasFunction(f, true)) {
            namespace.scope.addFunction(f,false)
        } else {
            LogProcessor.error("Already defined function:" + f.namespaceID)
            Function.currFunction = Function.nullFunction
        }
        if (f.isEntrance
            && ctx.functionDeclarationPart().functionParams().normalParams().parameterList().parameter().size != 0
            && (ctx.functionDeclarationPart().functionParams().readOnlyParams() == null || ctx.functionDeclarationPart().functionParams().readOnlyParams().parameterList().parameter().size != 0)
        ) {
            LogProcessor.error("Entrance function shouldn't have parameter:" + f.namespaceID)
        }
        return null
    }

    override fun visitCompileTimeFuncDeclaration(ctx: mcfppParser.CompileTimeFuncDeclarationContext): Any? = withCompilationContext(ctx) {
        //创建函数对象
        val f: Function
        //是否是编译时函数
        val identifier : String = ctx.functionDeclarationPart().Identifier().text
        f = CompileTimeFunction(
            identifier,Project.currNamespace,
            ctx.curlBlock()
        )
        f.returnType = if(ctx.functionDeclarationPart().functionReturnType()?.type() != null){
            MCFPPType.parseFromContextNotNull(ctx.functionDeclarationPart().functionReturnType().type(), typeScope)
        }else{
            MCFPPPrivateType.Void
        }
        //解析参数
        f.addParamsFromContext(ctx.functionDeclarationPart().functionParams())
        //不是类的成员
        f.ownerType = Function.Companion.OwnerType.NONE
        //写入域
        val namespace = GlobalScope.localNamespaces[f.namespace]!!
        if (!namespace.scope.hasFunction(f, true)) {
            f.setField(namespace.scope)
            namespace.scope.addFunction(f,false)
        } else {
            LogProcessor.error("Already defined function:" + f.namespaceID)
            Function.currFunction = Function.nullFunction
        }
        if (f.isEntrance
            && ctx.functionDeclarationPart().functionParams().normalParams().parameterList().parameter().size != 0
            && (ctx.functionDeclarationPart().functionParams().readOnlyParams() == null || ctx.functionDeclarationPart().functionParams().readOnlyParams().parameterList().parameter().size != 0)
        ) {
            LogProcessor.error("Entrance function shouldn't have parameter:" + f.namespaceID)
        }
        return null
    }


    //TODO 单例的拓展函数
    override fun visitExtensionFunctionDeclaration(ctx: mcfppParser.ExtensionFunctionDeclarationContext): Any? = withCompilationContext(ctx)  {
        val ownerType : Function.Companion.OwnerType
        //获取被拓展的类
        val type = MCFPPType.parseFromContext(ctx.type(), MCFPPFile.currFile!!.field)
        when(type){
            is MCFPPDataTemplateType -> {
                ownerType = Function.Companion.OwnerType.TEMPLATE
            }
            null -> {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.type().text))
                return null
            }
            else -> {
                ownerType = Function.Companion.OwnerType.BASIC
            }
        }
        val data = type.instanceData
        //创建函数对象
        val f = if(ctx.functionParams().readOnlyParams() != null && ctx.functionParams().readOnlyParams().parameterList().parameter().size != 0){
            GenericExtensionFunction(ctx.Identifier().text, data, Project.currNamespace, ctx.curlBlock())
        }else{
            ExtensionFunction(ctx.Identifier().text, data, Project.currNamespace, ctx.curlBlock())
        }
        //解析参数
        f.accessModifier = AccessModifier.PUBLIC
        f.returnType = if(ctx.functionReturnType()?.type() != null){
            MCFPPType.parseFromContextNotNull(ctx.functionReturnType().type(), typeScope)
        }else{
            MCFPPPrivateType.Void
        }
        f.ownerType = ownerType
        f.addParamsFromContext(ctx.functionParams())
        val field = data.scope
        //注册函数
        if (!field.addFunction(f,false)) {
            LogProcessor.error("Already defined function:" + ctx.Identifier().text + "in type " + type.typeName)
            Function.currFunction = Function.nullFunction
        }
        return null
    }

    /**
     * native函数的声明
     * @param ctx the parse tree
     * @return 如果是全局，返回null，否则返回这个函数对象
     */
    
    override fun visitNativeFuncDeclaration(ctx: mcfppParser.NativeFuncDeclarationContext): Any? = withCompilationContext(ctx) {
        val nf = NativeFunction(ctx.functionDeclarationPart().Identifier().text, Project.currNamespace)
        nf.returnType = if(ctx.functionDeclarationPart().functionReturnType()?.type() != null){
            MCFPPType.parseFromContextNotNull(ctx.functionDeclarationPart().functionReturnType().type(), typeScope)
        }else{
            MCFPPPrivateType.Void
        }
        nf.addParamsFromContext(ctx.functionDeclarationPart().functionParams())
        try {
            //根据JavaRefer找到类
            val refer = ctx.javaRefer().text
            val clsName = refer.substring(0,refer.lastIndexOf('.'))
            val clazz = Project.classLoader.loadClass(clsName)
            val methods = clazz.methods
            var hasFind = false
            for(method in methods){
                if(method.name != nf.identifier) continue
                val mniRegister = method.getAnnotation(MNIFunction::class.java) ?: continue
                //解析MNIMethod注解成员
                val readOnlyType = mniRegister.readOnlyParams.map {
                    MCFPPType.parseFromString(it.split(" ").last(), Namespace.currNamespaceField)
                }
                val normalType = mniRegister.normalParams.map {
                    MCFPPType.parseFromString(it.split(" ").last(), Namespace.currNamespaceField)
                }
                //比对
                if(nf.readOnlyParams.map { it.type } == readOnlyType && nf.normalParams.map { it.type } == normalType){
                    hasFind = true
                    nf.javaMethod = method
                    break
                }
            }
            if(!hasFind){
                throw NoSuchMethodException("Cannot find method ${ctx.functionDeclarationPart().Identifier().text} with correct parameters in class $clsName")
            }
        } catch (e: ClassNotFoundException) {
            LogProcessor.error("Cannot find java class: " + e.message)
            e.printStackTrace()
            return null
        }
        //写入域
        val namespace = GlobalScope.localNamespaces[nf.namespace]!!
        //是普通的函数
        nf.ownerType = Function.Companion.OwnerType.NONE
        if (!namespace.scope.hasFunction(nf, true)) {
            namespace.scope.addFunction(nf,false)
        } else {
            LogProcessor.error("Already defined function:" + ctx.functionDeclarationPart().Identifier().text)
            Function.currFunction = Function.nullFunction
        }
        return nf
    }
//endregion

//region template
    override fun visitTemplateDeclaration(ctx: TemplateDeclarationContext): Any? = withCompilationContext(ctx) {
        //获取注册的模板
        val id = (ctx.declarationName()?: ctx.compoundDeclaration().declarationName()).classWithoutNamespace().text
        val namespace1 = GlobalScope.localNamespaces[Project.currNamespace]!!
        val template = if(namespace1.scope.hasTemplate(id)){
            namespace1.scope.getTemplate(id)!!
        }else{
            throw UndefinedException("Template should have been defined: $id")
        }
        DataTemplate.currTemplate = template
        typeScope = template.scope
        for (c in ctx.compoundDeclaration()?.extendName() ?: emptyList()){
            //是否存在继承
            val (namespace, identifier) = c.text.splitNamespaceID()
            val s = GlobalScope.getTemplate(namespace, identifier)
            if(s == null){
                val o = GlobalScope.getObject(namespace, identifier)
                if(o is ObjectDataTemplate) {
                    template.extends(o)
                }else{
                    LogProcessor.error("Undefined template: " + c.text)
                }
            }else{
                if(s == template){
                    LogProcessor.error("Infinitive reference: $id -> $identifier")
                }else{
                    template.extends(s)
                }
            }
        }
        isInObject = false
        ctx.templateBody()?.let { visitTemplateBody(it) }

        if(!template.isAbstract){
            template.scope.forEachFunction {
                if(it.isAbstract){
                    LogProcessor.error("${it.identifier} is abstract, but not implemented.")
                }
            }
        }

        //默认构造函数和默认字段
        if(template is TypeDataTemplate){
            template.addMember(
                NativeDataTemplateConstructor(
                    DataTemplate.currTemplate!!,
                    TypeDataTemplate.Companion::defaultConstructor.javaMethod!!
                )
            )
        }else if(template.constructors.isEmpty()){
            template.addMember(DataTemplateConstructor(DataTemplate.currTemplate!!, null))
        }
        DataTemplate.currTemplate = null
        typeScope = MCFPPFile.currFile!!.field.namespaceField
        return null
    }

    override fun visitObjectTemplateDeclaration(ctx: mcfppParser.ObjectTemplateDeclarationContext): Any? = withCompilationContext(ctx) {
        //注册模板
        val id = ctx.compoundDeclaration().declarationName().classWithoutNamespace().text
        val namespace1 = GlobalScope.localNamespaces[Project.currNamespace]!!
        val objectTemplate = namespace1.scope.getObject(id)
        if(objectTemplate !is ObjectDataTemplate){
            throw UndefinedException("Template should have been defined: $id")
        }
        DataTemplate.currTemplate = objectTemplate
        typeScope = objectTemplate.scope
        for (c in ctx.compoundDeclaration().extendName()){
            //是否存在继承
            val (namespace, identifier) = c.text.splitNamespaceID()
            val s = GlobalScope.getData(namespace, identifier)
            if(s == null){
                LogProcessor.error("${c.text} is undefined.")
            }else{
                if(s.isSubOf(objectTemplate) || s == objectTemplate){
                    LogProcessor.error("Infinitive reference: $id -> $identifier")
                }else if(s.isFinal){
                    LogProcessor.error("Cannot extends $identifier because it's final")
                }else {
                    objectTemplate.extends(s)
                }
            }
        }
        isInObject = true
        ctx.templateBody()?.let { visitTemplateBody(it) }

        if(!objectTemplate.isAbstract){
            objectTemplate.scope.forEachFunction {
                if(it.isAbstract){
                    LogProcessor.error("${it.identifier} is abstract, but not implemented.")
                }
            }
        }

        //如果没有构造函数，生成默认的构造函数
        if(objectTemplate.constructors.isEmpty()){
            objectTemplate.addMember(DataTemplateConstructor(DataTemplate.currTemplate!!, null))
        }

        DataTemplate.currTemplate = null
        typeScope = MCFPPFile.currFile!!.field.namespaceField
        return null
    }

    override fun visitAnonymousTemplateType(ctx: mcfppParser.AnonymousTemplateTypeContext): Any = withCompilationContext(ctx) {
        //注册模板
        val template = DataTemplate(TempPool.getAnonymousTemplateIdentify())
        val qwq = DataTemplate.currTemplate
        DataTemplate.currTemplate = template
        typeScope = template.scope
        for (c in ctx.extendName()){
            //是否存在继承
            val (namespace, identifier) = c.text.splitNamespaceID()
            val s = GlobalScope.getData(namespace, identifier)
            if(s == null){
                LogProcessor.error("${c.text} is undefined.")
            }else{
                if(s == template){
                    LogProcessor.error("Infinitive reference: ${template.identifier} -> $identifier")
                }else if(s.isFinal){
                    LogProcessor.error("Cannot extends $identifier because it's final")
                }else {
                    template.extends(s)
                }
            }
        }
        isInObject = false
        visitTemplateBody(ctx.templateBody())

        if(!template.isAbstract){
            template.scope.forEachFunction {
                if(it.isAbstract){
                    LogProcessor.error("${it.identifier} is abstract, but not implemented.")
                }
            }
        }

        //如果没有构造函数，生成默认的构造函数
        if(template.constructors.isEmpty()){
            template.addMember(DataTemplateConstructor(DataTemplate.currTemplate!!, null))
        }
        DataTemplate.currTemplate = qwq
        typeScope = MCFPPFile.currFile!!.field.namespaceField
        return template
    }

    override fun visitInterfaceDeclaration(ctx: mcfppParser.InterfaceDeclarationContext): Any? = withCompilationContext(ctx) {
        //注册类
        val id = ctx.compoundDeclaration().declarationName().classWithoutNamespace().text
        val namespace1 = GlobalScope.localNamespaces[Project.currNamespace]!!
        val itf = if(namespace1.scope.hasInterface(id)){
            namespace1.scope.getInterface(id)!!
        }else{
            throw UndefinedException("Interface should have been defined: $id")
        }
        typeScope = itf.scope
        for (c in ctx.compoundDeclaration().extendName()){
            //是否存在继承
            val (namespace, identifier) = c.text.splitNamespaceID()
            val s = GlobalScope.getInterface(namespace, identifier)
            if(s == null){
                LogProcessor.error("Undefined interface: " + c.text)
            }else{
                if(s == itf || s.isSubOf(itf)){
                    LogProcessor.error("Infinitive reference: ${itf.identifier} -> $identifier")
                }else{
                    itf.extends(s)
                }
            }
        }
        //接口成员
        ctx.templateBody()?.let { visitTemplateBody(it) }
        typeScope = MCFPPFile.currFile!!.field.namespaceField
        return null
    }

    override fun visitTemplateBody(ctx: mcfppParser.TemplateBodyContext): Any? = withCompilationContext(ctx) {
        //解析成员
        //先解析函数
        for (c in ctx.templateMemberDeclaration()) {
            if (c!!.templateMember().templateFunctionDeclaration() != null || c.templateMember().templateConstructorDeclaration() != null) {
                visit(c)
            }
        }
        //再解析变量
        for (c in ctx.templateMemberDeclaration()) {
            if (c!!.templateMember().templateFieldDeclaration() != null) {
                visit(c)
            }
        }
        return null
    }

    override fun visitTemplateMemberDeclaration(ctx: mcfppParser.TemplateMemberDeclarationContext): Any? = withCompilationContext(ctx) {
        val m = visitTemplateMember(ctx.templateMember())
        val accessModifier = AccessModifier.valueOf((ctx.accessModifier()?.text?:"public").uppercase(Locale.getDefault()))
        //访问修饰符
        if(m is Member){
            if(DataTemplate.currTemplate!!.isInterface && accessModifier != AccessModifier.PUBLIC){
                LogProcessor.error("Members in interface must be public")
            }
            m.accessModifier = accessModifier
            DataTemplate.currTemplate!!.addMember(m)
        }else if(m is Pair<*,*>){//Pair<Var, Property>
            val v = m.first as Var<*>?
            val p = m.second as Property?
            if(v == null || p == null) return null
            //访问修饰符
            if(DataTemplate.currTemplate!!.isInterface && accessModifier != AccessModifier.PUBLIC){
                LogProcessor.error("Members in interface must be public")
            }
            v.accessModifier = AccessModifier.valueOf((ctx.accessModifier()?.text?:"public").uppercase(Locale.getDefault()))
            p.accessModifier = v.accessModifier
            DataTemplate.currTemplate!!.addMember(v)
            DataTemplate.currTemplate!!.addMember(p)
        }
        return null
    }
    
    override fun visitTemplateMember(ctx: mcfppParser.TemplateMemberContext): Any? = withCompilationContext(ctx) {
        return if (ctx.templateFunctionDeclaration() != null) {
            visitTemplateFunctionDeclaration(ctx.templateFunctionDeclaration())
        } else if (ctx.templateFieldDeclaration() != null) {
            visitTemplateFieldDeclaration(ctx.templateFieldDeclaration())
        } else if(ctx.templateConstructorDeclaration() != null) {
            visitTemplateConstructorDeclaration(ctx.templateConstructorDeclaration())
        } else{
            return null
        }
    }

    override fun visitTemplateFunctionDeclaration(ctx: mcfppParser.TemplateFunctionDeclarationContext): Any = withCompilationContext(ctx) {
        //创建函数对象
        val f = if(ctx.functionDeclarationPart().functionParams().readOnlyParams() != null && ctx.functionDeclarationPart().functionParams().readOnlyParams().parameterList().parameter().size != 0){
            GenericFunction(
                ctx.functionDeclarationPart().Identifier().text,
                DataTemplate.currTemplate!!,
                ctx.curlBlock()
            )
        }else {
            Function(
                ctx.functionDeclarationPart().Identifier().text,
                DataTemplate.currTemplate!!,
                ctx.curlBlock()
            )
        }
        f.returnType = if(ctx.functionDeclarationPart().functionReturnType()?.type() != null){
            MCFPPType.parseFromContextNotNull(ctx.functionDeclarationPart().functionReturnType().type(), typeScope)
        }else{
            MCFPPPrivateType.Void
        }
        if(ctx.OVERRIDE() != null){
            f.isOverride = true
        }
        //解析参数
        f.addParamsFromContext(ctx.functionDeclarationPart().functionParams())
        //检测重复定义
        if (DataTemplate.currTemplate!!.scope.hasFunction(f, true)) {
            LogProcessor.error("Already defined function:" + ctx.functionDeclarationPart().Identifier().text + "in template " + DataTemplate.currTemplate!!.identifier)
            Function.currFunction = Function.nullFunction
        }
        return f
    }

    override fun visitTemplateFieldDeclaration(ctx: mcfppParser.TemplateFieldDeclarationContext): Pair<Var<*>?, Property?> = withCompilationContext(ctx) {
        if(DataTemplate.currTemplate is TypeDataTemplate){
            LogProcessor.error("TypeDataTemplate cannot have field: " + ctx.Identifier().text)
            return null to null
        }
        val isConst = ctx.CONST() != null
        if(!isInObject && isConst){
            //只有在object中定义的常量才有意义
            LogProcessor.error("Constant can only be declared in object: " + ctx.Identifier().text)
            return null to null
        }
        var `var` = ctx.templateType()?.let {
            if (it.singleTemplateFieldType() != null) {
                val type = MCFPPType.parseFromContextNotNull(it.singleTemplateFieldType().type(), typeScope)
                if(!isInObject){
                    type.build(ctx.Identifier().text).apply {
                        nullable = it.singleTemplateFieldType().QUEST() != null
                    }
                }else{
                    //object中的字段作为全局字段，是长久保存并且不可追踪的，其中的字段应当是不确定的。
                    type.buildUnConcrete(ctx.Identifier().text).apply {
                        nullable = it.singleTemplateFieldType().QUEST() != null
                    }
                }
            } else {
                val unionTypes = ArrayList<MCFPPType>()
                for (type in it.unionTemplateFieldType().type()) {
                    unionTypes.add(MCFPPType.parseFromContextNotNull(type, typeScope))
                }
                if(!isInObject){
                    UnionTypeVarConcrete(
                        ctx.Identifier().text,
                        unionTypes[0].defaultValue(),
                        *unionTypes.toTypedArray()
                    ).apply {
                        nullable = it.unionTemplateFieldType().QUEST() != null
                    }
                }else{
                    UnionTypeVar(
                        ctx.Identifier().text,
                        *unionTypes.toTypedArray()
                    ).apply {
                        nullable = it.unionTemplateFieldType().QUEST() != null
                    }
                }
            }
        }

        var init: Var<*>? = null
        if(isConst && ctx.expression() == null){
            LogProcessor.error("Const template field ${ctx.Identifier().text} must have an initializer")
            return null to null
        }
        if(`var` == null && ctx.expression() == null){
            LogProcessor.error("Template field ${ctx.Identifier().text} must have a type or an initializer")
            return null to null
        }else if(`var` == null){
            if(isInObject){
                //for object data, only fields with const flag will be treated as concrete var, even if
                //they have a concrete initializer.
                Function.extraFunction.runInFunction { init = MCFPPExprVisitor().visit(ctx.expression())!! }
                val type = init!!.type
                if(isConst){
                    if(init !is MCFPPValue<*>){
                        LogProcessor.error("Const template field must have a concrete initializer.")
                        return null to null
                    }
                    `var` = type.build(ctx.Identifier().text, (init as MCFPPValue<*>).value)
                }else{
                    `var` = type.buildUnConcrete(ctx.Identifier().text)
                    `var`.isDynamic = true
                    DataTemplate.currTemplate!!.preInit[`var`.identifier] = ctx.expression()
                }
            }else{
                Function.extraFunction.runInFunction { init = MCFPPExprVisitor().visit(ctx.expression())!! }
                val type = init!!.type
                `var` = type.buildUnConcrete(ctx.Identifier().text)
                DataTemplate.currTemplate!!.preInit[`var`.identifier] = ctx.expression()
            }
        }

        //是否是静态的
        `var`.isStatic = isInObject
        `var`.isConst = isConst
        if (DataTemplate.currTemplate!!.scope.containVar(ctx.Identifier().text)
        ) {
            LogProcessor.error("Duplicate defined variable name:" + ctx.Identifier().text)
            return null to null
        }
        //属性访问器
        val properties = (ctx.accessor()?.let {visit(ctx.accessor())}?: Property.buildSimpleProperty(`var`)) as Property
        `var`.declaredParentTemplate = DataTemplate.currTemplate!!
        properties.declaredParentTemplate = DataTemplate.currTemplate!!
        return `var` to properties
    }

    override fun visitTemplateConstructorDeclaration(ctx: mcfppParser.TemplateConstructorDeclarationContext): Any? = withCompilationContext(ctx) {
        if(DataTemplate.currTemplate is TypeDataTemplate){
            LogProcessor.error("TypeDataTemplate cannot have constructor")
            return null to null
        }
        //类构造函数
        //创建构造函数对象，注册函数
        val f = DataTemplateConstructor(DataTemplate.currTemplate!!, ctx.curlBlock())
        f.file = MCFPPFile.currFile!!
        ctx.normalParams()?.let { f.addParamsFromContext(ctx.normalParams()) }
        if(isInObject && f.paramCount() != 0){
            LogProcessor.error("Object constructor must have no parameter.")
            return null
        }
        return f
    }

    //endregion
}