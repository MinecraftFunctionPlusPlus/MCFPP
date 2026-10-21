package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.Project.withCompilationContext
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.DeclarationBinding
import top.mcfpp.analysis.DeclarationLexicalSnapshot
import top.mcfpp.analysis.DeclarationPreparationContext
import top.mcfpp.analysis.PreparationFactFailure
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.api.TemplateProcessor
import top.mcfpp.mni.expand.TemplateProcessorRegistration
import top.mcfpp.mni.expand.TemplateProcessorSourceOrder
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.exception.UndefinedException
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.compound.ObjectCompoundData
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.scope.NamespaceScope
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil.toJava

class MCFPPAnnotationVisitor: mcfppParserBaseVisitor<Unit>(){

    private val annotationCache = ArrayList<Annotation>()
    private class ProcessorInput(val namespace: Namespace, val registration: TemplateProcessorRegistration,
                                 val syntax: mcfppParser.AnnotationContext, val arguments: List<CompilerValue>)
    private val processorInputs = arrayListOf<ProcessorInput>()

    /** Use the same visible namespace/import scopes as ordinary annotation lookup. A selected
     * import scope exposes only its selected annotation, never every processor in that namespace. */
    private fun visibleAnnotation(name: String): Pair<Namespace?, Class<out Annotation>?>? {
        val file = MCFPPFile.currFile
        val scopes = arrayListOf<Pair<NamespaceScope, Boolean>>()
        if (file != null) {
            scopes.add(file.field.namespaceField to true)
            file.field.importField.values.forEach { scopes.add(it to (it.getAnnotation(name) != null)) }
            file.field.importedNamespaceField.values.forEach { scopes.add(it to true) }
        }
        GlobalScope.stdNamespaces.values.forEach { scopes.add(it.scope to true) }
        for ((scope, allowMapping) in scopes) {
            val owners = listOfNotNull(GlobalScope.localNamespaces[scope.identifier],
                GlobalScope.importedLibNamespaces[scope.identifier], GlobalScope.libNamespaces[scope.identifier],
                GlobalScope.stdNamespaces[scope.identifier])
            // An identifier can belong to several distinct local/library scopes. Only the
            // owner of this actual visible scope may contribute a processor registration.
            val owner = owners.firstOrNull { it.scope === scope }
            if (allowMapping && owner?.templateProcessors?.find("${scope.identifier}:$name") != null)
                return owner to null
            scope.getAnnotation(name)?.let { annotation ->
                return owner to annotation
            }
        }
        return null
    }

    private fun recordProcessors(target: DataTemplate, declaration: org.antlr.v4.runtime.ParserRuleContext) {
        val inputs = processorInputs.toList()
        processorInputs.clear()
        if (inputs.isEmpty()) return
        val file = MCFPPFile.currFile
        if (file == null) {
            LogProcessor.error("Template processor annotations require an original source file")
            return
        }
        val files = Project.journalState().sourceFiles
        val position = files.indexOfFirst { it === file }
        if (position < 0 && files.isNotEmpty()) {
            LogProcessor.error("Template processor annotations require an indexed source file")
            return
        }
        for (input in inputs) input.namespace.templateProcessors.record(input.registration, target, file,
            declaration, input.syntax, input.arguments, TemplateProcessorSourceOrder(position.coerceAtLeast(0),
                declaration.start.tokenIndex, input.syntax.start.tokenIndex))
    }

    private fun rejectProcessors() {
        processorInputs.forEach { input ->
            withCompilationContext(input.syntax) {
                LogProcessor.error("Template processor '${input.registration.annotationName}' requires a named data declaration")
            }
        }
        processorInputs.clear()
    }

    override fun visitCompilationUnit(ctx: mcfppParser.CompilationUnitContext) {
        super.visitCompilationUnit(ctx)
        rejectProcessors()
    }

    override fun visitDeclarations(ctx: mcfppParser.DeclarationsContext) {
        if (ctx.annotation() == null && ctx.templateDeclaration() == null &&
            ctx.objectTemplateDeclaration() == null) rejectProcessors()
        super.visitDeclarations(ctx)
    }

    override fun visitTemplateMember(ctx: mcfppParser.TemplateMemberContext) {
        if (ctx.annotation() == null) rejectProcessors()
        super.visitTemplateMember(ctx)
    }

    override fun visitTemplateBody(ctx: mcfppParser.TemplateBodyContext) {
        super.visitTemplateBody(ctx)
        rejectProcessors()
    }

    /** Generic bodies defer legacy annotation evaluation until materialization. Validate only
     * source placement here: every body annotation is on a member, never a named template. */
    private fun scanDeferredProcessorPlacements(body: mcfppParser.TemplateBodyContext) {
        for (member in body.templateMemberDeclaration()) {
            val syntax = member.templateMember().annotation() ?: continue
            val name = syntax.Identifier().text
            val selected = visibleAnnotation(name) ?: continue
            val owner = selected.first
            val annotation = selected.second
            if (owner?.templateProcessors?.find("${owner.identifier}:$name") != null ||
                annotation != null && TemplateProcessor::class.java.isAssignableFrom(annotation)) {
                withCompilationContext(syntax) {
                    LogProcessor.error("Template processor '$name' requires a named data declaration")
                }
            }
        }
    }

    private fun visitDeclarationBody(body: mcfppParser.TemplateBodyContext?, template: DataTemplate) {
        body ?: return
        if (template is GenericDataTemplate) scanDeferredProcessorPlacements(body)
        else visitTemplateBody(body)
    }

    // Anonymous bodies replay annotations when their actual model is created.
    override fun visitAnonymousTemplateType(ctx: mcfppParser.AnonymousTemplateTypeContext) = Unit

