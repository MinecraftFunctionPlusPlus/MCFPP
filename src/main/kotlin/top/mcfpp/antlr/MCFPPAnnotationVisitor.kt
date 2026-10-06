package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.Project.withCompilationContext
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.exception.UndefinedException
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.compound.ObjectCompoundData
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil.toJava
import top.mcfpp.util.StringHelper.splitNamespaceID

class MCFPPAnnotationVisitor: mcfppParserBaseVisitor<Unit>(){

    private val annotationCache = ArrayList<Annotation>()

    // Anonymous bodies replay annotations when their actual model is created.
    override fun visitAnonymousTemplateType(ctx: mcfppParser.AnonymousTemplateTypeContext) = Unit

    override fun visitAnnotation(ctx: mcfppParser.AnnotationContext): Unit = withCompilationContext(ctx) {
        //获取注解
        val qwq = ctx.Identifier().text.splitNamespaceID()
        val annotation = GlobalScope.getAnnotation(qwq.first, qwq.second)
        if(annotation == null){
            //注解不存在
            LogProcessor.error("Annotation ${ctx.Identifier().text} not found")
            return
        }
        val args = ArrayList<Any>()
        //参数解析
        for(c in ctx.annotationArgs()?.value()?: emptyList()){
            val a = MCFPPExprVisitor().visitValue(c) as MCFPPValue<*>
            if(a.value is Tag<*>){
                args.add((a.value as Tag<*>).toJava())
            }else{
                args.add(a.value!!)
            }
        }
        Annotation.build(annotation, args)?.let { annotationCache.add(it) }
    }

    override fun visitTemplateDeclaration(ctx: mcfppParser.TemplateDeclarationContext): Unit = withCompilationContext(ctx) {
        //注册模板
        val id = (ctx.declarationName()?: ctx.compoundDeclaration().declarationName()).classWithoutNamespace().text
        val namespace1 = GlobalScope.localNamespaces[Project.currNamespace]!!
        val template = if(namespace1.scope.hasTemplate(id)){
            namespace1.scope.getTemplate(id)!!
        }else{
            throw UndefinedException("Template should have been defined: $id")
        }
        if (template is GenericDataTemplate || template is ObjectDataTemplate) {
            annotationCache.forEach { it.on(template) }
        }
        template.annotations.addAll(annotationCache)
        annotationCache.clear()
        DataTemplate.currTemplate = template
        if (template !is GenericDataTemplate) ctx.templateBody()?.let { visitTemplateBody(it) }
        DataTemplate.currTemplate = null
    }


    override fun visitInterfaceDeclaration(ctx: mcfppParser.InterfaceDeclarationContext) {
        //注册模板
        val id = ctx.compoundDeclaration().declarationName().classWithoutNamespace().text
        val namespace1 = GlobalScope.localNamespaces[Project.currNamespace]!!
        val itf = if(namespace1.scope.hasInterface(id)){
            namespace1.scope.getInterface(id)!!
        }else{
            throw UndefinedException("Interface should have been defined: $id")
        }
        annotationCache.forEach {
            it.on(itf)
        }
        itf.annotations.addAll(annotationCache)
        annotationCache.clear()
        DataTemplate.currTemplate = itf
        ctx.templateBody()?.let { visitTemplateBody(it) }
        DataTemplate.currTemplate = null
    }

    override fun visitObjectTemplateDeclaration(ctx: mcfppParser.ObjectTemplateDeclarationContext): Unit = withCompilationContext(ctx) {
        //注册模板
        val id = ctx.compoundDeclaration().declarationName().classWithoutNamespace().text
        val namespace1 = GlobalScope.localNamespaces[Project.currNamespace]!!
        val objectTemplate = namespace1.scope.getObject(id)
        if(objectTemplate !is DataTemplate || objectTemplate !is ObjectCompoundData){
            throw UndefinedException("Template should have been defined: $id")
        }
        annotationCache.forEach {
            it.on(objectTemplate)
        }
        objectTemplate.annotations.addAll(annotationCache)
        annotationCache.clear()
        DataTemplate.currTemplate = objectTemplate
        if (objectTemplate !is GenericDataTemplate) ctx.templateBody()?.let { visitTemplateBody(it) }
        DataTemplate.currTemplate = null
    }

    override fun visitFunctionDeclaration(ctx: mcfppParser.FunctionDeclarationContext): Unit = withCompilationContext(ctx) {
        val name = ctx.functionDeclarationPart().Identifier().text
        val f = GlobalScope.localNamespaces[Project.currNamespace]!!.scope.functions[name]
            ?.firstOrNull { it.ast === ctx.curlBlock() }
        if (f == null) {
            LogProcessor.error("Function declaration was not indexed: $name")
            annotationCache.clear()
            return@withCompilationContext
        }
        annotationCache.forEach {
            it.on(f)
        }
        f.annotations.addAll(annotationCache)
        annotationCache.clear()
    }

    override fun visitTemplateFieldDeclaration(ctx: mcfppParser.TemplateFieldDeclarationContext): Unit = withCompilationContext(ctx) {
        DataTemplate.currTemplate!!.deferredFields[ctx.Identifier().text]?.let { declaration ->
            declaration.annotations.addAll(annotationCache)
            annotationCache.clear()
            return
        }
        val template = DataTemplate.currTemplate!!
        val field = template.scope.getVar(ctx.Identifier().text) ?: run {
            annotationCache.clear()
            return@withCompilationContext
        }
        if (template !is GenericDataTemplate && template !is ObjectDataTemplate) {
            template.pendingFieldAnnotations.getOrPut(ctx.Identifier().text) { arrayListOf() }.addAll(annotationCache)
            annotationCache.clear()
            return@withCompilationContext
        }
        //获取字段对象
        annotationCache.forEach {
            it.on(field)
        }
        field.annotations.addAll(annotationCache)
        annotationCache.clear()
    }
}
