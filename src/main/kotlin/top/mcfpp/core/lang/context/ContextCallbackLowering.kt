package top.mcfpp.core.lang.context

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval
import top.mcfpp.Project
import top.mcfpp.analysis.DeclarationBinding
import top.mcfpp.analysis.EffectAnalysis
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.lambda.LambdaCaptureScope
import top.mcfpp.analysis.lambda.LambdaLowering
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.backend.EntityContextCommandOperations
import top.mcfpp.command.Command
import top.mcfpp.core.lang.FunctionValue
import top.mcfpp.core.lang.LambdaCaptureResult
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.Void
import top.mcfpp.core.lang.entity.EntityTargetAccess
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.FunctionScope
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** Compiler intrinsics: these source entry points remain available across fresh library imports. */
internal object ContextCallbackLowering {
    fun invoke(receiver: Var<*>, call: ContextCallbacks.Call, cache: ArrayList<Var<*>>): Var<*> {
        val creator = Function.currFunction
        val preceding = registeredFunctions()
        val spills = StorageAccess.spill(cache)
        var ownSession: ContextSession? = null
        try {
            val facts = (receiver as? EntityTargetAccess)?.entityFacts ?: (receiver as? ExecutionContextVar)?.context?.executorFacts
            val plan = DeclarationBinding(creator, emptyMap()).contextPlan(receiver.type, call, facts)
            val relation = plan.relation
            if (relation != null && relation.commandName !in
                top.mcfpp.command.TargetCapabilities.forVersion(Project.config.version)?.executeRelations.orEmpty())
                return fail("Target '${Project.config.version}' does not support execute on ${relation.commandName}")
            if (containsReturn(call.lambda().lambdaBody()) &&
                top.mcfpp.command.TargetCapabilities.forVersion(Project.config.version)?.functionReturnRun != true)
                return fail("Context callback return requires a target with function return support")
            val captured = StorageAccess.capture(receiver)
            if (captured.isError) return captured
            val parent = ContextBorrowing.currentContext ?: ContextSession().also { ownSession = it }.root()
            val entity = captured as? EntityTargetAccess
            val origin = (captured as? ExecutionContextVar)?.requireContext()
            // Freeze/validate target operands at the call site, before entering a child frame.
            val prefix = if (relation != null) {
                if (origin == null) return fail("Context '${call.name}' requires a bound ExecutionContext receiver")
                origin.executor.requireUsableIn(parent.scope)
                if (origin.executor.provenance == parent.components.executor) Command("execute on ${relation.commandName}")
                else Command("execute as").build(ContextRuntime.executor(origin.executor)).build("on ${relation.commandName}")
            } else {
                if (entity == null) return fail("Context '${call.name}' requires an entity access")
                val modifier = if (plan.operation == ContextCallbacks.Operation.POSITION) "at" else "as"
                Command("execute $modifier").build(ContextRuntime.entity(entity))
            }
            val scope = parent.session.openScope(parent.scope)
            val child = when (plan.operation) {
                ContextCallbacks.Operation.POSITION -> parent.withPosContext(scope)
                ContextCallbacks.Operation.ENTITY -> parent.withEntityContext(scope, requireNotNull(entity).entityFacts)
                ContextCallbacks.Operation.RELATION -> requireNotNull(origin).onRelation(requireNotNull(relation), scope, parent)
            }
            val observed = StorageAccess.visibleValues(creator.scope)
            StorageAccess.flush(observed)
            val errors = Project.errorCount
            ContextRuntime.withRegion(parent, child) { region ->
            ContextBorrowing.withContext(child) {
                val structured = top.mcfpp.analysis.lambda.StructuredLambdaBodies.handle(call.lambda(), creator, returnTail = false)
                val body = if (structured == null) body(plan) else null
                val targetFunction = Function(TempPool.getFunctionIdentify("context_body"), creator.namespace, body).apply {
                    bodyHandle = structured
                    returnType = MCFPPPrivateType.Void
                    actualCallBody = true
                    declarationFile = creator.declarationFile
                    declarationEnvironment = creator.declarationEnvironment
                }
                val captures = LambdaCaptureScope(targetFunction, creator.scope, controlledContext = true)
                targetFunction.scope = captures
                val parameter = plan.binding.parameters.single()
                targetFunction.appendNormalParam(parameter.type, parameter.name)
                val argument = ExecutionContextVar(parameter.name, child,
                    parameter.type as MCFPPExecutionContextType, EntityContextCommandOperations.encoder())
                captures.putVar(parameter.name, argument, true)
                GlobalScope.getOrCreateNamespace(creator.namespace).scope.addFunction(targetFunction, false)
                targetFunction.compileBody()
                if (Project.errorCount != errors) {
                    return@withContext
                }
                val ordinary = captures.captures().map { result -> when (result) {
                    is LambdaCaptureResult.Captured -> result.capture
                    is LambdaCaptureResult.Rejected -> {
                        LogProcessor.error(result.diagnostic.message)
                        return@withContext
                    }
                } }
                val callable = FunctionValue(TempPool.getVarIdentify(), plan.callback, targetFunction)
                callable.initializeEnvironment(ordinary)
                val entry = Function(TempPool.getFunctionIdentify("context_entry"), creator.namespace, null).apply {
                    actualCallBody = true
                    returnType = MCFPPPrivateType.Void
                    declarationFile = creator.declarationFile
                    bodyCompiled = true
                }
                GlobalScope.getOrCreateNamespace(creator.namespace).scope.addFunction(entry, false)
                entry.runInFunction {
                    Function.addCommands(ContextRuntime.pushFrame().toTypedArray())
                    LambdaLowering.invokeKnown(callable, listOf(argument), targetFunction)
                    Function.addCommand(ContextRuntime.popFrame())
                }
                if (Project.errorCount == errors) {
                    Function.addCommands(region.before().toTypedArray())
                    Function.addCommands(prefix.build("run function ${entry.namespaceID}").buildMacroFunction())
                    Function.addCommands(region.after().toTypedArray())
                    creator.child.add(entry)
                    entry.parent.add(creator)
                    val effect = EffectAnalysis.recordCall(targetFunction)
                    StorageAccess.applyEffect(observed, effect)
                }
            }
            }
            return if (Project.errorCount == errors) Void else UnknownVar("context_callback").apply { isError = true }
        } catch (failure: DeclarationBinding.Failure) {
            return fail(failure.message.orEmpty())
        } catch (failure: ContextAccessException) {
            return fail(failure.message.orEmpty())
        } finally {
            registeredFunctions().filter { it !in preceding }.forEach(::seal)
            ownSession?.close()
            StorageAccess.restore(spills)
        }
    }

