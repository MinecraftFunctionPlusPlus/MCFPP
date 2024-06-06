package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.antlr.mcfppParser.TemplateDeclarationContext
import top.mcfpp.compiletime.CompileTimeFunction
import top.mcfpp.exception.*
import top.mcfpp.io.MCFPPFile
import top.mcfpp.lang.*
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lang.type.UnresolvedType
import top.mcfpp.annotations.MNIRegister
import top.mcfpp.model.*
import top.mcfpp.model.Class
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.field.IFieldWithType
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.generic.GenericExtensionFunction
import top.mcfpp.model.generic.GenericFunction
import top.mcfpp.util.LazyWrapper
import top.mcfpp.util.LogProcessor
import java.util.*
import kotlin.collections.ArrayList

/**
 * 在编译工程之前，应当首先将所有文件中的资源全部遍历一次并写入缓存。
 * TODO 存在优化空间，因为部分代码和McfppTypeVisitor有高度重合和相关性
 */
open class McfppFieldVisitor : mcfppParserBaseVisitor<Any?>() {

    protected var isStatic = false

    protected lateinit var typeScope : IFieldWithType

    /**
     * 遍历整个文件。一个文件包含了命名空间的声明，函数的声明，类的声明以及全局变量的声明。全局变量是可以跨文件调用的。
     * @param ctx the parse tree
     * @return null
     */
    override fun visitCompilationUnit(ctx: mcfppParser.CompilationUnitContext): Any? {
        Project.ctx = ctx
        //命名空间
        Project.currNamespace = ctx.namespaceDeclaration()?.let{
            //获取命名空间
            var namespaceStr = ctx.namespaceDeclaration().Identifier()[0].text
            if(ctx.namespaceDeclaration().Identifier().size > 1){
                ctx.namespaceDeclaration().Identifier().forEach{e ->
                    run {
                        namespaceStr += ".${e.text}"
                    }
                }
            }
            namespaceStr
        }?: Project.config.defaultNamespace
        typeScope = GlobalField.localNamespaces[Project.currNamespace]!!.field
        //文件结构，类和函数
        for (t in ctx.typeDeclaration()) {
            visit(t)
        }
        return null
    }

    /**
     * 类或函数声明
     * @param ctx the parse tree
     * @return null
     */

    override fun visitDeclarations(ctx: mcfppParser.DeclarationsContext): Any? {
        Project.ctx = ctx
        if (ctx.globalDeclaration() != null) {
            return null
        }
        super.visitDeclarations(ctx)
        return null
    }

//region interface

    override fun visitInterfaceDeclaration(ctx: mcfppParser.InterfaceDeclarationContext): Any? {
        Project.ctx = ctx
        //注册类
        val id = ctx.classWithoutNamespace().text
        val namespace = GlobalField.localNamespaces[Project.currNamespace]!!

        if (namespace.field.hasInterface(id)) {
            //重复声明
            Interface.currInterface = namespace.field.getInterface(id)
        } else {
            throw UndefinedException("Interface Should have been defined: $id")
        }
        typeScope = Interface.currInterface!!.field
        //接口成员
        for (m in ctx.interfaceBody().interfaceFunctionDeclaration()){
            visit(m)
        }
        typeScope = MCFPPFile.currFile!!.field
        return null
    }


    override fun visitInterfaceFunctionDeclaration(ctx: mcfppParser.InterfaceFunctionDeclarationContext): Any? {
        Project.ctx = ctx
        //创建函数对象
        val f = Function(
            ctx.Identifier().text,
            Interface.currInterface!!,
            MCFPPType.parseFromIdentifier(if(ctx.functionReturnType() == null) "void" else ctx.functionReturnType().text, typeScope)
        )
        //解析参数
        f.addParamsFromContext(ctx.functionParams())
        //注册函数
        if (Interface.currInterface!!.field.hasFunction(f)) {
            LogProcessor.error("Already defined function:" + ctx.Identifier().text + "in class " + Class.currClass!!.identifier)
            Function.currFunction = Function.nullFunction
        }
        return null
    }
//endregion

//region native class
    /**
     * TODO native类的声明
     * @param ctx the parse tree
     * @return null
     */
    override fun visitNativeClassDeclaration(ctx: mcfppParser.NativeClassDeclarationContext): Any? {
        //NativeClassVisitor().visit(ctx)
        return null
    }
//endregion

//region class
    /**
     * 类的声明
     * @param ctx the parse tree
     * @return null
     */

