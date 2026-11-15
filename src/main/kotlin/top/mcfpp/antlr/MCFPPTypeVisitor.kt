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
import top.mcfpp.type.MCFPPType
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
        // Source declarations use named types. Internal projection/restoration can still synthesize anonymous types.
        object : mcfppParserBaseVisitor<Unit>() {
            override fun visitAnonymousTemplateType(node: mcfppParser.AnonymousTemplateTypeContext) {
                withCompilationContext(node) {
                    LogProcessor.error("Anonymous data types in source must be replaced by a named data declaration; nested declarations are public")
                }
            }
        }.visit(ctx)
        //命名空间
        if (ctx.namespaceDeclaration() != null) {
            //获取命名空间
            val namespaceStr = ctx.namespaceDeclaration().namespacePath().text
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
        ctx.typealiasDeclaration().forEach(::visitTypealiasDeclaration)
        //文件结构，类和函数
        for (t in ctx.typeDeclaration()) {
            visitTypeDeclaration(t)
        }
    }

    /**
     * 完成一次库的import
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
        if (!Namespace.currNamespaceField.putType(id, MCFPPTypeAliasType(ctx.type()))) {
            LogProcessor.error("Type has been defined: $id in namespace ${Project.currNamespace}")
        }
    }

    fun importType(ctx: mcfppParser.ImportTypeContext): Pair<String?, String>  {
        return ctx.text.splitNamespaceID()
    }


    override fun visitTemplateDeclaration(ctx: mcfppParser.TemplateDeclarationContext): Unit = withCompilationContext(ctx) {
        //注册模板
        val id = top.mcfpp.analysis.NestedTemplates.name(ctx)
        val nsp = GlobalScope.localNamespaces[Project.currNamespace]!!
        if (nsp.scope.hasDeclaredType(id)) {
            //重复声明
            LogProcessor.error("Type has been defined: $id in namespace ${Project.currNamespace}")
            DataTemplate.currTemplate = nsp.scope.getTemplate(id)
            return@withCompilationContext
        }
        val isAbstract = ctx.ABSTRACT() != null
        val isFinal = ctx.FINAL() != null
        if(ctx.AS() != null){
            if(isAbstract){
                LogProcessor.error("Typeas Template cannot be abstract")
            }
            if(isFinal){
                LogProcessor.warn("Redundant modifiers")
            }
            val type = MCFPPType.parsePrimitiveType(ctx.type())?: run {
                LogProcessor.error("Must be a primitive type: $id")
                MCFPPPrivateType.Void
            }
            val template = TypeDataTemplate(type, id, Project.currNamespace)
            template.document = top.mcfpp.doc.SourceDocumentation.document(ctx)
            nsp.scope.addTemplate(id, template)
        }else{
            val readonly = top.mcfpp.analysis.NestedTemplates.readonly(ctx)
            val template = if(readonly.isEmpty()) {
                DataTemplate(id,Project.currNamespace)
            } else {
                val qwq = GenericDataTemplate(ctx.templateBody(), id, Project.currNamespace)
                qwq.readOnlyParams.addAll(readonly.map {
                    DataTemplateParam(
                        ReadonlyParamsDeclarationSyntax.readonlyTypeName(it),
                        ReadonlyParamsDeclarationSyntax.readonlyParameterName(it),
                        variance = when {
                            it.OUT() != null -> DeclarationVariance.OUT
                            it.IN() != null -> DeclarationVariance.IN
                            else -> DeclarationVariance.INVARIANT
                        }
                    )
                })
                qwq
            }
            template.isAbstract = isAbstract
            template.isEntityTemplate = ctx.ENTITY() != null
            if (template.isEntityTemplate && ctx.compoundDeclaration().extendName().isEmpty() && id != "Entity")
                template.parentID.add("mcfpp.minecraft.entity:Entity")
            template.document = top.mcfpp.doc.SourceDocumentation.document(ctx)
            template.isFinal = isFinal
            template.parentID.addAll(ctx.compoundDeclaration().extendName().map { it.text })
            nsp.scope.addTemplate(id, template)
        }
        val registered = nsp.scope.getTemplate(id)!!
        val owner = top.mcfpp.analysis.NestedTemplates.owner(registered)
        if (owner != null) {
            owner.scope.putType(id.substringAfterLast('.'), registered.getType())
            registered.scope.parent.add(owner.scope)
            val member = ctx.parent as? mcfppParser.TemplateMemberContext
            val declaration = member?.parent as? mcfppParser.TemplateMemberDeclarationContext
            if (declaration?.accessModifier()?.text?.let { it != "public" } == true)
                LogProcessor.error("Nested template '$id' must be public")
        }
        ctx.templateBody()?.templateMemberDeclaration()?.forEach { member ->
            member.templateMember().templateDeclaration()?.let(::visitTemplateDeclaration)
        }
    }

    /**
     *
     */
    override fun visitObjectTemplateDeclaration(ctx: mcfppParser.ObjectTemplateDeclarationContext): Unit = withCompilationContext(ctx){
        //注册模板
        val id = ctx.compoundDeclaration().declarationName().classWithoutNamespace().text
        val nsp = GlobalScope.localNamespaces[Project.currNamespace]!!
        if (nsp.scope.hasObject(id)) {
            //重复声明
            LogProcessor.error("Type has been defined: $id in namespace ${Project.currNamespace}")
            return
        }
        val template = if(ctx.compoundDeclaration().declarationName().readOnlyParams() == null) {
            ObjectDataTemplate(id,Project.currNamespace)
        } else {
            val qwq = GenericObjectDataTemplate(ctx.templateBody(), id, Project.currNamespace)
            qwq.readOnlyParams.addAll(ctx.compoundDeclaration().declarationName().readOnlyParams().readOnlyParameterList().readOnlyParameter().map {
                DataTemplateParam(
                    ReadonlyParamsDeclarationSyntax.readonlyTypeName(it),
                    ReadonlyParamsDeclarationSyntax.readonlyParameterName(it),
                    variance = when {
                        it.OUT() != null -> DeclarationVariance.OUT
                        it.IN() != null -> DeclarationVariance.IN
                        else -> DeclarationVariance.INVARIANT
                    })
            })
            qwq
        }
        template.isFinal = ctx.FINAL() != null
        template.document = top.mcfpp.doc.SourceDocumentation.document(ctx)
        template.parentID.addAll(ctx.compoundDeclaration().extendName().map { it.text })
        template.companionObject = template
        nsp.scope.addObject(id, template)
        ctx.templateBody()?.templateMemberDeclaration()?.forEach { member ->
            member.templateMember().templateDeclaration()?.let(::visitTemplateDeclaration)
        }
    }

    override fun visitEnumDeclaration(ctx: mcfppParser.EnumDeclarationContext): Unit = withCompilationContext(ctx) {
        //注册枚举
        val id = ctx.Identifier().text
        val nsp = GlobalScope.localNamespaces[Project.currNamespace]!!
        if (nsp.scope.hasDeclaredType(id)) {
            //重复声明
            LogProcessor.error("Type has been defined: $id in namespace ${Project.currNamespace}")
        }
        val enum = Enum(id, Project.currNamespace)
        enum.document = top.mcfpp.doc.SourceDocumentation.document(ctx)
        nsp.scope.addEnum(id, enum)
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
