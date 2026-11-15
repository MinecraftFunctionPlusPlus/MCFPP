package top.mcfpp.analysis

import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.Var
import top.mcfpp.mni.annotation.NoInstance
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectCompoundData
import top.mcfpp.model.compound.TypeDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.*

internal object TemplateLiteralSupport {
    fun isTemplateLiteralType(type: MCFPPType): Boolean {
        if (type !is MCFPPDataTemplateType) return false
        if (type is MCFPPObjectType) return false
        if (type is MCFPPTypeDataTemplateType || type is MCFPPObjectDataTemplateType) return false
        type.tryResolve()
        val template = type.template
        return template !is TypeDataTemplate && template !is ObjectCompoundData
    }

    fun containsTemplateLiteralTarget(type: MCFPPType, seen: MutableSet<TypeId> = hashSetOf()): Boolean {
        if (!seen.add(type.typeId)) return false
        return when {
            isTemplateLiteralType(type) -> true
            type is MCFPPUnionType -> type.types.any { containsTemplateLiteralTarget(it, seen) }
            type is MCFPPListType || type is MCFPPImmutableListType || type is MCFPPDictType ->
                containsTemplateLiteralTarget((type as MCFPPTypeWithGeneric).generic.single(), seen)
            else -> false
        }
    }

    fun unwrapNbtLiteral(node: ParserRuleContext?): mcfppParser.NbtValueContext? {
        var current = node
        while (current != null) {
            if (current is mcfppParser.NbtValueContext) return current
            current = current.children.orEmpty().filterIsInstance<ParserRuleContext>().singleOrNull()
        }
        return null
    }

    fun unwrapInlineLambda(node: ParserRuleContext?): mcfppParser.InlineLambdaContext? {
        var current = node
        while (current != null) {
            if (current is mcfppParser.InlineLambdaContext) return current
            if (current is mcfppParser.PrimaryContext) return current.inlineLambda()
            if (current is mcfppParser.UnaryExpressionContext && current.rightVarExpression() == null ||
                current is mcfppParser.CastExpressionContext && current.type() != null) return null
            current = current.children.orEmpty().filterIsInstance<ParserRuleContext>().singleOrNull()
        }
        return null
    }

    /** Empty and single-name braces share syntax with a lambda; its target decides the value kind. */
    fun ambiguousObjectEntries(syntax: mcfppParser.InlineLambdaContext): List<Pair<String, mcfppParser.ExpressionContext>>? {
        if (syntax.lambdaParameterList() != null) return null
        val body = syntax.lambdaBody() ?: return null
        if (body.statement().isNotEmpty()) return null
        val expression = body.lambdaTailExpression()?.expression() ?: return emptyList()
        if (expression.start.type != top.mcfpp.antlr.mcfppLexer.Identifier ||
            expression.start.tokenIndex != expression.stop.tokenIndex) return null
        return listOf(expression.start.text to expression)
    }

    fun unwrapAmbiguousObject(node: ParserRuleContext?): mcfppParser.InlineLambdaContext? {
        var current = node
        while (current != null) {
            if (current is mcfppParser.InlineLambdaContext)
                return current.takeIf { ambiguousObjectEntries(it) != null }
            current = current.children.orEmpty().filterIsInstance<ParserRuleContext>().singleOrNull()
        }
        return null
    }

    /** A projected implicit lambda slot is inactive when its braces are selected as an object. */
    fun hasInactiveObjectParameter(syntax: mcfppParser.InlineLambdaContext, name: String): Boolean {
        if (name != "it") return false
        val lambda = top.mcfpp.model.function.StructuredBodyContexts.origin(syntax)?.node
            as? top.mcfpp.library.syntax.Expression.Lambda ?: return false
        val use = (lambda.tail as? top.mcfpp.library.syntax.Expression.Name)?.use
            as? top.mcfpp.library.syntax.NameUse.Local ?: return false
        return !lambda.explicitParameters && use.target in lambda.parameters
    }

    fun isCompoundLiteral(node: ParserRuleContext?): Boolean =
        unwrapNbtLiteral(node)?.nbtCompound() != null || unwrapAmbiguousObject(node) != null

    fun isListLiteral(node: ParserRuleContext?): Boolean = unwrapNbtLiteral(node)?.nbtList() != null

    fun unquoteKey(rawKey: String): String =
        if (rawKey.startsWith("\"") || rawKey.startsWith("'")) (Tag.toNBT(rawKey) as StringTag).value else rawKey

    /** Decode keys before comparing them, so `x` and `"x"` name the same field. */
    fun compoundEntries(compound: mcfppParser.NbtCompoundContext): List<Pair<String, mcfppParser.NbtKeyValuePairContext>> {
        val names = hashSetOf<String>()
        return compound.nbtKeyValuePair().map { entry ->
            val name = unquoteKey(entry.key.text)
            if (!names.add(name)) throw DeclarationBinding.Failure("Duplicate field '$name' in object literal")
            name to entry
        }
    }

