package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.Class
import top.mcfpp.model.ObjectClass
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor

class MCFPPGenericObjectClassFieldVisitor(val clazz: ObjectClass) : MCFPPFieldVisitor() {
    override fun visitClassDeclaration(ctx: mcfppParser.ClassDeclarationContext): Any? {
        Project.ctx = ctx

        Class.currClass = clazz

        typeScope = clazz.field
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
        //不可能为抽象类
        var il : Function? = null
        clazz.field.forEachFunction { f ->
            run {
                if(f.isAbstract){
                    il = f
                    return@run
                }
            }
        }
        if(il != null){
            LogProcessor.error("Class $clazz must either be declared abstract or implement abstract method ${il!!.nameWithNamespace}")
        }
        Class.currClass = null
        typeScope = MCFPPFile.currFile!!.field
        return null
    }
}