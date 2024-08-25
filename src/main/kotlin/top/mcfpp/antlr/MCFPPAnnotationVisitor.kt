package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.exception.UndefinedException
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.model.ObjectClass
import top.mcfpp.model.ObjectDataTemplate
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.field.GlobalField
import top.mcfpp.util.LogProcessor

class MCFPPAnnotationVisitor: mcfppParserBaseVisitor<Unit>(){

    val annotationCache = ArrayList<Annotation>()

    override fun visitAnnotation(ctx: mcfppParser.AnnotationContext?) {
        //获取注解
        val annotation = GlobalField.annotations[ctx!!.Identifier().text]
        if(annotation == null){
            //注解不存在
            LogProcessor.error("Annotation ${ctx.Identifier().text} not found")
            return
        }
        val args = ArrayList<Any>()
        //参数解析
        for(c in ctx.annotationArgs().value()){
            val a = MCFPPExprVisitor().visitValue(c) as MCFPPValue<*>
            args.add(a.value!!)
        }
        val a = Annotation.newInstance(annotation, args)
        annotationCache.add(a)
    }

    override fun visitTemplateDeclaration(ctx: mcfppParser.TemplateDeclarationContext) {
        //注册模板
        val id = ctx.classWithoutNamespace().text
        val namespace1 = GlobalField.localNamespaces[Project.currNamespace]!!
        val template = if(namespace1.field.hasTemplate(id)){
            namespace1.field.getTemplate(id)!!
        }else{
            throw UndefinedException("Template should have been defined: $id")
        }
        annotationCache.forEach {
            it.forDataObject(template)
        }
        template.annotations.addAll(annotationCache)
        annotationCache.clear()
    }

    override fun visitObjectTemplateDeclaration(ctx: mcfppParser.ObjectTemplateDeclarationContext) {
        //注册模板
        val id = ctx.classWithoutNamespace().text
        val namespace1 = GlobalField.localNamespaces[Project.currNamespace]!!
        val objectTemplate = namespace1.field.getObject(id)
        if(objectTemplate !is ObjectDataTemplate){
            throw UndefinedException("Template should have been defined: $id")
        }
        annotationCache.forEach {
            it.forDataObject(objectTemplate)
        }
        objectTemplate.annotations.addAll(annotationCache)
        annotationCache.clear()
    }

    override fun visitObjectClassDeclaration(ctx: mcfppParser.ObjectClassDeclarationContext) {
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
            it.forClass(clazz)
        }
        clazz.annotations.addAll(annotationCache)
        annotationCache.clear()
    }

    override fun visitClassDeclaration(ctx: mcfppParser.ClassDeclarationContext) {
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
            it.forClass(clazz)
        }
        clazz.annotations.addAll(annotationCache)
        annotationCache.clear()
    }
}