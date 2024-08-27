package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.exception.UndefinedException
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.model.Namespace
import top.mcfpp.model.ObjectClass
import top.mcfpp.model.ObjectDataTemplate
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.field.GlobalField
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper

class MCFPPAnnotationVisitor: mcfppParserBaseVisitor<Unit>(){

    val annotationCache = ArrayList<Annotation>()

    /**
     * 遍历整个文件。一个文件包含了命名空间的声明，函数的声明，类的声明以及全局变量的声明。全局变量是可以跨文件调用的。
     * @param ctx the parse tree
     * @return null
     */
    override fun visitCompilationUnit(ctx: mcfppParser.CompilationUnitContext) {
        Project.ctx = ctx
        //命名空间
        if (ctx.namespaceDeclaration() != null) {
            //获取命名空间
            var namespaceStr = ctx.namespaceDeclaration().Identifier()[0].text
            if(ctx.namespaceDeclaration().Identifier().size > 1){
                ctx.namespaceDeclaration().Identifier().forEach{e ->
                    run {
                        namespaceStr += ".${e.text}"
                    }
                }
            }
            Project.currNamespace = namespaceStr
        }
        //导入库
        for (lib in ctx.importDeclaration()){
            visit(lib)
        }
        if(!GlobalField.localNamespaces.containsKey(Project.currNamespace)){
            GlobalField.localNamespaces[Project.currNamespace] = Namespace(Project.currNamespace)
        }
        //文件结构，类和函数
        for (t in ctx.typeDeclaration()) {
            visit(t)
        }
    }

    override fun visitAnnotation(ctx: mcfppParser.AnnotationContext?) {
        //获取注解
        val qwq = StringHelper.splitNamespaceID(ctx!!.Identifier().text)
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