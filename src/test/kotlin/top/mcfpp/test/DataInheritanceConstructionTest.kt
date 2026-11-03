package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.Effect
import top.mcfpp.analysis.EffectAnalysis
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Pos3Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.test.util.compilerDiagnostics
import top.mcfpp.test.util.exportDatapackFunctions
import java.nio.file.Files
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DataInheritanceConstructionTest {
    @Test fun parentBodyUpdatesTheReceiverBeforeTheChildInitializerAndBody() {
        for ((child, expected) in listOf("" to 7, "var x:int=9;" to 9)) {
            val main = compile(
                """
                data Parent{var x:int=1;constructor(){this.x=7;
                    /say parent
                }}
                data Child:Parent{$child constructor(){
                    /say child
                }}
                func main(){var value=Child();external result=value.x;}
            """
            )
            val machine = execute(main)
            assertEquals(expected, machine.read(assertIs<MCInt>(main.scope.getVar("result"))))
            assertEquals(listOf("parent", "child"), machine.messages)
            assertSharedStages(template("Child").constructors.single(), template("Parent"))
        }
    }

    @Test fun aLocalDeclarationWithoutAnInitializerPreservesTheParentBodyValue() {
        val main = compile(
            """
            data Parent{var x:int=1;constructor(){this.x=7;}}
            data Child:Parent{var x:int;}
            func main(){var value=Child();external result=value.x;}
        """
        )
        assertEquals(7, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
    }

    @Test fun ancestorInitializersExecuteEvenWhenTheChildHasAnExplicitOverride() {
        val main = compile(
            """
            func initial(value:int):int{
                /say initial
                return value;
            }
            data Parent{var x:int=initial(1);var observed:int=0;constructor(){this.observed=this.x;this.x=7;}}
            data Child:Parent{var x:int=9;}
            func main(){var value=Child();external result=value.observed*10+value.x;}
        """
        )
        val machine = execute(main)
        assertEquals(19, machine.read(assertIs<MCInt>(main.scope.getVar("result"))))
        assertEquals(listOf("initial"), machine.messages)
    }

    @Test fun parentDefaultsAndLocalVariablesCannotOverwriteTheChildParameterFrame() {
        val main = compile(
            """
            data Parent{var x:int=1;constructor(value:int=7){var seed=value;this.x=seed;}}
            data Child:Parent{var x:int=seed;constructor(seed:int){}}
            func main(){var first=Child(9);var second=Child(4);external result=first.x*10+second.x;}
        """
        )
        assertEquals(94, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
        val parentStage = template("Child").constructors.single().child.single { it.owner === template("Parent") }
        assertEquals(1, parentStage.normalParams.size)
        assertEquals("value", parentStage.normalParams.single().identifier)
    }

    @Test fun implicitAndAbstractAncestorsInitializeFieldsBeforeTheConcreteBody() {
        val main = compile(
            """
            abstract data Parent{var x:int=3;}
            abstract data Middle:Parent{var seen:int=this.x;}
            data Child:Middle{constructor(){this.x=this.x+4;}}
            func main(){var value=Child();external result=value.seen*10+value.x;}
        """
        )
        assertEquals(37, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
        assertTrue(template("Parent").constructors.isEmpty())
        assertTrue(template("Middle").constructors.isEmpty())
    }

    @Test fun canonicalDiamondsExecuteEveryOwnerOnceInStableOrder() {
        for (parents in listOf("Left,Right", "Right,Left")) {
            val main = compile(
                """
                data Base<T>{var x:int=1;constructor(){this.x=this.x+6;
                    /say base
                }}
                data Left:Base<int>{constructor(){
                    /say left
                }}
                data Right:Base<int>{constructor(){
                    /say right
                }}
                data Child:$parents{constructor(){
                    /say child
                }}
                func main(){var first=Child();var second=Child();external result=first.x*10+second.x;}
            """
            )
            val machine = execute(main)
            assertEquals(77, machine.read(assertIs<MCInt>(main.scope.getVar("result"))))
            val order = if (parents.startsWith("Left")) listOf("base", "left", "right", "child")
            else listOf("base", "right", "left", "child")
            assertEquals(order + order, machine.messages)
        }
    }

    @Test fun differentGenericAncestorBindingsExecuteDistinctStages() {
        val main = compile(
            """
            data Base<T>{constructor(){
                /say base
            }}
            data Child:Base<int>,Base<bool>{constructor(){
                /say child
            }}
            func main(){var value=Child();}
        """
        )
        assertEquals(listOf("base", "base", "child"), execute(main).messages)
        val stages = template("Child").constructors.single().child.filter { it.owner?.namespace == "default.test" }
        assertEquals(2, stages.size)
        assertEquals(2, stages.map { it.owner!!.inheritanceIdentity }.distinct().size)
    }

    @Test fun returningFromAParentBodyDoesNotReturnFromTheChildConstruction() {
        val main = compile(
            """
            data Parent{var x:int=1;constructor(){this.x=7;
                /say parent
                return;
                /say unreachable
            }}
            data Child:Parent{var x:int=9;constructor(){
                /say child
            }}
            func main(){var value=Child();external result=value.x;}
        """
        )
        val machine = execute(main)
        assertEquals(9, machine.read(assertIs<MCInt>(main.scope.getVar("result"))))
        assertEquals(listOf("parent", "child"), machine.messages)
    }

    @Test fun eachConstDeclarationCanInitializeButBodiesAndOrdinaryMembersCannotReassign() {
        for ((childDeclaration, expected) in listOf("const x:int=8;" to 81, "const x:int;" to 11)) {
            val main = compile(
                """
                data Parent{const x:int=1;var seen:int=0;constructor(){this.seen=this.x;}}
                data Child:Parent{$childDeclaration}
                func main(){var value=Child();external result=value.x*10+value.seen;}
            """
            )
            assertEquals(expected, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
            assertTrue(template("Child").scope.getVar("x")!!.isConst)
        }
        for ((declarations, call) in listOf(
            "data Parent{const x:int=1;constructor(){this.x=7;}}\ndata Child:Parent{}" to "",
            "data Parent{const x:int=1;}\ndata Child:Parent{const x:int=8;func illegal(){this.x=7;}}" to "value.illegal();"
        )) {
            val messages = compilerDiagnostics {
                compile("$declarations\nfunc main(){var value=Child();$call}", valid = false)
            }
            assertTrue(messages.any { "constant" in it.lowercase() || "const" in it.lowercase() }, messages.joinToString("\n"))
            assertTrue(template("Parent").scope.getVar("x")!!.isConst)
            assertTrue(template("Child").scope.getVar("x")!!.isConst)
        }
    }

    @Test fun aFirstConstAssignmentInTheParentBodyRemainsLegalBeforeTheChildInitializer() {
        val main = compile(
            """
            data Parent{const x:int;constructor(){this.x=7;}}
            data Child:Parent{const x:int=8;}
            func main(){var value=Child();external result=value.x;}
        """
        )
        assertEquals(8, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
    }

    @Test fun compilerOnlyPartialReceiversShareStorageThroughParentPrivateCallsAndChildInitialization() {
        val main = compile(
            """
            func observe(value:pos3):int=top.mcfpp.test.DataInheritanceConstructionTest.observe
            data Parent{
                var position:pos3=1 0 0
                private func initialize(){this.position=7 0 0;}
                constructor(){this.initialize();
                    /say parent
                }
            }
            data Child:Parent{var position:pos3=9 0 0;var late:pos3=3 0 0;}
            func main(){var value=Child();external first=observe(value.position);external late=observe(value.late);}
        """
        )
        val value = assertIs<DataTemplateObject>(main.scope.getVar("value"))
        assertFalse(StorageAccess.hasRuntimeRepresentation(value))
        assertTrue(StorageAccess.hasActualPayload(value))
        val machine = execute(main)
        assertEquals(9, machine.read(assertIs<MCInt>(main.scope.getVar("first"))))
        assertEquals(3, machine.read(assertIs<MCInt>(main.scope.getVar("late"))))
        assertEquals(1, machine.messages.count { it == "parent" })
        val root = template("Child").scope.functions.values.flatten().single {
            it.identifier.startsWith("_init_child_0-call-") && it.constructedReceiver != null
        }
        assertSharedStages(root, template("Parent"))
        val parentStage = root.child.single { it.owner === template("Parent") }
        val privateBody = template("Parent").scope.functions.values.flatten().single {
            it.identifier.startsWith("initialize-call-") && it.constructedReceiver != null
        }
        assertSame(root.constructedReceiver!!.storageBinding!!.data, privateBody.constructedReceiver!!.storageBinding!!.data)
        assertEquals(parentStage.constructedReceiver!!.storageBinding!!.place, privateBody.constructedReceiver!!.storageBinding!!.place)
    }

    @Test fun ancestorInitializersAndBodiesUseTheirDeclarationImportsAndPrivateAccess() {
        files(
            listOf(
                "namespace fixture.child\nimport fixture.parent:Parent;\ndata Child:Parent{}\nfunc number():int{return 99;}\nfunc main(){var value=Child();external result=value.x;}",
                "namespace fixture.parent\nimport fixture.helper:*;\ndata Parent{var x:int=number();private func source():int{return number();}constructor(){this.x=this.source();}}",
                "namespace fixture.helper\nfunc number():int{return 7;}"
            )
        ) { main ->
            assertEquals(7, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
            val child = GlobalScope.localNamespaces.getValue("fixture.child").scope.template.getValue("Child")
            val parent = GlobalScope.localNamespaces.getValue("fixture.parent").scope.template.getValue("Parent")
            val stage = child.constructors.single().child.single { it.owner === parent }
            assertSame(parent.declarationFile, stage.declarationFile)
        }
    }

    @Test fun publishedDatapackContainsEveryReachableParentAndImportedConstructorTarget() {
        val output = Files.createTempDirectory("mcfpp-parent-published-")
        val ignore = CompileSettings.ignoreStdLib
        try {
            CompileSettings.ignoreStdLib = false
            val main = compile(
                """
                import mcfpp.minecraft.other:*
                data Parent{var x:int=1;constructor(){this.x=7;
                    /say parent
                }}
                data Child:Parent{var x:int=9;}
                func main(){var value=Child();var slot=Slot(3,SlotType.container);external result=value.x*10+slot.index;}
            """
            )
            val functions = exportDatapackFunctions(output)
            val reachable = assertPublishedTargets(main, functions)
            assertTrue(reachable.any { "parent/_constructor_stage_" in it })
            assertTrue(reachable.any { "slot/_init_" in it }, "must consume a bundled library constructor")
            assertTrue(reachable.none { "data_object/_constructor_stage_" in it }, "empty implicit ancestors need no call")
            val machine = ScoreCommandExecutor(functions.getValue(main.namespaceID.toString()), functions)
            assertEquals(93, machine.read(assertIs<MCInt>(main.scope.getVar("result"))))
            assertEquals(listOf("parent"), machine.messages)
            assertEquals(0, machine.stackDepth)
        } finally {
            CompileSettings.ignoreStdLib = ignore
            output.toFile().deleteRecursively()
        }
    }

    @Test fun publishedGenericDiamondsRunOneCanonicalOwnerStageInEitherParentOrder() {
        for (parents in listOf("Left,Right", "Right,Left")) {
            val output = Files.createTempDirectory("mcfpp-generic-diamond-published-")
            try {
                val main = compile(
                    """
                    data Base<T>{var x:int=1;private func seed():int{return 7;}constructor(){this.x=this.seed();
                        /say base
                    }}
                    data Left:Base<int>{}
                    data Right:Base<int>{}
                    data Child:$parents{var x:int=9;}
                    func main(){var first=Child();var second=Child();external result=first.x*10+second.x;}
                """
                )
                val functions = exportDatapackFunctions(output)
                val reachable = assertPublishedTargets(main, functions)
                val child = GlobalScope.localNamespaces.getValue("default.test").scope.template.getValue("Child")
                val baseOwners = child.constructors.single().initializationPlan().plan!!.ancestors
                    .map { it.owner }
                    .filterIsInstance<CompiledGenericDataTemplate>()
                    .filter { it.originTemplate.identifier == "Base" }
                    .distinctBy { it.inheritanceIdentity }
                assertEquals(1, baseOwners.size, "The Child plan must bind one canonical Base<int> owner")
                val baseOwner = baseOwners.single()
                val baseStageFunctions = baseOwner.scope.functions.values.flatten().filter {
                    it.identifier.startsWith("_constructor_stage_") &&
                        it.owner?.inheritanceIdentity == baseOwner.inheritanceIdentity
                }
                val stages = reachable.intersect(baseStageFunctions.map { it.namespaceID.toString() }.toSet())
                assertEquals(1, stages.size, "The shared Base<int> owner must emit one stage")
                val reachableBaseStage = baseStageFunctions.single { it.namespaceID.toString() == stages.single() }
                assertEquals(
                    baseOwner.inheritanceIdentity,
                    reachableBaseStage.owner?.inheritanceIdentity,
                    "The reachable stage must retain the canonical generic owner"
                )
                val machine = ScoreCommandExecutor(functions.getValue(main.namespaceID.toString()), functions)
                assertEquals(99, machine.read(assertIs<MCInt>(main.scope.getVar("result"))))
                assertEquals(listOf("base", "base"), machine.messages)
                assertEquals(0, machine.stackDepth)
            } finally {
                output.toFile().deleteRecursively()
            }
        }
    }

    @Test fun publishedDifferentGenericBindingsRetainDistinctOwnerStageTargets() {
        val output = Files.createTempDirectory("mcfpp-distinct-generic-published-")
        try {
            val main = compile(
                """
                data Base<T>{constructor(){
                    /say base
                }}
                data Child:Base<int>,Base<bool>{var x:int=9;}
                func main(){var value=Child();external result=value.x;}
            """
            )
            val functions = exportDatapackFunctions(output)
            val reachable = assertPublishedTargets(main, functions)
            val stages = reachable.filter { "/_constructor_stage_" in it }
            assertEquals(2, stages.size)
            assertEquals(2, stages.map { it.substringBefore("/_constructor_stage_") }.distinct().size)
            val machine = ScoreCommandExecutor(functions.getValue(main.namespaceID.toString()), functions)
            assertEquals(9, machine.read(assertIs<MCInt>(main.scope.getVar("result"))))
            assertEquals(listOf("base", "base"), machine.messages)
            assertEquals(0, machine.stackDepth)
        } finally {
            output.toFile().deleteRecursively()
        }
    }

    @Test fun failingStagePreparationRestoresFunctionFileAndEffectContext() {
        Project.init()
        val outer = Function("outer", "fixture", null)
        val owner = DataTemplate("Owner", "fixture").apply { declarationFile = null }
        val receiver = DataTemplateObject(owner, "receiver")
        val beforeFunction = Function.currFunction
        val beforeFile = MCFPPFile.currFile
        outer.runInFunction {
            EffectAnalysis.withActualEffects(outer) {
                StorageAccess.initializeTemplateReceiver(receiver)
                assertFailsWith<IllegalStateException> {
                    outer.emitConstructorStage(owner, null, receiver) { _, _ -> error("stage sentinel") }
                }
                assertSame(outer, Function.currFunction)
                assertSame(beforeFile, MCFPPFile.currFile)
                EffectAnalysis.recordEffect(Effect.ReadsRuntime)
                outer.bodyCompiled = true
            }
        }
        assertSame(beforeFunction, Function.currFunction)
        assertEquals(Effect.ReadsRuntime, outer.runtimeEffect)
        assertTrue(outer.commands.analyzeAll().none { it.startsWith("function ") })
    }

    private fun assertSharedStages(root: Function, parent: DataTemplate) {
        val receiver = assertNotNull(root.constructedReceiver)
        val stage = root.child.single { it.owner === parent }
        val alias = assertNotNull(stage.constructedReceiver)
        assertSame(receiver.storageBinding!!.data, alias.storageBinding!!.data)
        assertEquals(receiver.storageBinding!!.place, alias.storageBinding!!.place)
        assertEquals(receiver.storageBinding!!.location.frameOffset + 1, alias.storageBinding!!.location.frameOffset)
    }

    private fun compile(source: String, valid: Boolean = true): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
        if (valid) assertEquals(0, Project.errorCount) else assertTrue(Project.errorCount > 0)
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
    }

    private fun template(name: String) = GlobalScope.localNamespaces.getValue("default.test").scope.template.getValue(name)

    private fun execute(main: Function): ScoreCommandExecutor {
        val commands = linkedMapOf<String, List<String>>()
        val seen = Collections.newSetFromMap(IdentityHashMap<Function, Boolean>())
        fun collect(function: Function) {
            if (!seen.add(function)) return
            commands[function.namespaceID.toString()] = function.commands.analyzeAll()
            function.compiledFunctions.values.forEach(::collect)
            function.child.forEach(::collect)
        }
        GlobalScope.localNamespaces.values.forEach { namespace ->
            namespace.scope.functions.values.flatten().forEach(::collect)
            (namespace.scope.template.values).forEach { template ->
                template.constructors.forEach(::collect)
                template.scope.functions.values.flatten().forEach(::collect)
            }
        }
        commands.putAll(Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) })
        return ScoreCommandExecutor(main.commands.analyzeAll(), commands).also { assertEquals(0, it.stackDepth) }
    }

    private fun assertPublishedTargets(main: Function, functions: Map<String, List<String>>): Set<String> {
        val pending = ArrayDeque<String>()
        val reachable = linkedSetOf<String>()
        pending.add(main.namespaceID.toString())
        val call = Regex("""(?:^|\s)function\s+([a-z0-9_.-]+:[a-z0-9_./-]+)""")
        while (pending.isNotEmpty()) {
            val target = pending.removeFirst()
            if (!reachable.add(target)) continue
            val commands = assertNotNull(functions[target], "Missing published function $target")
            commands.filterNot { it.startsWith("#") }.forEach { command ->
                call.findAll(command).forEach { pending.add(it.groupValues[1]) }
            }
        }
        return reachable
    }

    private fun files(sources: List<String>, check: (Function) -> Unit) {
        val root = Files.createTempDirectory("mcfpp-parent-construction-")
        val config = Project.config
        val ignore = CompileSettings.ignoreStdLib
        try {
            sources.forEachIndexed { index, source -> Files.writeString(root.resolve("%02d.mcfpp".format(index)), source) }
            CompileSettings.ignoreStdLib = true
            Project.config = ProjectConfig(version = "26.3", sourcePath = root, rootNamespace = "fixture")
            val messages = compilerDiagnostics {
                Project.init()
                Project.readProject()
                Project.indexType()
                Project.resolveField()
                Project.runAnnotation()
                Project.compile()
                Project.optimization()
            }
            assertEquals(0, Project.errorCount, messages.joinToString("\n"))
            check(GlobalScope.localNamespaces.getValue("fixture.child").scope.functions.getValue("main").single())
        } finally {
            Project.config = config
            CompileSettings.ignoreStdLib = ignore
            root.toFile().deleteRecursively()
        }
    }

    companion object {
        @JvmStatic
        @MNIFunction(normalParams = ["pos3"], returnType = "int")
        fun observe(context: NativeCallContext) = context.withArguments { arguments ->
            val position = arguments.single() as Pos3Var
            Function.addCommand(Command("say").build(position.toCommandPart()))
            context.publishResult(MCInt(position.x.number.toInt()))
        }
    }
}
