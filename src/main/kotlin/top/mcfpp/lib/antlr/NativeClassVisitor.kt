package top.mcfpp.lib.antlr

import mcfppBaseVisitor
import top.mcfpp.Project
import top.mcfpp.exception.ClassDuplicationException
import top.mcfpp.lang.ClassPointer
import top.mcfpp.lang.INativeClass
import top.mcfpp.lang.Var
import top.mcfpp.lib.Class
import top.mcfpp.lib.GlobalField
import top.mcfpp.lib.Member
import top.mcfpp.lib.NativeClass
import top.mcfpp.lib.NativeFunction
import java.lang.reflect.Method

/**
 * 遍历一个Native类，并将其和java方法对应
 *
 * @constructor Create empty Native class visitor
 */
class NativeClassVisitor: mcfppBaseVisitor<Any?>() {
    @Override
    override fun visitNativeClassDeclaration(ctx: mcfppParser.NativeClassDeclarationContext?): Any? {
        Project.ctx = ctx
        //注册类
        val identifier: String = ctx!!.classWithoutNamespace().text
        if (GlobalField.localNamespaces[Project.currNamespace]!!.hasClass(identifier)) {
            //重复声明
            Project.error("Already defined class: $identifier")
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
            val ncls = NativeClass(identifier, cls)
            GlobalField.localNamespaces[Project.currNamespace]!!.addClass(identifier, ncls)
            Class.currClass = ncls
            if(ctx.nativeClassBody() != null){
                visit(ctx.nativeClassBody())
            }
            Class.currClass = null
        }
        return null
    }

    @Override
    override fun visitNativeClassBody(ctx: mcfppParser.NativeClassBodyContext?): Any? {
        ctx!!
        for (i in ctx.nativeClassFunctionDeclaration()) {
            val nf = visit(i) as NativeFunction?
            if(nf != null){
                Class.currClass!!.staticField.addFunction(nf,false)
            }
        }
        return null
    }

    @Override
    override fun visitNativeClassFunctionDeclaration(ctx: mcfppParser.NativeClassFunctionDeclarationContext?): Any? {
        //accessModifier? NATIVE 'func' Identifier '(' parameterList? ')' ';'
        ctx!!
        val accessModifier: Member.AccessModifier = if (ctx.accessModifier() != null) {
            Member.AccessModifier.valueOf(ctx.accessModifier().text.lowercase())
        } else {
            Member.AccessModifier.PUBLIC
        }
        //获取这个java方法
        val javaMethod : Method
        try{
            val cls: java.lang.Class<*> = (Class.currClass as NativeClass).cls
            javaMethod = cls.getMethod(ctx.Identifier().text , Array<Var>::class.java, ClassPointer::class.java)
        } catch (e: NoSuchMethodException) {
            Project.error("No such method:" + ctx.Identifier().text)
            return null
        }
        val nf = NativeFunction(ctx.Identifier().text, javaMethod)
        nf.accessModifier = accessModifier
        if (ctx.parameterList() != null) {
            nf.addParams(ctx.parameterList())
        }
        return nf
    }
}