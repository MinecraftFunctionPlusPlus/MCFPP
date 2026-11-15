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
        receiver?.let { Receiver(StorageAccess.actualType(it), StorageAccess.ensure(it).place) })

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
                if (candidates.isEmpty() && base == null) {
                    val template = top.mcfpp.model.scope.GlobalScope.getTemplate(
                        context.namespaceID().text.substringBefore(':', "").ifEmpty { null }, name)
                    if (template != null) {
                        candidates = template.constructors
                        target = Receiver(template.getType(), null)
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
                        part?.varWithSuffix() != null -> member(base, part.varWithSuffix().Identifier().text,
                            write && index == selectors.lastIndex, backingOnly && index == selectors.lastIndex)
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
