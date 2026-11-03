package top.mcfpp.core.lang.ref

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval
import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.analysis.lambda.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.context.ContextAccessException
import top.mcfpp.core.lang.context.ContextCallbackLowering
import top.mcfpp.core.lang.entity.EntityReadCallbacks
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** Synchronous source binding; helper dispatch completes before entering user code. */
internal object ReferenceReadCallbacks {
    fun selector(selector: P.SelectorContext, trailing: P.InlineLambdaContext? = null): EntityReadCallbacks.Call? =
        EntityReadCallbacks.selector(selector, trailing)

    fun signature(type: MCFPPType) = MCFPPFunctionType(listOf(type), MCFPPPrivateType.Void)

    fun invoke(receiver: Var<*>, call: EntityReadCallbacks.Call, cache: ArrayList<Var<*>>): Var<*> {
        val type = (receiver.type as? MCFPPReferenceType)?.valueType
            ?: return fail("tryRead requires ScoreRef or NBTRef<T>")
        val creator = Function.currFunction
        val preceding = ContextCallbackLowering.registeredFunctions()
        val spills = StorageAccess.spill(cache)
        try {
            ReferenceAccess.requireReadable(receiver)
            val binding = DeclarationBinding(creator, emptyMap()).entityReadPlan(type, call)
            val structured = StructuredLambdaBodies.handle(call.lambda(), creator, returnTail = false)
            fun text(node: ParserRuleContext) = node.start.inputStream.getText(Interval(node.start.startIndex, node.stop.stopIndex))
            val source = if (structured != null) "" else buildString {
                append("{\n")
                call.lambda().lambdaBody().statement().forEach { append(text(it)); append("\n;\n") }
                binding.tailExpression?.let { append(text(it)); append(";\n") }
                append("}")
            }
            val syntax = if (structured != null) null else {
                val parser = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(source, "reference tryRead callback"))))
                parser.curlBlock().also { if (parser.numberOfSyntaxErrors != 0) return fail("Cannot lower tryRead callback") }
            }
            val errors = Project.errorCount
            val target = Function(TempPool.getFunctionIdentify("reference_read_body"), creator.namespace, syntax).apply {
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
            val callable = FunctionValue(TempPool.getVarIdentify(), signature(type), target)
            callable.initializeEnvironment(ordinary)
            val observed = StorageAccess.visibleValues(creator.scope)
            val value = type.buildUnConcrete(TempPool.getVarIdentify()).apply { isTemp = true }
            val success = ScoreBool(TempPool.getVarIdentify()).apply { isTemp = true }
            ReferenceAccess.readInto(receiver, value, success)
            val entry = Function(TempPool.getFunctionIdentify("reference_read_entry"), creator.namespace, null).apply {
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
        return UnknownVar("reference_read").apply { isError = true }
    }
}
