package top.mcfpp.model.function

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.library.compiler.*
import top.mcfpp.library.model.AnnotationPhase
import top.mcfpp.library.model.AnnotationProviderBinding
import top.mcfpp.library.syntax.*
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.type.MCFPPType
import java.util.IdentityHashMap

data class TemplateAnnotationClone(val annotations: List<Annotation>, val pending: List<Annotation>)

/** One clone arena preserves H aliases and retains original session D/T objects via explicit bindings. */
class SessionAnnotationCloner(
    private val codecs: PortableAnnotationCodecs,
    private val originalPhase: (Annotation) -> AnnotationPhase
) : AutoCloseable {
    private val phases = IdentityHashMap<Annotation, AnnotationPhase>()
    private var closed = false

    fun phase(value: Annotation): AnnotationPhase = phases[value] ?: originalPhase(value)

    fun clone(roots: List<Annotation>): TemplateAnnotationClone {
        check(!closed) { "Annotation clone session is closed" }
        val providers = linkedMapOf<AnnotationProviderBinding, DeclarationReference>()
        val providerValues = linkedMapOf<DeclarationReference, AnnotationProviderBinding>()
        val constants = linkedMapOf<CompilerValue, ConstantRef>()
        val constantValues = linkedMapOf<ConstantRef, CompilerValue>()
        val types = IdentityHashMap<MCFPPType, TypeRef>()
        val typeValues = linkedMapOf<TypeRef, MCFPPType>()
        val declarations = IdentityHashMap<Any, DeclarationReference>()
        val declarationValues = linkedMapOf<DeclarationReference, Any>()
        var nextDeclaration = 0L
        val capture = object : AnnotationCaptureBindings {
            override fun provider(binding: AnnotationProviderBinding): DeclarationReference = providers.getOrPut(binding) {
                DeclarationReference.Local(DeclarationId(++nextDeclaration)).also { providerValues[it] = binding }
            }
            override fun constant(value: CompilerValue): ConstantRef = constants.getOrPut(value) {
                ConstantRef(constants.size + 1L).also { constantValues[it] = value }
            }
            override fun type(value: MCFPPType): TypeRef = types.getOrPut(value) {
                TypeRef(types.size + 1L).also { typeValues[it] = value }
            }
            override fun declaration(value: Any): DeclarationReference = declarations.getOrPut(value) {
                DeclarationReference.Local(DeclarationId(++nextDeclaration)).also { declarationValues[it] = value }
            }
            override fun phase(value: Annotation) = this@SessionAnnotationCloner.phase(value)
        }
        val snapshot = codecs.capture(roots, capture)
        val restored = codecs.restore(snapshot.annotations, snapshot.stateCells, object : AnnotationRestoreBindings {
            override fun provider(reference: DeclarationReference) = providerValues.getValue(reference)
            override fun constant(reference: ConstantRef) = constantValues.getValue(reference)
            override fun type(reference: TypeRef) = typeValues.getValue(reference)
            override fun declaration(reference: DeclarationReference) = declarationValues.getValue(reference)
        })
        snapshot.annotations.indices.forEach { index ->
            val reference = AnnotationId(index + 1L)
            phases[restored.annotation(reference)] = restored.phase(reference)
        }
        val copied = snapshot.roots.map(restored::annotation)
        return TemplateAnnotationClone(copied, copied.filter { phase(it) == AnnotationPhase.PENDING })
    }

    override fun close() { if (!closed) { closed = true; phases.clear() } }
}
