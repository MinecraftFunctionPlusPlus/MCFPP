package top.mcfpp.lib.antlr

import mcfppBaseVisitor
import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.exception.*
import top.mcfpp.exception.IllegalFormatException
import top.mcfpp.lang.ClassType
import top.mcfpp.lang.Var
import top.mcfpp.lang.Var.ConstStatus
import top.mcfpp.lib.*
import top.mcfpp.lib.Class
import top.mcfpp.lib.Function
import top.mcfpp.lib.Member.AccessModifier
import java.util.*

/**
 * 在编译工程之前，应当首先将所有文件中的资源全部遍历一次并写入缓存。
 */
class McfppFileVisitor : mcfppBaseVisitor<Any?>() {

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
        visit(ctx.classOrFunctionOrStructDeclaration())
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
    override fun visitClassOrFunctionOrStructDeclaration(ctx: mcfppParser.ClassOrFunctionOrStructDeclarationContext): Any? {
        Project.ctx = ctx
        if (ctx.classDeclaration() != null) {
            visit(ctx.classDeclaration())
        } else if (ctx.functionDeclaration() != null) {
            visit(ctx.functionDeclaration())
        } else if (ctx.nativeFuncDeclaration() != null) {
            visit(ctx.nativeFuncDeclaration())
        } else if (ctx.nativeClassDeclaration() != null ) {
            visit(ctx.nativeClassDeclaration())
        } else if (ctx.structDeclaration() != null){
            visit(ctx.structDeclaration())
        }
        return null
    }

