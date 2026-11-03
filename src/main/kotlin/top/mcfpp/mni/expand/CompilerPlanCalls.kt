package top.mcfpp.mni.expand

import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.StorageLayout
import top.mcfpp.core.lang.Var
import top.mcfpp.mni.api.CodeModelException
import top.mcfpp.mni.api.ParamMode
import top.mcfpp.mni.api.SymbolId
import top.mcfpp.model.function.DataTemplateConstructor
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.type.MCFPPGenericParamType
import top.mcfpp.type.MCFPPNotCompiledGenericType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.UnresolvedType

/**
 * Complete-argument ordinary/generic/native call emission.
 *
 * The detached plan has already evaluated every argument exactly once, in source order, and
 * [SourceCodeProjector] has already completed every default. This class must therefore only
 * adapt those values to the live target: it never visits an expression, never fills a formal
 * from a default, and never resolves a function by display name.
 */
internal class CompilerPlanCalls(private val out: CompilerPlanEmitter) {

    fun call(operation: PlanOperation.Call) {
        val spec = operation.function
        val target = out.bindings.function(spec.key)
        if (target is NativeFunction) {
            if (spec.kind != PlanCallKind.LEGACY_NATIVE)
                out.fail("Native target '${spec.name}' was selected as ${spec.kind} instead of a legacy native call")
            nativeCall(spec, target, operation)
            return
        }
        if (spec.kind == PlanCallKind.LEGACY_NATIVE)
            out.fail("Legacy native call lowering requires a native target for '${spec.name}', not ${target::class.simpleName}")
        if (spec.kind == PlanCallKind.HELPER) {
            helperCall(spec, target, operation)
            return
        }
        validateTarget(spec, target, operation)
        val readonly = readonlyArguments(spec, operation)
        val normal = LinkedHashMap<String, Var<*>>()
        spec.parameters.zip(operation.arguments).forEach { (parameter, argument) ->
            normal[parameter.name] = argument(parameter, argument)
        }
        val completed = LinkedHashMap<String, Var<*>>()
        completed.putAll(readonly)
        completed.putAll(normal)
        val receiver = operation.receiver?.let { reference ->
            out.locations.memberReceiver(
                reference.location,
                "receiver_${spec.name.substringAfterLast(':').replace(Regex("[^A-Za-z0-9_]"), "_")}"
            )
        }

        validateResultSlot(spec, operation)
        val diagnostics = Project.errorCount
        val returned = try {
            target.invokeComplete(completed, receiver)
        } catch (failure: CodeModelException) {
            throw failure
        } catch (failure: IllegalArgumentException) {
            out.fail("Complete call to '${spec.name}' is invalid: ${failure.message}")
        }
        if (Project.errorCount != diagnostics || returned.isError)
            out.fail("Complete call to '${spec.name}' did not produce a valid runtime invocation")
        storeResult(spec, operation, returned)
        // The visitor records the declared target for overload/tree purposes even when the
        // runtime body was specialized. The generated block is the actual caller here.
        val caller = Function.currFunction
        caller.child.add(target)
        target.parent.add(caller)
    }

    /** Complete result slots and stores are shared with the later ordinary member emitter. */
    private fun validateResultSlot(spec: PlanFunction, operation: PlanOperation.Call) {
        val result = operation.result
        val returnsValue = spec.resultType.kind != top.mcfpp.mni.api.TypeKind.VOID
        if ((result != null) != returnsValue)
            out.fail("Call '${spec.name}' result slot does not match its complete signature")
        if (result != null && !out.type(result.type).hasRuntimeRepresentation)
            out.fail("Call '${spec.name}' has a compiler-only result value that the runtime call boundary cannot carry")
    }

    private fun storeResult(spec: PlanFunction, operation: PlanOperation.Call, returned: Var<*>) {
        val result = operation.result ?: return
        if (!StorageAccess.hasRuntimeRepresentation(returned))
            out.fail("Call '${spec.name}' returned a compiler-only value without a closed runtime payload")
        val beforeStore = Project.errorCount
        StorageAccess.encodeTo(out.values.path(result), returned)
        if (Project.errorCount != beforeStore)
            out.fail("Call '${spec.name}' could not store its return value")
        out.values.recordReturn(operation, returned)
    }

