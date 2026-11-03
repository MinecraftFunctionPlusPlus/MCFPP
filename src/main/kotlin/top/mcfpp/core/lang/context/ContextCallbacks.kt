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
    enum class Operation { POSITION, ENTITY, RELATION }

    class Call(val name: String, arguments: List<ParserRuleContext>, val readonly: Boolean, val site: ParserRuleContext) {
        val arguments: List<ParserRuleContext> = Collections.unmodifiableList(ArrayList(arguments))
        fun lambda(): P.InlineLambdaContext {
            if (readonly) throw DeclarationBinding.Failure("Context '$name' does not accept readonly arguments")
            if (arguments.size != 1) throw DeclarationBinding.Failure("Context '$name' expects one inline synchronous callback")
            val argument = arguments.single()
            return argument as? P.InlineLambdaContext ?: (argument as? P.ExpressionContext)?.primary()?.inlineLambda()
                ?: throw DeclarationBinding.Failure("Context '$name' requires a statically bound inline lambda; runtime function values are unsupported")
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
    fun recognizes(name: String): Boolean = name == "withPosCtx" || name == "withEntityCtx" || name in relations

    fun selector(selector: P.SelectorContext, trailing: P.InlineLambdaContext? = null): Call? {
        val function = selector.`var`()?.functionCall()
        val name = function?.namespaceID()?.text ?: selector.`var`()?.varWithSuffix()
            ?.takeIf { it.identifierSuffix().isEmpty() }?.Identifier()?.text ?: return null
        if (!recognizes(name)) return null
        val syntax = function?.arguments() ?: selector.arguments()
        val normal = syntax?.normalArgs()
        val arguments = normal?.callArgumentList()?.callArgument()?.map {
            if (it.REF() != null) throw DeclarationBinding.Failure(top.mcfpp.antlr.LegacyReferenceSyntax.MESSAGE)
            it.inlineLambda() ?: it.expression()
        } ?: normal?.expressionList()?.expression().orEmpty()
        return Call(name, arguments.filterNotNull() + listOfNotNull(trailing), syntax?.readOnlyArgs() != null, selector)
    }

    /** The receiver and operation determine the signature; lambda nesting never does. */
    fun target(receiver: MCFPPType, call: Call): MCFPPFunctionType {
        if (!recognizes(call.name)) throw DeclarationBinding.Failure("Unknown execution context operation '${call.name}'")
        val context = when (call.name) {
            "withPosCtx", "withEntityCtx" -> {
                if (receiver !is MCFPPEntityType)
                    throw DeclarationBinding.Failure("Context '${call.name}' requires an entity receiver, found '$receiver'")
                if (call.name == "withPosCtx") ContextTypes.PosContext else ContextTypes.EntityContext
            }
            else -> {
                if (receiver !is MCFPPExecutionContextType)
                    throw DeclarationBinding.Failure("Context '${call.name}' requires an ExecutionContext receiver, found '$receiver'")
                ContextTypes.RelationContext
            }
        }
        return MCFPPFunctionType(listOf(context), MCFPPPrivateType.Void)
    }

    fun operation(call: Call): Operation = when (call.name) {
        "withPosCtx" -> Operation.POSITION
        "withEntityCtx" -> Operation.ENTITY
        else -> Operation.RELATION
    }
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
