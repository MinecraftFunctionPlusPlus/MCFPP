@file:Suppress("UNUSED_PARAMETER")

package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.util.ValueWrapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class MniSignatureValidationTest {
    @Test fun namespaceAndCompoundAcceptEveryLegacyCallerAndResultShape() {
        for (compound in listOf(false, true)) {
            Project.init()
            val functions = inject(ValidNativeSignatures::class.java, compound)
            assertEquals(0, Project.errorCount)
            assertEquals(
                setOf(
                    "free", "result", "receiver", "receiverResult", "args", "argsResult",
                    "argsReceiver", "argsReceiverResult", "context"
                ),
                functions.keys
            )
            for ((name, overloads) in functions) {
                val function = assertIs<NativeFunction>(overloads.single())
                if (name.startsWith("args")) {
                    assertEquals(1, function.readOnlyParams.size)
                    assertEquals(1, function.normalParams.size)
                }
            }
        }
    }

    @Test fun invalidJvmContractsAreRejectedAtRegistrationInBothContainers() {
        for (compound in listOf(false, true)) {
            Project.init()
            val functions = inject(InvalidNativeSignatures::class.java, compound)
            assertEquals(6, Project.errorCount)
            assertEquals(setOf("valid"), functions.keys)
        }
    }

    @Test fun duplicateValidEntriesReportOneErrorAndKeepTheFirstEntry() {
        for (compound in listOf(false, true)) {
            Project.init()
            val functions = inject(DuplicateNativeSignatures::class.java, compound)
            assertEquals(1, Project.errorCount)
            val function = assertIs<NativeFunction>(functions.getValue("duplicate").single())
            assertEquals("first", function.javaMethod.name)
        }
    }

    @Test fun generatedBridgeDoesNotAddASecondDiagnosticForTheSameDeclaration() {
        assertEquals(1, BridgeNativeSignatures::class.java.declaredMethods.count { it.isBridge })
        for (compound in listOf(false, true)) {
            Project.init()
            val functions = inject(BridgeNativeSignatures::class.java, compound)
            assertEquals(1, Project.errorCount)
            assertFalse(functions.containsKey("bridge"))
        }
    }

    private fun inject(provider: Class<*>, compound: Boolean): Map<String, List<top.mcfpp.model.function.Function>> {
        return if (compound) {
            CompoundData("Fixture", "fixture.native").apply { injectedBy(provider) }.scope.functions
        } else {
            Namespace("fixture.native").apply { injectedBy(provider) }.scope.functions
        }
    }
}

class ValidNativeSignatures {
    companion object {
        @JvmStatic @MNIFunction
        fun free() {}
        @JvmStatic
        @MNIFunction(returnType = "int")
        fun result(result: ValueWrapper<MCInt>) {}
        @JvmStatic
        @MNIFunction(caller = "int")
        fun receiver(caller: MCInt) {}
        @JvmStatic
        @MNIFunction(caller = "int", returnType = "int")
        fun receiverResult(caller: MCInt, result: ValueWrapper<MCInt>) {}
        @JvmStatic
        @MNIFunction(readOnlyParams = ["int"], normalParams = ["int"])
        fun args(constant: MCInt, value: Var<*>) {}
        @JvmStatic
        @MNIFunction(readOnlyParams = ["int"], normalParams = ["int"], returnType = "int")
        fun argsResult(constant: MCInt, value: MCInt, result: ValueWrapper<MCInt>) {}
        @JvmStatic
        @MNIFunction(readOnlyParams = ["int"], normalParams = ["int"], caller = "int")
        fun argsReceiver(constant: MCInt, value: MCInt, caller: MCInt) {}
        @JvmStatic
        @MNIFunction(readOnlyParams = ["int"], normalParams = ["int"], caller = "int", returnType = "int")
        fun argsReceiverResult(constant: MCInt, value: MCInt, caller: MCInt, result: ValueWrapper<MCInt>) {}
        @JvmStatic
        @MNIFunction(readOnlyParams = ["int"], normalParams = ["int"], caller = "int", returnType = "int")
        fun context(context: NativeCallContext) {}
    }
}

class InvalidNativeSignatures {
    @MNIFunction fun instance() {}

    companion object {
        @JvmStatic @MNIFunction
        private fun hidden() {}
        @JvmStatic @MNIFunction
        fun wrongCount(extra: MCInt) {}
        @JvmStatic
        @MNIFunction(normalParams = ["int"])
        fun primitive(value: Int) {}
        @JvmStatic
        @MNIFunction(returnType = "int")
        fun wrongWrapper(result: MCInt) {}
        @JvmStatic @MNIFunction
        fun wrongReturn(): Int = 1
        @JvmStatic @MNIFunction
        fun valid() {}
    }
}

class DuplicateNativeSignatures {
    companion object {
        @JvmStatic
        @MNIFunction(identifier = "duplicate")
        fun first() {}
        @JvmStatic
        @MNIFunction(identifier = "duplicate")
        fun second(context: NativeCallContext) {}
    }
}

interface GenericNativeSignature<T> {
    fun bridge(value: T)
}

class BridgeNativeSignatures : GenericNativeSignature<Var<*>> {
    @MNIFunction(normalParams = ["any"])
    override fun bridge(value: Var<*>) {}
}