    private fun validateTarget(spec: PlanFunction, target: Function, operation: PlanOperation.Call) {
        if (target is DataTemplateConstructor)
            out.fail("Constructor call '${spec.name}' requires a dedicated construction receiver protocol")
        if (target is UnknownFunction || target is top.mcfpp.model.function.UnresolvedFunction)
            out.fail("Call target '${spec.name}' is not a resolved ordinary function")
        val member = target.owner != null && !target.isStatic
        if (member != (operation.receiver != null))
            out.fail(
                if (member) "Member call '${spec.name}' requires its actual receiver position"
                else "Call '${spec.name}' cannot carry a receiver for a global/static target"
            )
        if (operation.receiver != null) {
            val receiver = out.locations.get(operation.receiver.location)
            if (!out.type(receiver.type).hasRuntimeRepresentation)
                out.fail("Member call '${spec.name}' has a compiler-only receiver")
            if (!receiver.writable)
                out.fail("Member call '${spec.name}' requires a writable original receiver location")
        }

        val targetReadonly = (target as? top.mcfpp.model.Generic<*>)?.readOnlyParams.orEmpty()
        if (targetReadonly.size != spec.readonlyParameters.size)
            out.fail("Call '${spec.name}' readonly arity differs from the selected target signature")
        targetReadonly.zip(spec.readonlyParameters).forEach { (actual, planned) ->
            if (actual.identifier != planned.name)
                out.fail("Call '${spec.name}' readonly parameter '${planned.name}' differs from the selected target")
            if (actual.type !is UnresolvedType &&
                actual.type !is MCFPPGenericParamType &&
                actual.type.typeId != out.bindings.compilerType(planned.type).typeId
            )
                out.fail("Call '${spec.name}' readonly parameter '${planned.name}' has a foreign prepared type")
        }
        if (target.normalParams.size != spec.parameters.size)
            out.fail("Call '${spec.name}' normal arity differs from the selected target signature")
        target.normalParams.zip(spec.parameters).forEach { (actual, planned) ->
            if (actual.identifier != planned.name)
                out.fail("Call '${spec.name}' normal parameter '${planned.name}' differs from the selected target")
            if (actual.type !is UnresolvedType &&
                actual.type !is MCFPPGenericParamType &&
                actual.type.typeId != out.bindings.compilerType(planned.type).typeId
            )
                out.fail("Call '${spec.name}' normal parameter '${planned.name}' has a foreign prepared type")
        }
        if (operation.arguments.size != spec.parameters.size || operation.readonlyArguments.size != spec.readonlyParameters.size)
            out.fail("Call '${spec.name}' does not carry every complete argument")
        if (target !is top.mcfpp.model.Generic<*> &&
            target.returnType !is UnresolvedType &&
            target.returnType !is MCFPPGenericParamType &&
            target.returnType != MCFPPPrivateType.Void &&
            target.returnType.typeId != out.bindings.compilerType(spec.resultType).typeId
        )
            out.fail("Call '${spec.name}' return type differs from the selected target signature")
    }

    /** Readonly facts are compared with the declaration preparation, not re-derived here. */
    private fun readonlyArguments(spec: PlanFunction, operation: PlanOperation.Call): LinkedHashMap<String, Var<*>> {
        val prepared = out.bindings.readonly(spec.key)
        if (prepared.size != operation.readonlyArguments.size)
            out.fail("Call '${spec.name}' readonly facts differ from the prepared concrete target")
        val result = LinkedHashMap<String, Var<*>>()
        spec.readonlyParameters.zip(operation.readonlyArguments).forEachIndexed { index, (parameter, argument) ->
            val expected = prepared[index]
            if (out.bindings.compilerType(argument.type).typeId != out.bindings.compilerType(expected.type).typeId)
                out.fail("Call '${spec.name}' readonly '${parameter.name}' type differs from the prepared concrete target")
            if (PlanConstant.detach(expected.value) != argument.value)
                out.fail("Call '${spec.name}' readonly '${parameter.name}' value differs from the prepared concrete target")
            if (out.bindings.compilerType(parameter.type).typeId != out.bindings.compilerType(argument.type).typeId)
                out.fail("Call '${spec.name}' readonly '${parameter.name}' plan type differs from its argument")
            val closed = out.values.typedConstant(argument.type, argument.value)
            val value = StorageAccess.restore(
                out.type(argument.type),
                closed,
                "readonly_${parameter.name}",
                out.values.compilerTypes(closed)
            )
                ?: out.fail("Call '${spec.name}' readonly '${parameter.name}' has no restorable compiler value")
            result[parameter.name] = value
        }
        return result
    }