    private fun containsReturn(node: org.antlr.v4.runtime.tree.ParseTree): Boolean {
        if (node is P.ReturnStatementContext) return true
        if (node is P.InlineLambdaContext) return false
        return (0 until node.childCount).any { containsReturn(node.getChild(it)) }
    }

    private fun body(plan: ContextCallbacks.Plan): P.CurlBlockContext {
        fun text(node: ParserRuleContext) = node.start.inputStream.getText(Interval(node.start.startIndex, node.stop.stopIndex))
        val source = buildString {
            append("{\n")
            plan.call.lambda().lambdaBody().statement().forEach { append(text(it)); append("\n;\n") }
            plan.binding.tailExpression?.let { append(text(it)); append(";\n") }
            append("}")
        }
        val parser = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(source, "controlled context callback"))))
        val body = parser.curlBlock()
        if (parser.numberOfSyntaxErrors != 0) throw DeclarationBinding.Failure("Cannot lower context callback body")
        return body
    }

    /** Temporary expression wrappers do not publish their child edges, so inspect registrations. */
    internal fun registeredFunctions(): Set<Function> {
        val functions = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Function, Boolean>())
        val compounds = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<top.mcfpp.model.compound.CompoundData, Boolean>())
        fun function(value: Function) {
            if (!functions.add(value)) return
            value.compiledFunctions.values.forEach(::function)
        }
        fun compound(value: top.mcfpp.model.compound.CompoundData) {
            if (!compounds.add(value)) return
            value.scope.forEachFunction(::function)
            if (value is top.mcfpp.model.compound.DataTemplate) {
                value.constructors.forEach(::function)
                value.companionObject?.let(::compound)
            }
            if (value is top.mcfpp.model.compound.GenericDataTemplate)
                value.compiledTemplates.values.forEach(::compound)
        }
        (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values).forEach { namespace ->
            namespace.scope.forEachFunction(::function)
            namespace.scope.template.values.forEach(::compound)
            namespace.scope.genericTemplate.values.forEach(::compound)
            namespace.scope.objects.forEach(::compound)
        }
        return functions
    }

    /** Only newly registered completed bodies may release their lexical capabilities. */
    internal fun seal(function: Function) {
        if (function.actualCallBody && function.bodyCompiled && !function.bodyBeingCompiled) {
            function.ast = null
            function.scope = FunctionScope(function.declarationFile?.field)
            function.staticRefValue.clear()
        }
    }

    private fun fail(message: String): Var<*> {
        LogProcessor.error(message)
        return UnknownVar("context_callback").apply { isError = true }
    }
}
