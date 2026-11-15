package top.mcfpp.core.lang.context

import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.analysis.DeclarationBinding
import top.mcfpp.analysis.lambda.LambdaBinding
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.type.MCFPPFunctionType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import java.util.Collections

/** Source-only binding data. Runtime region creation consumes this plan at a later boundary. */
internal object ContextCallbacks {
    enum class Operation { POSITION, ENTITY, ENTITY_AT, RELATION, POSITIONED, ROTATION, FACING, DIMENSION, ALIGN, ANCHOR, HEIGHTMAP, SUMMON, CURRENT }

    class Call(val name: String, arguments: List<ParserRuleContext>, val readonly: Boolean, val site: ParserRuleContext,
               val named: Boolean = false) {
        val arguments: List<ParserRuleContext> = Collections.unmodifiableList(ArrayList(arguments))
        fun lambda(): P.InlineLambdaContext {
            if (readonly) throw DeclarationBinding.Failure("Context '$name' does not accept readonly arguments")
            if (arguments.isEmpty()) throw DeclarationBinding.Failure("Context '$name' expects an inline synchronous callback")
            val argument = arguments.last()
            return top.mcfpp.analysis.TemplateLiteralSupport.unwrapInlineLambda(argument)
                ?: throw DeclarationBinding.Failure("Context '$name' requires a statically bound inline lambda; runtime function values are unsupported")
        }
        fun operands(): List<P.ExpressionContext> {
            if (named) throw DeclarationBinding.Failure("Context entry '$name' does not accept named context arguments")
            return arguments.dropLast(1).map {
            it as? P.ExpressionContext ?: throw DeclarationBinding.Failure("Context '$name' expects expression operands before its callback")
            }
        }
    }

    class Plan(
        val call: Call,
        val receiver: MCFPPType,
        val operation: Operation,
        val relation: ContextRelation?,
        val callback: MCFPPFunctionType,
        val binding: LambdaBinding.BoundLambda,
        nested: List<Plan>
    ) {
        val result: MCFPPType get() = MCFPPPrivateType.Void
        val nested: List<Plan> = Collections.unmodifiableList(ArrayList(nested))
    }

    private val relations = ContextRelation.values().associateBy {
        "on" + it.commandName.replaceFirstChar { character -> character.uppercaseChar() }
    }
    private val operations = mapOf("withPosCtx" to Operation.POSITION, "withEntityCtx" to Operation.ENTITY,
        "withPositionedCtx" to Operation.POSITIONED, "withRotationCtx" to Operation.ROTATION,
        "withFacingCtx" to Operation.FACING, "withDimensionCtx" to Operation.DIMENSION,
        "withAlignedCtx" to Operation.ALIGN, "withAnchorCtx" to Operation.ANCHOR,
        "withHeightmapCtx" to Operation.HEIGHTMAP, "withSummonedCtx" to Operation.SUMMON,
        "withCurrentCtx" to Operation.CURRENT, "forEach" to Operation.ENTITY, "forEachAt" to Operation.ENTITY_AT)
    fun recognizes(name: String): Boolean = name in operations || name in relations
    fun recognizesGlobal(name: String): Boolean = name in operations && name !in setOf("forEach", "forEachAt")

    fun global(call: P.FunctionCallContext?, trailing: P.InlineLambdaContext? = null,
               bare: P.VarWithSuffixContext? = null): Call? {
        val name = call?.namespaceID()?.text ?: bare?.Identifier()?.text ?: return null
        if (!recognizesGlobal(name)) return null
        val syntax = call?.arguments()
        val normal = syntax?.normalArgs()
        val named = normal?.callArgumentList()?.callArgument()?.any { it.ASSIGNMENT() != null } == true
        val arguments = normal?.callArgumentList()?.callArgument()?.map { it.inlineLambda() ?: it.expression() }
            ?: normal?.expressionList()?.expression().orEmpty()
        return Call(name, arguments.filterNotNull() + listOfNotNull(trailing), syntax?.readOnlyArgs() != null, call ?: requireNotNull(bare), named)
    }

