package top.mcfpp.core.lang.entity

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval
import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.analysis.lambda.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.command.Command
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.context.ContextAccessException
import top.mcfpp.core.lang.context.ContextCallbackLowering
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** Synchronous source intrinsic: the callback runs in precisely the caller's MC context. */
internal object EntityReadCallbacks {
    class Call(val arguments: List<ParserRuleContext>, val readonly: Boolean) {
        fun lambda(): P.InlineLambdaContext {
            if (readonly || arguments.size != 1) throw DeclarationBinding.Failure("tryRead expects one inline synchronous callback")
            val node = arguments.single()
            return node as? P.InlineLambdaContext ?: (node as? P.ExpressionContext)?.primary()?.inlineLambda()
                ?: throw DeclarationBinding.Failure("tryRead requires an inline synchronous callback")
        }
    }

    fun selector(selector: P.SelectorContext, trailing: P.InlineLambdaContext? = null): Call? {
        val function = selector.`var`()?.functionCall()
        val name = function?.namespaceID()?.text ?: selector.`var`()?.varWithSuffix()?.Identifier()?.text
        if (name != "tryRead") return null
        val syntax = function?.arguments() ?: selector.arguments()
        val normal = syntax?.normalArgs()
        val arguments = normal?.callArgumentList()?.callArgument()?.map {
            if (it.REF() != null) throw DeclarationBinding.Failure(top.mcfpp.antlr.LegacyReferenceSyntax.MESSAGE)
            it.inlineLambda() ?: it.expression()
        } ?: normal?.expressionList()?.expression().orEmpty()
        return Call(arguments.filterNotNull() + listOfNotNull(trailing), syntax?.readOnlyArgs() != null)
    }

    fun signature(type: MCFPPType) = MCFPPFunctionType(listOf((type as? MCFPPReferenceType)?.valueType ?: type), MCFPPPrivateType.Void)

    fun invoke(receiver: Var<*>, call: Call, cache: ArrayList<Var<*>>): Var<*> {
        val address = EntityDataAddress.selected(receiver) ?: return fail("tryRead requires a live entity NBT or score address")
        val creator = Function.currFunction
        val preceding = ContextCallbackLowering.registeredFunctions()
        val spills = StorageAccess.spill(cache)
        try {
            address.requireUsable()
            val binding = DeclarationBinding(creator, emptyMap()).entityReadPlan(address.type, call)
            val structured = top.mcfpp.analysis.lambda.StructuredLambdaBodies.handle(call.lambda(), creator, returnTail = false)
            fun text(node: ParserRuleContext) = node.start.inputStream.getText(Interval(node.start.startIndex, node.stop.stopIndex))
            val source = if (structured != null) "" else buildString {
                append("{\n")
                call.lambda().lambdaBody().statement().forEach { append(text(it)); append("\n;\n") }
                binding.tailExpression?.let { append(text(it)); append(";\n") }
                append("}")
            }
            val syntax = if (structured != null) null else {
                val parser = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(source, "entity tryRead callback"))))
                parser.curlBlock().also { if (parser.numberOfSyntaxErrors != 0) return fail("Cannot lower tryRead callback") }
            }
            val errors = Project.errorCount
            val target = Function(TempPool.getFunctionIdentify("entity_read_body"), creator.namespace, syntax).apply {
                bodyHandle = structured
                returnType = MCFPPPrivateType.Void
                actualCallBody = true
                declarationFile = creator.declarationFile
                declarationEnvironment = creator.declarationEnvironment
            }
            val captures = LambdaCaptureScope(target, creator.scope, controlledContext = true)
            target.scope = captures
            binding.parameters.forEach { target.appendNormalParam(it.type, it.name) }
            target.buildParamVar()
            GlobalScope.getOrCreateNamespace(creator.namespace).scope.addFunction(target, false)
            target.compileBody()
            if (Project.errorCount != errors) return fail("Cannot compile tryRead callback")
            val ordinary = captures.captures().map { result -> when (result) {
                is LambdaCaptureResult.Captured -> result.capture
                is LambdaCaptureResult.Rejected -> return fail(result.diagnostic.message)
            } }
            val callable = FunctionValue(TempPool.getVarIdentify(), signature(address.type), target)
            callable.initializeEnvironment(ordinary)
            val observed = StorageAccess.visibleValues(creator.scope)
            StorageAccess.flush(observed)
            val value = address.valueType.buildUnConcrete(TempPool.getVarIdentify()).apply { isTemp = true }
            val success = ScoreBool(TempPool.getVarIdentify()).apply { isTemp = true }
            address.readInto(value, success)
            StorageAccess.publishBoolean(success, StorageLayout.Scoreboard(success.name, success.boolObject.toString()))
            val entry = Function(TempPool.getFunctionIdentify("entity_read_entry"), creator.namespace, null).apply {
                actualCallBody = true
                returnType = MCFPPPrivateType.Void
                bodyCompiled = true
                declarationFile = creator.declarationFile
            }
            GlobalScope.getOrCreateNamespace(creator.namespace).scope.addFunction(entry, false)
            entry.runInFunction { LambdaLowering.invokeKnown(callable, listOf(value), target) }
            Function.addCommand("execute if score ${success.name} ${success.boolObject} matches 1 run function ${entry.namespaceID}")
            creator.child.add(entry)
            entry.parent.add(creator)
            StorageAccess.applyEffect(observed, EffectAnalysis.recordCall(target))
            return success
        } catch (failure: DeclarationBinding.Failure) { return fail(failure.message.orEmpty())
        } catch (failure: ContextAccessException) { return fail(failure.message.orEmpty())
        } finally {
            ContextCallbackLowering.registeredFunctions().filter { it !in preceding }.forEach(ContextCallbackLowering::seal)
            StorageAccess.restore(spills)
        }
    }

    private fun fail(message: String): Var<*> {
        LogProcessor.error(message)
        return UnknownVar("entity_read").apply { isError = true }
    }
}
