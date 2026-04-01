package top.mcfpp.antlr

import top.mcfpp.Project.withCompilationContext
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.compound.CompiledGenericObjectDataTemplate
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor

class MCFPPGenericObjectDataTemplateFieldVisitor(val template: CompiledGenericObjectDataTemplate) : MCFPPFieldVisitor() {
    override fun visitTemplateDeclaration(ctx: mcfppParser.TemplateDeclarationContext): Any? = withCompilationContext(ctx) {

        DataTemplate.currTemplate = template

        typeScope = template.scope
        //解析类中的成员
        //先解析函数和构造函数
        for (c in ctx.templateBody().templateMemberDeclaration()) {
            c!!
            if (c.templateMember() != null && (c.templateMember().templateFunctionDeclaration() != null)) {
                visit(c)
            }
        }
        //再解析变量
        for (c in ctx.templateBody().templateMemberDeclaration()) {
            if (c!!.templateMember() != null && c.templateMember().templateFieldDeclaration() != null) {
                visit(c)
            }
        }
        //不可能为抽象类
        var il : Function? = null
        template.scope.forEachFunction { f ->
            run {
                if(f.isAbstract){
                    il = f
                    return@run
                }
            }
        }
        if(il != null){
            LogProcessor.error("Class $template must either be declared abstract or implement abstract method ${il!!}")
        }
        DataTemplate.currTemplate = null
        typeScope = MCFPPFile.currFile!!.field.namespaceField
        return null
    }
}