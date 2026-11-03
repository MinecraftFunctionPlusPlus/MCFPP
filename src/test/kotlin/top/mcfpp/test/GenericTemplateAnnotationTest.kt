package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.OnScoreboard
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.test.library.LegacyAnnotationFixtures
import top.mcfpp.io.MCFPPFile
import top.mcfpp.io.info.DataTemplateInfo
import top.mcfpp.io.info.GenericDataTemplateInfo
import top.mcfpp.io.info.LibraryMetadata
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPDataTemplateType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class GenericTemplateAnnotationTest {
    @Test fun sourceHeadersAffectGenericInstancesAndGenericObjectFields() = isolated { _ ->
        source("$builtins\n$builtinInstances")
        assertBuiltinEffects(main(false), false)
    }

    @Test fun freshLibraryHeadersAffectGenericInstancesAndGenericObjectFields() = isolated { output ->
        source("$builtins\nfunc main(){}")
        val original = generic("Marked", false)
        assertTrue(original.compiledTemplates.isEmpty())
        LegacyAnnotationFixtures.write(output)
        fresh(output, builtinInstances)
        assertNotSame(original, generic("Marked", true))
        assertBuiltinEffects(main(true), true)
    }

    @Test fun sourceMutableHeaderGraphsAreIsolatedPerBindingAndApplyOnlyOnce() = isolated { _ ->
        for (objectDeclaration in listOf(false, true)) {
            source("${probeDeclaration(objectDeclaration)}\n${probeInstances(objectDeclaration)}")
            assertProbeEffects(false, objectDeclaration)
        }
    }

    @Test fun freshMutableHeaderGraphsAreIsolatedFromWriterAndOtherBindings() = isolated { output ->
        for (objectDeclaration in listOf(false, true)) {
            source("${probeDeclaration(objectDeclaration)}\nfunc main(){}")
            val original = generic("Box", false, objectDeclaration)
            val writerProbe = assertIs<HeaderProbe>(original.annotations.single())
            assertEquals(0, writerProbe.applications, "A raw generic header must not run against an empty scope")
            LegacyAnnotationFixtures.write(output)
            writerProbe.shared.add("writer-only")
            fresh(output, probeInstances(objectDeclaration))
            val restored = generic("Box", true, objectDeclaration)
            assertNotSame(original, restored)
            val readerProbe = assertIs<HeaderProbe>(restored.annotations.single())
            assertNotSame(writerProbe, readerProbe)
            assertEquals(listOf("seed"), readerProbe.shared)
            assertProbeEffects(true, objectDeclaration)
        }
    }

    @Test fun bindingsCreatedBySignaturesBeforeTheAnnotationPassReceiveTheirHeaderFacts() = isolated { _ ->
        source("""
            @HeaderProbe
            data Box<T>{@BodyProbe var value:int=3;}
            data Holder{var item:Box<int>;}
            func accept(value:Box<int>){}
            func main(){var first=Box<int>();}
        """)
        val raw = generic("Box", false)
        val compiled = raw.compiledTemplates.values.single()
        val signature = local("Holder").scope.getVar("item")!!.type as MCFPPDataTemplateType
        signature.tryResolve()
        assertSame(compiled, signature.template)
        assertSame(compiled, assertIs<DataTemplateObject>(main(false).scope.getVar("first")).templateType)
        assertEquals(0, assertIs<HeaderProbe>(raw.annotations.single()).applications)
        assertSingleApplication(compiled)
    }

    @Test fun ordinaryHeaderThenBodyOrderAndInheritedEffectsRemainUnchanged() = isolated { _ ->
        source("""
            @HeaderProbe
            data Parent{@BodyProbe var value:int=3;}
            data Child:Parent{}
            func main(){var value=Child();}
        """)
        val parent = local("Parent")
        val child = local("Child")
        val header = assertIs<HeaderProbe>(parent.annotations.single())
        val body = assertIs<BodyProbe>(parent.scope.getVar("value")!!.annotations.single())
        assertEquals(1, header.applications)
        assertEquals(1, body.applications)
        assertEquals(listOf("header", "body"), applicationOrder)
        assertTrue(assertIs<OnScoreboard>(child.scope.getVar("value")).isDataOnly)
        assertNotSame(parent.scope.getVar("value"), child.scope.getVar("value"))
        assertNotSame(body, child.scope.getVar("value")!!.annotations.single())
        Project.completeTemplateDeclarations()
        assertEquals(1, header.applications)
        assertEquals(1, body.applications)
        assertEquals(listOf("header", "body"), applicationOrder)
        assertEquals(0, Project.errorCount)
    }

    @Test fun genericAbstractDataHeadersRunAfterTheirFieldsHaveMaterialized() = isolated { _ ->
        source("""
            @HeaderProbe
            abstract data Box<T>{@BodyProbe var value:int;}
            data Implementation:Box<int>{}
            func main(){}
        """)
        val raw = assertIs<GenericDataTemplate>(GlobalScope.localNamespaces.getValue(namespace).scope.getTemplate("Box"))
        assertEquals(0, assertIs<HeaderProbe>(raw.annotations.single()).applications)
        val compiled = raw.compiledTemplates.values.single()
        assertTrue(compiled.isAbstract)
        assertSingleApplication(compiled)
        assertTrue(assertIs<OnScoreboard>(local("Implementation").scope.getVar("value")).isDataOnly)
    }

    private fun assertBuiltinEffects(main: Function, loaded: Boolean) {
        val marked = assertIs<DataTemplateObject>(main.scope.getVar("marked"))
        val plain = assertIs<DataTemplateObject>(main.scope.getVar("plain"))
        assertTrue(assertIs<OnScoreboard>(marked.templateType.scope.getVar("value")).isDataOnly)
        assertTrue(assertIs<OnScoreboard>(marked.instanceField.getVar("value")).isDataOnly)
        assertFalse(assertIs<OnScoreboard>(plain.templateType.scope.getVar("value")).isDataOnly)
        assertFalse(assertIs<OnScoreboard>(plain.instanceField.getVar("value")).isDataOnly)
        val defaults = generic("Defaults", loaded, true).compiledTemplates.values.single()
        assertTrue(assertIs<OnScoreboard>(defaults.scope.getVar("value")).isDataOnly)
        assertEquals(0, Project.errorCount)
    }

    private fun assertProbeEffects(loaded: Boolean, objectDeclaration: Boolean) {
        val raw = generic("Box", loaded, objectDeclaration)
        val rawProbe = assertIs<HeaderProbe>(raw.annotations.single())
        assertEquals(0, rawProbe.applications)
        assertEquals(listOf("seed"), rawProbe.shared)
        assertEquals(2, raw.compiledTemplates.size)
        val first = assertNotNull(raw.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Int))))
        val second = assertNotNull(raw.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Bool))))
        assertSame(first, raw.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Int))))
        assertNotSame(first, second)
        val firstProbe = assertSingleApplication(first)
        val secondProbe = assertSingleApplication(second)
        assertNotSame(rawProbe, firstProbe); assertNotSame(firstProbe, secondProbe)
        assertNotSame(rawProbe.parents.single(), firstProbe.parents.single())
        assertNotSame(firstProbe.parents.single(), secondProbe.parents.single())
        assertNotSame(firstProbe.shared, secondProbe.shared)
        firstProbe.shared.add("first-binding-only")
        assertEquals(listOf("seed", "parent-applied"), secondProbe.shared)
        assertEquals(listOf("seed"), rawProbe.shared)
        Project.completeTemplateDeclarations()
        assertSame(first, raw.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Int))))
        assertEquals(1, firstProbe.applications)
        assertEquals(1, secondProbe.applications)
        assertEquals(0, rawProbe.applications)
        assertEquals(0, Project.errorCount)
    }

    private fun assertSingleApplication(template: CompiledGenericDataTemplate): HeaderProbe {
        val header = assertIs<HeaderProbe>(template.annotations.single())
        assertEquals(1, header.applications)
        assertEquals(listOf(namespace, namespace), header.environments,
            "Apply in the original declaration file and namespace, including fresh library materialization")
        val parent = assertIs<ParentProbe>(header.parents.single())
        assertEquals(1, parent.applications)
        assertSame(header.shared, header.nested.single())
        assertSame(header.shared, parent.shared, "Deep copying must preserve internal annotation graph aliases")
        assertEquals(listOf("seed", "parent-applied"), header.shared)
        val field = template.scope.getVar("value")!!
        assertTrue(assertIs<OnScoreboard>(field).isDataOnly)
        assertEquals(1, assertIs<BodyProbe>(field.annotations.single()).applications,
            "A concrete body's annotation must not replay with the header")
        assertTrue(template.pendingDeclarationAnnotations.isEmpty())
        assertTrue(template.pendingFieldAnnotations.isEmpty())
        return header
    }

    private fun source(text: String) {
        applicationOrder.clear()
        Project.config.includes = arrayListOf()
        registered {
            MCFPPStringTest.readFromString("namespace $namespace\n${text.trimIndent()}", version = "26.3")
        }
        assertEquals(0, Project.errorCount)
    }

    private fun fresh(output: Path, text: String) {
        applicationOrder.clear()
        LibraryMetadata.reset()
        Project.config.includes = arrayListOf()
        registered { MCFPPStringTest.readFromString("import $namespace:*;\n${text.trimIndent()}", version = "26.3",
            afterFileCreated = { LegacyAnnotationFixtures.install(listOf(output)) }) }
        assertEquals(0, Project.errorCount)
    }

    private fun registered(action: () -> Unit) {
        val registration: () -> Unit = {
            val scope = (GlobalScope.localNamespaces[namespace] ?: GlobalScope.libNamespaces.getValue(namespace)).scope
            scope.addAnnotation("HeaderProbe", HeaderProbe::class.java)
            scope.addAnnotation("BodyProbe", BodyProbe::class.java)
        }
        val processors = Project.stageProcessor[Project.CompileStage.INDEX_TYPE.ordinal]
        processors.add(registration)
        try { action() } finally { processors.remove(registration) }
    }

    private fun local(name: String): DataTemplate = GlobalScope.localNamespaces.getValue(namespace).scope.getTemplate(name)!!

    private fun generic(name: String, loaded: Boolean, objectDeclaration: Boolean = false): GenericDataTemplate {
        val scope = (if (loaded) GlobalScope.libNamespaces else GlobalScope.localNamespaces).getValue(namespace).scope
        return assertIs<GenericDataTemplate>(if (objectDeclaration) scope.getObject(name) else scope.getTemplate(name))
    }

    private fun main(loaded: Boolean): Function = GlobalScope.localNamespaces
        .getValue(if (loaded) "default.test" else namespace).scope.functions.getValue("main").single()

    private fun probeDeclaration(objectDeclaration: Boolean) = """
        @HeaderProbe
        ${if (objectDeclaration) "object " else ""}data Box<T>{@BodyProbe var value:int=3;}
    """.trimIndent()

    private fun probeInstances(objectDeclaration: Boolean) = if (objectDeclaration)
        "func main(){external first=Box<int>.value;external second=Box<bool>.value;external repeated=Box<int>.value;}"
    else "func main(){var first=Box<int>();var second=Box<bool>();var repeated=Box<int>();}"

    private fun isolated(action: (Path) -> Unit) {
        // ParentProbe is intentionally unregistered, so restore global registrations directly
        // instead of using CompilerStateJournal's declaration-state walk.
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val base = DataTemplate.baseDataTemplate.checkpointDeclarationState()
        val output = Files.createTempDirectory("mcfpp-generic-annotations-")
        val config = Project.config
        val caller = Function.currFunction
        val file = MCFPPFile.currFile
        val forced = Function.forcedField
        val template = DataTemplate.currTemplate
        val settings = listOf(CompileSettings.isDebug, CompileSettings.ignoreStdLib, CompileSettings.isLib,
            CompileSettings.printAll, CompileSettings.foldIRConstants)
        val maxInline = CompileSettings.maxWhileInline
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = true
            action(output)
        } finally {
            LegacyAnnotationFixtures.close()
            base.restore(); namespaces.restore(); project.restore()
            Project.config = config
            Function.currFunction = caller; Function.forcedField = forced
            MCFPPFile.currFile = file; DataTemplate.currTemplate = template
            CompileSettings.isDebug = settings[0]; CompileSettings.ignoreStdLib = settings[1]
            CompileSettings.isLib = settings[2]; CompileSettings.printAll = settings[3]
            CompileSettings.foldIRConstants = settings[4]; CompileSettings.maxWhileInline = maxInline
            applicationOrder.clear()
            output.toFile().deleteRecursively()
        }
    }

    class HeaderProbe private constructor(restoring: Boolean) : Annotation("HeaderProbe", GenericTemplateAnnotationTest.namespace) {
        constructor() : this(false)
        var applications = 0
        var environments = arrayListOf<String>()
            private set
        var shared = arrayListOf("seed")
            private set
        var nested = arrayListOf(shared)
            private set
        init { if (!restoring) parents.add(ParentProbe(shared)) }
        internal fun restorePortableState(applications: Int, environments: ArrayList<String>, shared: ArrayList<String>, nested: ArrayList<ArrayList<String>>) {
            this.applications = applications; this.environments = environments; this.shared = shared; this.nested = nested
        }
        companion object { internal fun restoreShell() = HeaderProbe(true) }
        override fun forFunction(function: Function) = Unit
        override fun forField(field: Var<*>) = Unit
        override fun forDataTemplate(data: DataTemplate) {
            applications++
            applicationOrder.add("header")
            environments.add(Project.currNamespace)
            environments.add(MCFPPFile.currFile?.namespace?.identifier ?: "<missing-file>")
            assertIs<MCInt>(data.scope.getVar("value"), "The header must run after its fields exist").isDataOnly = true
        }
    }

    class ParentProbe(shared: ArrayList<String>) : Annotation("ParentProbe", GenericTemplateAnnotationTest.namespace) {
        var shared = shared
            private set
        var applications = 0
        internal fun restorePortableState(applications: Int, shared: ArrayList<String>) { this.applications = applications; this.shared = shared }
        companion object { internal fun restoreShell() = ParentProbe(arrayListOf()) }
        override fun forFunction(function: Function) = Unit
        override fun forField(field: Var<*>) = Unit
        override fun forDataTemplate(data: DataTemplate) { applications++; shared.add("parent-applied") }
    }

    class BodyProbe : Annotation("BodyProbe", GenericTemplateAnnotationTest.namespace) {
        var applications = 0
        override fun forFunction(function: Function) = Unit
        override fun forDataTemplate(data: DataTemplate) = Unit
        override fun forField(field: Var<*>) { applications++; applicationOrder.add("body") }
    }

    companion object {
        private const val namespace = "fixture.genericannotations"
        private val applicationOrder = arrayListOf<String>()
        internal fun portableApplicationOrder(): List<String> = applicationOrder.toList()
        internal fun clearPortableApplicationOrder() = applicationOrder.clear()
        private val builtins = """
            @DataOnly
            data Marked<T>{var value:int=3;}
            data Plain<T>{var value:int=3;}
            @DataOnly
            object data Defaults<T>{var value:int=3;}
        """.trimIndent()
        private const val builtinInstances = "func main(){var marked=Marked<int>();var plain=Plain<int>();external result=Defaults<int>.value;}"
    }
}
