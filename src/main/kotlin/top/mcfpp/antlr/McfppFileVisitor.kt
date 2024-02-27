package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.compiletime.CompileTimeFunction
import top.mcfpp.exception.*
import top.mcfpp.exception.IllegalFormatException
import top.mcfpp.lang.*
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lib.*
import top.mcfpp.lib.Class
import top.mcfpp.lib.Function
import top.mcfpp.lib.Member.AccessModifier
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper
import java.util.*
import kotlin.collections.ArrayList

/**
 * 在编译工程之前，应当首先将所有文件中的资源全部遍历一次并写入缓存。
 */
class McfppFileVisitor : mcfppParserBaseVisitor<Any?>() {

    var isStatic = false

    /**
     * 遍历整个文件。一个文件包含了命名空间的声明，函数的声明，类的声明以及全局变量的声明。全局变量是可以跨文件调用的。
     * @param ctx the parse tree
     * @return null
     */
    override fun visitCompilationUnit(ctx: mcfppParser.CompilationUnitContext): Any? {
        Project.ctx = ctx
        //命名空间
        if (ctx.namespaceDeclaration() != null) {
            //获取命名空间
            var namespaceStr = ctx.namespaceDeclaration().Identifier()[0].text
            if(ctx.namespaceDeclaration().Identifier().size > 1){
                ctx.namespaceDeclaration().Identifier().forEach{e ->
                    run {
                        namespaceStr += ".${e.text}"
                    }
                }
            }
            Project.currNamespace = namespaceStr
        }else{
            Project.currNamespace = Project.defaultNamespace
        }
        //导入库
        for (lib in ctx.importDeclaration()){
            visit(lib)
        }
        if(!GlobalField.localNamespaces.containsKey(Project.currNamespace)){
            GlobalField.localNamespaces[Project.currNamespace] = NamespaceField()
        }
        //文件结构，类和函数
        for (t in ctx.typeDeclaration()) {
            visit(t)
        }
        return null
    }

    /**
     * 完成一次库的import
     *
     * @param ctx
     */
    override fun visitImportDeclaration(ctx: mcfppParser.ImportDeclarationContext):Any? {
        Project.ctx = ctx
        //获取命名空间
        var namespace = ctx.Identifier(0).text
        if (ctx.Identifier().size > 1) {
            for (n in ctx.Identifier().subList(1, ctx.Identifier().size)) {
                namespace += ".$n"
            }
        }
        //获取库的命名空间
        val libNamespace = GlobalField.libNamespaces[namespace]
        if (libNamespace == null) {
            LogProcessor.error("Namespace $namespace not found")
            return null
        }
        //将库的命名空间加入到importedLibNamespaces中
        val nsp = NamespaceField()
        GlobalField.importedLibNamespaces[namespace] = nsp

        //这个库被引用的类
        if(ctx.cls == null){
            //只导入方法
            libNamespace.forEachFunction { f ->
                run {
                    nsp.addFunction(f,false)
                }
            }
            return null
        }
        //导入类和方法
        if(ctx.cls.text == "*"){
            //全部导入
            libNamespace.forEachClass { c ->
                run {
                    nsp.addClass(c.identifier,c)
                }
            }
            libNamespace.forEachFunction { f ->
                run {
                    nsp.addFunction(f,false)
                }
            }
        }else{
            //只导入声明的类
            val cls = libNamespace.getClass(ctx.cls.text)
            if(cls != null){
                nsp.addClass(cls.identifier,cls)
            }else{
                LogProcessor.error("Class ${ctx.cls.text} not found in namespace $namespace")
                return null
            }
        }
        return null
    }

    /**
     * 类或者函数的声明
     * @param ctx the parse tree
     * @return null
     */
    
    override fun visitTypeDeclaration(ctx: mcfppParser.TypeDeclarationContext): Any? {
        Project.ctx = ctx
        if(ctx.declarations() != null){
            visit(ctx.declarations())
        }
        return null
    }

