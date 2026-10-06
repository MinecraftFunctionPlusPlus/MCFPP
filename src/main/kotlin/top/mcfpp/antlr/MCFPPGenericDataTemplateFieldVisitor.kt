package top.mcfpp.antlr

import top.mcfpp.Project.withCompilationContext
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectCompoundData
import top.mcfpp.model.function.DataTemplateConstructor

class MCFPPGenericDataTemplateFieldVisitor(val template: DataTemplate) : MCFPPFieldVisitor() {
    override fun visitTemplateBody(ctx: mcfppParser.TemplateBodyContext): Any? = withCompilationContext(ctx) {

        DataTemplate.currTemplate = template

        typeScope = DataTemplate.currTemplate!!.scope
        isInObject = template is ObjectCompoundData
        //解析类中的成员
        //先解析函数和构造函数
        for (c in ctx.templateMemberDeclaration()) {
            c!!
            if (c.templateMember() != null && (c.templateMember().templateFunctionDeclaration() != null ||
                        c.templateMember().templateConstructorDeclaration() != null)) {
                visit(c)
            }
        }
        //再解析变量
        for (c in ctx.templateMemberDeclaration()) {
            if (c!!.templateMember() != null && c.templateMember().templateFieldDeclaration() != null) {
                visit(c)
            }
        }
        //如果没有构造函数，自动添加默认的空构造函数
        if (!isInObject && template.constructors.isEmpty()) {
            DataTemplate.currTemplate!!.addMember(DataTemplateConstructor(DataTemplate.currTemplate!!, null))
        }
        DataTemplate.currTemplate = null
        typeScope = MCFPPFile.currFile!!.field.namespaceField
        return null
    }
}