    fun selector(selector: P.SelectorContext, trailing: P.InlineLambdaContext? = null, receiver: MCFPPType? = null): Call? {
        val function = selector.`var`()?.functionCall()
        val name = function?.namespaceID()?.text ?: selector.`var`()?.varWithSuffix()
            ?.takeIf { it.identifierSuffix().isEmpty() }?.Identifier()?.text ?: return null
        if (!recognizes(name)) return null
        if (name in setOf("forEach", "forEachAt") && receiver !is MCFPPEntityType) return null
        val syntax = function?.arguments() ?: selector.arguments()
        val normal = syntax?.normalArgs()
        val arguments = normal?.callArgumentList()?.callArgument()?.map {
            if (it.REF() != null) throw DeclarationBinding.Failure(top.mcfpp.antlr.LegacyReferenceSyntax.MESSAGE)
            it.inlineLambda() ?: it.expression()
        } ?: normal?.expressionList()?.expression().orEmpty()
        return Call(name, arguments.filterNotNull() + listOfNotNull(trailing ?: selector.inlineLambda()), syntax?.readOnlyArgs() != null, selector,
            normal?.callArgumentList()?.callArgument()?.any { it.ASSIGNMENT() != null } == true)
    }

    /** The receiver and operation determine the signature; lambda nesting never does. */
    fun target(receiver: MCFPPType, call: Call, hasExecutor: Boolean = ContextParameters.currentExecutorKnown,
               ambientType: MCFPPType = ContextParameters.currentFrameType): MCFPPFunctionType {
        if (!recognizes(call.name)) throw DeclarationBinding.Failure("Unknown execution context operation '${call.name}'")
        val operation = operation(call)
        val global = receiver == MCFPPPrivateType.Void
        val expected = when {
            operation == Operation.RELATION -> 0
            operation == Operation.CURRENT -> 0
            !global && operation in setOf(Operation.POSITION, Operation.ENTITY, Operation.ENTITY_AT, Operation.POSITIONED, Operation.ROTATION) -> 0
            else -> 1
        }
        if (call.operands().size != expected)
            throw DeclarationBinding.Failure("Context '${call.name}' expects $expected operand(s) and one inline callback")
        val context = when {
            operation == Operation.RELATION -> {
                if (receiver !is MCFPPExecutionContextType)
                    throw DeclarationBinding.Failure("Context '${call.name}' requires an ExecutionContext receiver, found '$receiver'")
                if (!ContextTypes.supports(receiver, ContextTypes.EntityContext) && !ContextTypes.supports(receiver, ContextTypes.ExecutionContext))
                    throw DeclarationBinding.Failure("Context '${call.name}' requires an executor-capable receiver")
                ContextTypes.RelationContext
            }
            !global && receiver !is MCFPPEntityType -> throw DeclarationBinding.Failure("Context '${call.name}' requires an entity receiver")
            operation == Operation.ENTITY && global -> throw DeclarationBinding.Failure("withEntityCtx requires an entity receiver")
            operation !in setOf(Operation.ENTITY, Operation.ENTITY_AT, Operation.SUMMON) && ContextTypes.supports(ambientType, ContextTypes.RelationContext) -> ContextTypes.RelationContext
            operation in setOf(Operation.ENTITY, Operation.ENTITY_AT, Operation.SUMMON) || hasExecutor -> ContextTypes.EntityExecutionContext
            else -> ContextTypes.ExecutionContext
        }
        return MCFPPFunctionType(listOf(context), MCFPPPrivateType.Void)
    }

    fun operation(call: Call): Operation = operations[call.name] ?: Operation.RELATION
    fun relation(call: Call): ContextRelation? = relations[call.name]

    fun environment(base: LambdaBinding.Environment, nested: List<Plan>): LambdaBinding.Environment {
        val targets = java.util.IdentityHashMap<P.InlineLambdaContext, MCFPPFunctionType>()
        fun collect(plan: Plan) {
            targets[plan.call.lambda()] = plan.callback
            plan.nested.forEach(::collect)
        }
        nested.forEach(::collect)
        return LambdaBinding.Environment(base.caller, base.scope,
            resolveType = { syntax -> ContextTypes.named(syntax.text) ?: base.resolveType(syntax) },
            functions = base.functions, symbolTypes = base.symbolTypes,
            callReturnType = base.callReturnType,
            lambdaTargets = { syntax -> targets[syntax]?.let(::listOf) ?: base.lambdaTargets(syntax) },
            callReturnTypeWithArguments = base.callReturnTypeWithArguments)
    }
}
