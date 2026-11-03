@file:Suppress("UNUSED_PARAMETER")

package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.annotation.NoExternalWrites
import top.mcfpp.mni.annotation.WritesReceiver
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.ValueWrapper
import kotlin.test.*
import kotlin.test.Test

class MniLegacyInvocationTest {
    @Test fun invokesEveryLegacyShapeAndContextWithReadonlyNormalAndReceiverInOrder() {
        Project.init()
        val namespace = Namespace("fixture.legacy").apply { injectedBy(LegacyInvocationProvider::class.java) }
        assertEquals(0, Project.errorCount)
        val body = Function("invoke", "fixture.legacy", null)
        val outputs = linkedMapOf<String, MCInt>()
        LegacyInvocationProvider.calls.clear()
        body.runInFunction {
            val input = MCInt("input").apply { bindDeclaration(forceRuntime = true) }
            input.assignedBy(MCInt(3))
            val receiver = MCInt("receiver").apply { bindDeclaration(forceRuntime = true) }
            receiver.assignedBy(MCInt(4))
            for (name in listOf(
                "free", "result", "receiver", "receiverResult", "args", "argsResult",
                "argsReceiver", "argsReceiverResult", "context"
            )) {
                val native = assertIs<NativeFunction>(namespace.scope.functions.getValue(name).single())
                val result = native.invoke(
                    if (native.readOnlyParams.isEmpty()) emptyList() else listOf(MCInt(5)),
                    if (native.normalParams.isEmpty()) emptyList() else listOf(input),
                    if (native.caller == MCFPPPrivateType.Void) null else receiver
                )
                assertFalse(result.isError, name)
                if (native.returnType != MCFPPPrivateType.Void) {
                    outputs[name] = assertIs<MCInt>(result)
                    StorageAccess.materialize(result)
                }
            }
        }
        assertEquals(0, Project.errorCount)
        assertEquals(
            listOf(
                "free", "result", "receiver", "receiverResult", "args", "argsResult",
                "argsReceiver", "argsReceiverResult", "context"
            ),
            LegacyInvocationProvider.calls
        )
        val machine = execute(body)
        assertEquals(
            mapOf(
                "result" to 7,
                "receiverResult" to 5,
                "argsResult" to 8,
                "argsReceiverResult" to 12,
                "context" to 12
            ),
            outputs.mapValues { machine.read(it.value) }
        )
    }

    @Test fun receiverWritesUseTheOriginalStorageAndWrapperPropertyMethodsRemainBinaryCompatible() {
        Project.init()
        val body = Function("receiver", "fixture.legacy", null)
        lateinit var receiver: MCInt
        body.runInFunction {
            receiver = MCInt("actual").apply { bindDeclaration(forceRuntime = true) }
            receiver.assignedBy(MCInt(4))
            val native = NativeFunction("mutate", javaMethod = LegacyReceiverInvocationProvider::class.java.getMethod("mutate", MCInt::class.java))
                .apply { caller = top.mcfpp.type.MCFPPBaseType.Int }
            assertFalse(native.invoke(emptyList(), emptyList(), receiver).isError)
        }
        assertEquals(0, Project.errorCount)
        assertEquals(9, execute(body).read(receiver))
        val wrapper = ValueWrapper(MCInt(1))
        val type = ValueWrapper::class.java
        assertEquals(1, (type.getMethod("getValue").invoke(wrapper) as MCInt).value)
        type.getMethod("setValue", Var::class.java).invoke(wrapper, MCInt(2))
        assertEquals(2, wrapper.get().value)
        assertNotNull(type.getConstructor(Var::class.java))
    }