    /**
     * 类或函数声明
     * <pre>
     * `classOrFunctionDeclaration
     * :   classDeclaration
     * |   functionDeclaration
     * |   nativeDeclaration
     * ;`
    </pre> *
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
        val field = GlobalField.localNamespaces[Project.currNamespace]!!

        if (field.hasInterface(id)) {
            //重复声明
            LogProcessor.error("Interface has been defined: $id in namespace ${Project.currNamespace}")
            Interface.currInterface = field.getInterface(id)
        } else {
            //如果没有声明过这个类
            val itf = Interface(id, Project.currNamespace)
            for (p in ctx.className()){
                //是否存在继承
                val nsn = StringHelper.splitNamespaceID(p.text)
                val namespace  = nsn.first
                val identifier = nsn.second
                val pc = GlobalField.getInterface(namespace, identifier)
                if(pc == null){
                    LogProcessor.error("Undefined Interface: " + p.text)
                }else{
                    itf.parent.add(pc)
                }
            }
            field.addInterface(id, itf)
            Interface.currInterface = itf
        }

        //接口成员
        for (m in ctx.interfaceBody().interfaceFunctionDeclaration()){
            visit(m)
        }
        return null
    }

    
    override fun visitInterfaceFunctionDeclaration(ctx: mcfppParser.InterfaceFunctionDeclarationContext): Any? {
        Project.ctx = ctx
        val type = MCFPPType.parse(if(ctx.functionReturnType() == null) "void" else ctx.functionReturnType().text)
        //创建函数对象
        val f = Function(
            ctx.Identifier().text,
            Interface.currInterface!!,
            type
        )
        //解析参数
        f.addParams(ctx.parameterList())
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
     * native类的声明
     * @param ctx the parse tree
     * @return null
     */
    override fun visitNativeClassDeclaration(ctx: mcfppParser.NativeClassDeclarationContext): Any? {
        NativeClassVisitor().visit(ctx)
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
        val field = GlobalField.localNamespaces[Project.currNamespace]!!

        if (field.hasClass(id)) {
            //重复声明
            LogProcessor.error("Class has been defined: $id in namespace ${Project.currNamespace}")
            Class.currClass = field.getClass(id)
        } else {
            //如果没有声明过这个类
            val cls = Class(id, Project.currNamespace)
            cls.initialize()
            if(ctx.className().size != 0){
                for (p in ctx.className()){
                    //是否存在继承
                    val qwq = StringHelper.splitNamespaceID(p.text)
                    val identifier: String = qwq.second
                    val namespace : String? = qwq.first
                    var pc : CompoundData? = GlobalField.getClass(namespace, identifier)
                    if(pc == null){
                        pc = GlobalField.getInterface(namespace, identifier)
                        if(pc == null){
                            pc = Class.Companion.UndefinedClassOrInterface(identifier,namespace)
                        }
                    }
                    cls.extends(pc)
                }
            }else{
                //继承Any类
                cls.extends(MCAny.data)
            }

            field.addClass(id, cls)
            Class.currClass = cls
            cls.isStaticClass = ctx.STATIC() != null
            cls.isAbstract = ctx.ABSTRACT() != null
        }
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
        val type = MCFPPType.parse(if(ctx.functionReturnType() == null) "void" else ctx.functionReturnType().text)
        //创建函数对象
        val f = Function(
            ctx.Identifier().text,
            Class.currClass!!,
            ctx.parent is mcfppParser.StaticClassMemberDeclarationContext,
            type
        )
        if(!isStatic){
            val thisObj = Var.build("this", MCFPPType.parse(Class.currClass!!.identifier), f)
            f.field.putVar("this",thisObj)
        }
        //解析参数
        f.addParams(ctx.parameterList())
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
        val type = MCFPPType.parse(ctx.functionReturnType().text)
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
        f.addParams(ctx.parameterList())
        //注册函数
        if (Class.currClass!!.field.hasFunction(f)) {
            LogProcessor.error("Already defined function:" + ctx.Identifier().text + "in class " + Class.currClass!!.identifier)
            Function.currFunction = Function.nullFunction
        }
        return f
    }

