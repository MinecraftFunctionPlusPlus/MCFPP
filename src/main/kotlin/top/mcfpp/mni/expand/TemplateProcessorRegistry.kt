package top.mcfpp.mni.expand

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.preparationSpan
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.api.TemplateProcessor
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.type.TypeId
import top.mcfpp.util.LogProcessor
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap
import top.mcfpp.antlr.mcfppParser as P

/** Reflection metadata only. Discovery neither initializes nor constructs the processor class. */
internal class TemplateProcessorRegistration internal constructor(
    val annotationName: String,
    val processorClass: Class<out TemplateProcessor>
)

internal data class TemplateProcessorSourceOrder(val file: Int, val declaration: Int, val annotation: Int) {
    init {
        require(file >= 0 && declaration >= 0 && annotation >= 0) { "Source order cannot be negative" }
    }
}

/** Raw source input, never an executed processor or a projected/mutable declaration view. */
internal class TemplateProcessorRequest internal constructor(
    val registration: TemplateProcessorRegistration,
    val target: DataTemplate,
    val sourceFile: MCFPPFile,
    val declarationSyntax: ParserRuleContext,
    val annotationSyntax: P.AnnotationContext,
    arguments: List<CompilerValue>,
    val order: TemplateProcessorSourceOrder
) {
    val arguments: List<CompilerValue> = Collections.unmodifiableList(ArrayList(arguments))
    val source = preparationSpan(annotationSyntax)
}

/** One namespace owns these relations; no loader, registration, or request survives in a static cache. */
internal class TemplateProcessorRegistry(val namespace: String) {
    private val entries = linkedMapOf<String, TemplateProcessorRegistration>()
    private val inputs = arrayListOf<TemplateProcessorRequest>()

    fun registrations(): List<TemplateProcessorRegistration> = immutable(entries.values)
    fun requests(): List<TemplateProcessorRequest> = immutable(
        inputs.sortedWith(
            compareBy(
                { it.order.file },
                { it.order.declaration },
                { it.order.annotation }
            )
        )
    )
    fun find(canonicalName: String): TemplateProcessorRegistration? = entries[canonicalName]

    fun register(name: String, className: String, loader: ClassLoader): Boolean {
        if (!validName(name)) return error("Processor annotation '$name' must be a source identifier in namespace '$namespace'")
        val type = try {
            Class.forName(className, false, loader)
        } catch (failure: ClassNotFoundException) {
            return error("Template processor class '$className' was not found")
        } catch (failure: LinkageError) {
            return error("Cannot load template processor class '$className': ${failure.message}")
        } catch (failure: SecurityException) {
            return error("Cannot load template processor class '$className': ${failure.message}")
        }
        return register(name, type)
    }

    fun register(name: String, type: Class<*>): Boolean {
        if (!validName(name)) return error("Processor annotation '$name' must be a source identifier in namespace '$namespace'")
        if (!TemplateProcessor::class.java.isAssignableFrom(type))
            return error("Template processor class '${type.name}' must implement TemplateProcessor")
        if (type.isInterface ||
            Modifier.isAbstract(type.modifiers) ||
            type.isAnonymousClass ||
            type.isLocalClass ||
            generateSequence(type) { it.enclosingClass }.any { !Modifier.isPublic(it.modifiers) }
        )
            return error("Template processor class '${type.name}' must be public and concrete")
        try {
            type.getConstructor()
        } catch (failure: NoSuchMethodException) {
            return error("Template processor class '${type.name}' must have a public no-argument constructor")
        } catch (failure: SecurityException) {
            return error("Cannot inspect template processor class '${type.name}': ${failure.message}")
        }
        val previous = entries[name]
        if (previous != null) return if (previous.processorClass === type) true
        else error("Conflicting template processors for '$name': '${previous.processorClass.name}' and '${type.name}'")
        entries[name] = TemplateProcessorRegistration(name, type.asSubclass(TemplateProcessor::class.java))
        return true
    }

    /** C_SOURCE_REQUESTS supplies the actual source order after ordinary indexing/import resolution. */
    fun record(
        registration: TemplateProcessorRegistration,
        target: DataTemplate,
        file: MCFPPFile,
        declaration: ParserRuleContext,
        annotation: P.AnnotationContext,
        arguments: List<CompilerValue>,
        order: TemplateProcessorSourceOrder
    ): TemplateProcessorRequest? {
        if (entries[registration.annotationName] !== registration) {
            error("Template processor request uses a registration outside this namespace generation")
            return null
        }
        if (declaration.javaClass !in declarationClasses) {
            error("Template processors require a named data declaration")
            return null
        }
        try {
            (arguments as List<*>).forEach {
                require(it is CompilerValue.Typed) { "Processor annotation arguments require complete typed compiler values" }
                value(it, 0)
            }
        } catch (failure: IllegalArgumentException) {
            error(failure.message ?: "Invalid processor annotation arguments")
            return null
        }
        val previous = inputs.firstOrNull { it.target === target && it.sourceFile === file && it.annotationSyntax === annotation }
        if (previous != null) {
            if (previous.registration !== registration ||
                previous.declarationSyntax !== declaration ||
                previous.order != order ||
                previous.arguments != arguments
            ) {
                error("Conflicting facts for the same template processor source annotation")
                return null
            }
            return previous
        }
        return TemplateProcessorRequest(registration, target, file, declaration, annotation, arguments, order).also(inputs::add)
    }

