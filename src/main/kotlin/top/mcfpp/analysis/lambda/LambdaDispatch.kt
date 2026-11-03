package top.mcfpp.analysis.lambda

import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.FunctionValue
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NoStackFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.MCFPPFunctionType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** A static branch table over trusted targets; the whole payload keeps code/env correlated. */
internal object LambdaDispatch {
    fun invoke(value: FunctionValue, arguments: List<Var<*>>, targets: List<Function>): Var<*> {
        val signature = value.type as MCFPPFunctionType
        val caller = Function.currFunction
        val dispatcher = Function(TempPool.getFunctionIdentify("lambda_dispatch"), caller.namespace, null)
        dispatcher.isDerivedImplementation = true
        dispatcher.returnType = signature.result
        signature.parameters.forEachIndexed { index, type -> dispatcher.appendNormalParam(type, "argument_$index") }
        val payloadName = TempPool.getVarIdentify()
        dispatcher.appendNormalParam(MCFPPNBTType.NBT, payloadName)
        val namespace = GlobalScope.getOrCreateNamespace(dispatcher.namespace).scope
        namespace.addFunction(dispatcher, false)
        val errors = Project.errorCount
        dispatcher.runInFunction {
            dispatcher.buildParamVar()
            dispatcher.bindIncomingParameters()
            if (signature.result != MCFPPPrivateType.Void) {
                dispatcher.returnVar.nbtPath = NBTPath.stack.intIndex(0).memberIndex("return")
                StorageAccess.declareReturnSlot(dispatcher.returnVar)
            }
            val parameters = signature.parameters.indices.map { dispatcher.scope.getVar("argument_$it")!! }
            for (index in parameters.indices) StorageAccess.copyRuntimeEvidence(
                StorageAccess.ensure(arguments[index]), StorageAccess.ensure(parameters[index]), includeRoot = false)
            val payload = dispatcher.scope.getVar(payloadName)!!
            StorageAccess.copyRuntimeEvidence(StorageAccess.ensure(value), StorageAccess.ensure(payload), includeRoot = false)
            val callable = StorageAccess.adapter(signature, "callable", StorageAccess.ensure(payload)) as FunctionValue
            val targetPath = StorageAccess.ensure(payload).path.memberIndex("target")
            Function.addCommand("execute unless data ${targetPath.toCommandPart()} run return fail")
            for (target in targets) {
                val branch = NoStackFunction(TempPool.getFunctionIdentify("lambda_target"), dispatcher)
                branch.isDerivedImplementation = true
                namespace.addFunction(branch, false)
                dispatcher.child.add(branch)
                branch.runInFunction {
                    val returned = LambdaLowering.invokeKnown(callable, parameters, target)
                    if (!returned.isError && signature.result != MCFPPPrivateType.Void) dispatcher.assignReturnVar(returned)
                    Function.addCommand("return 1")
                }
                branch.bodyCompiled = true
                val probe = NBTPath.stack.intIndex(0).memberIndex(TempPool.getVarIdentify())
                val different = MCInt()
                Function.addCommand(Commands.dataSetFrom(probe, targetPath))
                Function.addCommand(Command("execute store success score ${different.name} ${different.sbObject} run")
                    .build(Commands.dataSetValue(probe, StringTag(target.namespaceID.toString()))))
                Function.addCommand("execute if score ${different.name} ${different.sbObject} matches 0 run return run function ${branch.namespaceID}")
            }
            Function.addCommand("return fail")
            if (signature.result != MCFPPPrivateType.Void &&
                !dispatcher.finishRuntimeReturns())
                LogProcessor.error("Finite callable dispatch has no initialized return on every target")
            dispatcher.hasReturnStatement = true
            dispatcher.bodyCompiled = true
        }
        if (Project.errorCount != errors) return UnknownVar("dispatch").apply { isError = true }
        caller.child.add(dispatcher)
        dispatcher.parent.add(caller)
        // Arguments and callable were already evaluated/captured once by the call boundary.
        return dispatcher.invoke(arguments + value.toNBTVar(), null)
    }
}