    override fun visitClassDeclaration(ctx: mcfppParser.ClassDeclarationContext): Any? {
        Project.ctx = ctx
        //注册类
        val id = ctx.classWithoutNamespace().text
        val namespace = GlobalField.localNamespaces[Project.currNamespace]!!
        if(ctx.readOnlyParams() != null){
            return null
        }
        val clazz = if (namespace.field.hasClass(id)) {
            namespace.field.getClass(id)
        } else {
            throw UndefinedException("Class Should have been defined: $id")
        }
        Class.currClass = clazz
        typeScope = Class.currClass!!.field
        //解析类中的成员
        //先静态
        isStatic = true
        //先解析函数
        for (c in ctx.classBody().staticClassMemberDeclaration()) {
            c!!
            if (c.classMember().classFunctionDeclaration() != null || c.classMember().abstractClassFunctionDeclaration() != null) {
                visit(c)
            }
        }
        //再解析变量
        for (c in ctx.classBody().staticClassMemberDeclaration()) {
            if (c!!.classMember().classFieldDeclaration() != null) {
                visit(c)
            }
        }
        //后成员
        isStatic = false
        //先解析函数和构造函数
        for (c in ctx.classBody().classMemberDeclaration()) {
            c!!
            if (c.classMember() != null && (c.classMember().classFunctionDeclaration() != null || c.classMember().abstractClassFunctionDeclaration() != null)) {
                visit(c)
            }
        }
        //再解析变量
        for (c in ctx.classBody().classMemberDeclaration()) {
            if (c!!.classMember() != null && c.classMember().classFieldDeclaration() != null) {
                visit(c)
            }
        }
        //如果没有构造函数，自动添加默认的空构造函数
        if (Class.currClass!!.constructors.size == 0) {
            Class.currClass!!.addConstructor(Constructor(Class.currClass!!))
        }
        //是否为抽象类
        if(!Class.currClass!!.isAbstract){
            var il : Function? = null
            Class.currClass!!.field.forEachFunction { f ->
                run {
                    if(f.isAbstract){
                        il = f
                        return@run
                    }
                }
            }
            if(il != null){
                LogProcessor.error("Class ${Class.currClass} must either be declared abstract or implement abstract method ${il!!.nameWithNamespace}")
            }
        }
        Class.currClass = null
        typeScope = MCFPPFile.currFile!!.field
        return null
    }

    
    override fun visitStaticClassMemberDeclaration(ctx: mcfppParser.StaticClassMemberDeclarationContext): Any? {
        Project.ctx = ctx
        val m = visit(ctx.classMember()) as Member
        //访问修饰符
        if (ctx.accessModifier() != null) {
            m.accessModifier = AccessModifier.valueOf(ctx.accessModifier().text.uppercase(Locale.getDefault()))
        }
        m.isStatic = true
        Class.currClass!!.addMember(m)
        if(m is Function){
            m.field.removeVar("this")
        }
        return null
    }

    /**
     * 类成员的声明。由于函数声明可以后置，因此需要先查明函数声明情况再进行变量的注册以及初始化。
     * <pre>
     * `classMemberDeclaration
     * :   accessModifier? (STATIC)? classMember
     * ;
    `</pre> *
     * @param ctx the parse tree
     * @return null
     */
    