    internal fun mergeFrom(other: TemplateProcessorRegistry) {
        if (other === this) return
        require(other.namespace == namespace)
        other.registrations().forEach { register(it.annotationName, it.processorClass) }
        other.requests().forEach { request ->
            val registration = find(request.registration.annotationName) ?: return@forEach
            if (registration.processorClass === request.registration.processorClass)
                record(
                    registration,
                    request.target,
                    request.sourceFile,
                    request.declarationSyntax,
                    request.annotationSyntax,
                    request.arguments,
                    request.order
                )
        }
    }

    /** Raw relation snapshots retain every generation, including requests no longer in the live list.
     * B_JOURNAL owns model restoration and must inspect these exact classes/roles before hashing.
     * File/syntax objects are retained inputs; only supported targets/field scopes/value facts are
     * published to the composing declaration journal, never registry internals or processor Classes.
     */
    internal class RawCheckpoint internal constructor(
        val registrations: List<TemplateProcessorRegistration>,
        val requests: List<TemplateProcessorRequest>,
        val retainedRegistrations: List<TemplateProcessorRegistration>,
        val retainedRequests: List<TemplateProcessorRequest>,
        val targets: List<DataTemplate>,
        val sourceFiles: List<MCFPPFile>,
        val syntax: List<ParserRuleContext>,
        private val registry: TemplateProcessorRegistry
    ) {
        fun restore() {
            registry.entries.clear()
            registrations.forEach { registry.entries[it.annotationName] = it }
            registry.inputs.clear()
            registry.inputs.addAll(requests)
        }
        fun captureCurrent(): RawCheckpoint = registry.checkpoint(retainedRegistrations, retainedRequests)
    }

    fun checkpoint(): RawCheckpoint = checkpoint(emptyList(), emptyList())
    private fun checkpoint(oldRegistrations: List<TemplateProcessorRegistration>, oldRequests: List<TemplateProcessorRequest>): RawCheckpoint {
        val registrations = registrations()
        val requests = immutable(inputs)
        val retainedRegistrations = identities(oldRegistrations + registrations)
        val retainedRequests = identities(oldRequests + requests)
        return RawCheckpoint(
            registrations,
            requests,
            retainedRegistrations,
            retainedRequests,
            identities(retainedRequests.map { it.target }),
            identities(retainedRequests.map { it.sourceFile }),
            identities(retainedRequests.flatMap { listOf(it.declarationSyntax, it.annotationSyntax) }),
            this
        )
    }

    private fun validName(name: String): Boolean {
        if (!name.startsWith("$namespace:")) return false
        val lexer = mcfppLexer(CharStreams.fromString(name))
        var valid = true
        lexer.removeErrorListeners()
        val errors = object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>?,
                offendingSymbol: Any?,
                line: Int,
                charPositionInLine: Int,
                msg: String?,
                error: RecognitionException?
            ) {
                valid = false
            }
        }
        lexer.addErrorListener(errors)
        val parser = P(CommonTokenStream(lexer))
        parser.removeErrorListeners()
        parser.addErrorListener(errors)
        val syntax = parser.namespaceID()
        return valid &&
            syntax.text == name &&
            syntax.namespacePath()?.text == namespace &&
            syntax.Identifier()?.symbol?.type == mcfppLexer.Identifier &&
            parser.currentToken.type == Token.EOF
    }

    private fun error(message: String): Boolean {
        LogProcessor.error(message)
        return false
    }

    companion object {
        private val declarationClasses = setOf(
            P.TemplateDeclarationContext::class.java,
            P.ObjectTemplateDeclarationContext::class.java,
        )
        private fun <T> immutable(items: Collection<T>): List<T> = Collections.unmodifiableList(ArrayList(items))
        private fun <T : Any> identities(items: List<T>): List<T> {
            val seen = Collections.newSetFromMap(IdentityHashMap<T, Boolean>())
            return immutable(items.filter { seen.add(it) })
        }
        private fun value(input: Any?, depth: Int) {
            require(depth <= 128) { "Processor annotation argument exceeds the closed-value depth" }
            when (input) {
                CompilerValue.NullValue, is CompilerValue.Integral, is CompilerValue.FloatBits,
                is CompilerValue.DoubleBits, is CompilerValue.Bool, is CompilerValue.Text, is CompilerValue.Nbt -> Unit
                is CompilerValue.TypeValue -> identity(input.id, depth + 1)
                is CompilerValue.Typed -> {
                    identity(input.type, depth + 1)
                    value(input.payload, depth + 1)
                }
                is CompilerValue.Sequence -> (input.elements as List<*>).forEach { value(it, depth + 1) }
                is CompilerValue.Record -> (input.fields as Map<*, *>).forEach { (key, child) ->
                    require(key is String) { "Processor annotation record key must be a string" }
                    value(child, depth + 1)
                }
                else -> throw IllegalArgumentException("Processor annotation argument is not a closed compiler value")
            }
        }
        private fun identity(input: Any?, depth: Int) {
            require(depth <= 128) { "Processor annotation type exceeds the identity depth" }
            when (input) {
                is TypeId.Builtin, is TypeId.Declaration, is TypeId.Opaque -> Unit
                is TypeId.Applied -> {
                    identity(input.constructor, depth + 1)
                    (input.arguments as List<*>).forEach { identity(it, depth + 1) }
                }
                is TypeId.Specialized -> {
                    identity(input.constructor, depth + 1)
                    (input.arguments as List<*>).forEach { value(it, depth + 1) }
                }
                is TypeId.Union -> (input.alternatives as Set<*>).forEach { identity(it, depth + 1) }
                is TypeId.Selector -> (input.entities as List<*>?)?.forEach { require(it is String) { "Selector entity must be a string" } }
                else -> throw IllegalArgumentException("Processor annotation argument contains an unsupported type identity")
            }
        }
    }
}
