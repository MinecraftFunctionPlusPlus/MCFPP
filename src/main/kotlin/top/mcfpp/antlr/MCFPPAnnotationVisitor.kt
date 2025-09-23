package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.Project.withCompilationContext
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.exception.UndefinedException
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil.toJava
import top.mcfpp.util.StringHelper.splitNamespaceID

class MCFPPAnnotationVisitor: mcfppParserBaseVisitor<Unit>(){

    private val annotationCache = ArrayList<Annotation>()

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
        Annotation.newInstance(annotation, args)?.let { annotationCache.add(it) }
    }

    override fun visitTemplateDeclaration(ctx: mcfppParser.TemplateDeclarationContext): Unit = withCompilationContext(ctx) {
        //注册模板
        val id = ctx.classWithoutNamespace().text
        val namespace1 = GlobalScope.localNamespaces[Project.currNamespace]!!
        val template = if(namespace1.field.hasTemplate(id)){
            namespace1.field.getTemplate(id)!!
        }else{
            throw UndefinedException("Template should have been defined: $id")
        }
        annotationCache.forEach {
            it.on(template)
        }
        template.annotations.addAll(annotationCache)
        annotationCache.clear()
        DataTemplate.currTemplate = template
        ctx.templateBody()?.let { visitTemplateBody(it) }
        DataTemplate.currTemplate = null
    }

    override fun visitObjectTemplateDeclaration(ctx: mcfppParser.ObjectTemplateDeclarationContext): Unit = withCompilationContext(ctx) {
        //注册模板
        val id = ctx.classWithoutNamespace().text
        val namespace1 = GlobalScope.localNamespaces[Project.currNamespace]!!
        val objectTemplate = namespace1.field.getObject(id)
        if(objectTemplate !is ObjectDataTemplate){
            throw UndefinedException("Template should have been defined: $id")
        }
        annotationCache.forEach {
            it.on(objectTemplate)
        }
        objectTemplate.annotations.addAll(annotationCache)
        annotationCache.clear()
        DataTemplate.currTemplate = objectTemplate
        ctx.templateBody()?.let { visitTemplateBody(it) }
        DataTemplate.currTemplate = null
    }

    override fun visitFunctionDeclaration(ctx: mcfppParser.FunctionDeclarationContext): Unit = withCompilationContext(ctx) {
        //获取函数对象
        val types = ctx.functionParams()?.let { FunctionParam.parseReadonlyAndNormalParamTypes(it) }
        //获取缓存中的对象
        val f = GlobalScope.getFunction(
            Project.currNamespace,
            ctx.Identifier().text,
            types?.first?.map { it.build("") }?:ArrayList(),
            types?.second?.map { it.build("") }?:ArrayList()
        )
        annotationCache.forEach {
            it.on(f)
        }
        f.annotations.addAll(annotationCache)
        annotationCache.clear()
    }

    override fun visitTemplateFieldDeclaration(ctx: mcfppParser.TemplateFieldDeclarationContext): Unit = withCompilationContext(ctx) {
        //获取字段对象
        val field = DataTemplate.currTemplate!!.field.getVar(ctx.Identifier().text)!!
        annotationCache.forEach {
            it.on(field)
        }
        field.annotations.addAll(annotationCache)
        annotationCache.clear()
    }
}