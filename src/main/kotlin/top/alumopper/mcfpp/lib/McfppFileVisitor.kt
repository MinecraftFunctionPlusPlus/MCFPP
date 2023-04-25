package top.alumopper.mcfpp.lib

import mcfppBaseVisitor
import top.alumopper.mcfpp.Project
import top.alumopper.mcfpp.exception.*
import top.alumopper.mcfpp.exception.IllegalFormatException
import top.alumopper.mcfpp.lang.INativeClass
import top.alumopper.mcfpp.lang.Var
import top.alumopper.mcfpp.lang.Var.ConstStatus
import top.alumopper.mcfpp.lib.ClassMember.AccessModifier
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
        //命名空间
        if (ctx.namespaceDeclaration() != null) {
            Project.currNamespace = ctx.namespaceDeclaration()!!.Identifier().text
        }
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
        visit(ctx.classOrFunctionDeclaration())
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
    override fun visitClassOrFunctionDeclaration(ctx: mcfppParser.ClassOrFunctionDeclarationContext): Any? {
        if (ctx.classDeclaration() != null) {
            visit(ctx.classDeclaration())
        } else if (ctx.functionDeclaration() != null) {
            visit(ctx.functionDeclaration())
        } else if (ctx.nativeFuncDeclaration() != null) {
            visit(ctx.nativeFuncDeclaration())
        } else {
            visit(ctx.nativeClassDeclaration())
        }
        return null
    }

    /**
     * native类的声明
     * @param ctx the parse tree
     * @return null
     */
    override fun visitNativeClassDeclaration(ctx: mcfppParser.NativeClassDeclarationContext): Any? {
        //注册类
        val identifier: String = ctx.className().text
        return if (Project.global.cache.classes.containsKey(identifier)) {
            //重复声明
            Project.logger.error(
                "The class has extended " + Class.currClass!!.identifier +
                        " at " + Project.currFile.name + " line: " + ctx.getStart().line
            )
            Project.errorCount++
            throw ClassDuplicationException()
        } else {
            //获取它指向的java类
            val cls: java.lang.Class<INativeClass> = try {
                java.lang.Class.forName(ctx.javaRefer().text) as java.lang.Class<INativeClass>
            } catch (e: ClassNotFoundException) {
                throw RuntimeException(e)
            } catch (e: ClassCastException) {
                throw RuntimeException(e)
            }
            val ncls: NativeClass = if (ctx.className().Identifier() != null) {
                //声明了命名空间
                NativeClass(identifier, ctx.className().Identifier().text, cls)
            } else {
                NativeClass(identifier, cls)
            }
            Project.global.cache.classes[identifier] = ncls
            null
        }
    }

    /**
     * 类的声明
     * @param ctx the parse tree
     * @return null
     */
    @Override
    override fun visitClassDeclaration(ctx: mcfppParser.ClassDeclarationContext): Any? {
        //注册类
        val identifier: String = ctx.className(0).text
        if (Project.global.cache.classes.containsKey(identifier)) {
            //重复声明
            Project.logger.error(
                "The class has extended " + Class.currClass!!.identifier +
                        " at " + Project.currFile.name + " line: " + ctx.getStart().line
            )
            Project.errorCount++
            throw ClassDuplicationException()
        } else {
            //如果没有声明过这个类
            val cls: Class = if (ctx.className(0).Identifier() != null) {
                //声明了命名空间
                Class(identifier, ctx.className(0).Identifier().text)
            } else {
                Class(identifier)
            }
            if (ctx.className().size != 1) {
                if (Project.global.cache.classes.containsKey(ctx.className(1).text)) {
                    cls.parent = Project.global.cache.classes[ctx.className(1).text]
                } else {
                    Project.logger.error(
                        "Undefined class: " + ctx.className(1).text +
                                " at " + Project.currFile.name + " line: " + ctx.getStart().line
                    )
                    Project.errorCount++
                }
            }
            Project.global.cache.classes[identifier] = cls
            Class.currClass = cls
            cls.isStaticClass = ctx.STATIC() != null
        }
        //解析类中的成员
        //先静态
        //先解析函数
        for (c in ctx.classBody().staticClassMemberDeclaration()) {
            if (c!!.classMember().fieldDeclaration() == null) {
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
            if (c!!.classMember().fieldDeclaration() == null) {
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
        val m = visit(ctx.classMember()) as ClassMember
        //访问修饰符
        if (ctx.accessModifier() != null) {
            m.accessModifier = AccessModifier.valueOf(ctx.accessModifier().text.uppercase(Locale.getDefault()))
        }
        m.isStatic = true
        Class.currClass!!.addMember(m)
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
        val m = visit(ctx.classMember()) as ClassMember
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
        return if (ctx.nativeFuncDeclaration() != null) {
            visit(ctx.nativeFuncDeclaration())
        } else if (ctx.classFunctionDeclaration() != null) {
            visit(ctx.classFunctionDeclaration())
        } else if (ctx.fieldDeclaration() != null) {
            visit(ctx.fieldDeclaration())
        } else if (ctx.constructorDeclaration() != null) {
            visit(ctx.constructorDeclaration())
        } else {
            visit(ctx.nativeConstructorDeclaration())
        }
    }

    /**
     * 类方法的声明
     * @param ctx the parse tree
     * @return 这个类方法的对象
     */
    @Override
    override fun visitClassFunctionDeclaration(ctx: mcfppParser.ClassFunctionDeclarationContext): Any {
        //创建函数对象
        val f = Function(
            ctx.Identifier().text,
            Class.currClass!!,
            ctx.parent is mcfppParser.StaticClassMemberDeclarationContext
        )
        //解析参数
        if (ctx.parameterList() != null) {
            f.addParams(ctx.parameterList())
        }
        //注册函数
        if (Class.currClass!!.cache!!.functions.contains(f) || Class.currClass!!.staticCache!!.functions.contains(
                f
            )
        ) {
            Project.logger.error(
                "Already defined function:" + ctx.Identifier()
                    .text + "in class " + Class.currClass!!.identifier +
                        " at " + Project.currFile.name + " line: " + ctx.getStart().line
            )
            Project.errorCount++
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
        //是构造函数
        //创建构造函数对象，注册函数
        var f: Constructor? = null
        try {
            f = Constructor(Class.currClass!!)
            Class.currClass!!.addConstructor(f)
        } catch (e: FunctionDuplicationException) {
            Project.logger.error(
                "Already defined function: " + ctx.className().text + "(" + ctx.parameterList().text + ")" +
                        " at " + Project.currFile.name + " line: " + ctx.getStart().line
            )
        }
        assert(f != null)
        if (ctx.parameterList() != null) {
            f!!.addParams(ctx.parameterList())
        }
        return f
    }

    @Override
    override fun visitNativeConstructorDeclaration(ctx: mcfppParser.NativeConstructorDeclarationContext?): Any? {
        //TODO
        throw TODOException("")
    }

    /**
     * 函数的声明
     * @param ctx the parse tree
     * @return null
     */
    @Override
    override fun visitFunctionDeclaration(ctx: mcfppParser.FunctionDeclarationContext): Any? {
        //创建函数对象
        val f: Function
        //是否是内联函数
        //是否是内联函数
        if (ctx.INLINE() == null) {
            //获取函数的文本内容
            //函数是否拥有命名空间声明
            f = if (ctx.namespaceID().Identifier().size == 1) {
                InlineFunction(ctx.namespaceID().Identifier(0).text, ctx)
            } else {
                InlineFunction(
                    ctx.namespaceID().Identifier(0).text,
                    ctx.namespaceID().Identifier(1).text,
                    ctx
                )
            }
        } else {
            //函数是否拥有命名空间声明
            f = if (ctx.namespaceID().Identifier().size == 1) {
                Function(ctx.namespaceID().Identifier(0).text)
            } else {
                Function(ctx.namespaceID().Identifier(0).text, ctx.namespaceID().Identifier(1).text)
            }
        }
        //解析参数
        if (ctx.parameterList() != null) {
            f.addParams(ctx.parameterList())
        }
        //解析函数的tag
        if (ctx.functionTag() != null) {
            val functionTag: FunctionTag = if (ctx.functionTag()!!.namespaceID().Identifier().size == 1) {
                FunctionTag(FunctionTag.MINECRAFT, ctx.functionTag()!!.namespaceID().Identifier(0).text)
            } else {
                FunctionTag(
                    ctx.functionTag()!!.namespaceID().Identifier(0).text,
                    ctx.functionTag()!!.namespaceID().Identifier(1).text
                )
            }
            if (Project.global.functionTags.containsKey(functionTag.namespaceID)) {
                f.tag = Project.global.functionTags[functionTag.namespaceID]
            } else {
                Project.global.functionTags[functionTag.namespaceID] = functionTag
                f.tag = functionTag
            }
            f.tag!!.cache.functions.add(f)
        }
        //不是类的成员
        f.isClassMember = false
        if (Project.global.cache.getFunction(f.namespace, f.name, f.paramTypeList) == null) {
            Project.global.cache.functions.add(f)
        } else {
            Project.logger.error(
                "Already defined function:" + f.namespaceID +
                        " at " + Project.currFile.name + " line: " + ctx.getStart().line
            )
            Project.errorCount++
            Function.currFunction = Function.nullFunction
        }
        if (f.tag != null && (f.tag!! == FunctionTag.TICK || f.tag!! == FunctionTag.LOAD) && (ctx.parent as mcfppParser.FunctionDeclarationContext).parameterList()!!
                .parameter().size != 0
        ) {
            Project.logger.error(
                "Entrance function shouldn't have parameter:" + f.namespaceID +
                        " at " + Project.currFile.name + " line: " + ctx.getStart().line
            )
            Project.errorCount++
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
        val nf: NativeFunction = try {
            NativeFunction(ctx.Identifier().text, ctx.javaRefer())
        } catch (e: IllegalFormatException) {
            Project.logger.error(
                "Illegal Java Method Name:" + e.message +
                        " at " + Project.currFile.name + " line: " + ctx.getStart().line
            )
            return null
        } catch (e: ClassNotFoundException) {
            Project.logger.error(
                "Cannot find java class:" + e.message +
                        " at " + Project.currFile.name + " line: " + ctx.getStart().line
            )
            return null
        } catch (e: NoSuchMethodException) {
            Project.logger.error(
                "No such method:" + e.message +
                        " at " + Project.currFile.name + " line: " + ctx.getStart().line
            )
            return null
        }
        if (ctx.parameterList() != null) {
            nf.addParams(ctx.parameterList())
        }
        if (Class.currClass == null) {
            //是普通的函数
            nf.isClassMember = false
            if (!Project.global.cache.functions.contains(nf)) {
                Project.global.cache.functions.add(nf)
            } else {
                Project.logger.error(
                    "Already defined function:" + ctx.Identifier().text +
                            " at " + Project.currFile.name + " line: " + ctx.getStart().line
                )
                Project.errorCount++
                Function.currFunction = Function.nullFunction
            }
            return nf
        }
        //是类成员
        nf.isClassMember = true
        nf.parentClass = Class.currClass
        return nf
    }

    /**
     * 类字段的声明
     * @param ctx the parse tree
     * @return null
     */
    @Override
    override fun visitFieldDeclaration(ctx: mcfppParser.FieldDeclarationContext): Any {
        //变量生成
        val `var`: Var = Var.build(ctx, Class.currClass!!)!!
        //只有可能是类变量
        if (Class.currClass!!.cache!!.containVar(
                ctx.Identifier().text
            ) || Class.currClass!!.staticCache!!.containVar(ctx.Identifier().text)
        ) {
            Project.logger.error(
                "Duplicate defined variable name:" + ctx.Identifier().text +
                        " at " + Project.currFile.name + " line:" + ctx.getStart().line
            )
            Project.errorCount++
            throw VariableDuplicationException()
        }
        //变量的初始化
        if (ctx.expression() != null) {
            `var`.isConst = ConstStatus.ASSIGNED
            Function.currFunction = Class.currClass!!.classPreInit!!
            Function.addCommand("#" + ctx.text)
            val init: Var = McfppExprVisitor().visit(ctx.expression())!!
            try {
                `var`.assign(init)
            } catch (e: VariableConverseException) {
                Project.logger.error(
                    "Cannot convert " + init.javaClass + " to " + `var`.javaClass +
                            " at " + Class.currClass!!.identifier + " line:" + ctx.getStart().line
                )
                Project.errorCount++
                Function.currFunction = Function.nullFunction
                throw VariableConverseException()
            }
            Function.currFunction = Function.nullFunction
        }
        return `var`
    }
}