    override fun visitNativeClassFunctionDeclaration(ctx: mcfppParser.NativeClassFunctionDeclarationContext): Any? {
        Project.ctx = ctx
        val type = MCFPPType.parse(ctx.functionReturnType().text)
        val nf: NativeFunction = try {
            NativeFunction(ctx.Identifier().text, ctx.javaRefer().text, type)
        } catch (e: IllegalFormatException) {
            LogProcessor.error("Illegal Java Method Name:" + e.message)
            return null
        } catch (e: ClassNotFoundException) {
            LogProcessor.error("Cannot find java class:" + e.message)
            return null
        } catch (e: NoSuchMethodException) {
            LogProcessor.error("No such method:" + e.message)
            return null
        }
        nf.addParams(ctx.parameterList())
        //是类成员
        nf.ownerType = Function.Companion.OwnerType.CLASS
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
        f.addParams(ctx.parameterList())
        if(!Class.currClass!!.addConstructor(f)){
            LogProcessor.error("Already defined constructor: " + ctx.className().text + "(" + ctx.parameterList().text + ")")
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
        val type = MCFPPType.parse(ctx.type().text)
        val `var`: Var<*> = Var.build(c.Identifier().text,type , Class.currClass!!)
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
        val f: Function
        //是否是内联函数
        //是否是内联函数
        val identifier : String = ctx.Identifier().text
        val type = MCFPPType.parse(if(ctx.functionReturnType() == null) "void" else ctx.functionReturnType().text)
        f = Function(identifier, Project.currNamespace,type )
        //解析参数
        f.addParams(ctx.parameterList())
        //TODO 解析函数的注解
        //不是类的成员
        f.ownerType = Function.Companion.OwnerType.NONE
        //写入域
        val field = GlobalField.localNamespaces[f.namespace]!!
        if (field.getFunction(f.identifier, f.paramTypeList) == null) {
            field.addFunction(f,false)
        } else {
            LogProcessor.error("Already defined function:" + f.namespaceID)
            Function.currFunction = Function.nullFunction
        }
        if (f.isEntrance && ctx.parameterList()!!.parameter().size != 0) {
            LogProcessor.error("Entrance function shouldn't have parameter:" + f.namespaceID)
        }
        return null
    }

    override fun visitInlineFunctionDeclaration(ctx: mcfppParser.InlineFunctionDeclarationContext): Any? {
        Project.ctx = ctx
        //创建函数对象
        val f: Function
        //是否是内联函数
        //是否是内联函数
        val identifier : String = ctx.Identifier().text
        f = InlineFunction(identifier, Project.currNamespace, ctx)
        //解析参数
        f.addParams(ctx.parameterList())
        //TODO 解析函数的注解
        //不是类的成员
        f.ownerType = Function.Companion.OwnerType.NONE
        //写入域
        val field = GlobalField.localNamespaces[f.namespace]!!
        if (field.getFunction(f.identifier, f.paramTypeList) == null) {
            field.addFunction(f,false)
        } else {
            LogProcessor.error("Already defined function:" + f.namespaceID)
            Function.currFunction = Function.nullFunction
        }
        if (f.isEntrance && ctx.parameterList()!!.parameter().size != 0) {
            LogProcessor.error("Entrance function shouldn't have parameter:" + f.namespaceID)
        }
        return null
    }

    override fun visitCompileTimeFuncDeclaration(ctx: mcfppParser.CompileTimeFuncDeclarationContext): Any? {
        Project.ctx = ctx
        //创建函数对象
        val f: Function
        //是否是编译时函数
        val type = MCFPPType.parse(if(ctx.functionReturnType() == null) "void" else ctx.functionReturnType().text )
        val identifier : String = ctx.Identifier().text
        f = CompileTimeFunction(
            identifier,Project.currNamespace,
            type,
            ctx.functionBody()
        )
        //解析参数
        f.addParams(ctx.parameterList())
        //TODO 解析函数的注解
        //不是类的成员
        f.ownerType = Function.Companion.OwnerType.NONE
        //写入域
        val field = GlobalField.localNamespaces[f.namespace]!!
        if (field.getFunction(f.identifier, f.paramTypeList) == null) {
            f.setField(field)
            field.addFunction(f,false)
        } else {
            LogProcessor.error("Already defined function:" + f.namespaceID)
            Function.currFunction = Function.nullFunction
        }
        if (f.isEntrance && ctx.parameterList()!!.parameter().size != 0) {
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
                val pwp = GlobalField.getStruct(nsp, id)
                if(pwp == null){
                    LogProcessor.error("Undefined class or struct:" + ctx.type().className().text)
                    return null
                }else{
                    ownerType = Function.Companion.OwnerType.STRUCT
                    pwp
                }
            }else{
                ownerType = Function.Companion.OwnerType.CLASS
                qwq
            }
        }
        //创建函数对象
        val type = MCFPPType.parse(ctx.functionReturnType().text)
        val f = ExtensionFunction(ctx.Identifier().text, data, Project.currNamespace,type )
        //解析参数
        f.accessModifier = AccessModifier.PUBLIC
        f.ownerType = ownerType
        f.isStatic = ctx.STATIC() != null
        f.addParams(ctx.parameterList())
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
        val nf: NativeFunction = try {
            val type = MCFPPType.parse(if(ctx.functionReturnType() == null) "void" else ctx.functionReturnType().text)
            NativeFunction(ctx.Identifier().text, ctx.javaRefer().text, type)
        } catch (e: IllegalFormatException) {
            LogProcessor.error("Illegal Java Method Name:" + e.message)
            return null
        } catch (e: ClassNotFoundException) {
            LogProcessor.error("Cannot find java class:" + e.message)
            return null
        } catch (e: NoSuchMethodException) {
            LogProcessor.error("No such method:" + e.message)
            return null
        }
        nf.addParams(ctx.parameterList())
        //写入域
        val field = GlobalField.localNamespaces[nf.namespace]!!
        //是普通的函数
        nf.ownerType = Function.Companion.OwnerType.NONE
        if (!field.hasFunction(nf)) {
            field.addFunction(nf,false)
        } else {
            LogProcessor.error("Already defined function:" + ctx.Identifier().text)
            Function.currFunction = Function.nullFunction
        }
        return nf
    }
//endregion