    /**
     * REF helpers are emitted as a stable call-site specialization: each REF parameter symbol is
     * replaced by the caller's already-captured location, including a live stack-frame offset.
     * No reference payload is copied into the helper frame and no copy-back is generated, so two
     * REF formals naming one address, and a REF formal aliasing a legal global capture, observe
     * the same writes in source order. VALUE parameters remain left-to-right one-shot copies into
     * the helper frame. Dynamic/cross-context addresses cannot be represented by a stable
     * specialization and are rejected before any helper body is installed.
     */
    private fun helperCall(spec: PlanFunction, target: Function, operation: PlanOperation.Call) {
        val binding = out.bindings.helper(spec.key)
            ?: out.fail("Helper '${spec.name}' has no captured compiler binding")
        if (binding.function !== target)
            out.fail("Helper '${spec.name}' no longer names its captured compiler function")
        if (operation.receiver != null)
            out.fail("Helper call '${spec.name}' cannot carry a receiver")
        if (spec.readonlyParameters.isNotEmpty() || operation.readonlyArguments.isNotEmpty())
            out.fail("Helper call '${spec.name}' cannot carry readonly arguments")
        if (target.normalParams.size != spec.parameters.size || operation.arguments.size != spec.parameters.size)
            out.fail("Helper call '${spec.name}' does not carry every declared parameter")
        if (binding.parameterOrder.size != spec.parameters.size)
            out.fail("Helper call '${spec.name}' parameter identity table differs from its signature")
        target.normalParams.zip(spec.parameters).forEach { (actual, planned) ->
            if (actual.identifier != planned.name)
                out.fail("Helper call '${spec.name}' parameter '${planned.name}' differs from its declaration")
            if (actual.type !is UnresolvedType &&
                actual.type !is MCFPPGenericParamType &&
                actual.type.typeId != out.bindings.compilerType(planned.type).typeId
            )
                out.fail("Helper call '${spec.name}' parameter '${planned.name}' has a foreign type")
        }
        val returnsValue = spec.resultType.kind != top.mcfpp.mni.api.TypeKind.VOID
        if ((operation.result != null) != returnsValue)
            out.fail("Helper call '${spec.name}' result slot does not match its complete signature")
        if (operation.result != null && !out.type(operation.result.type).hasRuntimeRepresentation)
            out.fail("Helper call '${spec.name}' has a compiler-only result that the stack-frame ABI cannot carry")
        if (target.returnType !is UnresolvedType &&
            target.returnType !is MCFPPGenericParamType &&
            target.returnType != MCFPPPrivateType.Void &&
            target.returnType.typeId != out.bindings.compilerType(spec.resultType).typeId
        )
            out.fail("Helper call '${spec.name}' return type differs from its declaration")

        val substitutions = linkedMapOf<SymbolId, CompilerPlanLocation>()
        val externalFrames = linkedMapOf<top.mcfpp.mni.api.ExecutionContextId, PlanExecutionFrame>()
        val externalExecutors = linkedMapOf<top.mcfpp.mni.api.ExecutionContextId, top.mcfpp.mni.api.ExecutionContextId>()
        spec.parameters.indices.forEach { index ->
            val parameter = spec.parameters[index]
            val symbol = binding.parameterOrder[index]
            val isRef = symbol in binding.refParameters
            if (isRef != (parameter.mode == ParamMode.REF))
                out.fail("Helper call '${spec.name}' parameter '${parameter.name}' lost its REF identity")
            if (!isRef) return@forEach
            val argument = operation.arguments[index]
            val reference = argument as? PlanArgument.Reference
                ?: out.fail("Helper REF parameter '${parameter.name}' requires an original captured address")
            val location = out.locations.get(reference.location)
            // A fixed address is representable in every executor. A context-sensitive one is
            // representable only while the call site still names the same executor; the helper
            // body's own nested contexts are checked again when the substituted root is used.
            location.executionContext?.let { context ->
                val frame = out.locationFrame(context)
                    ?: out.fail("Helper REF parameter '${parameter.name}' has an unrepresentable execution context")
                if (out.contextSensitive(location)) out.requireSameExecutor(
                    frame,
                    "Helper REF parameter '${parameter.name}'"
                )
                externalFrames[context] = frame
                externalExecutors[context] = out.executorBinding(frame)
                    ?: out.fail("Helper REF parameter '${parameter.name}' has no canonical executor binding")
            }
            if (!location.writable)
                out.fail("Helper REF parameter '${parameter.name}' does not name a writable original location")
            val address = location.address
            if (address != null && address.contains("\$("))
                out.fail("Helper REF parameter '${parameter.name}' has a dynamic address that cannot be specialized")
            if (address == null && location.layout !is StorageLayout.Scoreboard)
                out.fail("Helper REF parameter '${parameter.name}' has no stable physical address")
            if (location.type.key != parameter.type.key)
                out.fail("Helper REF parameter '${parameter.name}' address type differs from its declaration")
            substitutions[symbol] = location.withAddress(address?.let(::shiftFrame))
        }
        val callee = if (substitutions.isEmpty()) target
        else out.specializeHelper(binding, substitutions, externalFrames, externalExecutors, out.currentFrame())

        out.emit("data modify storage mcfpp:system stack_frame prepend value {}")
        spec.parameters.zip(operation.arguments).forEach { (parameter, argument) ->
            if (!out.type(parameter.type).hasRuntimeRepresentation)
                out.fail("Helper parameter '${parameter.name}' has no runtime representation")
            val destination = stackParameter(parameter.name)
            when (argument) {
                is PlanArgument.Value -> {
                    if (!out.type(argument.value.type).hasRuntimeRepresentation)
                        out.fail("Helper VALUE parameter '${parameter.name}' has no runtime representation")
                    val known = out.values.known(argument.value)
                    if (known != null && CompilerPlanValues.isNull(known)) out.emit("data remove $destination")
                    else out.emit("data modify $destination set from ${shiftFrame(out.values.slot(argument.value))}")
                }
                is PlanArgument.Reference -> {
                    if (parameter.mode != ParamMode.REF)
                        out.fail("Helper VALUE parameter '${parameter.name}' cannot receive a REF payload")
                    // The value is bound directly into the specialized body, never copied.
                }
            }
        }
        out.emit("function ${callee.namespaceID}")
        val resultSlot = operation.result
        if (resultSlot != null) {
            val result = binding.resultPath ?: out.fail("Helper '${spec.name}' has no captured result slot")
            out.emit("data modify ${shiftFrame(out.values.slot(resultSlot))} set from $result")
        }
        out.emit("data remove storage mcfpp:system stack_frame[0]")
        val caller = Function.currFunction
        caller.child.add(callee)
        callee.parent.add(caller)
    }

