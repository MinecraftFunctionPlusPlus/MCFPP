package top.mcfpp.analysis.lambda

import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.FunctionValue
import top.mcfpp.core.lang.LambdaCaptureResult
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPFunctionType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** Binding a method owns a value copy of its receiver, never just an unbound code pointer. */
internal object LambdaMemberReference {
    fun hasMethod(type: MCFPPType, name: String): Boolean = (type as? MCFPPDataTemplateType)?.template?.scope?.let {
        it.getVar(name) == null && it.getFunctionCandidates(name).isNotEmpty()
    } == true

    fun resolve(type: MCFPPType, name: String, signature: MCFPPFunctionType, site: ParserRuleContext, caller: Function): Function {
        val owner = (type as? MCFPPDataTemplateType)?.template
            ?: throw top.mcfpp.analysis.DeclarationBinding.Failure("A bound method reference requires a concrete receiver type")
        val selected = when (val bound = LambdaBinding.resolveReference(name, signature, owner.scope.getFunctionCandidates(name), site)) {
            is LambdaBinding.Result.Bound -> bound.value.function
            is LambdaBinding.Result.Rejected -> throw top.mcfpp.analysis.DeclarationBinding.Failure(bound.diagnostic.render())
        }
        val declarationOwner = selected.owner as? top.mcfpp.model.compound.DataTemplate ?: owner
        if (caller.accessTo(declarationOwner) < selected.accessModifier)
            throw top.mcfpp.analysis.DeclarationBinding.Failure("Cannot access method reference '$name'")
        return selected
    }

    fun create(receiver: Var<*>, name: String, signature: MCFPPFunctionType, site: ParserRuleContext): Var<*> {
        val original = receiver as? DataTemplateObject ?: return fail("Method reference '$name' requires a concrete value receiver")
        val caller = Function.currFunction
        val method = try { resolve(original.type, name, signature, site, caller) }
            catch (failure: top.mcfpp.analysis.DeclarationBinding.Failure) { return fail(failure.message.orEmpty()) }
        // A bound receiver is an evaluated value even when its expression is a global.
        // Freeze that value once, rather than applying free-global lookup semantics.
        val source = StorageAccess.capture(original) as? DataTemplateObject
            ?: return fail("Cannot create an owned receiver copy for method '$name'")
        source.bindDeclaration()
        val capture = when (val result = LambdaCaptureRules.capture(source, source.symbol)) {
            is LambdaCaptureResult.Captured -> result.capture
            is LambdaCaptureResult.Rejected -> return fail(result.diagnostic.message)
        }
        val wrapper = Function(TempPool.getFunctionIdentify("lambda_method"), caller.namespace, null)
        wrapper.isDerivedImplementation = true
        wrapper.returnType = signature.result
        signature.parameters.forEachIndexed { index, type -> wrapper.appendNormalParam(type, "argument_$index") }
        val environmentName = TempPool.getVarIdentify()
        wrapper.appendNormalParam(MCFPPNBTType.NBT, environmentName)
        GlobalScope.getOrCreateNamespace(wrapper.namespace).scope.addFunction(wrapper, false)
        val errors = Project.errorCount
        wrapper.runInFunction {
            wrapper.buildParamVar()
            wrapper.bindIncomingParameters()
            if (signature.result != MCFPPPrivateType.Void) {
                wrapper.returnVar.nbtPath = NBTPath.stack.intIndex(0).memberIndex("return")
                StorageAccess.declareReturnSlot(wrapper.returnVar)
            }
            val capturedReceiver = if (StorageAccess.hasRuntimeRepresentation(source)) {
                val environment = StorageAccess.ensure(wrapper.scope.getVar(environmentName)!!)
                val binding = environment.field("capture_${capture.symbol.value}")
                StorageAccess.copyRuntimeEvidence(StorageAccess.ensure(source), binding)
                StorageAccess.adapter(source.type, capture.name, binding)
            } else capture.snapshot?.let {
                StorageAccess.restore(source.type, it, TempPool.getVarIdentify(), StorageAccess.boundTypes(source))
            } ?: run {
                LogProcessor.error("Cannot preserve receiver '${capture.name}' under its copy contract")
                return@runInFunction
            }
            val arguments = signature.parameters.indices.map { wrapper.scope.getVar("argument_$it")!! }
            wrapper.child.add(method)
            method.parent.add(wrapper)
            val returned = method.invoke(arguments, capturedReceiver)
            if (!returned.isError && signature.result != MCFPPPrivateType.Void) wrapper.assignReturnVar(returned)
            Function.addCommand("return 1")
            wrapper.hasReturnStatement = true
            wrapper.bodyCompiled = true
        }
        if (Project.errorCount != errors) return UnknownVar("method_reference").apply { isError = true }
        return FunctionValue(TempPool.getVarIdentify(), signature, wrapper).also {
            it.initializeEnvironment(listOf(capture), StorageAccess.boundTypes(source))
        }
    }

    private fun fail(message: String): Var<*> {
        LogProcessor.error(message)
        return UnknownVar("method_reference").apply { isError = true }
    }
}
