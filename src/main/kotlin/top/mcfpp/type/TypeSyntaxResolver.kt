package top.mcfpp.type

import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.antlr.MCFPPFieldVisitor
import top.mcfpp.antlr.MCFPPReadonlyExprVisitor
import top.mcfpp.antlr.mcfppParser as Parser
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.UnionDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.scope.IScopeWithType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper.splitNamespaceID
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

/** The walker owns syntax; the context owns lookup and declaration preparation policy. */
internal abstract class TypeSyntaxContext {
    open fun known(body: Parser.TypeBodyContext): MCFPPType? = null
    abstract fun named(namespace: String?, identifier: String, site: Parser.TypeBodyContext): MCFPPType?

    open fun specialize(head: GenericDataTemplate, arguments: List<Parser.ExpressionContext>,
                        site: Parser.TypeBodyContext): MCFPPType? =
        throw TypeSyntaxFailure(site, "Generic type application requires prepared declaration facts")

    open fun finishNamed(type: MCFPPType, site: Parser.TypeBodyContext): MCFPPType? {
        // Reading a generic wrapper's template property can resolve and compile it.
        if (type is MCFPPDataTemplateType || site.readOnlyArgs() != null)
            throw TypeSyntaxFailure(site, "Template types require prepared declaration facts")
        return type
    }

    open fun intersect(types: List<MCFPPType>, site: Parser.UnionTemplateTypeContext): MCFPPType? =
        throw TypeSyntaxFailure(site, "Template intersections require prepared declaration facts")

    open fun anonymous(site: Parser.AnonymousTemplateTypeContext): MCFPPType? =
        throw TypeSyntaxFailure(site, "Anonymous template types require declaration preparation")

    open fun invalid(site: ParserRuleContext, message: String): MCFPPType? =
        throw TypeSyntaxFailure(site, message)
}

internal class TypeSyntaxFailure(site: ParserRuleContext, message: String) : RuntimeException(message) {
    val source: SourceSpan = site.let {
        val first = it.start
        val last = it.stop ?: first
        val start = first.startIndex.coerceAtLeast(0)
        val end = (last.stopIndex + 1).coerceAtLeast(start)
        val text = last.text.orEmpty()
        val lines = text.split('\n')
        SourceSpan(first.tokenSource?.sourceName?.takeIf(String::isNotBlank) ?: "<type>", start, end,
            first.line.coerceAtLeast(1), first.charPositionInLine + 1,
            last.line.coerceAtLeast(first.line) + lines.size - 1,
            if (lines.size == 1) last.charPositionInLine + text.length + 1 else lines.last().length + 1)
    }
}

internal object TypeSyntaxResolver {
    fun resolve(type: Parser.TypeContext, context: TypeSyntaxContext): MCFPPType? = resolve(type.typeBody(), context)

