package top.mcfpp.analysis

import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.antlr.mcfppParser as Parser
import top.mcfpp.core.lang.Var
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.property.*
import top.mcfpp.type.MCFPPType

/** Read declaration syntax without executing a provider or binding the actual receiver. */
internal object SourceEffects {
    private data class Receiver(val type: MCFPPType, val place: Place?)

    fun preview(function: Function, receiver: Var<*>?): Effect = Analyzer().function(function,
        receiver?.let { Receiver(it.type, StorageAccess.ensure(it).place) })

    /** A direct static setter proves initialization without inferring conditional writes. */
    fun definiteStaticInitializations(function: Function): Set<Place> {
        val statements = function.bodyContext()?.statement() ?: return emptySet()
        val parameters = function.normalParams.map { it.identifier }.toSet()
        fun directValue(node: ParseTree): Boolean {
            if (node is Parser.FunctionCallContext || node is Parser.SelectorContext ||
                node is Parser.PropertyOperatorExpressionContext || node is Parser.InlineLambdaContext)
                return false
            if (node is Parser.ValueContext && node.NULL() != null) return false
            if (node is Parser.VarWithSuffixContext &&
                (node.identifierSuffix().isNotEmpty() || node.Identifier().text !in parameters)) return false
            return (0 until node.childCount).all { directValue(node.getChild(it)) }
        }
        val places = linkedSetOf<Place>()
        for (statement in statements) {
            val assignment = statement.statementExpression() ?: return emptySet()
            if (assignment.assignmentOperator()?.text != "=" || assignment.expression()?.text == "null" ||
                assignment.expression()?.let(::directValue) != true) return emptySet()
            val target = assignment.varWithSelector() ?: return emptySet()
            val operator = target.jvmAccessExpression().propertyOperator()
            if (operator.LSQUARE() != null) return emptySet()
            val primary = operator.primary()
            val qualifier = primary.`var`()?.varWithSuffix()?.takeIf { it.identifierSuffix().isEmpty() }
            val root = qualifier?.Identifier()?.text
                ?: primary.type()?.text ?: return emptySet()
            val selectors = target.selector()
            val field = if (selectors.isEmpty()) function.scope.getVar(root) else {
                if (selectors.size != 1) return emptySet()
                val member = selectors.single().`var`()?.varWithSuffix()?.takeIf { it.identifierSuffix().isEmpty() }
                    ?: return emptySet()
                val owner = (function.owner as? top.mcfpp.model.compound.DataTemplate)?.takeIf {
                    it is top.mcfpp.model.compound.ObjectCompoundData && it.identifier == root
                } ?: return emptySet()
                if (function.scope.getVar(root) != null) continue
                if (qualifier != null) {
                    val binding = StructuredBindings.name(qualifier, function)
                    val shadowed = when (binding) {
                        null -> false
                        is StructuredNameBinding.Type -> {
                            // A library name retains its ordinary data declaration; static
                            // selection reaches the companion paired with that declaration.
                            val declaration = (binding.type as? top.mcfpp.type.MCFPPDataTemplateType)?.template
                            declaration !== owner && declaration?.companionObject !== owner
                        }
                        else -> true
                    }
                    if (shadowed) continue
                }
                owner.scope.getVar(member.Identifier().text)
            } ?: return emptySet()
            if (!field.isStatic || field.isConst) return emptySet()
            val property = field.declaredParentTemplate?.scope?.getProperty(field.identifier)
            if (property != null && property.mutator !is SimpleMutator) return emptySet()
            places += field.storageBinding?.place ?: return emptySet()
        }
        return places
    }

    private class Analyzer {
        private val active = linkedMapOf<Function, Receiver?>()

