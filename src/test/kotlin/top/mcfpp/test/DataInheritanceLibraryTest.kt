package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.OnScoreboard
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.library.format.LibraryWire
import top.mcfpp.io.LibBinWriter
import top.mcfpp.io.info.DataTemplateInfo
import top.mcfpp.io.info.GenericDataTemplateInfo
import top.mcfpp.io.info.LibraryMetadata
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.test.util.compilerDiagnostics
import top.mcfpp.test.util.executePublishedFunctions
import top.mcfpp.type.MCFPPDataTemplateType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

/** Execute the same contract against source models and a separately reconstructed .mclib. */
class DataInheritanceLibraryTest {
    @Test fun parentFieldConflictsAreRejectedInSourceAndFreshLibrariesInEitherOrder() {
        for (parents in listOf("A,B", "B,A")) {
            for (local in listOf("", "var x:int=9;")) {
                rejectsInBoth(
                    "data A{var x:int=1;}\ndata B{var x:bool=true;}",
                    "data Child:$parents{$local}\nfunc main(){}",
                    "Incompatible inherited field 'x'",
                    "required field type is never",
                    "name=A",
                    "name=B"
                )
            }
        }
    }

    @Test fun compatibleParentFieldsMergeIntoOneSlotInSourceAndFreshLibrariesInEitherOrder() {
        for (parents in listOf("A,B", "B,A")) {
            inBoth(
                "data A{var x:int=1;}\ndata B{var x:int=2;}\ndata Child:$parents{new(){return {x:3}}}",
                "func main(){var child=Child();external result=child.x;}"
            ) { main, loaded ->
                val child = template("Child", loaded)
                assertEquals(1, child.scope.allVars.count { it.identifier == "x" })
                assertEquals(2, child.scope.fieldOrigins.getValue("x").size)
                assertEquals(3, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
            }
        }
    }

    @Test fun explicitLocalMergesKeepTheirOwnSlotAndInitializerInEitherParentOrder() {
        for (parents in listOf("A,B", "B,A")) {
            inBoth(
                """
                data A{var x:int=1;}
                data B{var x:int=2;}
                data Child:$parents{var x:int=9;}
            """,
                "func main(){var child=Child();external result=child.x;}"
            ) { main, loaded ->
                val child = template("Child", loaded)
                assertSame(child, child.scope.getVar("x")!!.declaredParentTemplate)
                assertSame(child, child.scope.getProperty("x")!!.declaredParentTemplate)
                assertEquals(1, child.scope.allVars.count { it.identifier == "x" })
                assertEquals(9, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
            }
        }
    }

    @Test fun diamondFactoriesKeepMethodsLocalAndInvokeOnlyTheSelectedFactory() {
        for (parents in listOf("Left,Right", "Right,Left")) {
            inBoth(
                """
                data Root{var x:int=1;new(){
                    /say root
                    return {x:7}
                }func read():int{return 1;}}
                data Left:Root{new(){
                    /say left
                    return {x:7}
                }}
                data Right:Root{new(){
                    /say right
                    return {x:8}
                }func read():int{return 2;}}
                data Child:$parents{var x:int=9;new(){
                    /say child
                    return {x:9}
                }}
            """,
                "func main(){var child=Child();external result=child.x;}"
            ) { main, loaded ->
                assertFalse(template("Child", loaded).scope.functions.containsKey("read"))
                assertSame(template("Right", loaded), template("Right", loaded).scope.functions.getValue("read").single().owner)
                val machine = execute(main)
                assertEquals(9, machine.read(assertIs<MCInt>(main.scope.getVar("result"))))
                assertEquals(listOf("child"), machine.messages)
            }
        }
    }

    @Test fun genericDiamondsDeduplicateCanonicalFieldsAndUseExplicitParentFactoryCalls() {
        for (parents in listOf("Left,Right", "Right,Left")) {
            inBoth(
                """
                data Root<T>{var x:int=1;new(){
                    /say root
                    return {x:7}
                }}
                data Left:Root<int>{}
                data Right:Root<int>{}
                data Child:$parents{new(){return Root<int>()}}
            """,
                "func main(){var child=Child();external result=child.x;}"
            ) { main, loaded ->
                val child = template("Child", loaded)
                val left = template("Left", loaded).parent.filterIsInstance<CompiledGenericDataTemplate>().single()
                val right = template("Right", loaded).parent.filterIsInstance<CompiledGenericDataTemplate>().single()
                assertSame(left, right, "The two edges must reach one canonical Root<int> specialization")
                assertEquals(1, child.scope.fieldOrigins.getValue("x").size)
                assertSame(left, child.scope.getVar("x")!!.declaredParentTemplate)
                val machine = execute(main)
                assertEquals(7, machine.read(assertIs<MCInt>(main.scope.getVar("result"))))
                assertEquals(listOf("root"), machine.messages)
            }
        }
    }

    @Test fun compatibleGenericBindingsMergeFieldsAndKeepTheirDistinctOrigins() {
        for (parents in listOf("Root<int>,Root<bool>", "Root<bool>,Root<int>")) {
            inBoth(
                "data Root<T>{var x:int=1;}\ndata Child:$parents{new(){return {x:6}}}",
                "func main(){var child=Child();external result=child.x;}"
            ) { main, loaded ->
                val child = template("Child", loaded)
                assertEquals(1, child.scope.allVars.count { it.identifier == "x" })
                assertEquals(2, child.scope.fieldOrigins.getValue("x").size)
                val bindings = child.parent.filterIsInstance<CompiledGenericDataTemplate>()
                assertEquals(2, bindings.map { it.inheritanceIdentity }.distinct().size)
                bindings.forEach { assertSame(template("Root", loaded), it.originTemplate) }
                assertEquals(6, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
            }
        }
    }

    @Test fun localConstAnnotationsStayIsolatedAndReachTheActualInstanceFields() {
        inBoth(
            """
            data Parent{const x:int=1;var observed:int=0;new(){return {x:1,observed:1}}}
            data Child:Parent{@DataOnly const x:int=1;new(){return {x:1,observed:1}}}
            data Sibling:Parent{}
        """,
            "func main(){var child=Child();external result=child.x*10+child.observed;}"
        ) { main, loaded ->
            val parent = template("Parent", loaded).scope.getVar("x")!!
            val child = template("Child", loaded).scope.getVar("x")!!
            val sibling = template("Sibling", loaded).scope.getVar("x")!!
            assertNotSame(parent, child)
            assertNotSame(parent, sibling)
            assertNotSame(child, sibling)
            assertNotSame(parent.annotations, child.annotations)
            assertNotSame(parent.annotations, sibling.annotations)
            assertTrue(parent.isConst)
            assertTrue(child.isConst)
            assertTrue(sibling.isConst)
            assertFalse(assertIs<OnScoreboard>(parent).isDataOnly)
            assertTrue(assertIs<OnScoreboard>(child).isDataOnly)
            assertFalse(assertIs<OnScoreboard>(sibling).isDataOnly)
            val instance = assertIs<DataTemplateObject>(main.scope.getVar("child"))
            val member = instance.instanceField.getVar("x")!!
            assertTrue(member.isConst)
            assertTrue(assertIs<OnScoreboard>(member).isDataOnly)
            assertEquals(11, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
        }
    }

    @Test fun restoredConstAndAccessContractsRejectIllegalInstanceOperations() {
        val roots = "data Root{const x:int=1;protected var secret:int=2;}\ndata Child:Root{new(){return {x:1,secret:2}}}"
        rejectsInBoth(
            roots,
            "func main(){var child=Child();child.x=child.x;}",
            "Field 'x' cannot be written",
            "no setter"
        )
        rejectsInBoth(roots, "func main(){var child=Child();external result=child.secret;}", "Cannot access")
        rejectsInBoth(
            "data Root{var x:int=1;}",
            "data Child:Root{const x:int=8;}\nfunc main(){}",
            "Incompatible inherited field 'x'",
            "const",
            "name=Root",
            "name=Child"
        )
    }

    @Test fun overridesAreRejectedAndAbstractReceiversKeepOnlyTheirOwnConstructionRestriction() {
        val roots = """
            data Root{func read():int{return 1;}}
            abstract data Required{abstract func needed():int;}
        """
        rejectsInBoth(
            roots,
            "data Child:Root{override func read():bool{return true;}}\nfunc main(){}",
            "Function 'read'",
            "cannot override",
            "receiver functions"
        )
        rejectsInBoth(
            roots,
            "data Child:Root{private override func read():int{return 2;}}\nfunc main(){}",
            "Function 'read'",
            "cannot override",
            "receiver functions"
        )
        inBoth("$roots\ndata Child:Required{}", "func main(){var child=Child();}") { main, loaded ->
            val child = template("Child", loaded)
            assertFalse(child.isAbstract)
            assertFalse(child.scope.functions.containsKey("needed"))
            assertTrue(template("Required", loaded).scope.functions.getValue("needed").single().isAbstract)
            assertSame(child, assertIs<DataTemplateObject>(main.scope.getVar("child")).templateType)
            assertEquals(0, execute(main).stackDepth)
        }
        rejectsInBoth(
            roots,
            "func main(){var value=Required();}",
            "Cannot bind factory 'Required.new' to its declared argument types"
        ) { loaded ->
            assertTrue(template("Required", loaded).isAbstract)
            val entry = main(loaded)
            assertTrue(entry.scope.getVar("value")?.isError != false)
            assertTrue(entry.commands.analyzeAll().none { "required/new" in it })
            assertEquals(0, execute(entry).stackDepth)
        }
    }

    @Test fun independentChildReceiverFunctionsPreserveTheirOwnAccessAndReturnTypesThroughRoundTrip() {
        inBoth(
            """
            data Result{}
            data Specific:Result{}
            abstract data Root{
                protected var x:int=5;
                abstract func needed():int;
                protected func read():int{return this.x;}
                func make():Result{return Result();}
            }
            data Child:Root{
                new(){return {x:5}}
                func needed():int{return this.x;}
                func read():int{return this.x;}
                func make():Specific{return Specific();}
            }
        """,
            "func main(){var child=Child();external result=child.needed()+child.read();var made=child.make();}"
        ) { main, loaded ->
            val root = template("Root", loaded)
            val child = template("Child", loaded)
            assertTrue(root.isAbstract)
            assertTrue(root.scope.functions.getValue("needed").single().isAbstract)
            assertEquals(AccessModifier.PROTECTED, root.scope.functions.getValue("read").single().accessModifier)
            assertEquals(AccessModifier.PUBLIC, child.scope.functions.getValue("read").single().accessModifier)
            assertFalse(child.scope.functions.getValue("needed").single().isAbstract)
            assertTrue(child.scope.functions.values.flatten().none { it.isOverride })
            assertSame(
                template("Specific", loaded),
                assertIs<MCFPPDataTemplateType>(
                    child.scope.functions.getValue("make").single().returnType
                ).template
            )
            assertSame(template("Specific", loaded), assertIs<DataTemplateObject>(main.scope.getVar("made")).templateType)
            assertEquals(10, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
        }
    }

    @Test fun sourceGenericHeaderAnnotationsApplyToActualSpecializations() = isolated { _ ->
        source("$annotatedGenerics\n$annotatedInstances")
        assertGenericAnnotationEffects(main(false))
    }

    @Test fun freshGenericHeaderAnnotationsApplyToActualSpecializations() = isolated { output ->
        source("$annotatedGenerics\nfunc main(){}")
        val original = assertIs<GenericDataTemplate>(template("Annotated", false))
        assertTrue(original.compiledTemplates.isEmpty())
        write(output)
        fresh(output, annotatedInstances)
        assertNotSame(original, template("Annotated", true))
        assertGenericAnnotationEffects(main(true))
    }

    private fun assertGenericAnnotationEffects(main: Function) {
        val marked = assertIs<DataTemplateObject>(main.scope.getVar("marked"))
        val plain = assertIs<DataTemplateObject>(main.scope.getVar("plain"))
        val markedType = assertIs<CompiledGenericDataTemplate>(marked.templateType)
        assertTrue(
            assertIs<OnScoreboard>(markedType.scope.getVar("value")).isDataOnly,
            "@DataOnly on a generic declaration must affect the instantiated field"
        )
        assertTrue(
            assertIs<OnScoreboard>(marked.instanceField.getVar("value")).isDataOnly,
            "The actual receiver adapter must retain the declaration's annotation effect"
        )
        assertFalse(assertIs<OnScoreboard>(plain.templateType.scope.getVar("value")).isDataOnly)
        assertFalse(assertIs<OnScoreboard>(plain.instanceField.getVar("value")).isDataOnly)
        assertNotSame(markedType.annotations, markedType.originTemplate.annotations)
    }

    private fun inBoth(declarations: String, program: String, inspect: (Function, Boolean) -> Unit) = isolated { output ->
        source("$declarations\n$program")
        val original = GlobalScope.localNamespaces.getValue(namespace).scope.template.toMap()
        inspect(main(false), false)
        write(output)
        fresh(output, program)
        original.forEach { (name, declaration) -> assertNotSame(declaration, template(name, true), name) }
        inspect(main(true), true)
    }

    private fun rejectsInBoth(
        declarations: String,
        program: String,
        vararg reasons: String,
        inspect: (Boolean) -> Unit = {}
    ) = isolated { output ->
        source("$declarations\nfunc main(){}")
        write(output)
        for (loaded in listOf(false, true)) {
            val messages = if (loaded) fresh(output, program, valid = false)
            else source("$declarations\n$program", valid = false)
            assertTrue(Project.errorCount > 0, "Invalid inheritance must fail for loaded=$loaded")
            val text = messages.joinToString("\n")
            reasons.forEach { reason -> assertTrue(text.contains(reason, ignoreCase = true), "$reason missing for loaded=$loaded:\n$text") }
            inspect(loaded)
        }
    }

    private fun source(text: String, valid: Boolean = true): List<String> {
        Project.config.includes = arrayListOf()
        val messages = compilerDiagnostics { MCFPPStringTest.readFromString("namespace $namespace\n${text.trimIndent()}", version = "26.3") }
        if (valid) assertEquals(0, Project.errorCount, messages.joinToString("\n"))
        return messages
    }

    private fun write(output: Path) {
        LibBinWriter.write(output.toString())
        Files.newInputStream(output.resolve("bin.mclib")).use {
            assertEquals(LibraryWire.CURRENT_VERSION, LibraryWire.read(it).formatVersion)
        }
    }

    private fun fresh(output: Path, program: String, valid: Boolean = true): List<String> {
        // The compiler harness calls Project.init before readProject, rebuilding all namespace models.
        // Clear both reader and writer metadata caches explicitly before reading the physical file.
        LibraryMetadata.reset()
        DataTemplateInfo.clearWriteCache()
        GenericDataTemplateInfo.clearWriteCache()
        Project.config.includes = arrayListOf(output.toString())
        val messages = compilerDiagnostics { MCFPPStringTest.readFromString("import $namespace:*;\n${program.trimIndent()}", version = "26.3") }
        if (valid) assertEquals(0, Project.errorCount, messages.joinToString("\n"))
        return messages
    }

    private fun template(name: String, loaded: Boolean): DataTemplate = (if (loaded) GlobalScope.libNamespaces else GlobalScope.localNamespaces).getValue(namespace).scope.getTemplate(name)!!

    private fun main(loaded: Boolean): Function = GlobalScope.localNamespaces
        .getValue(if (loaded) "default.test" else namespace).scope.functions.getValue("main").single()

    private fun execute(main: Function): ScoreCommandExecutor = executePublishedFunctions(main).also { assertEquals(0, it.stackDepth) }

    private fun isolated(action: (Path) -> Unit) {
        val output = Files.createTempDirectory("mcfpp-inheritance-library-")
        val config = Project.config
        val settings = listOf(
            CompileSettings.isDebug,
            CompileSettings.ignoreStdLib,
            CompileSettings.isLib,
            CompileSettings.printAll,
            CompileSettings.foldIRConstants
        )
        val maxInline = CompileSettings.maxWhileInline
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = false
            action(output)
        } finally {
            Project.config = config
            CompileSettings.isDebug = settings[0]
            CompileSettings.ignoreStdLib = settings[1]
            CompileSettings.isLib = settings[2]
            CompileSettings.printAll = settings[3]
            CompileSettings.foldIRConstants = settings[4]
            CompileSettings.maxWhileInline = maxInline
            output.toFile().deleteRecursively()
        }
    }

    companion object {
        private const val namespace = "fixture.inheritance"
        private val annotatedGenerics = """
            @DataOnly
            data Annotated<T>{var value:int=3;}
            data Plain<T>{var value:int=3;}
        """.trimIndent()
        private val annotatedInstances = "func main(){var marked=Annotated<int>();var plain=Plain<int>();}"
    }
}
