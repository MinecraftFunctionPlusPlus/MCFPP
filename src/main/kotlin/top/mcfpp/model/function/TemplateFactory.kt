package top.mcfpp.model.function

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.misc.Interval
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.TypeKnowledge
import top.mcfpp.analysis.ValueKnowledge
import top.mcfpp.analysis.ValueState
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectCompoundData
import top.mcfpp.model.compound.TypeDataTemplate
import top.mcfpp.type.MCFPPNeverType
import top.mcfpp.type.TypeCompositions

/** A free function associated with a template. Every receiver is an ordinary explicit parameter. */
class TemplateFactory(
    val template: DataTemplate,
    context: mcfppParser.CurlBlockContext?,
    val isImplicit: Boolean = false
) : Function("new", template, context) {
    internal val isTrivialEmpty: Boolean
        get() = isImplicit && TypeCompositions.objectShape(template.getType())?.fields?.isEmpty() == true &&
            template.preInit.none { (name, _) -> template.scope.getVar(name)?.declaredParentTemplate === template }

    init {
        receiverless = true
        returnType = template.getType()
        scope.putType("Self", returnType, true)
    }

    fun addParamsFromContext(context: mcfppParser.NormalParamsContext) {
        for (parameter in context.parameterList()?.parameter().orEmpty()) {
            val (declaration, value) = parseParam(parameter)
            normalParams.add(declaration)
            scope.putVar(declaration.identifier, value)
        }
    }

    override fun invoke(normalArgs: LinkedHashMap<String, Var<*>>, caller: CanSelectMember?): Var<*> =
        super.invoke(normalArgs, null)

    protected override fun invoke(normalArgs: List<Var<*>>): Var<*> {
        val result = super.invoke(normalArgs)
        if (result.isError || !isTrivialEmpty) return result
        val binding = result.storageBinding ?: return result
        val fact = binding.data.facts.read(binding.place) ?: return result
        if (fact.state != ValueState.INITIALIZED) return result
        val type = template.getType()
        binding.data.types[type.typeId] = type
        binding.data.facts.refine(binding.place, fact.copy(
            type = TypeKnowledge.Exact(type.typeId),
            value = ValueKnowledge.Constant(CompilerValue.Typed(type.typeId, CompilerValue.Record(emptyMap())))
        ))
        return result
    }
}

/** Implicit factories use only their declaring template's initializers, never a parent factory. */
object TemplateFactories {
    private val synthesizing = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<DataTemplate, Boolean>())

    fun ensureImplicit(template: DataTemplate) {
        // A pure preparation reservation has no declaration members yet. Its empty scope
        // cannot establish that the eventual declaration has an empty default factory.
        if (template is CompiledGenericDataTemplate && template.identifier == "<prepared>") return
        if (template is ObjectCompoundData || template is TypeDataTemplate || template.isAbstract ||
            template.isEntityTemplate || !synthesizing.add(template)) return
        try {
            template.scope.ensureMembers()
            if (template.deferredFields.isNotEmpty() || template.hasPendingParentTypes || template.hasDeclarationCompletionFailure) return
            val registered = template.scope.functions["new"].orEmpty()
            if (registered.any { it is TemplateFactory && it.normalParams.isEmpty() }) return
            val shape = TypeCompositions.objectShape(template.getType()) ?: return
            val localInitializers = template.preInit.filterKeys { name ->
                template.scope.getVar(name)?.declaredParentTemplate === template
            }
            // Optional-never fields can be omitted but can never be initialized.
            if (shape.fields.any { field ->
                    (!field.optional && field.name !in localInitializers) ||
                        field.name in localInitializers && field.type == MCFPPNeverType
                }) return

            val source = buildString {
                append("{\n")
                for ((name, initializer) in localInitializers) {
                    append(if (template.scope.getVar(name)?.isConst == true) "const " else "var ")
                    append(name)
                    // Retain the author's type syntax so empty/nested collection literals get
                    // the field's context, including the actual owner's readonly type bindings.
                    // Inferred fields stay inferred; their types may have no source spelling.
                    val declaration = initializer.parent as? mcfppParser.TemplateFieldDeclarationContext
                    val declaredType = declaration?.templateType()?.let { type ->
                        type.singleTemplateFieldType()?.type()?.text
                            ?: type.unionTemplateFieldType()?.type()?.joinToString(" | ", "(", ")") { it.text }
                    }
                    if (declaredType != null) append(": ").append(declaredType)
                    append(" = ")
                    val start = initializer.start
                    val stop = initializer.stop
                    append(start.inputStream.getText(Interval(start.startIndex, stop.stopIndex)))
                    append('\n')
                }
                append("return {")
                append(localInitializers.keys.joinToString(", ") { "$it: $it" })
                append("}\n}")
            }
            val parser = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(source))))
            val body = parser.curlBlock()
            check(parser.numberOfSyntaxErrors == 0) { "Invalid generated factory for ${template.namespaceID}" }
            val factory = TemplateFactory(template, body, isImplicit = true)
            factory.declarationFile = template.declarationFile
            factory.declarationEnvironment = template.declarationEnvironment
            template.addMember(factory)
        } finally {
            synthesizing.remove(template)
        }
    }
}