    override fun visitClassMemberDeclaration(ctx: mcfppParser.ClassMemberDeclarationContext): Any? {
        Project.ctx = ctx
        val m = visit(ctx.classMember())
        if(m is Member){
            //访问修饰符
            if (ctx.accessModifier() != null) {
                m.accessModifier = AccessModifier.valueOf(ctx.accessModifier().text.uppercase(Locale.getDefault()))
            }
            if (m !is Constructor) {
                Class.currClass!!.addMember(m)
            }
        }else if(m is ArrayList<*>){
            //变量列表
            for (c in m){
                c as Member
                //访问修饰符
                if (ctx.accessModifier() != null) {
                    c.accessModifier = AccessModifier.valueOf(ctx.accessModifier().text.uppercase(Locale.getDefault()))
                }
                if (c !is Constructor) {
                    Class.currClass!!.addMember(c)
                }
            }
        }
        return null
    }

    override fun visitClassMember(ctx: mcfppParser.ClassMemberContext): Any? {
        Project.ctx = ctx
        return if (ctx.nativeClassFunctionDeclaration() != null) {
            visit(ctx.nativeClassFunctionDeclaration())
        } else if (ctx.classFunctionDeclaration() != null) {
            visit(ctx.classFunctionDeclaration())
        } else if (ctx.classFieldDeclaration() != null) {
            visit(ctx.classFieldDeclaration())
        } else if (ctx.constructorDeclaration() != null) {
            visit(ctx.constructorDeclaration())
        }else{
            return null
        }
    }

    /**
     * 类方法的声明
     * @param ctx the parse tree
     * @return 这个类方法的对象
     */
    
    override fun visitClassFunctionDeclaration(ctx: mcfppParser.ClassFunctionDeclarationContext): Any {
        Project.ctx = ctx
        val type = MCFPPType.parseFromIdentifier(ctx.functionReturnType()?.text?:"text", typeScope)
        //创建函数对象
        val f = if(ctx.functionParams().readOnlyParams() != null && ctx.functionParams().readOnlyParams().parameterList().parameter().size != 0){
            GenericFunction(
                ctx.Identifier().text,
                Class.currClass!!,
                ctx.parent is mcfppParser.StaticClassMemberDeclarationContext,
                type,
                ctx.functionBody()
            )
        }else{
            Function(
                ctx.Identifier().text,
                Class.currClass!!,
                ctx.parent is mcfppParser.StaticClassMemberDeclarationContext,
                type
            )
        }
        if(!isStatic){
            val thisObj = Var.build("this", MCFPPType.parseFromIdentifier(Class.currClass!!.identifier, typeScope), f)
            f.field.putVar("this",thisObj)
        }
        //解析参数
        f.addParamsFromContext(ctx.functionParams())
        //注册函数
        if (Class.currClass!!.field.hasFunction(f) || Class.currClass!!.staticField.hasFunction(f)) {
            if(ctx.OVERRIDE() != null){
                if(isStatic){
                    LogProcessor.error("Cannot override static method ${ctx.Identifier()}")
                    throw Exception()
                }
            }else{
                LogProcessor.error("Already defined function:" + ctx.Identifier().text + "in class " + Class.currClass!!.identifier)
                Function.currFunction = Function.nullFunction
            }
        }
        return f
    }

    
    override fun visitAbstractClassFunctionDeclaration(ctx: mcfppParser.AbstractClassFunctionDeclarationContext): Any {
        Project.ctx = ctx
        val type = MCFPPType.parseFromIdentifier(ctx.functionReturnType().text, typeScope)
        //抽象函数没有函数体，不能作为GenericFunction
        //创建函数对象
        val f = Function(
            ctx.Identifier().text,
            Class.currClass!!,
            ctx.parent is mcfppParser.StaticClassMemberDeclarationContext,
            type
        )
        f.isAbstract = true
        if(f.isStatic){
            LogProcessor.error("Static Function cannot be abstract: ${ctx.Identifier().text} in class ${Class.currClass!!.identifier}" )
            throw Exception()
        }
        //解析参数
        f.addParamsFromContext(ctx.functionParams())
        //注册函数
        if (Class.currClass!!.field.hasFunction(f)) {
            LogProcessor.error("Already defined function:" + ctx.Identifier().text + "in class " + Class.currClass!!.identifier)
            Function.currFunction = Function.nullFunction
        }
        return f
    }

