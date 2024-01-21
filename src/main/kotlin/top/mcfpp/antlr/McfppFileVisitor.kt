package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.exception.*
import top.mcfpp.exception.IllegalFormatException
import top.mcfpp.lang.*
import top.mcfpp.lib.*
import top.mcfpp.lib.Class
import top.mcfpp.lib.Function
import top.mcfpp.lib.Member.AccessModifier
import top.mcfpp.util.StringHelper
import java.util.*
import kotlin.collections.ArrayList

/**
 * 在编译工程之前，应当首先将所有文件中的资源全部遍历一次并写入缓存。
 */
class McfppFileVisitor : mcfppBaseVisitor<Any?>() {

    var isStatic = false
    /**
     * 遍历整个文件。一个文件包含了命名空间的声明，函数的声明，类的声明以及全局变量的声明。全局变量是可以跨文件调用的。
     * <pre>
     * `
     * compilationUnit
     * :   namespaceDeclaration?
     * typeDeclaration *
     * EOF
     * ;
    ` *
    </pre> *
     * @param ctx the parse tree
     * @return null
     */
    @Override
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
        if(!GlobalField.localNamespaces.containsKey(Project.currNamespace))
            GlobalField.localNamespaces[Project.currNamespace] = NamespaceField()
        //文件结构，类和函数
        for (t in ctx.typeDeclaration()) {
            visit(t)
        }
        return null
    }

    /**
     * 类或者函数的声明
     * @param ctx the parse tree
     * @return null
     */
    @Override
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
    @Override
    override fun visitDeclarations(ctx: mcfppParser.DeclarationsContext): Any? {
        Project.ctx = ctx
        if (ctx.classDeclaration() != null) {
            visit(ctx.classDeclaration())
        } else if (ctx.functionDeclaration() != null) {
            visit(ctx.functionDeclaration())
        } else if (ctx.nativeFuncDeclaration() != null) {
            visit(ctx.nativeFuncDeclaration())
        } else if (ctx.nativeClassDeclaration() != null ) {
            visit(ctx.nativeClassDeclaration())
        } else if (ctx.templateDeclaration() != null){
            visit(ctx.templateDeclaration())
        } else if (ctx.extensionFunctionDeclaration() != null){
            visit(ctx.extensionFunctionDeclaration())
        } else if (ctx.interfaceDeclaration() != null){
            visit(ctx.interfaceDeclaration())
        }
        return null
    }

    //region interface
    @Override
    override fun visitInterfaceDeclaration(ctx: mcfppParser.InterfaceDeclarationContext): Any? {
        Project.ctx = ctx
        //注册类
        val id = ctx.classWithoutNamespace().text
        val field = GlobalField.localNamespaces[Project.currNamespace]!!

        if (field.hasInterface(id)) {
            //重复声明
            Project.error("Interface has been defined: $id in namespace ${Project.currNamespace}")
            throw ClassDuplicationException()
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
                    Project.error("Undefined Interface: " + p.text)
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

    @Override
    override fun visitInterfaceFunctionDeclaration(ctx: mcfppParser.InterfaceFunctionDeclarationContext): Any? {
        Project.ctx = ctx
        //创建函数对象
        val f = Function(
            ctx.Identifier().text,
            Interface.currInterface!!,
            if(ctx.functionReturnType() == null) "void" else ctx.functionReturnType().text
        )
        //解析参数
        f.addParams(ctx.parameterList())
        //注册函数
        if (Interface.currInterface!!.field.hasFunction(f)) {
            Project.error("Already defined function:" + ctx.Identifier().text + "in class " + Class.currClass!!.identifier)
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
    @Override
    override fun visitClassDeclaration(ctx: mcfppParser.ClassDeclarationContext): Any? {
        Project.ctx = ctx
        //注册类
        val id = ctx.classWithoutNamespace().text
        val field = GlobalField.localNamespaces[Project.currNamespace]!!

        if (field.hasClass(id)) {
            //重复声明
            Project.error("Class has been defined: $id in namespace ${Project.currNamespace}")
            throw ClassDuplicationException()
        } else {
            //如果没有声明过这个类
            val cls = Class(id, Project.currNamespace)
            cls.initialize()
            for (p in ctx.className()){
                //是否存在继承
                val qwq = StringHelper.splitNamespaceID(p.text)
                val identifier: String = qwq.second
                val namespace : String? = qwq.first
                var pc : CompoundData? = GlobalField.getClass(namespace, identifier)
                if(pc == null){
                    pc = GlobalField.getInterface(namespace, identifier)
                    if(pc == null){
                        Project.error("Undefined class or interface: " + p.text)
                    }else{
                        cls.parent.add(pc)
                        cls.implements(pc)
                    }
                }else{
                    cls.parent.add(pc)
                    cls.extends(pc as Class)
                }
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
            if (c.classMember().classFunctionDeclaration() != null || c.classMember().abstractClassFunctionDeclaration() != null) {
                visit(c)
            }
        }
        //再解析变量
        for (c in ctx.classBody().classMemberDeclaration()) {
            if (c!!.classMember().classFieldDeclaration() != null) {
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
                Project.error("Class ${Class.currClass} must either be declared abstract or implement abstract method ${il!!.nameWithNamespace}")
            }
        }
        Class.currClass = null
        return null
    }

    @Override
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
    @Override
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

    @Override
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
    @Override
    override fun visitClassFunctionDeclaration(ctx: mcfppParser.ClassFunctionDeclarationContext): Any {
        Project.ctx = ctx
        //创建函数对象
        val f = Function(
            ctx.Identifier().text,
            Class.currClass!!,
            ctx.parent is mcfppParser.StaticClassMemberDeclarationContext,
            ctx.functionReturnType().text
        )
        if(!isStatic){
            val thisObj = Var.build("this", Class.currClass!!.identifier, f)
            f.field.putVar("this",thisObj)
        }
        //解析参数
        f.addParams(ctx.parameterList())
        //注册函数
        if (Class.currClass!!.field.hasFunction(f) || Class.currClass!!.staticField.hasFunction(f)) {
            if(ctx.OVERRIDE() != null){
                if(isStatic){
                    Project.error("Cannot override static method ${ctx.Identifier()}")
                    throw Exception()
                }
            }else{
                Project.error("Already defined function:" + ctx.Identifier().text + "in class " + Class.currClass!!.identifier)
                Function.currFunction = Function.nullFunction
            }
        }
        return f
    }

    @Override
    override fun visitAbstractClassFunctionDeclaration(ctx: mcfppParser.AbstractClassFunctionDeclarationContext): Any {
        Project.ctx = ctx
        //创建函数对象
        val f = Function(
            ctx.Identifier().text,
            Class.currClass!!,
            ctx.parent is mcfppParser.StaticClassMemberDeclarationContext,
            ctx.functionReturnType().text
        )
        f.isAbstract = true
        if(f.isStatic){
            Project.error("Static Function cannot be abstract: ${ctx.Identifier().text} in class ${Class.currClass!!.identifier}" )
            throw Exception()
        }
        //解析参数
        f.addParams(ctx.parameterList())
        //注册函数
        if (Class.currClass!!.field.hasFunction(f)) {
            Project.error("Already defined function:" + ctx.Identifier().text + "in class " + Class.currClass!!.identifier)
            Function.currFunction = Function.nullFunction
        }
        return f
    }

    override fun visitNativeClassFunctionDeclaration(ctx: mcfppParser.NativeClassFunctionDeclarationContext): Any? {
        Project.ctx = ctx
        val nf: NativeFunction = try {
            NativeFunction(ctx.Identifier().text, ctx.javaRefer().text, ctx.functionReturnType().text)
        } catch (e: IllegalFormatException) {
            Project.error("Illegal Java Method Name:" + e.message)
            return null
        } catch (e: ClassNotFoundException) {
            Project.error("Cannot find java class:" + e.message)
            return null
        } catch (e: NoSuchMethodException) {
            Project.error("No such method:" + e.message)
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
    @Override
    override fun visitConstructorDeclaration(ctx: mcfppParser.ConstructorDeclarationContext): Any? {
        Project.ctx = ctx
        //类构造函数
        //创建构造函数对象，注册函数
        val f: Constructor?
        try {
            f = Constructor(Class.currClass!!)
            Class.currClass!!.addConstructor(f)
            f.addParams(ctx.parameterList())
            return f
        } catch (e: FunctionDuplicationException) {
            Project.error("Already defined constructor: " + ctx.className().text + "(" + ctx.parameterList().text + ")")
        }
        return null
    }

    /**
     * 类字段的声明
     * @param ctx the parse tree
     * @return null
     */
    @Override
    @InsertCommand
    override fun visitClassFieldDeclaration(ctx: mcfppParser.ClassFieldDeclarationContext): Any? {
        Project.ctx = ctx
        //只有类字段构建
        val reList = ArrayList<Member>()
        val c = ctx.fieldDeclarationExpression()
        val `var`: Var = Var.build(c, compoundData = Class.currClass!!)
        //是否是静态的
        if(isStatic){
            `var`.isStatic = true
            `var`.parent = ClassType(Class.currClass!!)
        }else{
            `var`.parent = ClassPointer(Class.currClass!!,"temp")
        }
        if (Class.currClass!!.field.containVar(c.Identifier().text)
            || Class.currClass!!.staticField.containVar(c.Identifier().text)
        ) {
            Project.error("Duplicate defined variable name:" + c.Identifier().text)
            throw VariableDuplicationException()
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
            val init: Var = McfppExprVisitor().visit(c.expression())!!
            try {
                `var`.assign(init)
            } catch (e: VariableConverseException) {
                Project.error("Cannot convert " + init.javaClass + " to " + `var`.javaClass)
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
    @Override
    override fun visitFunctionDeclaration(ctx: mcfppParser.FunctionDeclarationContext): Any? {
        Project.ctx = ctx
        //创建函数对象
        val f: Function
        //是否是内联函数
        //是否是内联函数
        val identifier : String = ctx.Identifier().text
        f = if (ctx.INLINE() != null) {
            InlineFunction(identifier, Project.currNamespace, ctx)
        } else {
            Function(identifier, Project.currNamespace, if(ctx.functionReturnType() == null) "void" else ctx.functionReturnType().text)
        }
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
            Project.error("Already defined function:" + f.namespaceID)
            Function.currFunction = Function.nullFunction
        }
        if (f.isEntrance && ctx.parameterList()!!.parameter().size != 0) {
            Project.error("Entrance function shouldn't have parameter:" + f.namespaceID)
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
                    throw Exception("Cannot add extension function to ${ctx.type().text}")
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
                    Project.error("Undefined class or struct:" + ctx.type().className().text)
                    throw ClassNotDefineException()
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
        val f = ExtensionFunction(ctx.Identifier().text, data, Project.currNamespace, ctx.functionReturnType().text)
        //解析参数
        f.accessModifier = AccessModifier.PUBLIC
        f.ownerType = ownerType
        f.isStatic = ctx.STATIC() != null
        f.addParams(ctx.parameterList())
        val field = if(f.isStatic) data.staticField else data.field
        //注册函数
        if (!field.addFunction(f,false)) {
            Project.error("Already defined function:" + ctx.Identifier().text + "in class " + Class.currClass!!.identifier)
            Function.currFunction = Function.nullFunction
        }
        return null
    }

    /**
     * native函数的声明
     * @param ctx the parse tree
     * @return 如果是全局，返回null，否则返回这个函数对象
     */
    @Override
    override fun visitNativeFuncDeclaration(ctx: mcfppParser.NativeFuncDeclarationContext): Any? {
        Project.ctx = ctx
        val nf: NativeFunction = try {
            NativeFunction(ctx.Identifier().text, ctx.javaRefer().text, ctx.functionReturnType().text)
        } catch (e: IllegalFormatException) {
            Project.error("Illegal Java Method Name:" + e.message)
            return null
        } catch (e: ClassNotFoundException) {
            Project.error("Cannot find java class:" + e.message)
            return null
        } catch (e: NoSuchMethodException) {
            Project.error("No such method:" + e.message)
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
            Project.error("Already defined function:" + ctx.Identifier().text)
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
            Project.error("Template has been defined: $id in namespace ${Project.currNamespace}")
            throw StructDuplicationException()
        }
        val struct = Template(id, ctx.genericity().type().text, Project.currNamespace)
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
                Project.error("Undefined struct: " + ctx.className().text)
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

    @Override
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
    @Override
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
                (v as Var).accessModifier = accessModifier
            }
        }
        return null
    }

    @Override
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
        //创建函数对象
        val f = Function(
            ctx.Identifier().text,
            Template.currTemplate!!,
            ctx.parent is mcfppParser.StaticTemplateMemberDeclarationContext,
            if(ctx.functionReturnType() == null) "void" else ctx.functionReturnType().text
        )
        if(!isStatic){
            val thisObj = Var.build("this", Template.currTemplate!!.identifier, f)
            f.field.putVar("this",thisObj)
        }
        //解析参数
        f.addParams(ctx.parameterList())
        //注册函数
        if (Template.currTemplate!!.field.hasFunction(f) || Template.currTemplate!!.staticField.hasFunction(f)) {
            Project.error("Already defined function:" + ctx.Identifier().text + "in struct " + Template.currTemplate!!.identifier)
            Function.currFunction = Function.nullFunction
        }
        return f
    }

    override fun visitTemplateFieldDeclaration(ctx: mcfppParser.TemplateFieldDeclarationContext): Any {
        Project.ctx = ctx
        val re = ArrayList<Var>()
        for (i in ctx.Identifier()){
            //变量生成
            val `var`: Var = Var.build(i.text,Template.currTemplate!!.dataType, Template.currTemplate!!)
            //是否是静态的
            `var`.isStatic = isStatic
            //只有可能是结构体成员
            if (Template.currTemplate!!.field.containVar(i.text) || Template.currTemplate!!.staticField.containVar(i.text)
            ) {
                Project.error("Duplicate defined variable name:" + i.text)
                throw VariableDuplicationException()
            }
            re.add(`var`)
        }
        return re
    }
    //endregion
}