    /**
     * Bind every source annotation argument through a pure declaration reader. The resulting
     * closed [CompilerValue] facts are the only values later projected into `AnnotationView`.
     */
    private fun bindArguments(ctx: mcfppParser.AnnotationContext, binding: DeclarationBinding): List<CompilerValue> =
        ctx.annotationArgs()?.value().orEmpty().map { value ->
            val proof = binding.value(value).constant
                ?: throw DeclarationBinding.Failure("Annotation arguments require complete closed values")
            if (proof !is CompilerValue.Typed) throw DeclarationBinding.Failure(
                "Annotation arguments require complete typed compiler values")
            proof
        }

    override fun visitAnnotation(ctx: mcfppParser.AnnotationContext): Unit = withCompilationContext(ctx) {
        //获取注解
        val name = ctx.Identifier().text
        val selected = visibleAnnotation(name)
        val namespace = selected?.first
        val annotation = selected?.second
        val registered = namespace?.templateProcessors?.find("${namespace.identifier}:$name")
        if (registered != null || annotation != null && TemplateProcessor::class.java.isAssignableFrom(annotation)) {
            // Reflection checks do not initialize/construct the adapter. Its legacy protocol must
            // never run as well as expand(), including when its registration is invalid.
            val owner = namespace
            if (owner == null) {
                LogProcessor.error("Template processor '$name' has no visible owning namespace")
                return@withCompilationContext
            }
            if (registered == null && !owner.registerTemplateProcessor("${owner.identifier}:$name", annotation!!))
                return@withCompilationContext
            val registration = owner.templateProcessors.find("${owner.identifier}:$name")!!
            val file = MCFPPFile.currFile
            if (file == null) {
                LogProcessor.error("Template processor annotations require an original source file")
                return@withCompilationContext
            }
            val arguments = if (ctx.annotationArgs() == null) emptyList<CompilerValue>() else try {
                val binding = DeclarationBinding(DeclarationPreparationContext(
                    DeclarationLexicalSnapshot.capture(file, file.namespace.identifier)))
                bindArguments(ctx, binding)
            } catch (failure: PreparationFactFailure) {
                LogProcessor.error("${failure.code}: ${failure.message} at ${failure.source}")
                return@withCompilationContext
            } catch (failure: DeclarationBinding.Failure) {
                LogProcessor.error(failure.message ?: "Cannot bind processor annotation argument")
                return@withCompilationContext
            }
            processorInputs.add(ProcessorInput(owner, registration, ctx, arguments))
            return@withCompilationContext
        }
        if(annotation == null){
            //注解不存在
            LogProcessor.error("Annotation ${ctx.Identifier().text} not found")
            return
        }
        val arguments = if (ctx.annotationArgs() == null) emptyList<CompilerValue>() else try {
            bindArguments(ctx, DeclarationBinding(Function.currFunction, emptyMap()))
        } catch (failure: PreparationFactFailure) {
            LogProcessor.error("${failure.code}: ${failure.message} at ${failure.source}")
            return
        } catch (failure: DeclarationBinding.Failure) {
            LogProcessor.error(failure.message ?: "Cannot bind annotation argument")
            return
        }
        val args = ArrayList<Any>()
        //参数解析：the same closed facts drive both the legacy JVM constructor and the view.
        for (value in arguments) {
            val converted = annotationValue(value)
            if (converted == null) {
                LogProcessor.error("Annotation arguments require complete immutable values")
                return
            }
            args.add(converted)
        }
        Annotation.build(annotation, args)?.let {
            it.recordCompilerArguments(arguments)
            annotationCache.add(it)
        }
    }

    private fun annotationValue(value: CompilerValue): Any? = when (value) {
        is CompilerValue.Typed -> when (val payload = value.payload) {
            is CompilerValue.Integral -> when (value.type) {
                MCFPPNBTType.Byte.typeId -> payload.value.toByte()
                MCFPPNBTType.Short.typeId -> payload.value.toShort()
                MCFPPBaseType.Int.typeId -> payload.value.toInt()
                else -> payload.value
            }
            else -> annotationValue(payload)
        }
        is CompilerValue.Text -> value.value
        is CompilerValue.Bool -> value.value
        is CompilerValue.Integral -> value.value
        is CompilerValue.FloatBits -> Float.fromBits(value.bits)
        is CompilerValue.DoubleBits -> Double.fromBits(value.bits)
        is CompilerValue.Nbt -> Tag.toNBT(value.snbt).toJava()
        is CompilerValue.Sequence -> value.elements.map { annotationValue(it) }
        is CompilerValue.Record -> value.fields.mapValues { annotationValue(it.value) }
        else -> null
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
        if (template is GenericDataTemplate) template.recordDeclarationAnnotations(annotationCache)
        else {
            if (template is ObjectDataTemplate) annotationCache.forEach { it.on(template) }
            else template.pendingDeclarationAnnotations.addAll(annotationCache)
            template.annotations.addAll(annotationCache)
        }
        annotationCache.clear()
        recordProcessors(template, ctx)
        DataTemplate.currTemplate = template
        visitDeclarationBody(ctx.templateBody(), template)
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
        if (objectTemplate is GenericDataTemplate) objectTemplate.recordDeclarationAnnotations(annotationCache)
        else {
            annotationCache.forEach { it.on(objectTemplate) }
            objectTemplate.annotations.addAll(annotationCache)
        }
        annotationCache.clear()
        recordProcessors(objectTemplate, ctx)
        DataTemplate.currTemplate = objectTemplate
        visitDeclarationBody(ctx.templateBody(), objectTemplate)
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