        fun function(function: Function, receiver: Receiver?): Effect {
            if (function is NativeFunction) {
                val method = function.javaMethod
                return when {
                    method.isAnnotationPresent(top.mcfpp.mni.annotation.NoExternalWrites::class.java) ||
                        method.declaringClass.isAnnotationPresent(top.mcfpp.mni.annotation.NoExternalWrites::class.java) -> Effect.ReadsRuntime
                    method.isAnnotationPresent(top.mcfpp.mni.annotation.WritesReceiver::class.java) ->
                        receiver?.place?.let { Effect.Writes(setOf(it), setOf(it)) } ?: Effect.Pure
                    else -> Effect.Unknown
                }
            }
            if (function in active) return if (active[function] == receiver) Effect.Pure else Effect.Unknown
            val body = function.bodyContext() ?: return Effect.Unknown
            active[function] = receiver
            var effect: Effect = Effect.Pure
            val locals = linkedMapOf<String, MCFPPType>()
            fun observe(next: Effect) { effect = EffectAnalysis.combine(effect, next) }
            fun call(candidates: List<Function>, target: Receiver?) {
                if (candidates.isEmpty()) observe(Effect.Unknown)
                else candidates.forEach { observe(function(it, target)) }
            }
            fun member(base: Receiver?, name: String, write: Boolean, backingOnly: Boolean): Receiver? {
                if (base == null) { observe(Effect.Unknown); return null }
                val declaration = base.type.instanceData
                val field = declaration.scope.getVar(name) ?: return null
                val property = declaration.scope.getProperty(name)
                if (backingOnly) Unit else if (write) when (val setter = property?.mutator) {
                    is FunctionMutator -> observe(function(setter.function, base))
                    is NativeMutator -> observe(function(setter.function, base))
                } else when (val getter = property?.accessor) {
                    is FunctionAccessor -> observe(function(getter.function, base))
                    is NativeAccessor -> observe(function(getter.function, base))
                }
                return Receiver(field.type, if (field.isStatic) StorageAccess.ensure(field).place else base.place?.field(name))
            }
            lateinit var visit: (ParseTree) -> Unit
            fun invocation(context: Parser.FunctionCallContext, base: Receiver?): Receiver? {
                val name = context.namespaceID().text.substringAfter(':')
                var candidates = if (base != null) base.type.instanceData.scope.getFunctionCandidates(name)
                    else top.mcfpp.model.scope.GlobalScope.getFunctionCandidates(
                        context.namespaceID().text.substringBefore(':', "").ifEmpty { null }, name, function.declarationFile?.field)
                var target = base
                if (name == "new" && base?.type is top.mcfpp.type.MCFPPDataTemplateType) {
                    candidates = (base.type as top.mcfpp.type.MCFPPDataTemplateType).template.factories
                    target = null
                }
                if (candidates.isEmpty() && base == null) {
                    val template = top.mcfpp.model.scope.GlobalScope.getTemplate(
                        context.namespaceID().text.substringBefore(':', "").ifEmpty { null }, name)
                    if (template != null) {
                        val legacy = template.isEntityTemplate || template is top.mcfpp.model.compound.ObjectCompoundData ||
                            template is top.mcfpp.model.compound.TypeDataTemplate
                        candidates = if (legacy) template.constructors else template.factories
                        target = if (legacy) Receiver(template.getType(), null) else null
                    }
                }
                context.arguments()?.let { visit(it) }
                if (candidates.all { it is top.mcfpp.model.function.DataTemplateConstructor && it.bodyContext() == null } && candidates.isNotEmpty()) Unit
                else call(candidates, target)
                return candidates.firstOrNull()?.returnType?.let { Receiver(it, null) }
            }
            fun selected(context: Parser.VarWithSelectorContext, write: Boolean, backingOnly: Boolean = false): Receiver? {
                val primary = context.jvmAccessExpression().propertyOperator().primary()
                val variable = primary.`var`()
                var base = when {
                    primary.THIS() != null || primary.SUPER() != null -> receiver
                    primary.type() != null -> {
                        val declared = try { function.parseDeclaredType(primary.type()) } catch (_: DeclarationBinding.Failure) { null }
                        val template = (declared as? top.mcfpp.type.MCFPPDataTemplateType)?.template
                        val static = if (template is top.mcfpp.model.compound.ObjectCompoundData) template else template?.companionObject
                        val factory = context.selector().firstOrNull()?.memberKeyword()?.text == "new"
                        (if (factory) declared else static?.getType() ?: declared)?.let { Receiver(it, null) }
                    }
                    variable?.functionCall() != null -> invocation(variable.functionCall(), null)
                    variable?.varWithSuffix() != null -> {
                        val identifier = variable.varWithSuffix().Identifier().text
                        val value = function.scope.getVar(identifier)
                        val backing = if (identifier == "field") function.accessorField?.let { function.owner?.scope?.getVar(it) } else null
                        if (backing != null) Receiver(backing.type, if (backing.isStatic) StorageAccess.ensure(backing).place
                            else receiver?.place?.field(backing.identifier))
                        else if (identifier in locals) Receiver(locals.getValue(identifier), null)
                        else if (value == null) {
                            val objectType = top.mcfpp.model.scope.GlobalScope.getObject(null, identifier)?.getType()
                                ?: top.mcfpp.model.scope.GlobalScope.getTemplate(null, identifier)?.getType()
                            if (objectType == null) observe(Effect.Unknown)
                            objectType?.let { Receiver(it, null) }
                        }
                        else Receiver(value.type, if (value.isStatic) StorageAccess.ensure(value).place else null)
                    }
                    else -> { visit(primary); null }
                }
                val selectors = context.selector()
                for ((index, selector) in selectors.withIndex()) {
                    val part = selector.`var`()
                    base = when {
                        part?.functionCall() != null -> invocation(part.functionCall(), base)
                        part?.varWithSuffix() != null -> {
                            val name = part.varWithSuffix().Identifier().text
                            val parentFactory = selectors.getOrNull(index + 1)?.memberKeyword()?.text == "new"
                            val parent = if (parentFactory) top.mcfpp.model.scope.GlobalScope.getTemplate(null, name) else null
                            if (base != null && parent != null && top.mcfpp.type.TypeRelations.isNominalSubtype(base.type, parent.getType()))
                                Receiver(parent.getType(), null)
                            else member(base, name, write && index == selectors.lastIndex, backingOnly && index == selectors.lastIndex)
                        }
                        selector.memberKeyword() != null -> top.mcfpp.antlr.KeywordMemberCalls.call(selector)?.let {
                            invocation(it, base)
                        } ?: run { observe(Effect.Unknown); null }
                        else -> { observe(Effect.Unknown); null }
                    }
                    // Index expressions can themselves contain calls. An unknown index overlaps every element.
                    part?.varWithSuffix()?.identifierSuffix()?.forEach { suffix ->
                        suffix.expression()?.let { visit(it) }
                        base = base?.copy(place = base?.place?.unknownIndex())
                    }
                }
                variable?.varWithSuffix()?.identifierSuffix()?.forEach { suffix ->
                    suffix.expression()?.let { visit(it) }
                    base = base?.copy(place = base?.place?.unknownIndex())
                }
                return base
            }
            visit = { node ->
                when (node) {
                    is Parser.FieldDeclarationContext -> {
                        val type = try {
                            node.type()?.let(function::parseDeclaredType) ?: node.expression()?.let {
                                DeclarationBinding(function, locals).expression(it).type
                            }
                        } catch (_: DeclarationBinding.Failure) { null }
                        locals[node.Identifier().text] = type ?: top.mcfpp.type.MCFPPBaseType.Any
                        node.expression()?.let { visit(it) }
                    }
                    is Parser.StatementExpressionContext -> {
                        val target = node.varWithSelector()
                        val deletion = node.assignmentOperator()?.text == "=" && node.expression().text == "null"
                        if (target != null) selected(target, true, deletion)?.place?.let { observe(Effect.Writes(setOf(it))) }
                        visit(node.expression())
                    }
                    is Parser.EqualityExpressionContext -> {
                        val operands = node.relationalExpression()
                        val nullIndex = operands.indexOfFirst { it.text == "null" }
                        var address: ParseTree? = if (operands.size == 2 && nullIndex >= 0) operands[1 - nullIndex] else null
                        while (address != null && address !is Parser.VarWithSelectorContext && address.childCount == 1)
                            address = address.getChild(0)
                        if (address is Parser.VarWithSelectorContext) selected(address, false, true)
                        else operands.forEach { visit(it) }
                    }
                    is Parser.VarWithSelectorContext -> { selected(node, false); Unit }
                    is Parser.FunctionCallContext -> { invocation(node, null); Unit }
                    is Parser.OrgCommandContext -> observe(EffectAnalysis.rawCommandEffect(node.start.inputStream.getText(
                        org.antlr.v4.runtime.misc.Interval(node.start.startIndex, node.stop.stopIndex)).removePrefix("/")))
                    else -> for (i in 0 until node.childCount) visit(node.getChild(i))
                }
            }
            try { visit(body) } finally { active.remove(function) }
            return effect
        }
    }
}
