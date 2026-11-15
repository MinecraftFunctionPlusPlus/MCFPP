package top.mcfpp.test

import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.io.DatapackCreator
import top.mcfpp.io.info.LibraryMetadata
import top.mcfpp.library.api.LibraryExport
import top.mcfpp.library.api.LibraryImportSession
import top.mcfpp.library.compiler.ExportSourceServices
import top.mcfpp.library.compiler.LibraryExporter
import top.mcfpp.library.compiler.LibraryIdentity
import top.mcfpp.library.model.LibraryTarget
import top.mcfpp.library.syntax.PackageKey
import top.mcfpp.test.library.AnnotationParentFixturesTest
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.UnionDataTemplate
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPObjectType
import top.mcfpp.type.MCFPPNeverType
import top.mcfpp.type.TypeCompositions
import top.mcfpp.util.LogProcessor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class IntersectionTemplateCompletionTest {
    @Test fun sourceAndFreshFieldsCompleteForEarlySignaturesAndLateExpressions() {
        for (parents in listOf("A&B", "B&A")) {
            var sourceIntersection: top.mcfpp.type.TypeId? = null
            inBoth("data A{var x:int;}\ndata B{var y:int;}", """
                data Holder{var pair:($parents);}
                func read(value:($parents)):int{return value.x+value.y;}
                func main(){
                    var first:($parents)={x:3,y:4} as ($parents);
                    var second:($parents)={x:5,y:6} as ($parents);
                    external result=read(first)+read(second);
                }
            """) { entry, loaded ->
                val first = assertIs<DataTemplateObject>(entry.scope.getVar("first"))
                val second = assertIs<DataTemplateObject>(entry.scope.getVar("second"))
                val intersection = assertNotNull(TypeCompositions.objectShape(first.type))
                if (loaded) assertEquals(sourceIntersection, intersection.typeId)
                else sourceIntersection = intersection.typeId
                val holder = consumerTemplate("Holder", loaded)
                val fieldType = assertNotNull(TypeCompositions.objectShape(holder.scope.getVar("pair")!!.type))
                assertEquals(intersection.typeId, fieldType.typeId)
                assertEquals(intersection.typeId, TypeCompositions.objectShape(second.type)!!.typeId)
                val read = GlobalScope.localNamespaces.getValue(consumerNamespace(loaded)).scope.functions.getValue("read").single()
                assertEquals(intersection.typeId, TypeCompositions.objectShape(read.normalParams.single().type)!!.typeId)
                assertTrue((read.namespaceID.identifier + ".mcfunction").length <= 255)
                assertEquals(listOf("x", "y"), intersection.fields.map { it.name })
                val layout = intersection.templateForRuntime()
                assertTrue(layout.parent.isEmpty())
                assertEquals(listOf("x", "y"), layout.scope.allVars.map { it.identifier })
                for (name in listOf("x", "y")) {
                    assertNotNull(first.getMemberVar(name, top.mcfpp.model.Member.AccessModifier.PUBLIC).first)
                }
                assertEquals(18, execute(entry).read(assertIs<MCInt>(entry.scope.getVar("result"))))
                assertEquals(0, Project.errorCount)
            }
        }
    }

    @Test fun inheritedAndNestedSignaturesNormalizeAfterAllNominalFieldsComplete() {
        inBoth("""
            data Root{var x:int;}
            data A:Root{}
            data B{var y:int;}
            data PairBase{var pair:(A&B);}
            data Left{var composed:A;}
            data Right{var composed:B;}
            data Holder:PairBase,Left,Right{var items:list<(A&B)>;}
        """, """
            func pass(value:(A&B)):(A&B){return value;}
            func read(values:list<(A&B)>):int{return values[0].x+values[0].y;}
            func main(){
                var holder={pair:{x:3,y:4},composed:{x:7,y:8},items:[{x:5,y:6}]} as Holder;
                var pair=pass(holder.pair);
                external result=pair.x+pair.y+read(holder.items);
            }
        """) { entry, loaded ->
            val holder = parentTemplate("Holder", loaded)
            val pair = assertIs<DataTemplateObject>(holder.scope.getVar("pair"))
            val shape = assertNotNull(TypeCompositions.objectShape(pair.type))
            assertEquals(listOf("x", "y"), shape.fields.map { it.name })
            assertEquals(shape.typeId, holder.scope.getVar("composed")!!.type.typeId)
            val list = assertIs<top.mcfpp.type.MCFPPListType>(holder.scope.getVar("items")!!.type)
            assertEquals(shape.typeId, list.generic.single().typeId)
            assertFalse(TypeCompositions.hasDeferredComposition(list))
            val functions = GlobalScope.localNamespaces.getValue(consumerNamespace(loaded)).scope.functions
            val pass = functions.getValue("pass").single()
            assertEquals(shape.typeId, pass.normalParams.single().type.typeId)
            assertEquals(shape.typeId, pass.returnType.typeId)
            val read = functions.getValue("read").single()
            assertEquals(list.typeId, read.normalParams.single().type.typeId)
            assertEquals(list.typeId, read.scope.getVar("values")!!.type.typeId)
            assertEquals(18, execute(entry).read(assertIs<MCInt>(entry.scope.getVar("result"))))
        }
    }

    @Test fun unionSignaturesKeepAlternativesUntilInheritedFieldsComplete() {
        inBoth("""
            data Root{var x:int;}
            data A:Root{var common:int;}
            data B{var common:int;var y:int;}
            data Holder{var value:(A|B);}
        """, """
            func keep(value:(A|B)):(A|B){return value;}
            func main(){
                var holder={value:{common:8,y:9}} as Holder;
                var value=keep(holder.value);
                external result=value.common;
            }
        """) { entry, loaded ->
            val field = parentTemplate("Holder", loaded).scope.getVar("value")!!
            val union = assertIs<top.mcfpp.type.MCFPPUnionType>(field.type)
            assertEquals(setOf(parentTemplate("A", loaded).getType().typeId, parentTemplate("B", loaded).getType().typeId),
                union.types.map { it.typeId }.toSet())
            assertFalse(TypeCompositions.hasDeferredComposition(union))
            val keep = GlobalScope.localNamespaces.getValue(consumerNamespace(loaded)).scope.functions.getValue("keep").single()
            assertEquals(union.typeId, keep.normalParams.single().type.typeId)
            assertEquals(union.typeId, keep.returnType.typeId)
            assertEquals(8, execute(entry).read(assertIs<MCInt>(entry.scope.getVar("result"))))
        }
    }

    @Test fun sourceAndFreshIntersectionsCallOriginalReceiverFunctionsThroughNominalViews() {
        for (parents in listOf("Left&Right", "Right&Left")) {
            inBoth("""
                data Root{var marker:int;func read():int{return 1;}}
                data Left:Root{}
                data Right:Root{func read():int{return 2;}}
            """, "func main(){var value={marker:0} as ($parents);var view=value as Right;external result=view.read();}") { entry, loaded ->
                val intersection = TypeCompositions.objectShape(assertIs<DataTemplateObject>(entry.scope.getVar("value")).type)!!
                assertTrue(intersection.templateForRuntime().scope.functions.isEmpty())
                val original = parentTemplate("Right", loaded).scope.functions.getValue("read").single()
                assertSame(parentTemplate("Right", loaded), original.owner)
                assertEquals(2, execute(entry).read(assertIs<MCInt>(entry.scope.getVar("result"))))
            }
        }
    }

    @Test fun sourceAndFreshPlainIntersectionsNormalizeConflictingAndCompatibleFields() {
        for (parents in listOf("A&B", "B&A")) {
            inBoth("data A{var x:int;}\ndata B{var x:bool;}",
                "func impossible(value:($parents)){}\nfunc main(){}") { _, loaded ->
                val function = GlobalScope.localNamespaces.getValue(consumerNamespace(loaded)).scope.functions.getValue("impossible").single()
                assertSame(MCFPPNeverType, function.normalParams.single().type)
            }
            inBoth("data A{var x:int;}\ndata B{var x:int;}",
                "func main(){var value={x:3} as ($parents);external result=value.x;}") { entry, _ ->
                val shape = TypeCompositions.objectShape(assertIs<DataTemplateObject>(entry.scope.getVar("value")).type)!!
                assertEquals(listOf("x"), shape.fields.map { it.name })
                assertEquals(MCFPPBaseType.Int.typeId, shape.fields.single().type.typeId)
                assertEquals(3, execute(entry).read(assertIs<MCInt>(entry.scope.getVar("result"))))
            }
        }
    }

    @Test fun sourceAndFreshNominalViewsKeepProtectedAccessChecks() {
        for (parents in listOf("A&B", "B&A")) {
            rejectsInBoth("data A{protected var x:int;}\ndata B{var y:int;}",
                "func main(){var value={x:3,y:4} as ($parents);var nominal=value as A;external result=nominal.x;}", "Cannot access")
        }
    }

    @Test fun canonicalIdentityDistinguishesFullNamespacesAndGenericBindings() = isolated { _ ->
        source("data Box<T>{var x:int;}\ndata Extra{var y:int;}\nfunc main(){}")
        val extra = parentTemplate("Extra", false)
        val left = DataTemplate("Shared", "identity.left").apply { declarationFile = null }
        val right = DataTemplate("Shared", "identity.right").apply { declarationFile = null }
        val first = UnionDataTemplate.resolve(listOf(left, extra))
        val second = UnionDataTemplate.resolve(listOf(right, extra))
        assertSame(first, UnionDataTemplate.resolve(listOf(left, extra)))
        assertNotSame(first, second)
        assertNotEquals(first.inheritanceIdentity, second.inheritanceIdentity)
        for (intersection in listOf(first, second)) {
            assertEquals(intersection.inheritanceIdentity, intersection.getType().typeId)
            assertSame(extra, intersection.scope.getVar("y")!!.declaredParentTemplate)
            assertSame(intersection, Project.findRegisteredAnonymousTemplate(intersection.getType().typeId))
        }
        val box = assertIs<GenericDataTemplate>(parentTemplate("Box", false))
        val intBox = assertNotNull(box.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Int))))
        val boolBox = assertNotNull(box.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Bool))))
        val ints = UnionDataTemplate.resolve(listOf(intBox, extra))
        val bools = UnionDataTemplate.resolve(listOf(boolBox, extra))
        assertSame(ints, UnionDataTemplate.resolve(listOf(intBox, extra)))
        assertNotEquals(ints.getType().typeId, bools.getType().typeId)
        assertSame(intBox, ints.scope.getVar("x")!!.declaredParentTemplate)
        assertSame(boolBox, bools.scope.getVar("x")!!.declaredParentTemplate)
        assertEquals(ints.inheritanceIdentity, ints.getType().typeId)
        assertEquals(bools.inheritanceIdentity, bools.getType().typeId)
        assertEquals(0, Project.errorCount)
        val identity = first.inheritanceIdentity
        Project.init()
        assertNull(Project.findRegisteredAnonymousTemplate(identity))
    }

    @Test fun freshExportedIntersectionFieldSignaturesRetainTheirCompletedMembers() = portableIntersection { it }

    internal fun portableIntersection(roundTrip: (RestoreProbe) -> RestoreProbe) = isolated { output ->
        val declarations = """
            data Root{var x:int;}
            @DataOnly
            data A:Root{}
            data B{var y:int;}
            data Holder{var pair:(A&B);}
            data Other{var pair:(A&B);}
        """.trimIndent()
        val program = "func main(){var holder={pair:{x:3,y:4}} as Holder;external result=holder.pair.x+holder.pair.y;}"
        source("$declarations\n$program")
        val sourceHolder = parentTemplate("Holder", false)
        val sourceType = assertNotNull(TypeCompositions.objectShape(sourceHolder.scope.getVar("pair")!!.type))
        val sourceOwner = parentTemplate("A", false)
        assertTrue(assertIs<MCInt>(sourceOwner.scope.getVar("x")).isDataOnly)
        assertEquals(listOf("x", "y"), sourceType.fields.map { it.name })
        val probe = RestoreProbe()
        probe.on(sourceOwner)
        val portableProbe = roundTrip(probe)
        assertEquals(1, portableProbe.applications, "Portable restore must retain the completed action count")
        sourceOwner.annotations.add(portableProbe)
        assertEquals(1, probe.applications)
        assertEquals(7, execute(main(false)).read(assertIs<MCInt>(main(false).scope.getVar("result"))))
        write(output)
        fresh(output, program)
        val restoredHolder = parentTemplate("Holder", true)
        assertNotSame(sourceHolder, restoredHolder)
        val restoredType = assertNotNull(TypeCompositions.objectShape(restoredHolder.scope.getVar("pair")!!.type))
        assertNotSame(sourceType, restoredType)
        assertEquals(sourceType.typeId, restoredType.typeId)
        val restored = restoredType.templateForRuntime()
        assertTrue(restored.parent.isEmpty())
        assertTrue(restored.scope.functions.isEmpty())
        val restoredOwner = parentTemplate("A", true)
        val restoredProbe = restoredOwner.annotations.filterIsInstance<RestoreProbe>().single()
        assertNotSame(probe, restoredProbe)
        assertEquals(1, restoredProbe.applications, "Completed declaration annotations must not run again on restore")
        val x = assertIs<MCInt>(restoredOwner.scope.getVar("x"))
        val y = assertNotNull(restored.scope.getProperty("y"))
        assertTrue(x.isDataOnly, "Completed nominal declaration effects must survive metadata")
        assertSame(parentTemplate("Root", true), x.declaredParentTemplate)
        val otherType = assertNotNull(TypeCompositions.objectShape(parentTemplate("Other", true).scope.getVar("pair")!!.type))
        repeat(2) {
            assertEquals(restoredType.typeId, otherType.typeId)
            assertEquals(listOf("x", "y"), restored.scope.allVars.map { it.identifier })
            assertSame(x, restoredOwner.scope.getVar("x"))
            assertSame(y, restored.scope.getProperty("y"))
            assertEquals(1, restoredProbe.applications)
        }
        assertEquals(7, execute(main(true)).read(assertIs<MCInt>(main(true).scope.getVar("result"))))
    }

    class RestoreProbe : Annotation("RestoreProbe", "fixture.intersection") {
        var applications = 0
        override fun forDataTemplate(data: DataTemplate) { applications++ }
        override fun forFunction(function: Function) = Unit
        override fun forField(field: Var<*>) = Unit
    }

    @Test fun freshEmbeddedGenericIntersectionsPreserveTheirParentBindings() = isolated { output ->
        source("""
            data Box<T>{var x:T;}
            data Extra{var y:int;}
            data Holder{var ints:(Box<int>&Extra);var bools:(Box<bool>&Extra);}
            func main(){}
        """)
        val sourceHolder = parentTemplate("Holder", false)
        val sourceTypes = listOf("ints", "bools").map { name ->
            assertNotNull(TypeCompositions.objectShape(sourceHolder.scope.getVar(name)!!.type))
        }
        assertNotEquals(sourceTypes[0].typeId, sourceTypes[1].typeId)
        write(output)
        fresh(output, "func main(){}")
        val restoredHolder = parentTemplate("Holder", true)
        val box = assertIs<GenericDataTemplate>(parentTemplate("Box", true))
        val extra = parentTemplate("Extra", true)
        for ((index, name) in listOf("ints", "bools").withIndex()) {
            val type = assertNotNull(TypeCompositions.objectShape(restoredHolder.scope.getVar(name)!!.type))
            assertNotSame(sourceTypes[index], type)
            val argument = if (index == 0) MCFPPBaseType.Int else MCFPPBaseType.Bool
            val specialized = assertNotNull(box.compile(listOf(MCFPPTypeVar(argument))))
            assertSame(box, specialized.originTemplate)
            assertSame(specialized, assertNotNull(box.compile(listOf(MCFPPTypeVar(argument)))))
            assertEquals(sourceTypes[index].typeId, type.typeId)
            assertEquals(argument.typeId, type.fieldsByName.getValue("x").type.typeId)
            assertEquals(MCFPPBaseType.Int.typeId, type.fieldsByName.getValue("y").type.typeId)
            assertEquals(type.typeId, TypeCompositions.objectShape(TypeCompositions.intersection(specialized.getType(), extra.getType()))!!.typeId)
            assertTrue(type.templateForRuntime().parent.isEmpty())
        }
        assertEquals(0, Project.errorCount)
    }

    private fun inBoth(declarations: String, program: String, inspect: (Function, Boolean) -> Unit) = isolated { output ->
        source("$declarations\n$program")
        inspect(main(false), false)
        source("$declarations\nfunc main(){}")
        val original = GlobalScope.localNamespaces.getValue(namespace).scope.template.toMap()
        write(output)
        fresh(output, program)
        original.forEach { (name, template) -> assertNotSame(template, parentTemplate(name, true)) }
        inspect(main(true), true)
    }

    private fun rejectsInBoth(declarations: String, program: String, vararg reasons: String) = isolated { output ->
        source("$declarations\nfunc main(){}")
        write(output)
        for (loaded in listOf(false, true)) {
            val messages = if (loaded) fresh(output, program, valid = false)
                else source("$declarations\n$program", valid = false)
            assertTrue(Project.errorCount > 0, "Invalid intersection must fail for loaded=$loaded")
            val text = messages.joinToString("\n")
            reasons.forEach { assertTrue(text.contains(it, ignoreCase = true), "$it missing for loaded=$loaded:\n$text") }
        }
    }

    private fun source(text: String, valid: Boolean = true): List<String> {
        Project.config.includes = arrayListOf()
        val messages = diagnostics { MCFPPStringTest.readFromString("namespace $namespace\n${text.trimIndent()}", version = "26.3") }
        if (valid) assertEquals(0, Project.errorCount, messages.joinToString("\n"))
        if (valid) {
            val main = main(false)
            var tree: org.antlr.v4.runtime.tree.ParseTree = assertNotNull(main.ast)
            while (tree.parent != null) tree = tree.parent
            Project.trees[assertNotNull(main.declarationFile)] = tree
        }
        return messages
    }

    private fun write(output: Path) {
        AnnotationParentFixturesTest.registry().use { codecs ->
            val selection = LibraryExporter.snapshotCurrentProject(
                LibraryIdentity(PackageKey("fixture:intersections", "1"), "mcfpp"), LibraryTarget("26.3"))
            val providers = AnnotationParentFixturesTest.sourceProviders
            LibraryExport.write(selection.copy(services = ExportSourceServices.create(selection,
                AnnotationParentFixturesTest.managed, codecs, providers::get, providers::containsKey)), output.resolve("bin.mclib"))
        }
    }

    private fun fresh(output: Path, text: String, valid: Boolean = true): List<String> {
        LibraryMetadata.reset()
        Project.config.includes = arrayListOf()
        val messages = diagnostics {
            MCFPPStringTest.readFromString("import $namespace:*;\n${text.trimIndent()}", version = "26.3", afterFileCreated = {
                val previous = assertNotNull(Project.libraryImportSession)
                assertTrue(previous.exportDependencies().isEmpty(), "This fixture must not detach a standard-library owner")
                Project.closeLibraryImports()
                val session = LibraryImportSession()
                Project.libraryImportSession = session
                session.configureNative(AnnotationParentFixturesTest.managed, AnnotationParentFixturesTest.registry(),
                    AnnotationParentFixturesTest.sourceProviders)
                session.stage(output.resolve("bin.mclib"))
                session.bindAll(); session.commit()
                (GlobalScope.libNamespaces.getValue(namespace).scope.template["Box"] as? GenericDataTemplate)?.let { box ->
                    assertTrue(box.compiledTemplates.isEmpty(), "READ_LIB must defer generic specialization until consumer compilation")
                }
            })
        }
        if (valid) assertEquals(0, Project.errorCount, messages.joinToString("\n"))
        return messages
    }

    private fun parentTemplate(name: String, loaded: Boolean): DataTemplate =
        (if (loaded) GlobalScope.libNamespaces else GlobalScope.localNamespaces).getValue(namespace).scope.getTemplate(name)!!

    private fun consumerNamespace(loaded: Boolean) = if (loaded) "default.test" else namespace
    private fun consumerTemplate(name: String, loaded: Boolean): DataTemplate = GlobalScope.localNamespaces
        .getValue(consumerNamespace(loaded)).scope.getTemplate(name)!!
    private fun main(loaded: Boolean): Function = GlobalScope.localNamespaces
        .getValue(consumerNamespace(loaded)).scope.functions.getValue("main").single()

    private fun execute(entry: Function): ScoreCommandExecutor {
        val output = Files.createTempDirectory("mcfpp-intersection-execution-")
        try {
            DatapackCreator.createDatapack(output.toString())
            val functions = linkedMapOf<String, List<String>>()
            for (data in listOf(output.resolve("Imports/data"), output.resolve(Project.config.name).resolve("data"))) {
                if (!Files.exists(data)) continue
                Files.walk(data).use { paths ->
                    paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".mcfunction") }.forEach { file ->
                        val path = data.relativize(file)
                        if (path.nameCount >= 3 && path.getName(1).toString() == "function") {
                            val name = path.subpath(2, path.nameCount).joinToString("/").removeSuffix(".mcfunction")
                            functions["${path.getName(0)}:$name"] = Files.readAllLines(file).map(String::trim)
                        }
                    }
                }
            }
            return ScoreCommandExecutor(assertNotNull(functions[entry.namespaceID.toString()]), functions).also {
                assertEquals(0, it.stackDepth)
            }
        } finally { output.toFile().deleteRecursively() }
    }

    private fun diagnostics(action: () -> Unit): List<String> {
        val logger = assertIs<Logger>(LogProcessor.logger)
        val messages = arrayListOf<String>()
        val appender = object : AbstractAppender("intersection-${java.util.UUID.randomUUID()}", null, null, false, Property.EMPTY_ARRAY) {
            override fun append(event: LogEvent) { messages.add(event.message.formattedMessage) }
        }
        appender.start(); logger.addAppender(appender)
        try { action() } finally { logger.removeAppender(appender); appender.stop() }
        return messages
    }

    private fun isolated(action: (Path) -> Unit) {
        val output = Files.createTempDirectory("mcfpp-intersection-library-")
        val config = Project.config
        val settings = listOf(CompileSettings.isDebug, CompileSettings.ignoreStdLib, CompileSettings.isLib,
            CompileSettings.printAll, CompileSettings.foldIRConstants)
        val maxInline = CompileSettings.maxWhileInline
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = true
            action(output)
        } finally {
            Project.closeLibraryImports()
            Project.config = config
            CompileSettings.isDebug = settings[0]; CompileSettings.ignoreStdLib = settings[1]
            CompileSettings.isLib = settings[2]; CompileSettings.printAll = settings[3]
            CompileSettings.foldIRConstants = settings[4]; CompileSettings.maxWhileInline = maxInline
            output.toFile().deleteRecursively()
        }
    }

    companion object { private const val namespace = "fixture.intersection" }
}
