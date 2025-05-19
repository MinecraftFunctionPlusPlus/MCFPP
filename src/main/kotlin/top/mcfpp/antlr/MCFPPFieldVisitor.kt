package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.Project.withCompilationContext
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.annotations.MNIBinaryOperator
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.antlr.mcfppParser.ClassDeclarationContext
import top.mcfpp.antlr.mcfppParser.TemplateDeclarationContext
import top.mcfpp.compiletime.CompileTimeFunction
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.obj.ClassPointer
import top.mcfpp.exception.UndefinedException
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.io.MCFPPFile
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.Member
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.*
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.field.IFieldWithType
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.generic.GenericExtensionFunction
import top.mcfpp.model.generic.GenericFunction
import top.mcfpp.model.property.*
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPEnumType
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

    protected var isStatic = false

    protected lateinit var typeScope : IFieldWithType

    private var currClassOrTemplate: CompoundData? = null

    /**
     * 遍历整个文件。一个文件包含了命名空间的声明，函数的声明，类的声明以及全局变量的声明。全局变量是可以跨文件调用的。
     * @param ctx the parse tree
     * @return null
     */
    override fun visitCompilationUnit(ctx: mcfppParser.CompilationUnitContext): Any? = withCompilationContext(ctx) {
        typeScope = GlobalField.localNamespaces[Project.currNamespace]!!.field
        //文件结构，类和函数
        for (t in ctx.typeDeclaration()) {
            visit(t)
        }
        return null
    }

    override fun visitNamespaceFieldDeclaration(ctx: mcfppParser.NamespaceFieldDeclarationContext): Any? = withCompilationContext(ctx) {
        Function.currFunction = NoStackFunction("", Function.nullFunction)
        //变量生成
        val fieldModifier = ctx.fieldModifier()?.text
        val namespace = GlobalField.localNamespaces[Project.currNamespace]!!
        if(ctx.VAR() != null){
            //自动判断类型
            val init: Var<*> = MCFPPExprVisitor().visitValue(ctx.value())
            var `var` = if(fieldModifier == "import"){
                val qwq = init.type.buildUnConcrete(ctx.Identifier().text, namespace)
                qwq.hasAssigned = true
                qwq
            }else{
                init.type.build(ctx.Identifier().text, namespace)
            }
            `var`.nbtPath = NBTPath.global.memberIndex(`var`.identifier)
            //变量赋值
            `var` = `var`.assignedBy(init)
            //一定是函数变量
            if (!namespace.field.putVar(ctx.Identifier().text, `var`, false)) {
                LogProcessor.error("Duplicate defined variable name:" + ctx.Identifier().text)
            }
            when(fieldModifier){
                "const" -> {
                    if(!`var`.hasAssigned){
                        LogProcessor.error("The const field ${`var`.identifier} must be initialized.")
                    }
                    `var`.isConst = true
                }
                "dynamic" -> {
                    LogProcessor.error("Modifier 'dynamic' cannot used in namespace field declaration")
                }
            }
        }else{
            //获取类型
            val type = MCFPPType.parseFromContext(ctx.type(), namespace.field)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.type().text))
                MCFPPBaseType.Any
            }
            for (c in ctx.namespaceFieldDeclarationExpression()){
                //函数变量，生成
                var `var` = if(fieldModifier == "import"){
                    val qwq = type.buildUnConcrete(c.Identifier().text, namespace)
                    qwq.hasAssigned = true
                    qwq
                }else{
                    type.build(c.Identifier().text, namespace)
                }
                //变量注册
                //一定是函数变量
                if (namespace.field.containVar(c.Identifier().text)) {
                    LogProcessor.error("Duplicate defined variable name:" + c.Identifier().text)
                }
                `var`.nbtPath = NBTPath.global.memberIndex(`var`.identifier)
                //变量初始化
                if (c.value() != null) {
                    val init: Var<*> = MCFPPExprVisitor(enumType = if(type is MCFPPEnumType) type else null).visitValue(c.value())
                    `var` = `var`.assignedBy(init)
                }
                when(fieldModifier){
                    "const" -> {
                        if(!`var`.hasAssigned){
                            LogProcessor.error("The const field ${`var`.identifier} must be initialized.")
                        }
                        `var`.isConst = true
                    }
                    "dynamic" -> {
                        LogProcessor.error("Modifier 'dynamic' cannot used in namespace field declaration")
                    }
                }
                namespace.field.putVar(`var`.identifier, `var`, true)
            }
        }
        Function.currFunction = Function.nullFunction
        return null
    }

//region interface

    override fun visitInterfaceDeclaration(ctx: mcfppParser.InterfaceDeclarationContext): Any? = withCompilationContext(ctx) {
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
        currClassOrTemplate = Interface.currInterface
        //接口成员
        for (m in ctx.interfaceBody().interfaceFunctionDeclaration()){
            visit(m)
        }
        typeScope = MCFPPFile.currFile!!.field.namespaceField
        currClassOrTemplate = null
        return null
    }


    override fun visitInterfaceFunctionDeclaration(ctx: mcfppParser.InterfaceFunctionDeclarationContext): Any? = withCompilationContext(ctx) {
        //创建函数对象
        val f = Function(
            ctx.Identifier().text,
            Interface.currInterface!!,
            null
        )
        f.returnType = ctx.functionReturnType()?.type()?.let {
            MCFPPType.parseFromContext(it.typeWithoutExcl().type(), typeScope)?:  run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(it.text))
                MCFPPBaseType.Any
            }
        }?: MCFPPPrivateType.Void
        //解析参数
        f.addParamsFromContext(ctx.functionParams())
        //注册函数
        if (Interface.currInterface!!.field.hasFunction(f, true)) {
            LogProcessor.error("Already defined function:" + ctx.Identifier().text + "in class " + Class.currClass!!.identifier)
            Function.currFunction = Function.nullFunction
        }
        return null
    }