    override fun visitNativeClassFunctionDeclaration(ctx: mcfppParser.NativeClassFunctionDeclarationContext): Any? {
        Project.ctx = ctx
        val nf = NativeFunction(ctx.Identifier().text, MCFPPType.parseFromIdentifier(ctx.functionReturnType()?.text?:"void", typeScope), Project.currNamespace)
        nf.addParamsFromContext(ctx.functionParams())
        //是类成员
        nf.ownerType = Function.Companion.OwnerType.CLASS
        try {
            //根据JavaRefer找到类
            val refer = ctx.javaRefer().text
            val clsName = refer.substring(0,refer.lastIndexOf('.'))
            val clazz = java.lang.Class.forName(clsName)
            val methods = clazz.methods
            var hasFind = false
            for(method in methods){
                val mniRegister = method.getAnnotation(MNIRegister::class.java) ?: continue
                //解析MNIMethod注解成员
                val readOnlyType = mniRegister.readOnlyParams.map {
                    MCFPPType.parseFromIdentifier(it.split(" ", limit = 2)[0], Namespace.currNamespaceField)
                }
                val normalType = mniRegister.normalParams.map {
                    MCFPPType.parseFromIdentifier(it.split(" ", limit = 2)[0], Namespace.currNamespaceField)
                }
                //比对
                if(nf.readOnlyParams.map { it.type } == readOnlyType && nf.normalParams.map { it.type } == normalType){
                    hasFind = true
                    nf.javaMethod = method
                    break
                }
            }
            if(!hasFind){
                throw NoSuchMethodException("Cannot find method ${ctx.Identifier().text} with correct parameters in class $clsName")
            }
        } catch (e: ClassNotFoundException) {
            LogProcessor.error("Cannot find java class: " + e.message)
            return null
        }
        return nf
    }

    /**
     * 构造函数的声明
     * @param ctx the parse tree
     * @return 这个构造函数的对象
     */
    override fun visitConstructorDeclaration(ctx: mcfppParser.ConstructorDeclarationContext): Any {
        Project.ctx = ctx
        //类构造函数
        //创建构造函数对象，注册函数
        val f = Constructor(Class.currClass!!)
        f.addParamsFromContext(ctx.normalParams())
        if(!Class.currClass!!.addConstructor(f)){
            LogProcessor.error("Already defined constructor: " + ctx.className().text + "(" + ctx.normalParams().text + ")")
        }
        return f
    }

    /**
     * 类字段的声明
     * @param ctx the parse tree
     * @return null
     */
    @InsertCommand
    override fun visitClassFieldDeclaration(ctx: mcfppParser.ClassFieldDeclarationContext): Any {
        Project.ctx = ctx
        //只有类字段构建
        val reList = ArrayList<Member>()
        val c = ctx.fieldDeclarationExpression()
        //字段的解析在Analyse阶段结束后，Compile阶段开始的时候进行
        val type = MCFPPType.parseFromIdentifier(ctx.type().text, typeScope)
        val `var` = Var.build(c.Identifier().text, type, Class.currClass!!)
        //是否是静态的
        if(isStatic){
            `var`.isStatic = true
            `var`.parent = Class.currClass!!.getType()
        }else{
            `var`.parent = ClassPointer(Class.currClass!!,"temp")
        }
        if (Class.currClass!!.field.containVar(c.Identifier().text)
            || Class.currClass!!.staticField.containVar(c.Identifier().text)
        ) {
            LogProcessor.error("Duplicate defined variable name:" + c.Identifier().text)
            return reList
        }
        //变量的初始化
        if (c.expression() != null) {
            Function.currFunction = if(isStatic){
                `var`.isStatic = true
                Class.currClass!!.classPreStaticInit
            }else{
                Class.currClass!!.classPreInit
            }
            //是类的成员
            Function.addCommand("#" + ctx.text)
            val init: Var<*> = McfppExprVisitor().visit(c.expression())!!
            try {
                `var`.assign(init)
            } catch (e: VariableConverseException) {
                LogProcessor.error("Cannot convert " + init.javaClass + " to " + `var`.javaClass)
                Function.currFunction = Function.nullFunction
                throw VariableConverseException()
            }
            Function.currFunction = Function.nullFunction
            `var`.hasAssigned = true
        }
        reList.add(`var`)
        return reList
    }

//endregion

//region function
    /**
     * 函数的声明
     * @param ctx the parse tree
     * @return null
     */
    