    //region struct
    override fun visitTemplateDeclaration(ctx: mcfppParser.TemplateDeclarationContext?): Any? {
        Project.ctx = ctx!!
        //注册模板
        val id = ctx.classWithoutNamespace().text
        val field = GlobalField.localNamespaces[Project.currNamespace]!!
        if (field.hasTemplate(id)) {
            //重复声明
            LogProcessor.error("Template has been defined: $id in namespace ${Project.currNamespace}")
            Template.currTemplate = field.getTemplate(id)
        }
        val type = MCFPPType.parse(ctx.genericity().type().text)
        val struct = Template(id, type, Project.currNamespace)
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
            val s = GlobalField.getStruct(namespace, identifier)
            if(s == null){
                LogProcessor.error("Undefined struct: " + ctx.className().text)
            }else{
                struct.parent.add(s)
            }
        }
        field.addTemplate(id, struct)
        Template.currTemplate = struct
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
        val type = MCFPPType.parse(if(ctx.functionReturnType() == null) "void" else ctx.functionReturnType().text)
        //创建函数对象
        val f = Function(
            ctx.Identifier().text,
            Template.currTemplate!!,
            ctx.parent is mcfppParser.StaticTemplateMemberDeclarationContext,
            type
        )
        if(!isStatic){
            val varType = MCFPPType.parse(Template.currTemplate!!.identifier)
            val thisObj = Var.build("this",varType , f)
            f.field.putVar("this",thisObj)
        }
        //解析参数
        f.addParams(ctx.parameterList())
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
        val `var`: Var<*> = Var.build(ctx.Identifier().text,Template.currTemplate!!.dataType, Template.currTemplate!!)
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