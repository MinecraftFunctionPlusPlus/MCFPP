package top.mcfpp.mni.expand

import top.mcfpp.Project
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.util.LogProcessor
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Dispatches the recorded original source requests exactly once after the complete declaration
 * barrier.
 *
 * Each request owns a fresh processor instance, detached declaration session and cumulative
 * compiler transaction. A committed request becomes visible to the next processor and to ordinary
 * source body binding. Generated metadata is published into the declaration model only; nothing
 * re-enters the annotation visitor, so a generated annotation can never schedule another processor.
 */
internal object TemplateProcessorRunner {
    /** Documented built-in marker for declaration processors that publish generated members. */
    internal const val GENERATED_MEMBERS = "mni:GeneratedMembers"

    private val executed = Collections.newSetFromMap(IdentityHashMap<TemplateProcessorRequest, Boolean>())
    private val metadataPolicy = linkedMapOf<String, DeclarationMetadataRule>()

    /** Project initialization starts a new compilation unit; no request or policy leaks across it. */
    internal fun reset() {
        executed.clear()
        metadataPolicy.clear()
        // The external-provider marker is always available, non-repeatable and argument-free.
        // It is metadata only: a generated fact never re-enters the annotation visitor.
        registerMetadata(GENERATED_MEMBERS, DeclarationMetadataRule(emptyMap()))
    }

    /**
     * Register the exact compile-time metadata whitelist used by declaration processors.
     *
     * The whitelist is an internal compiler mapping; processors never describe their own shapes.
     */
    internal fun registerMetadata(name: String, rule: DeclarationMetadataRule) {
        require(name.isNotBlank()) { "Generated metadata requires a canonical annotation name" }
        metadataPolicy[name] = rule
    }

    /**
     * Execute every not-yet-executed original input in (file, declaration, annotation) order.
     * The caller must have completed all files' fields and inheritance before this entry runs.
     */
    internal fun run(): Int {
        check(Project.templateDeclarationsReady) {
            "Template processors require the complete multi-file declaration barrier"
        }
        val requests = namespaces().flatMap { it.templateProcessors.requests() }
            .distinct()
            .sortedWith(
                compareBy<TemplateProcessorRequest>(
                    { it.order.file },
                    { it.order.declaration },
                    { it.order.annotation }
                )
            )
        var count = 0
        for (request in requests) {
            if (!executed.add(request)) continue
            count++
            execute(request)
        }
        return count
    }

    private fun namespaces(): List<Namespace> = (
        GlobalScope.localNamespaces.values + GlobalScope.importedLibNamespaces.values +
            GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values
        ).distinct()

    private fun execute(request: TemplateProcessorRequest) {
        val target = request.target
        val label = request.registration.annotationName
        if (!target.hasCompletedDeclaration || target.hasDeclarationCompletionFailure) {
            LogProcessor.error("Template processor '$label' requires the completed declaration of ${target.namespaceID}")
            return
        }
        val callerFile = MCFPPFile.currFile
        val callerFunction = Function.currFunction
        val callerTemplate = DataTemplate.currTemplate
        var prepared: PreparedDeclarationEdits? = null
        try {
            MCFPPFile.currFile = request.sourceFile
            DataTemplate.currTemplate = target
            // The synthetic caller is a real declaration owned by the target, so the ordinary
            // accessibility walk authorizes exactly the private self fields of this template. Its
            // lexical scope stays the source file (not the template field scope), so declaration
            // field prototypes never enter the captured storage/original-value domain.
            val caller = Function(
                "__mni_processor_${request.order.file}_${request.order.declaration}",
                request.sourceFile.namespace.identifier,
                null
            )
            caller.owner = target
            caller.declarationFile = request.sourceFile
            Function.currFunction = caller

            val environment = CompilerSourceIndex(caller, declarationTarget = target, processorRequest = request)
                .prepareDeclarationEnvironment(request.annotationSyntax)
            val view = requireNotNull(environment.declarationView) {
                "Completed template '${target.namespaceID}' produced no canonical declaration view"
            }
            val processor = request.registration.processorClass.getConstructor().newInstance()
            val backend = CompilerCommitBackend()
            val configuration = "processor:${request.registration.annotationName}"
            val expansion = CompilerSourceBindings.expansionEnvironment(
                environment,
                configurationKey = configuration,
                namespace = request.sourceFile.namespace.identifier
            )
            val site = ExpansionSite(
                request.source,
                "processor:${request.registration.annotationName}:${request.order.file}:${request.order.declaration}",
                configuration
            )
            val validator = DeclarationEditValidator(
                site,
                Project.macroExpansionBudget(),
                expansion,
                backend,
                view,
                metadataPolicy
            )
            prepared = validator.prepare(processor)
            val bindings = CompilerSourceBindings.capture(environment, prepared.session, caller)
            backend.attach(bindings)
            GeneratedDeclarationCompiler(prepared, bindings).compile()
        } catch (failure: ExpansionFailure) {
            val diagnostic = failure.diagnostic
            LogProcessor.error("Template processor '$label' failed at ${diagnostic.source}: ${diagnostic.message}")
        } catch (failure: Throwable) {
            LogProcessor.error("Template processor '$label' failed: ${failure.message ?: failure.javaClass.simpleName}", failure)
        } finally {
            try {
                prepared?.close()
            } catch (_: Throwable) { }
            Function.currFunction = callerFunction
            MCFPPFile.currFile = callerFile
            DataTemplate.currTemplate = callerTemplate
            // `Function.currFunction` may have been changed by the journaled commit; the ambient
            // restoration above is the only state the next request may inherit.
        }
    }
}