    override fun visitFunctionDeclaration(ctx: mcfppParser.FunctionDeclarationContext): Any? {
        Project.ctx = ctx
        //创建函数对象
        val identifier : String = ctx.Identifier().text
        val type = MCFPPType.parseFromIdentifier(if(ctx.functionReturnType() == null) "void" else ctx.functionReturnType().text, typeScope)
        val f = if(ctx.functionParams().readOnlyParams() != null && ctx.functionParams().readOnlyParams().parameterList().parameter().size != 0){
            GenericFunction(identifier, Project.currNamespace, type, ctx.functionBody())
        }else {
            Function(identifier, Project.currNamespace,type)
        }
        //解析参数
        f.addParamsFromContext(ctx.functionParams())
        //TODO 解析函数的注解
        //不是类的成员
        f.ownerType = Function.Companion.OwnerType.NONE
        //写入域
        val namespace = GlobalField.localNamespaces[f.namespace]!!
        if (!namespace.field.hasFunction(f)) {
            namespace.field.addFunction(f,false)
        } else {
            LogProcessor.error("Already defined function:" + f.namespaceID)
            Function.currFunction = Function.nullFunction
        }
        if (f.isEntrance
            && ctx.functionParams().normalParams().parameterList().parameter().size != 0
            && (ctx.functionParams().readOnlyParams() == null || ctx.functionParams().readOnlyParams().parameterList().parameter().size != 0)
            ) {
            LogProcessor.error("Entrance function shouldn't have parameter:" + f.namespaceID)
        }
        return null
    }

    override fun visitInlineFunctionDeclaration(ctx: mcfppParser.InlineFunctionDeclarationContext): Any? {
        Project.ctx = ctx
        //创建函数对象
        val f: Function
        //是否是内联函数
        val identifier : String = ctx.Identifier().text
        f = InlineFunction(identifier, Project.currNamespace, ctx)
        //解析参数
        f.addParamsFromContext(ctx.functionParams())
        //TODO 解析函数的注解
        //不是类的成员
        f.ownerType = Function.Companion.OwnerType.NONE
        //写入域
        val namespace = GlobalField.localNamespaces[f.namespace]!!
        if (!namespace.field.hasFunction(f)) {
            namespace.field.addFunction(f,false)
        } else {
            LogProcessor.error("Already defined function:" + f.namespaceID)
            Function.currFunction = Function.nullFunction
        }
        if (f.isEntrance
            && ctx.functionParams().normalParams().parameterList().parameter().size != 0
            && (ctx.functionParams().readOnlyParams() == null || ctx.functionParams().readOnlyParams().parameterList().parameter().size != 0)
        ) {
            LogProcessor.error("Entrance function shouldn't have parameter:" + f.namespaceID)
        }
        return null
    }

    override fun visitCompileTimeFuncDeclaration(ctx: mcfppParser.CompileTimeFuncDeclarationContext): Any? {
        Project.ctx = ctx
        //创建函数对象
        val f: Function
        //是否是编译时函数
        val identifier : String = ctx.Identifier().text
        f = CompileTimeFunction(
            identifier,Project.currNamespace,
            MCFPPType.parseFromIdentifier(if(ctx.functionReturnType() == null) "void" else ctx.functionReturnType().text, typeScope),
            ctx.functionBody()
        )
        //解析参数
        f.addParamsFromContext(ctx.functionParams())
        //TODO 解析函数的注解
        //不是类的成员
        f.ownerType = Function.Companion.OwnerType.NONE
        //写入域
        val namespace = GlobalField.localNamespaces[f.namespace]!!
        if (!namespace.field.hasFunction(f)) {
            f.setField(namespace.field)
            namespace.field.addFunction(f,false)
        } else {
            LogProcessor.error("Already defined function:" + f.namespaceID)
            Function.currFunction = Function.nullFunction
        }
        if (f.isEntrance
            && ctx.functionParams().normalParams().parameterList().parameter().size != 0
            && (ctx.functionParams().readOnlyParams() == null || ctx.functionParams().readOnlyParams().parameterList().parameter().size != 0)
        ) {
            LogProcessor.error("Entrance function shouldn't have parameter:" + f.namespaceID)
        }
        return null
    }