    private fun stackParameter(name: String): String = "storage mcfpp:system stack_frame[0].${top.mcfpp.analysis.StorageAccess.quotedKey(name)}"

    /**
     * A helper call prepends its own frame, so a caller workspace path that was emitted as
     * `stack_frame[0]` must address `stack_frame[1]` between prepend and remove. Helper parameter
     * and return slots deliberately keep index zero because they belong to the new frame.
     */
    private fun shiftFrame(path: String): String = Regex("^(storage \\S+ stack_frame\\[)(\\d+)(])").replace(path) {
        it.groupValues[1] + (it.groupValues[2].toInt() + 1) + it.groupValues[3]
    }

    /**
     * Native targets use their own readonly list rather than the generic one, carry a
     * captured receiver when their declaration has a non-void caller type, and enter the
     * complete native entry so no default or argument binding is evaluated a second time.
     */
    private fun nativeCall(spec: PlanFunction, target: NativeFunction, operation: PlanOperation.Call) {
        validateNativeTarget(spec, target, operation)
        val readonly = readonlyArguments(spec, operation)
        val normal = LinkedHashMap<String, Var<*>>()
        spec.parameters.zip(operation.arguments).forEach { (parameter, argument) ->
            normal[parameter.name] = argument(parameter, argument)
        }
        validateResultSlot(spec, operation)
        val needsReceiver = target.caller != MCFPPPrivateType.Void
        if (needsReceiver != (operation.receiver != null))
            out.fail("Native call '${spec.name}' receiver presence differs from its captured caller requirement")
        val receiver = operation.receiver?.let { reference ->
            val location = out.locations.get(reference.location)
            val callerType = target.caller
            if (callerType !is UnresolvedType &&
                callerType !is MCFPPGenericParamType &&
                callerType !is MCFPPNotCompiledGenericType &&
                callerType.typeId != out.bindings.compilerType(location.type).typeId
            )
                out.fail("Native call '${spec.name}' receiver type differs from its declared caller type")
            out.locations.memberReceiver(
                reference.location,
                "receiver_${spec.name.substringAfterLast(':').replace(Regex("[^A-Za-z0-9_]"), "_")}"
            )
        }
        val diagnostics = Project.errorCount
        val returned = try {
            target.invokeCompleteArguments(readonly, normal, receiver)
        } catch (failure: CodeModelException) {
            throw failure
        } catch (failure: IllegalArgumentException) {
            out.fail("Complete native call to '${spec.name}' is invalid: ${failure.message}")
        }
        if (Project.errorCount != diagnostics || returned.isError)
            out.fail("Complete native call to '${spec.name}' did not produce a valid runtime invocation")
        storeResult(spec, operation, returned)
        val caller = Function.currFunction
        caller.child.add(target)
        target.parent.add(caller)
    }

