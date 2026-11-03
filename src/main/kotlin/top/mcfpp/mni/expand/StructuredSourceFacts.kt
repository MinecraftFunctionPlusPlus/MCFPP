package top.mcfpp.mni.expand

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.CommonToken
import top.mcfpp.analysis.StructuredBindings
import top.mcfpp.analysis.preparationSpan
import top.mcfpp.analysis.StructuredNameBinding
import top.mcfpp.analysis.StructuredSessionBindings
import top.mcfpp.mni.api.TypeRef
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.StructuredBodyContexts

/** Converts author-session facts to this immutable MNI catalog without resolving display spellings. */
internal object StructuredSourceFacts {
    /** Only registered, open projections admit their compiler-owned UTF-16 display coordinates. */
    fun span(site: ParserRuleContext, file: String? = null): SourceSpan {
        val projected = StructuredBodyContexts.projected(site)
        if (projected == null) {
            val source = preparationSpan(site)
            return if (file == null || file == source.file) source else SourceSpan(file, source.startOffset, source.endOffset,
                source.startLine, source.startColumn, source.endLine, source.endColumn)
        }
        val source = projected.syntax.source
        val first = requireNotNull(site.start) { "Projected syntax has no start boundary" }
        val last = site.stop ?: first
        require(first.javaClass === CommonToken::class.java && last.javaClass === CommonToken::class.java) {
            "Projected syntax requires compiler-owned boundary tokens"
        }
        require(first.tokenSource === source && first.inputStream === source && last.tokenSource === source && last.inputStream === source) {
            "Projected syntax boundaries belong to another source"
        }
        val start = first.startIndex
        val end = last.stopIndex + 1
        val text = source.text
        require(start >= 0 && end >= start && end <= text.length) { "Projected syntax range is outside its display source" }
        fun position(offset: Int): Pair<Int, Int> {
            val prefix = text.substring(0, offset)
            return (prefix.count { it == '\n' } + 1) to (offset - prefix.lastIndexOf('\n'))
        }
        val begin = position(start)
        val finish = position(end)
        return SourceSpan(file ?: source.sourceName, start, end, begin.first, begin.second, finish.first, finish.second)
    }

    fun literal(site: ParserRuleContext, environment: BindingEnvironment): BoundExpr? {
        StructuredBindings.constant(site)?.let { return SourceDefaultBinder().typedLiteral(it, environment.types, span(site, environment.sourceFile)) }
        if (StructuredBindings.isBound(site)) {
            val binding = StructuredBodyContexts.origin(site)?.boundName?.binding
            val type = when (binding) {
                is top.mcfpp.analysis.StructuredNameBinding.Type -> binding.type
                is top.mcfpp.type.MCFPPType -> binding
                else -> null
            }
            if (type != null) {
                val value = top.mcfpp.mni.api.ConstantValue.TypeValue(environment.types.ref(type))
                return BoundExpr(BoundExprNode.Literal(value), environment.types.ref(top.mcfpp.type.MCFPPConcreteType.Type),
                    value, BoundEffect.PURE, span(site, environment.sourceFile))
            }
        }
        return null
    }

    fun type(site: ParserRuleContext, environment: BindingEnvironment): TypeRef<*>? =
        StructuredBindings.type(site)?.let { environment.types.ref(it) }

    fun functions(site: ParserRuleContext, environment: BindingEnvironment): List<SourceFunction>? {
        val origin = StructuredBodyContexts.origin(site) ?: return null
        val use = origin.nameUse ?: return null
        val frozen = StructuredBindings.name(site, null) as? StructuredNameBinding.Macros
        if (frozen != null) {
            val prepared = environment.functions.associateBy {
                (it.function as? top.mcfpp.mni.api.ImmutableFunctionRef<*>)?.key
            }
            return frozen.candidates.bind(environment.types).map { it.reference.key }.distinct().map { key ->
                prepared[key] ?: throw SourceBindingFailure(span(site, environment.sourceFile),
                    "LIB_BODY_UNSUPPORTED: Author macro declaration is absent from the prepared snapshot")
            }
        }
        val provider = StructuredBodyContexts.bindings(site) as? StructuredSessionBindings
        fun functions(binding: Any?): List<Function>? = when (binding) {
            is StructuredNameBinding.Functions -> binding.candidates
            is Function -> listOf(binding)
            is List<*> -> binding.filterIsInstance<Function>().takeIf { it.size == binding.size }
            else -> null
        }
        val functions = when (use) {
            is top.mcfpp.library.syntax.NameUse.Lookup -> if (provider != null)
                use.candidates.flatMap { functions(provider.declaration(it)).orEmpty() }
                else functions(origin.boundName?.binding).orEmpty()
            is top.mcfpp.library.syntax.NameUse.Declaration -> functions(origin.boundName?.binding)
                ?: provider?.let { functions(it.declaration(use.target)) }.orEmpty()
            is top.mcfpp.library.syntax.NameUse.Local -> emptyList()
        }
        val ids = functions.map { it.declarationId.value }.toSet()
        val found = environment.functions.filter { it.compilerDeclarationId in ids }
        if (functions.isNotEmpty() && found.isEmpty())
            throw SourceBindingFailure(span(site, environment.sourceFile), "LIB_BODY_UNSUPPORTED: Author callback declarations are absent from the prepared snapshot")
        return found
    }

    fun symbol(site: ParserRuleContext, environment: BindingEnvironment): SourceSymbol? {
        val use = StructuredBodyContexts.origin(site)?.nameUse ?: return null
        return environment.structuredSymbols[use]
            ?: throw SourceBindingFailure(span(site, environment.sourceFile), "LIB_BODY_UNSUPPORTED: Bound callback name has no author symbol snapshot")
    }
}