    override fun visitExtensionFunctionDeclaration(ctx: mcfppParser.ExtensionFunctionDeclarationContext?): Any? {
        Project.ctx = ctx!!
        val ownerType : Function.Companion.OwnerType
        //获取被拓展的类
        val data : CompoundData = if(ctx.type().className() == null){
            ownerType = Function.Companion.OwnerType.BASIC
            when(ctx.type().text){
                "int" -> MCInt.data
                else -> {
                    LogProcessor.error("Cannot add extension function to ${ctx.type().text}")
                    return null
                }
            }
        }else{
            val clsStr = ctx.type().className().text.split(":")
            val id : String
            val nsp : String?
            if(clsStr.size == 1){
                id = clsStr[0]
                nsp = null
            }else{
                id = clsStr[1]
                nsp = clsStr[0]
            }
            val qwq: Class? = GlobalField.getClass(nsp, id)
            if (qwq == null) {
                val pwp = GlobalField.getTemplate(nsp, id)
                if(pwp == null){
                    LogProcessor.error("Undefined class or struct:" + ctx.type().className().text)
                    return null
                }else{
                    ownerType = Function.Companion.OwnerType.TEMPLATE
                    pwp
                }
            }else{
                ownerType = Function.Companion.OwnerType.CLASS
                qwq
            }
        }
        //创建函数对象
        val f = if(ctx.functionParams().readOnlyParams() != null && ctx.functionParams().readOnlyParams().parameterList().parameter().size != 0){
            GenericExtensionFunction(ctx.Identifier().text, data, Project.currNamespace, MCFPPType.parseFromIdentifier(ctx.functionReturnType().text, typeScope), ctx.functionBody())
        }else{
            ExtensionFunction(ctx.Identifier().text, data, Project.currNamespace, MCFPPType.parseFromIdentifier(ctx.functionReturnType().text, typeScope))
        }
        //解析参数
        f.accessModifier = AccessModifier.PUBLIC
        f.ownerType = ownerType
        f.isStatic = ctx.STATIC() != null
        f.addParamsFromContext(ctx.functionParams())
        val field = if(f.isStatic) data.staticField else data.field
        //注册函数
        if (!field.addFunction(f,false)) {
            LogProcessor.error("Already defined function:" + ctx.Identifier().text + "in class " + Class.currClass!!.identifier)
            Function.currFunction = Function.nullFunction
        }
        return null
    }

    /**
     * native函数的声明
     * @param ctx the parse tree
     * @return 如果是全局，返回null，否则返回这个函数对象
     */
    