    private fun validateNativeTarget(spec: PlanFunction, target: NativeFunction, operation: PlanOperation.Call) {
        val targetReadonly = target.readOnlyParams
        if (targetReadonly.size != spec.readonlyParameters.size)
            out.fail("Native call '${spec.name}' readonly arity differs from the selected target signature")
        targetReadonly.zip(spec.readonlyParameters).forEach { (actual, planned) ->
            if (actual.identifier != planned.name)
                out.fail("Native call '${spec.name}' readonly parameter '${planned.name}' differs from the selected target")
            if (actual.type !is UnresolvedType &&
                actual.type !is MCFPPGenericParamType &&
                actual.type !is MCFPPNotCompiledGenericType &&
                actual.type.typeId != out.bindings.compilerType(planned.type).typeId
            )
                out.fail("Native call '${spec.name}' readonly parameter '${planned.name}' has a foreign prepared type")
        }
        if (target.normalParams.size != spec.parameters.size)
            out.fail("Native call '${spec.name}' normal arity differs from the selected target signature")
        target.normalParams.zip(spec.parameters).forEach { (actual, planned) ->
            if (actual.identifier != planned.name)
                out.fail("Native call '${spec.name}' normal parameter '${planned.name}' differs from the selected target")
            if (actual.type !is UnresolvedType &&
                actual.type !is MCFPPGenericParamType &&
                actual.type !is MCFPPNotCompiledGenericType &&
                actual.type.typeId != out.bindings.compilerType(planned.type).typeId
            )
                out.fail("Native call '${spec.name}' normal parameter '${planned.name}' has a foreign prepared type")
        }
        if (operation.arguments.size != spec.parameters.size || operation.readonlyArguments.size != spec.readonlyParameters.size)
            out.fail("Native call '${spec.name}' does not carry every complete argument")
        if (target.returnType !is UnresolvedType &&
            target.returnType !is MCFPPGenericParamType &&
            target.returnType !is MCFPPNotCompiledGenericType &&
            target.returnType != MCFPPPrivateType.Void &&
            target.returnType.typeId != out.bindings.compilerType(spec.resultType).typeId
        )
            out.fail("Native call '${spec.name}' return type differs from the selected target signature")
    }

    private fun argument(parameter: PlanParameter, argument: PlanArgument): Var<*> {
        val expected = out.type(parameter.type)
        if (expected == MCFPPPrivateType.Void)
            out.fail("Call argument '${parameter.name}' has void type")
        return when (argument) {
            is PlanArgument.Value -> {
                if (out.type(argument.value.type).typeId != expected.typeId)
                    out.fail("Call argument '${parameter.name}' has a different prepared value type")
                out.values.borrowAdapter(argument.value, "argument_${parameter.name}")?.let { return it }
                val closed = out.values.known(argument.value)
                val adapter = if (closed != null) {
                    StorageAccess.restore(expected, closed, "argument_${parameter.name}", out.values.compilerTypes(closed))
                        ?: out.fail("Call argument '${parameter.name}' has no restorable complete value")
                } else {
                    out.adapter(argument.value.type, "argument_${parameter.name}", out.values.path(argument.value))
                }
                out.values.attachReferences(argument.value, adapter)
            }
            is PlanArgument.Reference ->
                out.fail("Call argument '${parameter.name}' is a reference, but ordinary/native VALUE parameters require an explicit reference ABI")
        }
    }
}