//endregion

//region class
    /**
     * 类的声明
     * @param ctx the parse tree
     * @return null
     */

    override fun visitClassDeclaration(ctx: ClassDeclarationContext): Any? = withCompilationContext(ctx) {
        //注册类
        val id = ctx.classWithoutNamespace().text
        val namespace = GlobalField.localNamespaces[Project.currNamespace]!!
        if(ctx.readOnlyParams() != null){
            //如果是泛型类，将类型实例化，但暂时不编译
            val types = ctx.readOnlyParams().parameterList().parameter().map { MCFPPType.parseFromContext(it.type(), namespace.field)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(it.text))
                MCFPPBaseType.Any
            } }
            val clazz = namespace.field.getClass(id, types)!!
            for (p in clazz.readOnlyParams) {
                p.type = MCFPPType.parseFromString(p.typeIdentifier, namespace.field)
            }
            return null
        }
        val clazz = if (namespace.field.hasClass(id)) {
            namespace.field.getClass(id)
        } else {
            throw UndefinedException("Class Should have been defined: $id")
        }
        Class.currClass = clazz
        currClassOrTemplate = clazz
        typeScope = Class.currClass!!.field
        isStatic = false
        //解析类中的成员
        //先解析函数和构造函数
        for (c in ctx.classBody().classMemberDeclaration()) {
            if (c.classMember() != null
                && (c.classMember().classFunctionDeclaration() != null
                        || c.classMember().abstractClassFunctionDeclaration() != null
                        || c.classMember().nativeClassFunctionDeclaration() != null
                        || c.classMember().classConstructorDeclaration() != null)
            ) {
                visit(c)
            }
        }
        //再解析变量
        for (c in ctx.classBody().classMemberDeclaration()) {
            if (c.classMember() != null && c.classMember().classFieldDeclaration() != null) {
                visit(c)
            }
        }
        //如果没有构造函数，自动添加默认的空构造函数
        if (Class.currClass!!.constructors.size == 0) {
            Class.currClass!!.addConstructor(ClassConstructor(Class.currClass!!))
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
                LogProcessor.error("Class ${Class.currClass} must either be declared abstract or implement abstract method ${il!!}")
            }
        }
        Class.currClass = null
        currClassOrTemplate = null
        typeScope = MCFPPFile.currFile!!.field.namespaceField
        return null
    }

    override fun visitObjectClassDeclaration(ctx: mcfppParser.ObjectClassDeclarationContext): Any? = withCompilationContext(ctx) {
        //注册类
        val id = ctx.classWithoutNamespace().text
        val namespace = GlobalField.localNamespaces[Project.currNamespace]!!
        if(ctx.readOnlyParams() != null){
            return null
        }
        val clazz = namespace.field.getObject(id)
        if(clazz !is ObjectClass){
            throw UndefinedException("Class should have been defined: $id")
        }
        //链接伴随对象
        val cls = namespace.field.getClass(id)
        if(cls != null){
            if(cls.objectClass != null){
                LogProcessor.error("Duplicate object class definition: $id")
            }else{
                cls.objectClass = clazz
            }
        }
        Class.currClass = clazz
        currClassOrTemplate = clazz
        typeScope = Class.currClass!!.field
        isStatic = true
        //解析类中的成员
        //先解析函数和构造函数
        for (c in ctx.classBody().classMemberDeclaration()) {
            if (c.classMember() != null
                && (c.classMember().classFunctionDeclaration() != null
                        || c.classMember().abstractClassFunctionDeclaration() != null
                        || c.classMember().nativeClassFunctionDeclaration() != null
                        || c.classMember().classConstructorDeclaration() != null)
                ) {
                visit(c)
            }
        }
        //再解析变量
        for (c in ctx.classBody().classMemberDeclaration()) {
            if (c.classMember() != null && c.classMember().classFieldDeclaration() != null) {
                visit(c)
            }
        }
        Class.currClass = null
        currClassOrTemplate = null
        typeScope = MCFPPFile.currFile!!.field.namespaceField
        return null
    }

    /**
     * 类成员的声明。由于函数声明可以后置，因此需要先查明函数声明情况再进行变量的注册以及初始化。
     * @param ctx the parse tree
     * @return null
     */
    override fun visitClassMemberDeclaration(ctx: mcfppParser.ClassMemberDeclarationContext): Any? = withCompilationContext(ctx) {
        val m = visit(ctx.classMember())
        if(m is Member){
            //访问修饰符
            m.accessModifier = AccessModifier.valueOf((ctx.accessModifier()?.text?:"public").uppercase(Locale.getDefault()))
            if (m !is ClassConstructor) {
                Class.currClass!!.addMember(m)
            }else{
                Class.currClass!!.addConstructor(m)
            }
        }else if(m is Pair<*,*>){//Pair<out Var<*>?, out Property?>
            val v = m.first as Var<*>?
            val p = m.second as Property?
            if(v == null || p == null) return null
            //访问修饰符
            v.accessModifier = AccessModifier.valueOf((ctx.accessModifier()?.text?:"public").uppercase(Locale.getDefault()))
            p.accessModifier = v.accessModifier
            Class.currClass!!.addMember(v)
            Class.currClass!!.addMember(p)
        }
        return null
    }

    override fun visitClassMember(ctx: mcfppParser.ClassMemberContext): Any? = withCompilationContext(ctx) {
        return if (ctx.nativeClassFunctionDeclaration() != null) {
            visitNativeClassFunctionDeclaration(ctx.nativeClassFunctionDeclaration())
        } else if (ctx.classFunctionDeclaration() != null) {
            visitClassFunctionDeclaration(ctx.classFunctionDeclaration())
        } else if (ctx.classFieldDeclaration() != null) {
            visitClassFieldDeclaration(ctx.classFieldDeclaration())
        } else if (ctx.classConstructorDeclaration() != null) {
            visitClassConstructorDeclaration(ctx.classConstructorDeclaration())
        }else{
            return null
        }
    }

    /**
     * 类方法的声明
     * @param ctx the parse tree
     * @return 这个类方法的对象
     */
    
    override fun visitClassFunctionDeclaration(ctx: mcfppParser.ClassFunctionDeclarationContext): Any = withCompilationContext(ctx) {
        //创建函数对象
        val f = if(ctx.functionParams().readOnlyParams() != null && ctx.functionParams().readOnlyParams().parameterList().parameter().size != 0){
            GenericFunction(
                ctx.Identifier().text,
                Class.currClass!!,
                ctx.functionBody()
            )
        }else{
            Function(
                ctx.Identifier().text,
                Class.currClass!!,
                ctx.functionBody()
            )
        }
        f.returnType = if(ctx.functionReturnType()?.type() != null){
            MCFPPType.parseFromContext(ctx.functionReturnType().type(), typeScope)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.functionReturnType().text))
                MCFPPBaseType.Any
            }
        }else{
            MCFPPPrivateType.Void
        }
        if(!isStatic){
            val thisObj = Class.currClass!!.getType().buildUnConcrete("this")
            f.field.putVar("this",thisObj)
        }
        //解析参数
        f.addParamsFromContext(ctx.functionParams())
        //注册函数
        if (Class.currClass!!.field.hasFunction(f, true)) {
            if(ctx.OVERRIDE() != null){
                if(isStatic){
                    LogProcessor.error("Cannot override static method ${ctx.Identifier()}")
                    throw Exception()
                }
                f.isOverride = true
            }else{
                LogProcessor.error("Already defined function:" + ctx.Identifier().text + "in class " + Class.currClass!!.identifier)
                Function.currFunction = Function.nullFunction
            }
        }else {
            if(ctx.OVERRIDE()!= null){
                LogProcessor.error("Method ${f.identifier} in class ${Class.currClass!!.namespaceID} overrides nothing")
            }
        }
        f.ast = null
        return f
    }

    
    override fun visitAbstractClassFunctionDeclaration(ctx: mcfppParser.AbstractClassFunctionDeclarationContext): Any = withCompilationContext(ctx) {
        //抽象函数没有函数体，不能作为GenericFunction
        //创建函数对象
        val f = Function(
            ctx.Identifier().text,
            Class.currClass!!,
            null
        )
        f.returnType = if(ctx.functionReturnType()?.type() != null){
            MCFPPType.parseFromContext(ctx.functionReturnType().type(), typeScope)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.functionReturnType().text))
                MCFPPBaseType.Any
            }
        }else{
            MCFPPPrivateType.Void
        }
        f.isAbstract = true
        if(f.isStatic){
            LogProcessor.error("Static Function cannot be abstract: ${ctx.Identifier().text} in class ${Class.currClass!!.identifier}" )
            throw Exception()
        }
        //解析参数
        f.addParamsFromContext(ctx.functionParams())
        //注册函数
        if (Class.currClass!!.field.hasFunction(f, true)) {
            LogProcessor.error("Already defined function:" + ctx.Identifier().text + "in class " + Class.currClass!!.identifier)
            Function.currFunction = Function.nullFunction
        }
        return f
    }

    override fun visitNativeClassFunctionDeclaration(ctx: mcfppParser.NativeClassFunctionDeclarationContext): Any? = withCompilationContext(ctx) {
        val nf = NativeFunction(ctx.Identifier().text, Project.currNamespace)
        nf.returnType = if(ctx.functionReturnType()?.type() != null){
            MCFPPType.parseFromContext(ctx.functionReturnType().type(), typeScope)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.functionReturnType().text))
                MCFPPBaseType.Any
            }
        }else{
            MCFPPPrivateType.Void
        }
        nf.addParamsFromContext(ctx.functionParams())
        //是类成员
        nf.ownerType = Function.Companion.OwnerType.CLASS
        try {
            //根据JavaRefer找到类
            val refer = ctx.javaRefer().text
            val clsName = refer.substring(0,refer.lastIndexOf('.'))
            val clazz = Project.classLoader.loadClass(clsName)
            val methods = clazz.methods
            var hasFind = false
            for(method in methods){
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
    override fun visitClassConstructorDeclaration(ctx: mcfppParser.ClassConstructorDeclarationContext): Any = withCompilationContext(ctx) {
        //类构造函数
        //创建构造函数对象，注册函数
        val f = ClassConstructor(Class.currClass!!)
        f.addParamsFromContext(ctx.normalParams())
        if(Class.currClass!!.hasConstructor(f)){
            LogProcessor.error("Already defined constructor:  constructor(" + ctx.normalParams().text + ") in class " + Class.currClass)
        }
        return f
    }

    private lateinit var currVar: Var<*>
    /**
     * 类字段的声明
     * @param ctx the parse tree
     * @return null
     */
    @InsertCommand
    override fun visitClassFieldDeclaration(ctx: mcfppParser.ClassFieldDeclarationContext): Pair<Var<*>?, Property?> = withCompilationContext(ctx) {
        //只有类字段构建
        var type = ctx.type()?.let { MCFPPType.parseFromContext(it, typeScope)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(it.text))
                MCFPPBaseType.Any
            }
        }
        var init: Var<*>? = null
        //变量的初始化
        if (ctx.expression() != null) {
            Class.currClass!!.classPreInit.runInFunction {
                //是类的成员
                Function.addComment(ctx.text)
                init = MCFPPExprVisitor().visit(ctx.expression())!!
            }
        }
        //类型推断
        if(type == null && init == null){
            LogProcessor.error("Class field ${ctx.Identifier().text} must have a type or an initializer")
            return null to null
        }else if(type == null){
            type = init!!.type
        }
        val `var` = type.buildUnConcrete(ctx.Identifier().text, Class.currClass!!)
        if(Class.currClass is ObjectClass && `var` is OnScoreboard){
            `var`.name = (Class.currClass as ObjectClass).mcuuid.uuid.toString()
        }else if(`var` is OnScoreboard){
            `var`.name = "@s"
        }
        `var`.isDynamic = true
        `var`.parent = ClassPointer(Class.currClass!!, "this")
        if (Class.currClass!!.field.containVar(ctx.Identifier().text)) {
            LogProcessor.error("Duplicate defined variable name:" + ctx.Identifier().text)
            return null to null
        }
        if(init != null) {
            Class.currClass!!.classPreInit.runInFunction {
                try {
                    `var`.assignedBy(init!!)
                } catch (e: VariableConverseException) {
                    LogProcessor.error("Cannot convert " + init!!.javaClass + " to " + `var`.javaClass)
                    throw VariableConverseException()
                }
                `var`.hasAssigned = true
            }
        }
        //属性访问器
        `var`.parent = null
        currVar = `var`
        val properties = (ctx.accessor()?.let{visit(ctx.accessor())}?: Property.buildSimpleProperty(`var`)) as Property
        return `var` to properties
    }

    override fun visitAccessor(ctx: mcfppParser.AccessorContext): Any? = withCompilationContext(ctx) {
        val getter = if(ctx.getter() != null){
            visit(ctx.getter()) as AbstractAccessor
        }else{
            null
        }
        val setter = if(ctx.setter() != null){
            visit(ctx.setter()) as AbstractMutator
        }else{
            null
        }
        return Property(currVar.identifier, getter, setter)
    }

    override fun visitGetter(ctx: mcfppParser.GetterContext): Any? = withCompilationContext(ctx) {
        return if(ctx.functionBody() != null){
            FunctionAccessor(currVar, currClassOrTemplate!!)
        }else if(ctx.javaRefer() != null){
            NativeAccessor(ctx.javaRefer().text, currClassOrTemplate!!, currVar)
        }else if(ctx.expression() != null){
            ExpressionAccessor(ctx.expression(), currVar)
        }else{
            SimpleAccessor()
        }
    }

    override fun visitSetter(ctx: mcfppParser.SetterContext): Any? = withCompilationContext(ctx) {
        return if(ctx.functionBody() != null){
            FunctionMutator(currVar, currClassOrTemplate!!)
        }else if(ctx.javaRefer() != null){
            NativeMutator(ctx.javaRefer().text, currClassOrTemplate!!, currVar)
        }else if(ctx.expression() != null){
            ExpressionMutator(ctx.expression(), currVar)
        }else{
            SimpleMutator()
        }
    }

    override fun visitOperationOverrideDeclaration(ctx: mcfppParser.OperationOverrideDeclarationContext): Any? = withCompilationContext(ctx) {
        return if(ctx.parent is ClassDeclarationContext){
            visitClassOperationOverrideDeclaration(ctx)
        }else{
            visitTemplateOperationOverrideDeclaration(ctx)
        }
    }

    private fun visitClassOperationOverrideDeclaration(ctx: mcfppParser.OperationOverrideDeclarationContext): Any? = withCompilationContext(ctx) {
        val op = ctx.supportOperator().text
        //创建函数对象
        val f = Function(
            ctx.supportOperator().text,
            Class.currClass!!,
            ctx.functionBody()
        )
        f.returnType = if(ctx.functionReturnType()?.type() != null){
            MCFPPType.parseFromContext(ctx.functionReturnType().type(), typeScope)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.functionReturnType().text))
                MCFPPBaseType.Any
            }
        }else{
            MCFPPPrivateType.Void
        }
        val thisObj = Class.currClass!!.getType().buildUnConcrete("this")
        f.field.putVar("this",thisObj)
        //解析参数
        f.addParamsFromContext(ctx.functionParams())
        //参数数量检查
        if(f.normalParams.size != 1){
            LogProcessor.error("Operator $op must have only one parameter: ${ctx.text}")
            return null
        }
        //注册函数
        if (Class.currClass!!.field.hasOperator(op, f.normalParams[0].type)) {
            LogProcessor.error("Already defined operator: $op(${f.normalParams[0].type}) in class " + Class.currClass!!.identifier)
        } else {
            Class.currClass!!.field.addOperator(op, f)
        }
        f.ast = null
        return f
    }

    private fun visitTemplateOperationOverrideDeclaration(ctx: mcfppParser.OperationOverrideDeclarationContext): Any? = withCompilationContext(ctx) {
        val op = ctx.supportOperator().text
        //创建函数对象
        val f = Function(
            ctx.supportOperator().text,
            DataTemplate.currTemplate!!,
            ctx.functionBody()
        )
        f.returnType = if(ctx.functionReturnType()?.type() != null){
            MCFPPType.parseFromContext(ctx.functionReturnType().type(), typeScope)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.functionReturnType().text))
                MCFPPBaseType.Any
            }
        }else{
            MCFPPPrivateType.Void
        }
        val thisObj = DataTemplate.currTemplate!!.getType().buildUnConcrete("this")
        f.field.putVar("this",thisObj)
        //解析参数
        f.addParamsFromContext(ctx.functionParams())
        //参数数量检查
        if(f.normalParams.size != 1){
            LogProcessor.error("Operator $op must have only one parameter: ${ctx.text}")
            return null
        }
        //注册函数
        if (DataTemplate.currTemplate!!.field.hasOperator(op, f.normalParams[0].type)) {
            LogProcessor.error("Already defined operator: $op(${f.normalParams[0].type}) in class " + DataTemplate.currTemplate!!.identifier)
        } else {
            DataTemplate.currTemplate!!.field.addOperator(op, f)
        }
        f.ast = null
        return f
    }

    override fun visitNativeOperationOverrideDeclaration(ctx: mcfppParser.NativeOperationOverrideDeclarationContext): Any? = withCompilationContext(ctx) {
        return if(ctx.parent is ClassDeclarationContext){
            visitClassNativeOperationOverrideDeclaration(ctx)
        }else{
            visitTemplateNativeOperationOverrideDeclaration(ctx)
        }
    }

    private fun visitClassNativeOperationOverrideDeclaration(ctx: mcfppParser.NativeOperationOverrideDeclarationContext): Any? = withCompilationContext(ctx) {
        val op = ctx.supportOperator().text
        val nf = NativeFunction(op, Project.currNamespace)
        nf.returnType = if(ctx.functionReturnType()?.type() != null){
            MCFPPType.parseFromContext(ctx.functionReturnType().type(), typeScope)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.functionReturnType().text))
                MCFPPBaseType.Any
            }
        }else{
            MCFPPPrivateType.Void
        }
        nf.addParamsFromContext(ctx.functionParams())
        //参数数量检查
        if(nf.normalParams.size != 1){
            LogProcessor.error("Operator $op must have only one parameter: ${ctx.text}")
            return null
        }
        try {
            //根据JavaRefer找到类
            val refer = ctx.javaRefer().text
            val clsName = refer.substring(0,refer.lastIndexOf('.'))
            val clazz = Project.classLoader.loadClass(clsName)
            val methods = clazz.methods
            var hasFind = false
            for(method in methods){
                val mniRegister = method.getAnnotation(MNIBinaryOperator::class.java) ?: continue
                //比对
                if(nf.normalParams[0].type.typeName == mniRegister.paramType){
                    hasFind = true
                    nf.javaMethod = method
                    break
                }
            }
            if(!hasFind){
                throw NoSuchMethodException("Cannot find operator $op(${nf.normalParams[0].type}) in jvm class $clsName")
            }
        } catch (e: ClassNotFoundException) {
            LogProcessor.error("Cannot find java class: " + e.message)
            return null
        }
        //注册函数
        if (Class.currClass!!.field.hasOperator(op, nf.normalParams[0].type)) {
            LogProcessor.error("Already defined operator: $op(${nf.normalParams[0].type}) in template " + Class.currClass!!.identifier)
        } else {
            Class.currClass!!.field.addOperator(op, nf)
        }
        return nf
    }

    private fun visitTemplateNativeOperationOverrideDeclaration(ctx: mcfppParser.NativeOperationOverrideDeclarationContext): Any? = withCompilationContext(ctx) {
        val op = ctx.supportOperator().text
        val nf = NativeFunction(op, Project.currNamespace)
        nf.returnType = if(ctx.functionReturnType()?.type() != null){
            MCFPPType.parseFromContext(ctx.functionReturnType().type(), typeScope)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.functionReturnType().text))
                MCFPPBaseType.Any
            }
        }else{
            MCFPPPrivateType.Void
        }
        nf.addParamsFromContext(ctx.functionParams())
        //参数数量检查
        if(nf.normalParams.size != 1){
            LogProcessor.error("Operator $op must have only one parameter: ${ctx.text}")
            return null
        }
        try {
            //根据JavaRefer找到类
            val refer = ctx.javaRefer().text
            val clsName = refer.substring(0,refer.lastIndexOf('.'))
            val clazz = Project.classLoader.loadClass(clsName)
            val methods = clazz.methods
            var hasFind = false
            for(method in methods){
                val mniRegister = method.getAnnotation(MNIBinaryOperator::class.java) ?: continue
                //比对
                if(nf.normalParams[0].type.typeName == mniRegister.paramType){
                    hasFind = true
                    nf.javaMethod = method
                    break
                }
            }
            if(!hasFind){
                throw NoSuchMethodException("Cannot find operator $op(${nf.normalParams[0].type}) in jvm class $clsName")
            }
        } catch (e: ClassNotFoundException) {
            LogProcessor.error("Cannot find java class: " + e.message)
            return null
        }
        //注册函数
        if (DataTemplate.currTemplate!!.field.hasOperator(op, nf.normalParams[0].type)) {
            LogProcessor.error("Already defined operator: $op(${nf.normalParams[0].type}) in template " + DataTemplate.currTemplate!!.identifier)
        } else {
            DataTemplate.currTemplate!!.field.addOperator(op, nf)
        }
        return nf
    }