    override fun visitNativeFuncDeclaration(ctx: mcfppParser.NativeFuncDeclarationContext): Any? {
        Project.ctx = ctx
        val nf = NativeFunction(ctx.Identifier().text, MCFPPType.parseFromIdentifier(ctx.functionReturnType()?.text?:"void", typeScope), Project.currNamespace)
        nf.addParamsFromContext(ctx.functionParams())
        try {
            //根据JavaRefer找到类
            val refer = ctx.javaRefer().text
            val clsName = refer.substring(0,refer.lastIndexOf('.'))
            val clazz = java.lang.Class.forName(clsName)
            val methods = clazz.methods
            var hasFind = false
            for(method in methods){
                val mniRegister = method.getAnnotation(MNIRegister::class.java) ?: continue
                //解析MNIMethod注解成员
                val readOnlyType = mniRegister.readOnlyParams.map {
                    MCFPPType.parseFromIdentifier(it.split(" ", limit = 2)[0], Namespace.currNamespaceField)
                }
                val normalType = mniRegister.normalParams.map {
                    MCFPPType.parseFromIdentifier(it.split(" ", limit = 2)[0], Namespace.currNamespaceField)
                }
                //比对
                if(nf.readOnlyParams.map { it.type } == readOnlyType && nf.normalParams.map { it.type } == normalType){
                    hasFind = true
                    nf.javaMethod = method
                    break
                }
            }
            if(!hasFind){
                throw NoSuchMethodException("Cannot find method ${ctx.Identifier().text} with correct parameters in class $clsName")
            }
        } catch (e: ClassNotFoundException) {
            LogProcessor.error("Cannot find java class: " + e.message)
            e.printStackTrace()
            return null
        }
        //写入域
        val namespace = GlobalField.localNamespaces[nf.namespace]!!
        //是普通的函数
        nf.ownerType = Function.Companion.OwnerType.NONE
        if (!namespace.field.hasFunction(nf)) {
            namespace.field.addFunction(nf,false)
        } else {
            LogProcessor.error("Already defined function:" + ctx.Identifier().text)
            Function.currFunction = Function.nullFunction
        }
        return nf
    }
//endregion

//region template
    override fun visitTemplateDeclaration(ctx: TemplateDeclarationContext?): Any? {
        Project.ctx = ctx!!
        //注册模板
        val id = ctx.classWithoutNamespace().text
        val namespace1 = GlobalField.localNamespaces[Project.currNamespace]!!
        if (namespace1.field.hasTemplate(id)) {
            //重复声明
            LogProcessor.error("Template has been defined: $id in namespace ${Project.currNamespace}")
            Template.currTemplate = namespace1.field.getTemplate(id)
        }
        val template = Template(id, LazyWrapper{MCFPPType.parseFromIdentifier(ctx.type().text, typeScope)}, Project.currNamespace)
        if (ctx.className() != null) {
            //是否存在继承
            val qwq = ctx.className().text.split(":")
            val identifier: String
            val namespace : String?
            if(qwq.size == 1){
                identifier = qwq[0]
                namespace = null
            }else{
                namespace = qwq[0]
                identifier = qwq[1]
            }
            val s = GlobalField.getTemplate(namespace, identifier)
            if(s == null){
                LogProcessor.error("Undefined struct: " + ctx.className().text)
            }else{
                template.parent.add(s)
            }
        }
        namespace1.field.addTemplate(id, template)
        Template.currTemplate = template
        typeScope = template.field
        //解析成员
        //先静态
        isStatic = true
        //先解析函数
        for (c in ctx.templateBody().staticTemplateMemberDeclaration()) {
            if (c!!.templateMember().templateFunctionDeclaration() != null) {
                visit(c)
            }
        }
        //再解析变量
        for (c in ctx.templateBody().staticTemplateMemberDeclaration()) {
            if (c!!.templateMember().templateFieldDeclaration() != null) {
                visit(c)
            }
        }
        //后成员
        isStatic = false
        //先解析函数和构造函数
        for (c in ctx.templateBody().templateMemberDeclaration()) {
            if (c!!.templateMember().templateFunctionDeclaration() != null) {
                visit(c)
            }
        }
        //再解析变量
        for (c in ctx.templateBody().templateMemberDeclaration()) {
            if (c!!.templateMember().templateFieldDeclaration() != null) {
                visit(c)
            }
        }
        Template.currTemplate = null
        typeScope = MCFPPFile.currFile!!.field
        return null
    }

    
    override fun visitStaticTemplateMemberDeclaration(ctx: mcfppParser.StaticTemplateMemberDeclarationContext): Any? {
        Project.ctx = ctx
        val m = visit(ctx.templateMember()) as Member
        //访问修饰符
        if (ctx.accessModifier() != null) {
            m.accessModifier = AccessModifier.valueOf(ctx.accessModifier().text.uppercase(Locale.getDefault()))
        }
        m.isStatic = true
        Template.currTemplate!!.addMember(m)
        if(m is Function){
            m.field.removeVar("this")
        }
        return null
    }

