package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.Class
import top.mcfpp.model.function.ClassConstructor
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor

class MCFPPGenericClassFieldVisitor(val clazz: Class) : MCFPPFieldVisitor() {
    override fun visitClassDeclaration(ctx: mcfppParser.ClassDeclarationContext): Any? {
        Project.ctx = ctx

        Class.currClass = clazz

        typeScope = Class.currClass!!.field
        //解析类中的成员
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
                LogProcessor.error("Class ${Class.currClass} must either be declared abstract or implement abstract method ${il!!.nameWithNamespace}")
            }
        }
        Class.currClass = null
        typeScope = MCFPPFile.currFile!!.field
        return null
    }
}