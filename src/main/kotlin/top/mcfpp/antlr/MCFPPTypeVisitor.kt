package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.Project.withCompilationContext
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.*
import top.mcfpp.model.compound.Enum
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPTypeAliasType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper.splitNamespaceID

/**
 * 解析当前项目的类型
 */
class MCFPPTypeVisitor: mcfppParserBaseVisitor<Unit>() {

    /**
     * 遍历整个文件。一个文件包含了命名空间的声明，函数的声明，类的声明以及全局变量的声明。全局变量是可以跨文件调用的。
     * @param ctx the parse tree
     * @return null
     */
    override fun visitCompilationUnit(ctx: mcfppParser.CompilationUnitContext): Unit = withCompilationContext(ctx) {
        //命名空间
        if (ctx.namespaceDeclaration() != null) {
            //获取命名空间
            val namespaceStr = ctx.namespaceDeclaration().Identifier().joinToString(".") { it.text }
            Project.currNamespace = namespaceStr
            MCFPPFile.currFile!!.namespace = GlobalScope.getOrCreateNamespace(namespaceStr)
        }
        if(!GlobalScope.localNamespaces.containsKey(Project.currNamespace)){
            GlobalScope.localNamespaces[Project.currNamespace] = Namespace(Project.currNamespace)
        }
        //导入库
        for (lib in ctx.importDeclaration()){
            visitImportDeclaration(lib)
        }
        //文件结构，类和函数
        for (t in ctx.typeDeclaration()) {
            visitTypeDeclaration(t)
        }
    }

    /**
     * 完成一次库的import
     * TODO 类型别名
     *
     * @param ctx
     */
    override fun visitImportDeclaration(ctx: mcfppParser.ImportDeclarationContext): Unit = withCompilationContext(ctx) {
        //获取命名空间和导入类型
        val nsp = importType(ctx.importType())
        MCFPPFile.currFile!!.unsolvedImports[nsp.first!!] = nsp.second
    }

    override fun visitTypealiasDeclaration(ctx: mcfppParser.TypealiasDeclarationContext) {
        //类型别名引用
        val id = ctx.Identifier().text
        Namespace.currNamespaceField.putType(id, MCFPPTypeAliasType(ctx.type()))
    }

    fun importType(ctx: mcfppParser.ImportTypeContext): Pair<String?, String>  {
        return ctx.text.splitNamespaceID()
    }

    override fun visitInterfaceDeclaration(ctx: mcfppParser.InterfaceDeclarationContext): Unit = withCompilationContext(ctx){
        //注册类
        val id = ctx.classWithoutNamespace().text
        val nsp = GlobalScope.localNamespaces[Project.currNamespace]!!
        if (nsp.field.hasDeclaredType(id)) {
            //重复声明
            LogProcessor.error("Type has been defined: $id in namespace ${Project.currNamespace}")
            Interface.currInterface = nsp.field.getInterface(id)
        } else {
            //如果没有声明过这个类
            val itf = Interface(id, Project.currNamespace)
            for (p in ctx.className()){
                //是否存在继承
                val nsn = p.text.splitNamespaceID()
                val namespace  = nsn.first
                val identifier = nsn.second
                val pc = GlobalScope.getInterface(namespace, identifier)
                if(pc == null){
                    LogProcessor.error("Undefined Interface: " + p.text)
                }else{
                    itf.extends(pc)
                }
            }
            nsp.field.addInterface(id, itf)
        }
    }

    override fun visitTemplateDeclaration(ctx: mcfppParser.TemplateDeclarationContext): Unit = withCompilationContext(ctx) {
        //注册模板
        val id = ctx.classWithoutNamespace().text
        val nsp = GlobalScope.localNamespaces[Project.currNamespace]!!
        if (nsp.field.hasDeclaredType(id)) {
            //重复声明
            LogProcessor.error("Type has been defined: $id in namespace ${Project.currNamespace}")
            DataTemplate.currTemplate = nsp.field.getTemplate(id)
        }
        if(ctx.AS() != null){
            val template = TypeDataTemplate(MCFPPPrivateType.Void, id, Project.currNamespace)
            nsp.field.addTemplate(id, template)
        }else{
            val template = DataTemplate(id,Project.currNamespace)
            template.extends(DataTemplate.baseDataTemplate)
            nsp.field.addTemplate(id, template)
        }
    }

    /**
     *
     */
    override fun visitObjectTemplateDeclaration(ctx: mcfppParser.ObjectTemplateDeclarationContext): Unit = withCompilationContext(ctx){
        //注册模板
        val id = ctx.classWithoutNamespace().text
        val nsp = GlobalScope.localNamespaces[Project.currNamespace]!!
        if (nsp.field.hasObject(id)) {
            //重复声明
            LogProcessor.error("Type has been defined: $id in namespace ${Project.currNamespace}")
            return
        }
        val template = ObjectDataTemplate(id,Project.currNamespace)
        template.extends(DataTemplate.baseDataTemplate)
        nsp.field.addObject(id, template)
    }

    override fun visitEnumDeclaration(ctx: mcfppParser.EnumDeclarationContext): Unit = withCompilationContext(ctx) {
        //注册枚举
        val id = ctx.Identifier().text
        val nsp = GlobalScope.localNamespaces[Project.currNamespace]!!
        if (nsp.field.hasDeclaredType(id)) {
            //重复声明
            LogProcessor.error("Type has been defined: $id in namespace ${Project.currNamespace}")
        }
        val enum = Enum(id, Project.currNamespace)
        nsp.field.addEnum(id, enum)
        //添加成员
        for (m in ctx.enumBody().enumMember()) {
            val value = enum.getNextMemberValue()
            val data = m.nbtValue()?.let { Tag.toNBT(it.text)}
            val member =
                EnumMember(m.Identifier().text, value, data ?: IntTag(0))
            enum.addMember(member)
        }
    }
}