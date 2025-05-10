package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.Project.withCompilationContext
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.exception.UndefinedException
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.Class
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectClass
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil.toJava
import top.mcfpp.util.StringHelper.splitNamespaceID

class MCFPPAnnotationVisitor: mcfppParserBaseVisitor<Unit>(){

    private val annotationCache = ArrayList<Annotation>()

    override fun visitAnnotation(ctx: mcfppParser.AnnotationContext): Unit = withCompilationContext(ctx) {
        //获取注解
        val qwq = ctx.Identifier().text.splitNamespaceID()
        val annotation = GlobalField.getAnnotation(qwq.first, qwq.second)
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
        val namespace1 = GlobalField.localNamespaces[Project.currNamespace]!!
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
        val namespace1 = GlobalField.localNamespaces[Project.currNamespace]!!
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

    override fun visitObjectClassDeclaration(ctx: mcfppParser.ObjectClassDeclarationContext): Unit = withCompilationContext(ctx) {
        val id = ctx.classWithoutNamespace().text
        val namespace = GlobalField.localNamespaces[Project.currNamespace]!!
        if(ctx.readOnlyParams() != null){
            return
        }
        val clazz = namespace.field.getObject(id)
        if(clazz !is ObjectClass){
            throw UndefinedException("Class should have been defined: $id")
        }
        annotationCache.forEach {
            it.on(clazz)
        }
        clazz.annotations.addAll(annotationCache)
        annotationCache.clear()
    }

    override fun visitClassDeclaration(ctx: mcfppParser.ClassDeclarationContext): Unit = withCompilationContext(ctx) {
        val id = ctx.classWithoutNamespace().text
        val namespace = GlobalField.localNamespaces[Project.currNamespace]!!
        if(ctx.readOnlyParams() != null){
            return
        }
        val clazz = if (namespace.field.hasClass(id)) {
            namespace.field.getClass(id)!!
        } else {
            throw UndefinedException("Class Should have been defined: $id")
        }
        annotationCache.forEach {
            it.on(clazz)
        }
        clazz.annotations.addAll(annotationCache)
        annotationCache.clear()
        Class.currClass = clazz
        visitClassBody(ctx.classBody())
        Class.currClass = null
    }

    override fun visitFunctionDeclaration(ctx: mcfppParser.FunctionDeclarationContext): Unit = withCompilationContext(ctx) {
        //获取函数对象
        val types = ctx.functionParams()?.let { FunctionParam.parseReadonlyAndNormalParamTypes(it) }
        //获取缓存中的对象
        val f = GlobalField.getFunction(
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

    override fun visitClassFieldDeclaration(ctx: mcfppParser.ClassFieldDeclarationContext): Unit = withCompilationContext(ctx)  {
        //获取字段对象
        val field = Class.currClass!!.field.getVar(ctx.Identifier().text)!!
        annotationCache.forEach {
            it.on(field)
        }
        field.annotations.addAll(annotationCache)
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