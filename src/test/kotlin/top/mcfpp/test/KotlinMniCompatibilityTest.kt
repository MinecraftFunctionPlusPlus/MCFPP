package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.test.mnifixtures.JavaMniProvider
import top.mcfpp.test.mnifixtures.KotlinMniCompanionProvider
import top.mcfpp.test.mnifixtures.KotlinMniObjectProvider
import top.mcfpp.test.mnifixtures.KotlinMniObservations
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.util.ValueWrapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KotlinMniCompatibilityTest {
    private data class Provider(
        val namespace: Namespace,
        val calls: MutableList<String>,
        val values: Map<String, Int>
    )

    @Test fun topLevelObjectAndCompanionEntriesMatchJavaLegacyAbiAtRuntime() {
        Project.init()
        KotlinMniObservations.calls.clear()
        KotlinMniObservations.values.clear()
        val java = inject(JavaMniProvider::class.java, "fixture.mni.java", JavaMniProvider.calls, JavaMniProvider.values)
        val topLevel = inject(
            Class.forName("top.mcfpp.test.mnifixtures.KotlinMniProvidersKt"),
            "fixture.mni.kotlin.top",
            KotlinMniObservations.calls,
            KotlinMniObservations.values
        )
        val objectProvider = inject(
            KotlinMniObjectProvider::class.java,
            "fixture.mni.kotlin.object",
            KotlinMniObservations.calls,
            KotlinMniObservations.values
        )
        val companion = inject(
            KotlinMniCompanionProvider::class.java,
            "fixture.mni.kotlin.companion",
            KotlinMniObservations.calls,
            KotlinMniObservations.values
        )
        assertEquals(1, Project.errorCount, "Only the suspend fixture should be rejected")

        val body = Function("invoke", "fixture.mni.compatibility", null)
        val results = linkedMapOf<String, MCInt>()
        JavaMniProvider.calls.clear()
        JavaMniProvider.values.clear()
        KotlinMniObservations.calls.clear()
        KotlinMniObservations.values.clear()
        body.runInFunction {
            for (provider in listOf(java, topLevel, objectProvider, companion)) {
                val prefix = when (provider) {
                    java -> "java"
                    topLevel -> "kotlinTop"
                    objectProvider -> "kotlinObject"
                    else -> "kotlinCompanion"
                }
                val voidEntry = native(provider, "${prefix}Void")
                assertJvmAbi(voidEntry, emptyArray())
                assertFalse(voidEntry.invoke(emptyList(), emptyList(), null).isError, voidEntry.identifier)

                val argsEntry = native(provider, "${prefix}Args")
                assertJvmAbi(argsEntry, arrayOf(MCInt::class.java, Var::class.java))
                assertFalse(argsEntry.invoke(listOf(MCInt(5)), listOf(MCInt(6)), null).isError, argsEntry.identifier)

                val callerEntry = native(provider, "${prefix}Caller")
                assertJvmAbi(callerEntry, arrayOf(MCInt::class.java))
                assertFalse(callerEntry.invoke(emptyList(), emptyList(), MCInt(7)).isError, callerEntry.identifier)

                val resultEntry = native(provider, "${prefix}Result")
                assertJvmAbi(resultEntry, arrayOf(ValueWrapper::class.java))
                val simpleResult = resultEntry.invoke(emptyList(), emptyList(), null)
                assertFalse(simpleResult.isError, resultEntry.identifier)
                results[resultEntry.identifier] = assertIs<MCInt>(simpleResult)
                observeResult(resultEntry.identifier, simpleResult)

                val allEntry = native(provider, "${prefix}All")
                assertJvmAbi(allEntry, arrayOf(MCInt::class.java, MCInt::class.java, MCInt::class.java, ValueWrapper::class.java))
                val result = allEntry.invoke(listOf(MCInt(5)), listOf(MCInt(6)), MCInt(7))
                assertFalse(result.isError, allEntry.identifier)
                results[allEntry.identifier] = assertIs<MCInt>(result)
                observeResult(allEntry.identifier, result)
            }
        }

        val machine = execute(body)
        assertEquals(
            mapOf(
                "javaResult" to 7,
                "kotlinTopResult" to 7,
                "kotlinObjectResult" to 7,
                "kotlinCompanionResult" to 7,
                "javaAll" to 567,
                "kotlinTopAll" to 567,
                "kotlinObjectAll" to 567,
                "kotlinCompanionAll" to 567
            ),
            results.keys.associateWith { name ->
                assertIs<IntTag>(machine.readNbt("fixture:mni_observed", name)).value
            }
        )
        assertEquals(listOf("javaVoid", "javaArgs", "javaCaller", "javaResult", "javaAll"), JavaMniProvider.calls)
        assertEquals(
            listOf(
                "kotlinTopVoid", "kotlinTopArgs", "kotlinTopCaller", "kotlinTopResult", "kotlinTopAll",
                "kotlinObjectVoid", "kotlinObjectArgs", "kotlinObjectCaller", "kotlinObjectResult", "kotlinObjectAll",
                "kotlinCompanionVoid", "kotlinCompanionArgs", "kotlinCompanionCaller", "kotlinCompanionResult", "kotlinCompanionAll"
            ),
            KotlinMniObservations.calls
        )
        assertEquals(5, JavaMniProvider.values["javaArgs"])
        assertEquals(6, JavaMniProvider.values["javaNormal"])
        assertEquals(7, JavaMniProvider.values["javaCaller"])
        assertEquals(5, KotlinMniObservations.values["kotlinTopArgs"])
        assertEquals(6, KotlinMniObservations.values["kotlinTopNormal"])
        assertEquals(7, KotlinMniObservations.values["kotlinTopCaller"])
        assertEquals(5, KotlinMniObservations.values["kotlinObjectArgs"])
        assertEquals(6, KotlinMniObservations.values["kotlinObjectNormal"])
        assertEquals(7, KotlinMniObservations.values["kotlinObjectCaller"])
        assertEquals(5, KotlinMniObservations.values["kotlinCompanionArgs"])
        assertEquals(6, KotlinMniObservations.values["kotlinCompanionNormal"])
        assertEquals(7, KotlinMniObservations.values["kotlinCompanionCaller"])
        assertEquals(0, machine.stackDepth)
        assertEquals(1, Project.errorCount)
    }

    @Test fun kotlinDefaultArgumentsDoNotBecomeMcfppDefaultsAndSuspendEntriesAreRejected() {
        Project.init()
        KotlinMniObservations.calls.clear()
        KotlinMniObservations.values.clear()
        val provider = inject(
            KotlinMniCompanionProvider::class.java,
            "fixture.mni.kotlin.rules",
            KotlinMniObservations.calls,
            KotlinMniObservations.values
        )
        assertEquals(1, Project.errorCount, "The suspend entry must be rejected because its JVM ABI is not void/Var-wrapper ABI")
        assertFalse(provider.namespace.scope.functions.containsKey("kotlinSuspend"))
        val defaulted = native(provider, "kotlinDefaultArgument")
        assertJvmAbi(defaulted, arrayOf(MCInt::class.java))
        assertEquals(1, defaulted.normalParams.size)
        assertFalse(defaulted.normalParams.single().hasDefault)
        val suspendMethod = KotlinMniCompanionProvider::class.java.declaredMethods.single {
            it.getAnnotation(top.mcfpp.annotations.MNIFunction::class.java)?.identifier == "kotlinSuspend"
        }
        assertEquals(Any::class.java, suspendMethod.returnType)
        assertEquals(kotlin.coroutines.Continuation::class.java, suspendMethod.parameterTypes.last())

        val body = Function("defaultArgument", "fixture.mni.compatibility", null)
        lateinit var omittedResult: Var<*>
        body.runInFunction {
            val errors = Project.errorCount
            omittedResult = defaulted.invoke(emptyList(), emptyList(), null)
            assertTrue(omittedResult.isError)
            assertEquals(errors + 1, Project.errorCount)
        }
        assertFalse(
            KotlinMniObservations.values.containsKey("kotlinDefaultArgument"),
            "The Kotlin default value must not run when the MCFPP argument is omitted"
        )

        val explicitBody = Function("explicitDefaultArgument", "fixture.mni.compatibility", null)
        explicitBody.runInFunction {
            val result = defaulted.invoke(emptyList(), listOf(MCInt(5)), null)
            assertFalse(result.isError)
        }
        assertEquals(5, KotlinMniObservations.values["kotlinDefaultArgument"])
        assertEquals(2, Project.errorCount)
    }

    private fun inject(provider: Class<*>, namespaceName: String, calls: MutableList<String>, values: Map<String, Int>) = Provider(Namespace(namespaceName).apply { injectedBy(provider) }, calls, values)

    private fun native(provider: Provider, name: String): NativeFunction {
        val function = assertIs<NativeFunction>(provider.namespace.scope.functions.getValue(name).single())
        assertTrue(java.lang.reflect.Modifier.isPublic(function.javaMethod.modifiers))
        assertTrue(java.lang.reflect.Modifier.isStatic(function.javaMethod.modifiers))
        return function
    }

    private fun assertJvmAbi(function: NativeFunction, parameters: Array<Class<*>>) {
        assertEquals(parameters.toList(), function.javaMethod.parameterTypes.toList(), function.identifier)
        assertEquals(Void.TYPE, function.javaMethod.returnType, function.identifier)
    }

    private fun observeResult(name: String, result: Var<*>) {
        StorageAccess.materialize(result)
        val path = assertNotNull(result.storageBinding).path.pathToCommandPart().toString()
        Function.addCommand(Command("data modify storage fixture:mni_observed $name set from storage mcfpp:system $path"))
    }

    private fun execute(body: Function) = ScoreCommandExecutor(
        listOf("data modify storage mcfpp:system stack_frame prepend value {}") + body.commands.analyzeAll() +
            "data remove storage mcfpp:system stack_frame[0]"
    ).also { assertEquals(0, it.stackDepth) }
}
