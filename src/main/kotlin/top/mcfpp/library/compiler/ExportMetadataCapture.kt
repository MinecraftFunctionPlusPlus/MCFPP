package top.mcfpp.library.compiler

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.backend.ReferenceHelperRecipe
import top.mcfpp.io.info.MacroFunctionInfo
import top.mcfpp.library.model.AnnotationPhase
import top.mcfpp.library.model.AnnotationProviderBinding
import top.mcfpp.library.model.AnnotationProviderDeclaration
import top.mcfpp.library.model.CanonicalIdentityBindings
import top.mcfpp.library.model.DeclCommon
import top.mcfpp.library.model.MacroDeclaration
import top.mcfpp.library.model.NativeBinding
import top.mcfpp.library.model.PortableValue
import top.mcfpp.library.model.ReferenceHelperDeclaration
import top.mcfpp.library.model.StateCell
import top.mcfpp.library.syntax.AnnotationId
import top.mcfpp.library.syntax.DeclarationReference
import top.mcfpp.library.syntax.PackageKey
import top.mcfpp.library.syntax.TypeRef
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.type.MCFPPType
import java.lang.reflect.Method
import java.util.IdentityHashMap

/** The export session owns declaration IDs, dependencies and the prepared author type directory. */
interface ExportMetadataBindings {
    val types: CompilerTypeCapture
    val identities: CanonicalIdentityBindings
    val managed: ManagedNativeBindings
    val annotations: PortableAnnotationCodecs
    fun sourceProvider(ownerClass: String): PackageKey?
    fun sourceType(namespace: String, expression: String): MCFPPType
    fun provider(binding: AnnotationProviderBinding): DeclarationReference
    fun declaration(value: Any): DeclarationReference
    fun phase(value: Annotation): AnnotationPhase
}

/** Captures native metadata using the same T/C tables and A/H arena as declaration capture. */
class ExportMetadataCapture(private val bindings: ExportMetadataBindings) {
    private val native = object : NativeCaptureBindings {
        override val types get() = bindings.types
        override val identities get() = bindings.identities
        override fun loader(provider: PackageKey?) = bindings.managed.loader(provider)
        override fun provider(owner: Class<*>) = bindings.managed.provider(owner)
        override fun sourceProvider(ownerClass: String) = bindings.sourceProvider(ownerClass)
        override fun sourceType(namespace: String, expression: String) = bindings.sourceType(namespace, expression)
    }
    private val annotationBindings = object : AnnotationCaptureBindings {
        override fun provider(binding: AnnotationProviderBinding) = bindings.provider(binding)
        override fun constant(value: CompilerValue) = bindings.types.constant(value)
        override fun type(value: MCFPPType) = bindings.types.type(value)
        override fun declaration(value: Any) = bindings.declaration(value)
        override fun phase(value: Annotation) = bindings.phase(value)
    }
    private val annotationIds = IdentityHashMap<Annotation, AnnotationId>()
    private var captured: CapturedAnnotations? = null

    val capturedAnnotations: CapturedAnnotations
        get() = checkNotNull(captured) { "Pre-discover annotation roots before capturing declaration annotation references" }

    fun captureMacro(namespace: String, common: DeclCommon, descriptor: MacroFunctionInfo): MacroDeclaration =
        NativeDeclarations.captureMacro(namespace, common, descriptor, native)

    fun captureReference(common: DeclCommon, recipe: ReferenceHelperRecipe): ReferenceHelperDeclaration =
        NativeDeclarations.captureReference(common, recipe, native)

    /** Return only the descriptor; the source Method is never retained in the portable model. */
    fun nativeBinding(method: Method, caller: TypeRef? = null): NativeBinding {
        val binding = NativeBinding(bindings.managed.provider(method.declaringClass), method.declaringClass.name,
            method.name, method.parameterTypes.map { it.name }, method.returnType.name, caller = caller)
        val resolved = NativeDeclarations.resolveMethod(binding, bindings.managed)
        require(resolved == method && resolved.declaringClass === method.declaringClass) { "Native source method differs from its managed provider" }
        return binding
    }

    fun captureProvider(common: DeclCommon, provider: AnnotationProviderBinding): AnnotationProviderDeclaration {
        require(provider in bindings.annotations.bindings()) { "Annotation provider requires an explicitly registered portable codec" }
        NativeDeclarations.restoreAnnotationProvider(provider, bindings.managed)
        return AnnotationProviderDeclaration(common, provider)
    }

    /**
     * Run once with every declaration annotation selected by source discovery. Recursive parent
     * providers are captured by the SDK in this same arena. Root A IDs can differ from list indices.
     */
    fun captureAnnotations(roots: List<Annotation>): CapturedAnnotations {
        check(captured == null) { "The export annotation arena has already been captured" }
        val selected = roots.toList()
        val result = bindings.annotations.capture(selected, annotationBindings)
        validateArena(result)
        require(selected.size == result.roots.size)
        selected.forEachIndexed { index, annotation ->
            val reference = result.roots[index]
            val previous = annotationIds.put(annotation, reference)
            require(previous == null || previous == reference) { "A shared annotation was assigned different arena references" }
        }
        captured = result
        return result
    }

    /** Suitable for FunctionCaptureBindings.annotation after the complete discovery pass. */
    fun annotation(value: Annotation): AnnotationId {
        capturedAnnotations
        return annotationIds[value] ?: throw PortableAnnotationException(
            "Declaration annotation was not included in source discovery: ${value.javaClass.name}")
    }

    private fun validateArena(arena: CapturedAnnotations) {
        val tables = bindings.types.snapshot()
        fun annotation(reference: AnnotationId): Int {
            require(reference.value in 1..arena.annotations.size.toLong()) { "Annotation reference is outside the captured A arena" }
            return (reference.value - 1).toInt()
        }
        fun value(value: PortableValue) {
            when (value) {
                is PortableValue.Annotation -> annotation(value.value)
                is PortableValue.Cell -> require(value.value.value in 1..arena.stateCells.size.toLong()) { "Cell reference is outside the captured H arena" }
                is PortableValue.Constant -> tables.constant(value.value)
                is PortableValue.Type -> tables.type(value.value)
                is PortableValue.Declaration -> Unit // The session owns local/dependency D resolution.
            }
        }
        // Validate every edge once. A/H aliases and finite cell cycles remain portable state.
        arena.roots.forEach(::annotation)
        arena.annotations.forEach { record ->
            record.arguments.forEach(tables::constant)
            record.parents.forEach(::annotation)
            record.state?.fields?.forEach { value(it.value) }
        }
        arena.stateCells.forEach { cell -> when (cell) {
            is StateCell.Sequence -> cell.values.forEach(::value)
            is StateCell.Map -> cell.entries.forEach { tables.constant(it.key); value(it.value) }
            is StateCell.Set -> cell.values.forEach(tables::constant)
        } }
        val colors = IntArray(arena.annotations.size)
        fun pendingParents(position: Int) {
            require(colors[position] != 1) { "Cycle reachable from pending annotation parents" }
            if (colors[position] == 2) return
            colors[position] = 1
            arena.annotations[position].parents.forEach { pendingParents(annotation(it)) }
            colors[position] = 2
        }
        arena.annotations.forEachIndexed { index, record ->
            if (record.phase == AnnotationPhase.PENDING) pendingParents(index)
        }
    }
}
