package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.*
import kotlin.test.Test

class TemplateConstInitializationTest {
    private fun compile(source: String, errors: Int = 0): Function {
        val diagnostics = top.mcfpp.test.util.compilerDiagnostics {
            MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
        }
        assertEquals(errors, Project.errorCount, diagnostics.joinToString("\n"))
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
    }

    private fun objectTemplate(): ObjectDataTemplate = GlobalScope.localNamespaces.getValue("default.test").scope.objects
        .filterIsInstance<ObjectDataTemplate>().single { it.identifier == "Defaults" }

    private fun execute(main: Function): ScoreCommandExecutor {
        val functions = LinkedHashMap<String, List<String>>()
        fun collect(function: Function) {
            functions[function.namespaceID.toString()] = function.commands.analyzeAll()
            function.compiledFunctions.values.forEach(::collect)
        }
        (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values).forEach { namespace ->
            namespace.scope.functions.values.flatten().forEach(::collect)
            (namespace.scope.template.values + namespace.scope.objects.filterIsInstance<DataTemplate>()).forEach { template ->
                template.constructors.forEach(::collect)
                template.scope.forEachFunction(::collect)
                (template as? top.mcfpp.model.compound.GenericDataTemplate)?.compiledTemplates?.values?.forEach { actual ->
                    actual.constructors.forEach(::collect)
                    actual.scope.forEachFunction(::collect)
                }
            }
        }
        functions.putAll(Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) })
        // The executor models storage and scores; omit only load's fixed world bootstrap.
        val load = Project.projectLoad.commands.analyzeAll().filterNot {
            it.startsWith("scoreboard objectives add ") ||
                it == "execute unless score math_version mcfpp_init matches 315 run function math:_init" ||
                it == "scoreboard players set math_version mcfpp_init 315" ||
                it.startsWith("summon item 0 0 0 {Tags:[\"mcfpp_ptr_marker\"]") ||
                it.startsWith("summon marker 0 0 0 {Tags:[\"mcfpp_float_marker\"]")
        }
        functions[Project.projectLoad.namespaceID.toString()] = load
        return ScoreCommandExecutor(listOf("function ${Project.projectLoad.namespaceID}", "function ${main.namespaceID}"), functions)
            .also { assertEquals(0, it.stackDepth) }
    }

    @Test fun typedAndInferredObjectConstKeepSnapshotsAndRejectReassignment() {
        fun source(assignments: String) = """
            func produce(value: int): int {
                /say produced
                return value
            }
            object data Defaults {
                const typed: int = 4
                @DataOnly
                const inferred = 5
            }
            func main(){
                $assignments
                external result = Defaults.typed*10 + Defaults.inferred
            }
        """
        for (assignments in listOf("", "Defaults.typed = 99; Defaults.inferred = 99;")) {
            val main = compile(source(assignments), if (assignments.isEmpty()) 0 else 2)
            val defaults = objectTemplate()
            assertTrue(defaults.deferredFields.isEmpty())
            for (name in listOf("typed", "inferred")) {
                val field = defaults.scope.getVar(name)!!
                assertTrue(field.isConst)
                assertFalse(field.symbol!!.mutable)
                assertNotNull(top.mcfpp.analysis.StorageAccess.snapshot(field))
                if (name == "inferred") {
                    assertTrue((field as MCInt).isDataOnly)
                    assertTrue(field.annotations.any { it is top.mcfpp.mni.annotation.DataOnly })
                    assertEquals(1, field.annotations.count { it is top.mcfpp.mni.annotation.DataOnly })
                }
            }
            val constructor = defaults.constructors.single()
            assertTrue(constructor.bodyCompiled)
            val machine = execute(main)
            assertTrue(machine.messages.isEmpty())
            assertEquals(45, machine.read(main.scope.getVar("result") as MCInt))
            assertEquals(IntTag(5), machine.readNbt("mcfpp:system", defaults.nbtPath.memberIndex("inferred").pathToCommandPart().toString()))
        }
    }

    @Test fun declarationOrderSupportsEarlierFieldsAndRejectsPendingSelfOrForwardReads() {
        val main = compile("""
            object data Defaults {
                const first = 4
                const second = Defaults.first + 1
            }
            func main(){}
        """)
        val defaults = objectTemplate()
        assertEquals(listOf("first", "second"), defaults.preInit.keys.toList())
        val machine = execute(main)
        for ((name, value) in listOf("first" to 4, "second" to 5)) {
            assertEquals(IntTag(value), machine.readNbt("mcfpp:system", defaults.nbtPath.memberIndex(name).pathToCommandPart().toString()))
        }
        for (fields in listOf("const first = Defaults.first + 1;", "const first = Defaults.later + 1; const later = 4;")) {
            MCFPPStringTest.readFromString("object data Defaults {$fields}\nfunc main(){}", version = "26.3")
            assertTrue(Project.errorCount > 0)
        }
    }

    @Test fun runtimeReadonlyPropertiesInitializeFromEachIncomingReceiverCall() {
        for (assignment in listOf("", "first.value = 99;")) {
            val main = compile("""
                data Box {
                    var value: int { get }
                    new(initial: int){ return { value: initial } }
                }
                func main(){
                    var first = Box(1)
                    var second = Box(2)
                    $assignment
                    external result = first.value*10 + second.value
                }
            """, if (assignment.isEmpty()) 0 else 1)
            val box = GlobalScope.getTemplate("default.test", "Box")!!
            assertFalse(box.scope.getVar("value")!!.isConst)
            assertTrue(box.factories.single().compiledFunctions.isEmpty())
            assertEquals(12, execute(main).read(main.scope.getVar("result") as MCInt))
        }
    }

    @Test fun constFieldsAcceptConstantsAndRejectRuntimeInitializers() {
        val main = compile("""
            object data Defaults {
                const required: int = 3
                const mirrored = Defaults.required
            }
            data Box { new(value: int){ return {} } }
            func accept(value: int){ var accepted = Box(value); }
            func main(){ accept(3); }
        """)
        val defaults = objectTemplate()
        assertTrue(defaults.scope.getVar("required")!!.isConst)
        assertTrue(defaults.scope.getVar("mirrored")!!.isConst)
        val machine = execute(main)
        assertEquals(IntTag(3), machine.readNbt("mcfpp:system", defaults.nbtPath.memberIndex("required").pathToCommandPart().toString()))
        assertEquals(IntTag(3), machine.readNbt("mcfpp:system", defaults.nbtPath.memberIndex("mirrored").pathToCommandPart().toString()))
        MCFPPStringTest.readFromString("""
            func produce(value: int, valid: bool): int {
                /say produced
                if(valid){ return value; }
                return 0
            }
            object data Defaults { const required: int = produce(4, true); }
            func main(){}
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0)
    }
}