//endregion

//region function
    /**
     * 函数的声明
     * @param ctx the parse tree
     * @return null
     */
    
    override fun visitFunctionDeclaration(ctx: mcfppParser.FunctionDeclarationContext): Any? = withCompilationContext(ctx) {
        //创建函数对象
        val identifier = ctx.Identifier().text
        val f = if(ctx.functionParams()?.readOnlyParams() != null && ctx.functionParams().readOnlyParams().parameterList().parameter().size != 0){
            GenericFunction(identifier, Project.currNamespace, ctx.functionBody())
        }else {
            Function(identifier, Project.currNamespace, ctx.functionBody())
        }
        f.returnType = if(ctx.functionReturnType()?.type() != null){
            MCFPPType.parseFromContext(ctx.functionReturnType().type(), typeScope)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.functionReturnType().text))
                MCFPPBaseType.Any
            }
        }else{
            MCFPPPrivateType.Void
        }
        //解析参数
        ctx.functionParams()?.let { f.addParamsFromContext(it) }
        //不是类的成员
        f.ownerType = Function.Companion.OwnerType.NONE
        //写入域
        val namespace = GlobalField.localNamespaces[f.namespace]!!
        if (namespace.field.hasFunction(f, true)) {
            LogProcessor.error("Already defined function: " + f.namespaceID)
            Function.currFunction = Function.nullFunction
        } else if(namespace.field.hasDeclaredType(f.identifier)) {
            LogProcessor.error("Function name conflicted with type name: " + f.identifier)
            Function.currFunction = Function.nullFunction
        } else{
            namespace.field.addFunction(f,false)
        }
        if (f.isEntrance
            && ctx.functionParams().normalParams().parameterList().parameter().size != 0
            && (ctx.functionParams().readOnlyParams() == null || ctx.functionParams().readOnlyParams().parameterList().parameter().size != 0)
            ) {
            LogProcessor.error("Entrance function shouldn't have parameter:" + f.namespaceID)
        }
        return null
    }

    override fun visitInlineFunctionDeclaration(ctx: mcfppParser.InlineFunctionDeclarationContext): Any? = withCompilationContext(ctx) {
        //创建函数对象
        val f: Function
        //是否是内联函数
        val identifier : String = ctx.Identifier().text
        f = InlineFunction(identifier, Project.currNamespace, ctx.functionBody())
        //解析参数
        f.addParamsFromContext(ctx.functionParams())
        //不是类的成员
        f.ownerType = Function.Companion.OwnerType.NONE
        //写入域
        val namespace = GlobalField.localNamespaces[f.namespace]!!
        if (!namespace.field.hasFunction(f, true)) {
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

    override fun visitCompileTimeFuncDeclaration(ctx: mcfppParser.CompileTimeFuncDeclarationContext): Any? = withCompilationContext(ctx) {
        //创建函数对象
        val f: Function
        //是否是编译时函数
        val identifier : String = ctx.Identifier().text
        f = CompileTimeFunction(
            identifier,Project.currNamespace,
            ctx.functionBody()
        )
        f.returnType = if(ctx.functionReturnType()?.type() != null){
            MCFPPType.parseFromContext(ctx.functionReturnType().type(), typeScope)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.functionReturnType().text))
                MCFPPBaseType.Any
            }
        }else{
            MCFPPPrivateType.Void
        }
        //解析参数
        f.addParamsFromContext(ctx.functionParams())
        //不是类的成员
        f.ownerType = Function.Companion.OwnerType.NONE
        //写入域
        val namespace = GlobalField.localNamespaces[f.namespace]!!
        if (!namespace.field.hasFunction(f, true)) {
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


    //TODO 单例的拓展函数
    override fun visitExtensionFunctionDeclaration(ctx: mcfppParser.ExtensionFunctionDeclarationContext): Any? = withCompilationContext(ctx)  {
        val ownerType : Function.Companion.OwnerType
        //获取被拓展的类
        val data : CompoundData = if(ctx.type().typeWithoutExcl().className() == null){
            ownerType = Function.Companion.OwnerType.BASIC
            when(ctx.type().text){
                "int" -> MCInt.data
                else -> {
                    LogProcessor.error("Cannot add extension function to ${ctx.type().text}")
                    return null
                }
            }
        }else{
            val (nsp, id) = ctx.type().typeWithoutExcl().className().text.splitNamespaceID()
            val qwq: Class? = GlobalField.getClass(nsp, id)
            if (qwq == null) {
                val pwp = GlobalField.getTemplate(nsp, id)
                if(pwp == null){
                    LogProcessor.error("Undefined class or struct:" + ctx.type().typeWithoutExcl().className().text)
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
            GenericExtensionFunction(ctx.Identifier().text, data, Project.currNamespace, ctx.functionBody())
        }else{
            ExtensionFunction(ctx.Identifier().text, data, Project.currNamespace, ctx.functionBody())
        }
        //解析参数
        f.accessModifier = AccessModifier.PUBLIC
        f.returnType = if(ctx.functionReturnType()?.type() != null){
            MCFPPType.parseFromContext(ctx.functionReturnType().type(), typeScope)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.functionReturnType().text))
                MCFPPBaseType.Any
            }
        }else{
            MCFPPPrivateType.Void
        }
        f.ownerType = ownerType
        f.addParamsFromContext(ctx.functionParams())
        val field = data.field
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
    
    override fun visitNativeFuncDeclaration(ctx: mcfppParser.NativeFuncDeclarationContext): Any? = withCompilationContext(ctx) {
        val nf = NativeFunction(ctx.Identifier().text, Project.currNamespace)
        nf.returnType = if(ctx.functionReturnType()?.type() != null){
            MCFPPType.parseFromContext(ctx.functionReturnType().type(), typeScope)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.functionReturnType().text))
                MCFPPBaseType.Any
            }
        }else{
            MCFPPPrivateType.Void
        }
        nf.addParamsFromContext(ctx.functionParams())
        try {
            //根据JavaRefer找到类
            val refer = ctx.javaRefer().text
            val clsName = refer.substring(0,refer.lastIndexOf('.'))
            val clazz = Project.classLoader.loadClass(clsName)
            val methods = clazz.methods
            var hasFind = false
            for(method in methods){
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
        if (!namespace.field.hasFunction(nf, true)) {
            namespace.field.addFunction(nf,false)
        } else {
            LogProcessor.error("Already defined function:" + ctx.Identifier().text)
            Function.currFunction = Function.nullFunction
        }
        return nf
    }
//endregion

//region template
    override fun visitTemplateDeclaration(ctx: TemplateDeclarationContext): Any? = withCompilationContext(ctx) {
        //获取注册的模板
        val id = ctx.classWithoutNamespace().text
        val namespace1 = GlobalField.localNamespaces[Project.currNamespace]!!
        val template = if(namespace1.field.hasTemplate(id)){
            namespace1.field.getTemplate(id)!!
        }else{
            throw UndefinedException("Template should have been defined: $id")
        }
        DataTemplate.currTemplate = template
        currClassOrTemplate = template
        typeScope = template.field
        for (c in ctx.className()){
            //是否存在继承
            val (namespace, identifier) = c.text.splitNamespaceID()
            val s = GlobalField.getTemplate(namespace, identifier)
            if(s == null){
                val o = GlobalField.getObject(namespace, identifier)
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
        if(ctx.AS() != null){
            template as TypeDataTemplate
            template.typeAs = MCFPPType.parseFromContext(ctx.type(), typeScope)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.text))
                MCFPPPrivateType.Void
            }
        }
        isStatic = false
        ctx.templateBody()?.let { visitTemplateBody(it) }

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
        currClassOrTemplate = null
        typeScope = MCFPPFile.currFile!!.field.namespaceField
        return null
    }

    override fun visitObjectTemplateDeclaration(ctx: mcfppParser.ObjectTemplateDeclarationContext): Any? = withCompilationContext(ctx) {
        //注册模板
        val id = ctx.classWithoutNamespace().text
        val namespace1 = GlobalField.localNamespaces[Project.currNamespace]!!
        val objectTemplate = namespace1.field.getObject(id)
        if(objectTemplate !is ObjectDataTemplate){
            throw UndefinedException("Template should have been defined: $id")
        }
        DataTemplate.currTemplate = objectTemplate
        currClassOrTemplate = objectTemplate
        typeScope = objectTemplate.field
        for (c in ctx.className()){
            //是否存在继承
            val (namespace, identifier) = c.text.splitNamespaceID()
            val s = GlobalField.getTemplate(namespace, identifier)
            if(s == null){
                val o = GlobalField.getObject(namespace, identifier)
                if(o is ObjectDataTemplate) {
                    if(o == objectTemplate){
                        LogProcessor.error("Infinitive reference: $id -> $identifier")
                    }else{
                        objectTemplate.extends(o)
                    }
                }else{
                    LogProcessor.error("Undefined template: " + c.text)
                }
            }else{
                objectTemplate.extends(s)
            }
        }
        ctx.templateBody()?.let { visitTemplateBody(it) }
        isStatic = true
        DataTemplate.currTemplate = null
        currClassOrTemplate = null
        typeScope = MCFPPFile.currFile!!.field.namespaceField
        return null
    }

    override fun visitAnonymousTemplateType(ctx: mcfppParser.AnonymousTemplateTypeContext): Any = withCompilationContext(ctx) {
        //注册模板
        val template = DataTemplate(TempPool.getAnonymousTemplateIdentify())
        val qwq = DataTemplate.currTemplate
        DataTemplate.currTemplate = template
        currClassOrTemplate = template
        typeScope = template.field
        for (c in ctx.className()){
            //是否存在继承
            val (namespace, identifier) = c.text.splitNamespaceID()
            val s = GlobalField.getTemplate(namespace, identifier)
            if(s == null){
                val o = GlobalField.getObject(namespace, identifier)
                if(o is ObjectDataTemplate) {
                    template.extends(o)
                }else{
                    LogProcessor.error("Undefined template: " + c.text)
                }
            }else{
                if(s == template){
                    LogProcessor.error("Infinitive reference: ${template.identifier} -> $identifier")
                }else{
                    template.extends(s)
                }
            }
        }
        isStatic = false
        visitTemplateBody(ctx.templateBody())
        //如果没有构造函数，生成默认的构造函数
        if(template.constructors.isEmpty()){
            template.addMember(DataTemplateConstructor(DataTemplate.currTemplate!!, null))
        }
        DataTemplate.currTemplate = qwq
        currClassOrTemplate = qwq
        typeScope = MCFPPFile.currFile!!.field.namespaceField
        return template
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
            m.accessModifier = accessModifier
            DataTemplate.currTemplate!!.addMember(m)
        }else if(m is Pair<*,*>){//Pair<Var, Property>
            val v = m.first as Var<*>?
            val p = m.second as Property?
            if(v == null || p == null) return null
            //访问修饰符
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
        val f = if(ctx.functionParams().readOnlyParams() != null && ctx.functionParams().readOnlyParams().parameterList().parameter().size != 0){
            GenericFunction(
                ctx.Identifier().text,
                DataTemplate.currTemplate!!,
                ctx.functionBody()
            )
        }else {
            Function(
                ctx.Identifier().text,
                DataTemplate.currTemplate!!,
                ctx.functionBody()
            )
        }
        f.returnType = if(ctx.functionReturnType()?.type() != null){
            MCFPPType.parseFromContext(ctx.functionReturnType().type(), typeScope)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.functionReturnType().text))
                MCFPPBaseType.Any
            }
        }else{
            MCFPPPrivateType.Void
        }
        //解析参数
        f.addParamsFromContext(ctx.functionParams())
        //注册函数
        //注册函数
        if (DataTemplate.currTemplate!!.field.hasFunction(f, true)) {
            if(ctx.OVERRIDE() != null){
                if(isStatic){
                    LogProcessor.error("Cannot override static method ${ctx.Identifier()}")
                    throw Exception()
                }
                f.isOverride = true
            }else{
                LogProcessor.error("Already defined function:" + ctx.Identifier().text + "in template " + DataTemplate.currTemplate!!.identifier)
                Function.currFunction = Function.nullFunction
            }
        }else {
            if(ctx.OVERRIDE()!= null){
                LogProcessor.error("Method ${f.identifier} in template ${DataTemplate.currTemplate!!.namespaceID} overrides nothing")
            }
        }
        return f
    }

    override fun visitTemplateFieldDeclaration(ctx: mcfppParser.TemplateFieldDeclarationContext): Pair<Var<*>?, Property?> = withCompilationContext(ctx) {
        if(DataTemplate.currTemplate is TypeDataTemplate){
            LogProcessor.error("TypeDataTemplate cannot have field: " + ctx.Identifier().text)
            return null to null
        }
        var `var` = ctx.templateType()?.let {
            if (it.singleTemplateFieldType() != null) {
                val type = MCFPPType.parseFromContext(it.singleTemplateFieldType().type(), typeScope) ?: run {
                    LogProcessor.error(
                        TextTranslator.INVALID_TYPE_ERROR.translate(
                            it.singleTemplateFieldType().type().text
                        )
                    )
                    MCFPPBaseType.Any
                }
                type.build(ctx.Identifier().text).apply {
                    nullable = it.singleTemplateFieldType().QUEST() != null
                }
            } else {
                val vars = ArrayList<Var<*>>()
                for (type in it.unionTemplateFieldType().type()) {
                    val t = MCFPPType.parseFromContext(type, typeScope) ?: run {
                        LogProcessor.error(
                            TextTranslator.INVALID_TYPE_ERROR.translate(
                                it.singleTemplateFieldType().type().text
                            )
                        )
                        MCFPPBaseType.Any
                    }
                    vars.add(
                        t.build(ctx.Identifier().text)
                    )
                }
                UnionTypeVarConcrete(
                    ctx.Identifier().text,
                    (vars[0] as MCFPPValue<*>).value,
                    *vars.toTypedArray()
                ).apply {
                    nullable = it.unionTemplateFieldType().QUEST() != null
                }
            }
        }
        var init: Var<*>?
        if(`var` == null && ctx.expression() == null){
            LogProcessor.error("Template field ${ctx.Identifier().text} must have a type or an initializer")
            return null to null
        }else if(`var` == null){
            Function.extraFunction.runInFunction {
                init = MCFPPExprVisitor().visit(ctx.expression())!!
                val type = init!!.type
                `var` = type.buildUnConcrete(ctx.Identifier().text, DataTemplate.currTemplate!!)
                if(init is MCFPPValue<*>){
                    DataTemplate.currTemplate!!.preInit2[`var`!!.identifier] = init!!
                }else{
                    DataTemplate.currTemplate!!.preInit[`var`!!.identifier] = ctx.expression()
                }
            }
        }
        //是否是静态的
        `var`!!.isStatic = isStatic
        if (DataTemplate.currTemplate!!.field.containVar(ctx.Identifier().text)
        ) {
            LogProcessor.error("Duplicate defined variable name:" + ctx.Identifier().text)
            return null to null
        }
        //属性访问器
        currVar = `var`!!
        val properties = (ctx.accessor()?.let {visit(ctx.accessor())}?: Property.buildSimpleProperty(`var`!!)) as Property
        return `var` to properties
    }

    override fun visitTemplateConstructorDeclaration(ctx: mcfppParser.TemplateConstructorDeclarationContext): Any = withCompilationContext(ctx) {
        if(DataTemplate.currTemplate is TypeDataTemplate){
            LogProcessor.error("TypeDataTemplate cannot have constructor")
            return null to null
        }
        //类构造函数
        //创建构造函数对象，注册函数
        val f = DataTemplateConstructor(DataTemplate.currTemplate!!, ctx.functionBody())
        f.file = MCFPPFile.currFile!!
        f.addParamsFromContext(ctx.normalParams())
        return f
    }

    //endregion
}