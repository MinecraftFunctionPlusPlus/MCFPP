package example.mniscore

import top.mcfpp.mni.api.kotlin
import top.mcfpp.Project
import top.mcfpp.core.lang.Var
import top.mcfpp.mni.api.BoolT
import top.mcfpp.mni.api.ConstantValue
import top.mcfpp.mni.api.DeclarationContext
import top.mcfpp.mni.api.DeclarationEdit
import top.mcfpp.mni.api.DeclarationEdits
import top.mcfpp.mni.api.FieldRef
import top.mcfpp.mni.api.FunctionSignature
import top.mcfpp.mni.api.IntT
import top.mcfpp.mni.api.ScoreHolder
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.mni.api.TemplateProcessor
import top.mcfpp.mni.api.TemplateView
import top.mcfpp.mni.api.TypeRef
import top.mcfpp.model.annotation.FieldAnnotation
import top.mcfpp.model.scope.GlobalScope

/** Pure field annotation; the processor reads the objective from the canonical AnnotationView. */
class KotlinScoreAnnotation(private val objective: String) : FieldAnnotation("Score", "example.score") {
    override fun forField(field: Var<*>) {
        // The declaration processor owns this mapping; keep the legacy action empty.
    }
}

/**
 * Annotation-derived score load/save provider.
 *
 * All fields and duplicate targets are checked before a declaration edit is constructed. The
 * generated methods use fixed score holders, so they do not depend on the world/context backend.
 */
class KotlinScoreMappingProcessor : TemplateProcessor {
    private class Mapping(val field: FieldRef<*>, val integer: Boolean, val objective: String)

    override fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits {
        val configuration = template.annotations().firstOrNull { it.name().endsWith(":ScoreMapping") }
            ?: fail(context, template.source(), "ScoreMapping requires a @ScoreMapping annotation")
        val holder = (configuration.arguments()["0"] as? ConstantValue.Text)?.value
            ?: fail(context, configuration.source(),
                "ScoreMapping argument 0 must be the fixed score holder string")
        try {
            ScoreHolder.named(holder)
        } catch (failure: IllegalArgumentException) {
            fail(context, configuration.source(), "Invalid score holder: ${failure.message}")
        }

        val intType = context.types().intType()
        val boolType = context.types().boolType()
        val mappings = arrayListOf<Mapping>()
        val targets = hashSetOf<String>()
        for (field in template.fieldDeclarations()) {
            val annotations = field.annotations().filter { it.name().endsWith(":Score") }
            if (annotations.isEmpty()) continue
            if (annotations.size != 1)
                fail(context, field.source(), "Field '${field.field().name()}' has more than one @Score mapping")
            val annotation = annotations.single()
            val objective = (annotation.arguments()["0"] as? ConstantValue.Text)?.value
                ?: fail(context, annotation.source(), "Field '${field.field().name()}' requires a string objective")
            if (!objective.matches(Regex("[A-Za-z0-9_.+-]{1,16}")))
                fail(context, annotation.source(), "Invalid score objective '$objective'")
            if (!field.field().writable())
                fail(context, field.source(), "Field '${field.field().name()}' is read-only; score load requires a writable field")
            val type = field.field().type()
            val integer = type.sameType(intType)
            val bool = type.sameType(boolType)
            if (!integer && !bool)
                fail(context, field.source(), "Field '${field.field().name()}' has unsupported type '${type.name()}'; " +
                    "score mapping supports only int and bool")
            if (!targets.add("$holder $objective"))
                fail(context, annotation.source(), "Duplicate score target mapping '$holder $objective'")
            mappings += Mapping(field.field(), integer, objective)
        }
        if (mappings.isEmpty()) fail(context, template.source(), "ScoreMapping found no @Score fields to map")

        @Suppress("UNCHECKED_CAST")
        val owner = template.type() as TypeRef<Any>
        val noArgsVoid = FunctionSignature.of(emptyList(), context.types().voidType())
        val declarations = context.declarations()
        val load = declarations.method(owner, "load", noArgsVoid) { body ->
            body.builder().kotlin {
            for (mapping in mappings) {
                val score = score(ScoreHolder.named(holder), mapping.objective)
                if (mapping.integer) {
                    @Suppress("UNCHECKED_CAST")
                    val field = mapping.field as FieldRef<IntT>
                    fieldRef(body.self(), field).value = score.value
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val field = mapping.field as FieldRef<BoolT>
                    fieldRef(body.self(), field).value = score.value ne literal(0)
                }
            }
            }
        }
        val save = declarations.method(owner, "save", noArgsVoid) { body ->
            body.builder().kotlin {
            for (mapping in mappings) {
                val score = score(ScoreHolder.named(holder), mapping.objective)
                if (mapping.integer) {
                    @Suppress("UNCHECKED_CAST")
                    val field = mapping.field as FieldRef<IntT>
                    score.value = fieldRef(body.self(), field).value
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val field = mapping.field as FieldRef<BoolT>
                    val value = fieldRef(body.self(), field)
                    ifElse(value.value,
                        { score.value = literal(1) },
                        { score.value = literal(0) })
                }
            }
            }
        }
        val marker = declarations.metadata(owner, GENERATED_MEMBERS, emptyMap())
        return declarations.edits(listOf<DeclarationEdit>(load, save, marker))
    }

    private fun fail(context: DeclarationContext, site: SourceSpan, message: String): Nothing {
        context.diagnostics().error(site, message)
        throw IllegalStateException("Declaration diagnostics did not abort score mapping")
    }

    companion object {
        private const val GENERATED_MEMBERS = "mni:GeneratedMembers"
    }
}

/** `injectedBy` runs the existing static import hook with the active namespace installed. */
class KotlinScoreMappingProvider private constructor() {
    companion object {
        @JvmStatic
        fun importToMNI(): Array<String> {
            val namespace = GlobalScope.getNamespace(Project.currNamespace) ?: return emptyArray()
            namespace.registerTemplateProcessor(
                "${namespace.identifier}:ScoreMapping", KotlinScoreMappingProcessor::class.java)
            namespace.scope.addAnnotation("Score", KotlinScoreAnnotation::class.java)
            return emptyArray()
        }
    }
}