    /**
     * 类成员的声明。由于函数声明可以后置，因此需要先查明函数声明情况再进行变量的注册以及初始化。
     * <pre>
     * `classMemberDeclaration
     * :   accessModifier? (STATIC)? classMember
     * ;
    `</pre> *
     * @param ctx the parse tree
     * @return null
     */
    
    override fun visitTemplateMemberDeclaration(ctx: mcfppParser.TemplateMemberDeclarationContext): Any? {
        Project.ctx = ctx
        val m = visit(ctx.templateMember())
        val accessModifier = AccessModifier.valueOf(ctx.accessModifier().text.uppercase(Locale.getDefault()))
        //访问修饰符
        if(m is Member){
            if (ctx.accessModifier() != null) {
                m.accessModifier = accessModifier
            }
        }else if(m is ArrayList<*>){
            for(v in m){
                (v as Var<*>).accessModifier = accessModifier
            }
        }
        return null
    }

    
    override fun visitTemplateMember(ctx: mcfppParser.TemplateMemberContext): Any? {
        Project.ctx = ctx
        return if (ctx.templateFunctionDeclaration() != null) {
            visit(ctx.templateFunctionDeclaration())
        } else if (ctx.templateFieldDeclaration() != null) {
            visit(ctx.templateFieldDeclaration())
        } else{
            return null
        }
    }

    override fun visitTemplateFunctionDeclaration(ctx: mcfppParser.TemplateFunctionDeclarationContext): Any {
        Project.ctx = ctx
        val type = MCFPPType.parseFromIdentifier(if(ctx.functionReturnType() == null) "void" else ctx.functionReturnType().text, typeScope)
        //创建函数对象
        val f = if(ctx.functionParams().readOnlyParams() != null && ctx.functionParams().readOnlyParams().parameterList().parameter().size != 0){
            GenericFunction(
                ctx.Identifier().text,
                Template.currTemplate!!,
                ctx.parent is mcfppParser.StaticTemplateMemberDeclarationContext,
                type,
                ctx.functionBody()
            )
        }else {
            Function(
                ctx.Identifier().text,
                Template.currTemplate!!,
                ctx.parent is mcfppParser.StaticTemplateMemberDeclarationContext,
                type
            )
        }
        if(!isStatic){
            val varType = Template.currTemplate!!.getType()
            val thisObj = Var.build("this",varType , f)
            f.field.putVar("this",thisObj)
        }
        //解析参数
        f.addParamsFromContext(ctx.functionParams())
        //注册函数
        if (Template.currTemplate!!.field.hasFunction(f) || Template.currTemplate!!.staticField.hasFunction(f)) {
            LogProcessor.error("Already defined function:" + ctx.Identifier().text + "in struct " + Template.currTemplate!!.identifier)
            Function.currFunction = Function.nullFunction
        }
        return f
    }

    override fun visitTemplateFieldDeclaration(ctx: mcfppParser.TemplateFieldDeclarationContext): ArrayList<Var<*>> {
        Project.ctx = ctx
        val re = ArrayList<Var<*>>()
        for (i in ctx.templateFieldDeclarationExpression()){
            //变量生成
            val `var` = visit(i) as Var<*>
            re.add(`var`)
        }
        return re
    }

    override fun visitTemplateFieldDeclarationExpression(ctx: mcfppParser.TemplateFieldDeclarationExpressionContext): Var<*>? {
        //TODO 地狱绘图（指parent串）
        val `var` = UnresolvedVar(
            ctx.Identifier().text,
            UnresolvedType ((ctx.parent.parent.parent.parent.parent as TemplateDeclarationContext).type().text),
            typeScope
        )
        //是否是静态的
        `var`.isStatic = isStatic
        //只有可能是结构体成员
        if (Template.currTemplate!!.field.containVar(ctx.Identifier().text) || Template.currTemplate!!.staticField.containVar(ctx.Identifier().text)
        ) {
            LogProcessor.error("Duplicate defined variable name:" + ctx.Identifier().text)
            return null
        }
        return `var`
    }

    //endregion
}