    private fun resolve(body: Parser.TypeBodyContext, context: TypeSyntaxContext): MCFPPType? {
        if (body.ENTITY() != null)
            return context.invalid(body, "Legacy entity subtype '${body.text}' is no longer supported; use entity and express count/kind constraints on the target value (selector filters or entityByName).")
        context.known(body)?.let { return it }
        body.normalType()?.let { normal ->
            val builtin = when {
                normal.OBJECT() != null -> MCFPPBaseType.Object
                normal.INT() != null -> MCFPPBaseType.Int
                normal.ENTITY() != null -> MCFPPEntityType.NormalSelector
                normal.BOOL() != null -> MCFPPBaseType.Bool
                normal.BYTE() != null -> MCFPPNBTType.Byte
                normal.SHORT() != null -> MCFPPNBTType.Short
                normal.LONG() != null -> MCFPPNBTType.Long
                normal.FLOAT() != null -> MCFPPBaseType.Float
                normal.DOUBLE() != null -> MCFPPNBTType.Double
                normal.STRING() != null -> MCFPPBaseType.String
                normal.JTEXT() != null -> MCFPPBaseType.JsonText
                normal.NBT() != null -> MCFPPNBTType.NBT
                normal.TYPE() != null -> MCFPPConcreteType.Type
                normal.ANY() != null -> MCFPPBaseType.Any
                normal.BYTEARRAY() != null -> MCFPPNBTType.ByteArray
                normal.INTARRAY() != null -> MCFPPNBTType.IntArray
                normal.LONGARRAY() != null -> MCFPPNBTType.LongArray
                else -> null
            }
            if (builtin != null) return builtin
        }
        body.functionType()?.let { syntax ->
            val parameters = syntax.type().map { parameter ->
                resolve(parameter, context)
                    ?: context.invalid(parameter, TextTranslator.INVALID_TYPE_ERROR.translate(parameter.text))
                    ?: MCFPPBaseType.Any
            }
            val declaredResult = syntax.functionReturnType()
            val result = when {
                declaredResult == null -> MCFPPPrivateType.Void
                declaredResult.VOID() != null -> MCFPPPrivateType.Void
                else -> {
                    val resultSyntax = declaredResult.type()
                    resolve(resultSyntax, context)
                        ?: context.invalid(resultSyntax, TextTranslator.INVALID_TYPE_ERROR.translate(resultSyntax.text))
                        ?: MCFPPBaseType.Any
                }
            }
            return MCFPPFunctionType(parameters, result)
        }
        body.VecType()?.let { return MCFPPVectorType(it.text.substring(3).toInt()) }
        if (body.LIST() != null || body.IMMUTABLE_LIST() != null || body.DICT() != null || body.MAP() != null) {
            val element = body.type()?.let {
                resolve(it, context) ?: context.invalid(it, TextTranslator.INVALID_TYPE_ERROR.translate(it.text))
                    ?: MCFPPBaseType.Any
            } ?: MCFPPPrivateType.Wildcard
            return when {
                body.LIST() != null -> MCFPPListType(element)
                body.IMMUTABLE_LIST() != null -> MCFPPImmutableListType(element)
                body.DICT() != null -> MCFPPDictType(element)
                else -> MCFPPMapType(element)
            }
        }
        body.unionTemplateType()?.let { union ->
            val alternatives = arrayListOf<MCFPPType>()
            for (syntax in union.type()) {
                val type = resolve(syntax, context)
                    ?: context.invalid(syntax, TextTranslator.INVALID_TYPE_ERROR.translate(syntax.text))
                    ?: MCFPPBaseType.Any
                alternatives.add(type)
            }
            return context.intersect(alternatives, union)
        }
        body.unionType()?.let { union ->
            val alternatives = arrayListOf<MCFPPType>()
            for (syntax in union.type()) {
                val type = resolve(syntax, context)
                    ?: return context.invalid(syntax, TextTranslator.INVALID_TYPE_ERROR.translate(syntax.text))
                alternatives.add(type)
            }
            return MCFPPUnionType(*alternatives.toTypedArray())
        }
        body.anonymousTemplateType()?.let { return context.anonymous(it) }
        val name = body.className()?.text ?: body.Identifier()?.text ?: body.text
        val (namespace, identifier) = name.splitNamespaceID()
        if (namespace == null && identifier == "NBTRef") {
            val arguments = body.readOnlyArgs()?.expressionList()?.expression().orEmpty()
            if (arguments.size != 1) return context.invalid(body, "NBTRef requires exactly one stored type argument")
            val expression = arguments.single()
            fun originalType(node: org.antlr.v4.runtime.tree.ParseTree): Parser.TypeContext? {
                if (node is Parser.TypeContext && node.text == expression.text) return node
                for (index in 0 until node.childCount) originalType(node.getChild(index))?.let { return it }
                return null
            }
            // Primitive/nested type values already have original type syntax. A named type
            // may parse as an identifier expression, so only that ambiguity needs reparsing.
            val argument = originalType(expression) ?: run {
                val tokens = org.antlr.v4.runtime.CommonTokenStream(top.mcfpp.antlr.mcfppLexer(
                    org.antlr.v4.runtime.CharStreams.fromString(expression.text)))
                val parser = Parser(tokens).apply { removeErrorListeners() }
                val parsed = parser.type()
                if (parser.numberOfSyntaxErrors != 0 || tokens.LA(1) != org.antlr.v4.runtime.Token.EOF)
                    return context.invalid(body, "NBTRef requires a stored type argument")
                parsed
            }
            val stored = resolve(argument, context) ?: return context.invalid(body, "Unknown NBTRef stored type")
            return try { MCFPPNBTRefType(stored) } catch (failure: IllegalArgumentException) {
                context.invalid(body, failure.message ?: "NBTRef requires a runtime storage type")
            }
        }
        val type = context.named(namespace, identifier, body) ?: return if (namespace == null && identifier == "Player")
            context.invalid(body, "Legacy entity subtype 'Player' is no longer supported; use entity and express player constraints on the target value (@a, @p or entityByName).")
        else null
        return context.finishNamed(type, body)
    }
}

/** Actual legacy adapter. Preparation contexts never inherit its execution policy implicitly. */
internal open class LegacyTypeSyntaxContext(
    private val scope: IScopeWithType,
    private val caller: Function? = null
) : TypeSyntaxContext() {
    override fun known(body: Parser.TypeBodyContext): MCFPPType? = MCFPPType.cachedSyntaxType(body.text)

    override fun named(namespace: String?, identifier: String, site: Parser.TypeBodyContext): MCFPPType? {
        if (scope.containType(site.text)) return scope.getType(site.text)
        val template = GlobalScope.getTemplate(namespace, identifier)
            ?: (GlobalScope.getObject(namespace, identifier) as? DataTemplate)
        return template?.getType() ?: GlobalScope.getEnum(namespace, identifier)?.getType()
    }

    override fun specialize(head: GenericDataTemplate, arguments: List<Parser.ExpressionContext>,
                            site: Parser.TypeBodyContext): MCFPPType? {
        val visitor = MCFPPReadonlyExprVisitor(lookupTypeScope = scope, lexicalCaller = caller)
        val values = arrayListOf<Var<*>>()
        for (syntax in arguments) {
            val value = visitor.visit(syntax) ?: return null
            if (value.isError || value is UnknownVar) return null
            values.add(value)
        }
        return head.compile(values)?.getType()
    }

    override fun finishNamed(type: MCFPPType, site: Parser.TypeBodyContext): MCFPPType? {
        val template = (type as? MCFPPDataTemplateType)?.template
        if (template is GenericDataTemplate)
            return specialize(template, site.readOnlyArgs()?.expressionList()?.expression().orEmpty(), site)
        if (template != null && site.readOnlyArgs() != null)
            return invalid(site, "Ordinary template '${template.identifier}' does not accept readonly arguments")
        return type
    }

    override fun intersect(types: List<MCFPPType>, site: Parser.UnionTemplateTypeContext): MCFPPType {
        val templates = arrayListOf<MCFPPDataTemplateType>()
        types.forEachIndexed { index, type ->
            if (type is MCFPPDataTemplateType) templates.add(type)
            else invalid(site.type(index), "Union type can only be used with data template type")
        }
        templates.forEach { it.tryResolve() }
        return UnionDataTemplate.resolve(templates.map { it.template }).getType()
    }

    override fun anonymous(site: Parser.AnonymousTemplateTypeContext): MCFPPType =
        (MCFPPFieldVisitor().visitAnonymousTemplateType(site) as DataTemplate).getType()

    override fun invalid(site: ParserRuleContext, message: String): MCFPPType? {
        LogProcessor.error(message)
        return null
    }
}