    @Test fun unpublishedWrongTypeThrownAndDiagnosedResultsNeverEscapeAsSuccess() {
        Project.init()
        val body = Function("failures", "fixture.legacy", null)
        body.runInFunction {
            for (name in listOf("unpublished", "wrongResult", "failed", "thrown")) {
                val native = NativeFunction(name, javaMethod = LegacyInvocationProvider::class.java.getMethod(name, ValueWrapper::class.java))
                    .apply { returnType = top.mcfpp.type.MCFPPBaseType.Int }
                val errors = Project.errorCount
                val commands = body.commands.size
                val result = native.invoke(emptyList(), emptyList(), null)
                assertTrue(result.isError, name)
                assertNull(StorageAccess.snapshot(result), name)
                assertEquals(errors + 1, Project.errorCount, name)
                assertTrue(body.commands.drop(commands).all { it.toString().startsWith("#") }, name)
            }
        }
    }

    private fun execute(body: Function) = ScoreCommandExecutor(
        listOf("data modify storage mcfpp:system stack_frame prepend value {}") + body.commands.analyzeAll()
    )
}

@NoExternalWrites
object LegacyInvocationProvider {
    val calls = arrayListOf<String>()
    @JvmStatic @MNIFunction
    fun free() {
        calls.add("free")
    }
    @JvmStatic
    @MNIFunction(returnType = "int")
    fun result(result: ValueWrapper<MCInt>) {
        calls.add("result")
        result.assign(MCInt(7))
    }
    @JvmStatic
    @MNIFunction(caller = "int")
    fun receiver(caller: MCInt) {
        calls.add("receiver")
    }
    @JvmStatic
    @MNIFunction(caller = "int", returnType = "int")
    fun receiverResult(caller: MCInt, result: ValueWrapper<MCInt>) {
        calls.add("receiverResult")
        result.set(caller.plus(MCInt(1)) as MCInt)
    }
    @JvmStatic
    @MNIFunction(readOnlyParams = ["int"], normalParams = ["int"])
    fun args(constant: MCInt, value: MCInt) {
        calls.add("args")
        assertEquals(5, constant.value)
    }
    @JvmStatic
    @MNIFunction(readOnlyParams = ["int"], normalParams = ["int"], returnType = "int")
    fun argsResult(constant: MCInt, value: MCInt, result: ValueWrapper<MCInt>) {
        calls.add("argsResult")
        result.value = constant.plus(value) as MCInt
    }
    @JvmStatic
    @MNIFunction(readOnlyParams = ["int"], normalParams = ["int"], caller = "int")
    fun argsReceiver(constant: MCInt, value: MCInt, caller: MCInt) {
        calls.add("argsReceiver")
        assertEquals(5, constant.value)
    }
    @JvmStatic
    @MNIFunction(readOnlyParams = ["int"], normalParams = ["int"], caller = "int", returnType = "int")
    fun argsReceiverResult(constant: MCInt, value: MCInt, caller: MCInt, result: ValueWrapper<MCInt>) {
        calls.add("argsReceiverResult")
        result.get().assignedBy((constant.plus(value) as MCInt).plus(caller))
    }
    @JvmStatic
    @MNIFunction(readOnlyParams = ["int"], normalParams = ["int"], caller = "int", returnType = "int")
    fun context(context: NativeCallContext) {
        calls.add("context")
        context.withAdapters { caller, arguments ->
            context.publishResult(((arguments[0] as MCInt).plus(arguments[1]) as MCInt).plus(caller))
        }
    }
    @JvmStatic fun unpublished(result: ValueWrapper<MCInt>) {}
    @JvmStatic fun wrongResult(result: ValueWrapper<Var<*>>) {
        result.set(ScoreBool(true))
    }
    @JvmStatic fun failed(result: ValueWrapper<MCInt>) {
        result.set(MCInt(7))
        LogProcessor.error("Legacy native rejected input")
    }
    @JvmStatic fun thrown(result: ValueWrapper<MCInt>) {
        result.set(MCInt(7))
        error("Legacy native failed")
    }
}

object LegacyReceiverInvocationProvider {
    @JvmStatic @WritesReceiver
    fun mutate(caller: MCInt) {
        caller.assignedBy(caller.plus(MCInt(5)))
    }
}