    /**
     * native类的声明
     * @param ctx the parse tree
     * @return null
     */
    override fun visitNativeClassDeclaration(ctx: mcfppParser.NativeClassDeclarationContext): Any? {
        NativeClassVisitor().visit(ctx)
        return null
    }

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
            if (ctx.className() != null) {
                //是否存在继承
                if (field.hasClass(ctx.className().text)) {
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
                    cls.parent = GlobalField.getClass(namespace, identifier)
                } else {
                    Project.error("Undefined class: " + ctx.className().text)
                }
            }
            field.addClass(id, cls)
            Class.currClass = cls
            cls.isStaticClass = ctx.STATIC() != null
        }
        //解析类中的成员
        //先静态
        //先解析函数
        for (c in ctx.classBody().staticClassMemberDeclaration()) {
            if (c!!.classMember().classFunctionDeclaration() != null) {
                visit(c)
            }
        }
        //再解析变量
        for (c in ctx.classBody().staticClassMemberDeclaration()) {
            if (c!!.classMember().fieldDeclaration() != null) {
                visit(c)
            }
        }
        //后成员
        //先解析函数和构造函数
        for (c in ctx.classBody().classMemberDeclaration()) {
            if (c!!.classMember().classFunctionDeclaration() != null) {
                visit(c)
            }
        }
        //再解析变量
        for (c in ctx.classBody().classMemberDeclaration()) {
            if (c!!.classMember().fieldDeclaration() != null) {
                visit(c)
            }
        }
        //如果没有构造函数，自动添加默认的空构造函数
        if (Class.currClass!!.constructors.size == 0) {
            Class.currClass!!.addConstructor(Constructor(Class.currClass!!))
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
        val m = visit(ctx.classMember()) as Member
        //访问修饰符
        if (ctx.accessModifier() != null) {
            m.accessModifier = AccessModifier.valueOf(ctx.accessModifier().text.uppercase(Locale.getDefault()))
        }
        if (m !is Constructor) {
            Class.currClass!!.addMember(m)
        }
        return null
    }

    @Override
    override fun visitClassMember(ctx: mcfppParser.ClassMemberContext): Any? {
        Project.ctx = ctx
        return if (ctx.nativeFuncDeclaration() != null) {
            visit(ctx.nativeFuncDeclaration())
        } else if (ctx.classFunctionDeclaration() != null) {
            visit(ctx.classFunctionDeclaration())
        } else if (ctx.fieldDeclaration() != null) {
            visit(ctx.fieldDeclaration())
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
        //解析参数
        f.addParams(ctx.parameterList())
        //注册函数
        if (Class.currClass!!.field.hasFunction(f) || Class.currClass!!.staticField.hasFunction(f)) {
            Project.error("Already defined function:" + ctx.Identifier().text + "in class " + Class.currClass!!.identifier)
            Function.currFunction = Function.nullFunction
        }
        return f
    }

    /**
     * 构造函数的声明
     * @param ctx the parse tree
     * @return 这个构造函数的对象
     */
    @Override
    override fun visitConstructorDeclaration(ctx: mcfppParser.ConstructorDeclarationContext): Any? {
        Project.ctx = ctx
        if(ctx.parent is mcfppParser.StructMemberContext){
            //是结构体构造函数
            //创建构造函数对象，注册函数
            val f: StructConstructor?
            try {
                f = StructConstructor(Struct.currStruct!!)
                Struct.currStruct!!.addConstructor(f)
                f.addParams(ctx.parameterList())
                return f
            } catch (e: FunctionDuplicationException) {
                Project.error("Already defined constructor: " + ctx.className().text + "(" + ctx.parameterList().text + ")")
            }
        }else{
            //是类构造函数
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
        }
        return null
    }

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
            Function(identifier, Project.currNamespace, ctx.functionReturnType().text)
        }
        //解析参数
        f.addParams(ctx.parameterList())
        //TODO 解析函数的tag
        //不是类的成员
        f.isClassMember = false
        //写入域
        val field = GlobalField.localNamespaces[f.namespace]!!
        if (field.getFunction(f.name, f.paramTypeList) == null) {
            field.addFunction(f)
        } else {
            Project.error("Already defined function:" + f.namespaceID)
            Function.currFunction = Function.nullFunction
        }
        if (f.isEntrance && ctx.parameterList()!!.parameter().size != 0) {
            Project.error("Entrance function shouldn't have parameter:" + f.namespaceID)
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
            NativeFunction(ctx.Identifier().text, ctx.javaRefer().text)
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
        if (Class.currClass == null) {
            //是普通的函数
            nf.isClassMember = false
            if (!field.hasFunction(nf)) {
                field.addFunction(nf)
            } else {
                Project.error("Already defined function:" + ctx.Identifier().text)
                Function.currFunction = Function.nullFunction
            }
            return nf
        }
        //是类成员
        nf.isClassMember = true
        nf.ownerClass = Class.currClass
        return nf
    }

    /**
     * 类字段的声明
     * @param ctx the parse tree
     * @return null
     */
    @Override
    @InsertCommand
    override fun visitFieldDeclaration(ctx: mcfppParser.FieldDeclarationContext): Any {
        Project.ctx = ctx
        //变量生成
        val `var`: Var = Var.build(ctx, ClassType(Class.currClass!!))!!
        //是否是静态的
        if(ctx.parent.parent is mcfppParser.StaticClassMemberDeclarationContext){
            `var`.isStatic = true
        }
        //只有可能是类变量
        if (Class.currClass!!.field.containVar(ctx.Identifier().text)
            || Class.currClass!!.staticField.containVar(ctx.Identifier().text)
        ) {
            Project.error("Duplicate defined variable name:" + ctx.Identifier().text)
            throw VariableDuplicationException()
        }
        //变量的初始化
        if (ctx.expression() != null) {
            if(`var`.isConst == ConstStatus.NULL) `var`.isConst = ConstStatus.ASSIGNED
            Function.currFunction = if(ctx.parent.parent is mcfppParser.StaticClassMemberDeclarationContext){
                `var`.isStatic = true
                Class.currClass!!.classPreStaticInit
            }else{
                Class.currClass!!.classPreInit
            }
            //是类的成员
            Function.addCommand("#" + ctx.text)
            val init: Var = McfppExprVisitor().visit(ctx.expression())!!
            try {
                `var`.assign(init)
            } catch (e: VariableConverseException) {
                Project.error("Cannot convert " + init.javaClass + " to " + `var`.javaClass)
                Function.currFunction = Function.nullFunction
                throw VariableConverseException()
            }
            Function.currFunction = Function.nullFunction
        }
        return `var`
    }

    override fun visitStructDeclaration(ctx: mcfppParser.StructDeclarationContext?): Any? {
        Project.ctx = ctx!!
        //注册类
        val id = ctx.classWithoutNamespace().text
        val field = GlobalField.localNamespaces[Project.currNamespace]!!

        if (field.hasStruct(id)) {
            //重复声明
            Project.error("Struct has been defined: $id in namespace ${Project.currNamespace}")
            throw StructDuplicationException()
        } else {
            //如果没有声明过这个结构体
            val struct = Struct(id, Project.currNamespace)
            if (ctx.className() != null) {
                //是否存在继承
                if (field.hasStruct(ctx.className().text)) {
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
                    struct.parent = GlobalField.getStruct(namespace, identifier)
                } else {
                    Project.error("Undefined struct: " + ctx.className().text)
                }
            }
            field.addStruct(id, struct)
            Struct.currStruct = struct
        }
        //解析类中的成员
        //先静态
        //先解析函数
        for (c in ctx.structBody().staticStructMemberDeclaration()) {
            if (c!!.structMember().structFunctionDeclaration() != null) {
                visit(c)
            }
        }
        //再解析变量
        for (c in ctx.structBody().staticStructMemberDeclaration()) {
            if (c!!.structMember().structFieldDeclaration() != null) {
                visit(c)
            }
        }
        //后成员
        //先解析函数和构造函数
        for (c in ctx.structBody().structMemberDeclaration()) {
            if (c!!.structMember().structFunctionDeclaration() != null) {
                visit(c)
            }
        }
        //再解析变量
        for (c in ctx.structBody().structMemberDeclaration()) {
            if (c!!.structMember().structFieldDeclaration() != null) {
                visit(c)
            }
        }
        //如果没有构造函数，自动添加默认的空构造函数
        if (Struct.currStruct!!.constructors.size == 0) {
            Struct.currStruct!!.addConstructor(StructConstructor(Struct.currStruct!!))
        }
        Struct.currStruct = null
        return null
    }

    @Override
    override fun visitStaticStructMemberDeclaration(ctx: mcfppParser.StaticStructMemberDeclarationContext): Any? {
        Project.ctx = ctx
        val m = visit(ctx.structMember()) as Member
        //访问修饰符
        if (ctx.accessModifier() != null) {
            m.accessModifier = AccessModifier.valueOf(ctx.accessModifier().text.uppercase(Locale.getDefault()))
        }
        m.isStatic = true
        Struct.currStruct!!.addMember(m)
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
    override fun visitStructMemberDeclaration(ctx: mcfppParser.StructMemberDeclarationContext): Any? {
        Project.ctx = ctx
        val m = visit(ctx.structMember()) as Member
        //访问修饰符
        if (ctx.accessModifier() != null) {
            m.accessModifier = AccessModifier.valueOf(ctx.accessModifier().text.uppercase(Locale.getDefault()))
        }
        if (m !is Constructor) {
            Struct.currStruct!!.addMember(m)
        }
        return null
    }

    @Override
    override fun visitStructMember(ctx: mcfppParser.StructMemberContext): Any? {
        Project.ctx = ctx
        return if (ctx.structFunctionDeclaration() != null) {
            visit(ctx.structFunctionDeclaration())
        } else if (ctx.structFieldDeclaration() != null) {
            visit(ctx.structFieldDeclaration())
        } else if (ctx.constructorDeclaration() != null) {
            visit(ctx.constructorDeclaration())
        }else{
            return null
        }
    }

    override fun visitStructFunctionDeclaration(ctx: mcfppParser.StructFunctionDeclarationContext): Any {
        Project.ctx = ctx
        //创建函数对象
        val f = Function(
            ctx.Identifier().text,
            Struct.currStruct!!,
            ctx.parent is mcfppParser.StaticStructMemberDeclarationContext,
            ctx.functionReturnType().text
        )
        //解析参数
        f.addParams(ctx.parameterList())
        //注册函数
        if (Struct.currStruct!!.field.hasFunction(f) || Struct.currStruct!!.staticField.hasFunction(f)) {
            Project.error("Already defined function:" + ctx.Identifier().text + "in struct " + Struct.currStruct!!.identifier)
            Function.currFunction = Function.nullFunction
        }
        return f
    }

    override fun visitStructFieldDeclaration(ctx: mcfppParser.StructFieldDeclarationContext): Any {
        Project.ctx = ctx
        //变量生成
        val `var`: Var = Var.build(ctx.Identifier().text,"int", Struct.currStruct!!)!!
        //是否是静态的
        if(ctx.parent.parent is mcfppParser.StaticStructMemberDeclarationContext){
            `var`.isStatic = true
        }
        //只有可能是结构体成员
        if (Struct.currStruct!!.field.containVar(ctx.Identifier().text)
            || Struct.currStruct!!.staticField.containVar(ctx.Identifier().text)
        ) {
            Project.error("Duplicate defined variable name:" + ctx.Identifier().text)
            throw VariableDuplicationException()
        }
        return `var`
    }

}