    fun canInstantiateTemplateLiteral(template: DataTemplate, caller: Function?): String? {
        template.scope.ensureMembers()
        if (template is TypeDataTemplate || template is ObjectCompoundData) {
            return "Cannot instantiate '${template.namespaceID}' with a template literal"
        }
        if (template.annotations.any { it is NoInstance }) {
            return "Template '${template.namespaceID}' is not allowed to be instantiated"
        }
        if (!template.canInstantiate()) {
            return "Template '${template.namespaceID}' cannot be instantiated"
        }
        if (caller != null && template.constructors.isNotEmpty() &&
            template.constructors.none { caller.accessTo(template) >= it.accessModifier }) {
            return "Cannot construct '${template.namespaceID}': no accessible constructor"
        }
        return null
    }

    fun resolveFieldInitializer(template: DataTemplate, field: Var<*>): Pair<DataTemplate, mcfppParser.ExpressionContext>? {
        template.preInit[field.identifier]?.let { return template to it }
        field.declaredParentTemplate?.let { owner ->
            owner.preInit[field.identifier]?.let { return owner to it }
        }
        (template.scope.fieldOrigins[field.identifier]?.singleOrNull()?.owner as? DataTemplate)?.let { owner ->
            owner.preInit[field.identifier]?.let { return owner to it }
        }
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<DataTemplate, Boolean>())
        val pending = ArrayDeque<DataTemplate>()
        pending.addLast(template)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (!visited.add(current)) continue
            if (current !== template) {
                current.preInit[field.identifier]?.let { return current to it }
            }
            current.parent.filterIsInstance<DataTemplate>()
                .filter { it !== DataTemplate.baseDataTemplate }
                .forEach(pending::addLast)
        }
        return null
    }

    fun inheritanceOrderedFields(template: DataTemplate, fields: List<Var<*>>): List<Var<*>> {
        val order = linkedMapOf<DataTemplate, Int>()
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<DataTemplate, Boolean>())
        fun visit(current: DataTemplate) {
            if (!visited.add(current)) return
            current.parent.filterIsInstance<DataTemplate>()
                .filter { it !== DataTemplate.baseDataTemplate }
                .forEach(::visit)
            order[current] = order.size
        }
        visit(template)
        return fields.withIndex()
            .sortedWith(compareBy({ order[it.value.declaredParentTemplate ?: template] ?: Int.MAX_VALUE }, { it.index }))
            .map { it.value }
    }

    fun acceptsCompound(type: MCFPPType): Boolean = when {
        TypeCompositions.objectShape(type) != null -> true
        isTemplateLiteralType(type) -> true
        type is MCFPPDictType -> true
        type == MCFPPNBTType.NBT -> true
        type == MCFPPBaseType.Object || type == MCFPPBaseType.Any -> true
        type is MCFPPUnionType -> type.types.any(::acceptsCompound)
        else -> false
    }

    fun acceptsList(type: MCFPPType): Boolean = when {
        type is MCFPPListType || type is MCFPPImmutableListType -> true
        type == MCFPPNBTType.NBT -> true
        type == MCFPPBaseType.Object || type == MCFPPBaseType.Any -> true
        type is MCFPPUnionType -> type.types.any(::acceptsList)
        else -> false
    }

    fun selectExpectedLiteralType(
        node: ParserRuleContext?,
        targets: List<MCFPPType>,
        probeCompound: (MCFPPType) -> Boolean
    ): MCFPPType? {
        val distinct = targets.distinctBy { it.typeId }
        if (distinct.isEmpty()) return null
        if (distinct.size == 1) return distinct.single()
        if (isCompoundLiteral(node)) {
            val compatible = distinct.filter(::acceptsCompound)
            if (compatible.size == 1) return compatible.single()
            val matching = compatible.filter(probeCompound)
            if (matching.size == 1) return matching.single()
            val mostSpecific = matching.filter { candidate -> matching.all { other ->
                TypeRelations.resolveImplicitConversion(candidate, other) != null
            } }
            if (mostSpecific.size == 1) return mostSpecific.single()
        } else if (isListLiteral(node)) {
            val compatible = distinct.filter(::acceptsList)
            if (compatible.size == 1) return compatible.single()
        }
        return null
    }

    fun resolveCompoundUnionTarget(
        union: MCFPPUnionType,
        probeTemplate: (MCFPPDataTemplateType) -> Boolean
    ): MCFPPType? {
        val templates = union.types.filter(::isTemplateLiteralType).map { it as MCFPPDataTemplateType }
        val hasDict = union.types.filterIsInstance<MCFPPDictType>()
        val hasNbt = union.types.any { it == MCFPPNBTType.NBT }
        if (templates.size == 1 && hasDict.isEmpty() && !hasNbt) return templates.single()
        if (templates.isNotEmpty()) {
            val matching = templates.filter(probeTemplate)
            if (matching.size == 1) return matching.single()
            if (matching.isEmpty()) {
                if (hasDict.size == 1) return hasDict.single()
                if (hasNbt) return MCFPPNBTType.NBT
                if (templates.size == 1) return templates.single()
            }
            return null
        }
        if (hasDict.size == 1 && !hasNbt) return hasDict.single()
        if (hasDict.isEmpty() && hasNbt) return MCFPPNBTType.NBT
        return null
    }

    fun resolveListUnionTarget(union: MCFPPUnionType): MCFPPType? {
        val lists = union.types.filter { it is MCFPPListType || it is MCFPPImmutableListType }
        if (lists.size == 1 && union.types.none { it == MCFPPNBTType.NBT }) return lists.single()
        return null
    }
}
