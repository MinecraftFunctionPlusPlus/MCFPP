package top.mcfpp.test

import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.expand.FunctionStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.function.DataTemplateConstructor
import top.mcfpp.model.function.Function
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.TypeId
import top.mcfpp.util.Module
import top.mcfpp.util.TempPool
import kotlin.test.*
import kotlin.test.Test

class MniProjectStateJournalTest {
    private inline fun isolated(action: () -> Unit) {
        val original = ProjectStateJournal.checkpoint()
        val caller = Function.currFunction
        val forced = Function.forcedField
        try {
            Project.init()
            Project.config = ProjectConfig(version = "26.3")
            Function.currFunction = Function("projectJournal", "journal", null)
            action()
        } finally {
            original.restore()
            Function.currFunction = caller
            Function.forcedField = forced
        }
    }

    @Test fun publicRelationshipsRestoreOriginalContainersAndDetachedAfterContents() = isolated {
        val config = Project.config
        val modules = Project.modules
        val trees = Project.trees
        val tokens = Project.tokens
        val contexts = Project.ctx
        val constants = Project.constants
        val macros = Project.macroFunction
        val file = MCFPPFile()
        val context = ParserRuleContext()
        val tokenStream = org.antlr.v4.runtime.CommonTokenStream(
            org.antlr.v4.runtime.ListTokenSource(emptyList<org.antlr.v4.runtime.Token>())
        )
        trees[file] = context
        tokens[file] = tokenStream
        val value = MCInt(3, "constant")
        Project.ctx.add(context)
        Project.constants["before"] = value
        Project.macroFunction["same"] = "before"
        MCFPPFile.currFile = file
        Project.currNamespace = "before"
        Project.compileStage = Project.CompileStage.COMPILE
        val before = ProjectStateJournal.checkpoint()
        contexts.clear()
        constants.clear()
        macros.clear()
        trees.clear()
        tokens.clear()
        Project.config = ProjectConfig(rootNamespace = "after")
        Project.modules = arrayListOf(Module("new"))
        Project.trees = linkedMapOf()
        Project.tokens = linkedMapOf()
        Project.currNamespace = "after"
        Project.compileStage = Project.CompileStage.GEN_DATAPACK
        MCFPPFile.currFile = null
        val afterModules = Project.modules
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(config, Project.config)
            assertSame(modules, Project.modules)
            assertSame(trees, Project.trees)
            assertSame(tokens, Project.tokens)
            assertSame(context, trees[file])
            assertSame(tokenStream, tokens[file])
            assertSame(contexts, Project.ctx)
            assertSame(context, Project.ctx.single())
            assertSame(constants, Project.constants)
            assertSame(value, constants["before"])
            assertSame(macros, Project.macroFunction)
            assertEquals("before", macros["same"])
            assertSame(file, MCFPPFile.currFile)
            assertEquals("before", Project.currNamespace)
            assertEquals(Project.CompileStage.COMPILE, Project.compileStage)
            after.restore()
            assertSame(afterModules, Project.modules)
            assertEquals("new", Project.modules.single().id)
            assertTrue(contexts.isEmpty())
            assertTrue(constants.isEmpty())
            assertTrue(macros.isEmpty())
            assertTrue(trees.isEmpty())
            assertTrue(tokens.isEmpty())
            assertEquals("after", Project.currNamespace)
            assertNull(MCFPPFile.currFile)
        }
        before.restore()
        assertTrue(after.elements.any { it === value })
        assertTrue(after.elements.any { it === file })
    }

    @Test fun configAndModulePackagesRestoreAliasesAndDisconnectedListsInPlace() = isolated {
        val config = Project.config
        val includes = config.includes.apply { add("old") }
        config.jars = includes
        val module = Module("module")
        val pack = Module.Companion.Package("pack").apply { provides = arrayListOf("functions") }
        val provides = pack.provides
        module.base.add(pack)
        module.packages[pack] = false
        val resources = mapOf("datapack/module/data/pack/function/main.mcfunction" to "say old".toByteArray())
        module.resourceSnapshot = resources
        Project.modules.add(module)
        val packages = module.packages
        val before = ProjectStateJournal.checkpoint()
        includes.clear()
        includes.add("changed")
        config.includes = arrayListOf("replacement")
        config.name = "changed"
        config.copyImport = false
        module.packages = hashMapOf()
        module.base.clear()
        val changedResources = emptyMap<String, ByteArray>()
        module.resourceSnapshot = changedResources
        pack.id = "changedPack"
        (provides as MutableList<String>).clear()
        pack.provides = listOf("replacement")
        val after = before.captureCurrent()
        before.restore()
        assertSame(includes, config.includes)
        assertSame(includes, config.jars)
        assertEquals(listOf("old"), includes)
        assertEquals("new_mcfpp_project", config.name)
        assertTrue(config.copyImport)
        assertSame(packages, module.packages)
        assertEquals(false, packages[pack])
        assertSame(pack, module.base.single())
        assertSame(resources, module.resourceSnapshot)
        assertSame(provides, pack.provides)
        assertEquals(listOf("functions"), provides)
        assertEquals("pack", pack.id)
        after.restore()
        assertEquals(listOf("changed"), includes)
        assertEquals("replacement", config.includes.single())
        assertFalse(packages.isEmpty(), "The detached original map was not changed by the preparation")
        assertTrue(module.packages.isEmpty())
        assertTrue(module.base.isEmpty())
        assertSame(changedResources, module.resourceSnapshot)
        assertTrue(provides.isEmpty())
        before.restore()
        assertSame(packages, module.packages)
        assertSame(pack, module.base.single())
        assertEquals(listOf("functions"), provides)
    }

    @Test fun configUuidRestoresOriginalArrayAndItsContents() = isolated {
        val uuid = Project.config.tempItemEntityUUID
        val array = uuid.uuidNBT.value
        val contents = array.clone()
        val before = ProjectStateJournal.checkpoint()
        array[0] = 7
        uuid.uuidNBT.value = intArrayOf(1, 2, 3, 4)
        val replacement = uuid.uuidNBT.value
        val after = before.captureCurrent()
        before.restore()
        assertSame(array, uuid.uuidNBT.value)
        assertContentEquals(contents, array)
        after.restore()
        assertSame(replacement, uuid.uuidNBT.value)
        assertEquals(7, array[0])
        before.restore()
        assertSame(array, uuid.uuidNBT.value)
        assertContentEquals(contents, array)
    }

    @Test fun fixedSizeListsRestoreElementsBackingAliasesAndDetachedAfterContents() = isolated {
        val namespaces = Project.config.stdNamespace as MutableList<String>
        val originalNamespaces = namespaces.toList()
        val backing = arrayOf("base", "other")
        val dependencies = backing.asList()
        val pack = Module.Companion.Package("pack").apply {
            depends = dependencies
            provides = dependencies
        }
        Project.modules.add(Module("module").apply { base.add(pack) })
        val before = ProjectStateJournal.checkpoint()
        namespaces[0] = "prepared.namespace"
        backing[0] = "prepared.base"
        (dependencies as MutableList<String>)[1] = "prepared.other"
        pack.depends = listOf("replacement", "dependency")
        pack.provides = listOf("replacement", "resource")
        val replacement = pack.depends
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(namespaces, Project.config.stdNamespace)
            assertEquals(originalNamespaces, namespaces)
            assertSame(dependencies, pack.depends)
            assertSame(dependencies, pack.provides)
            assertContentEquals(arrayOf("base", "other"), backing)
            after.restore()
            assertSame(namespaces, Project.config.stdNamespace)
            assertEquals("prepared.namespace", namespaces[0])
            assertSame(replacement, pack.depends)
            assertEquals(listOf("replacement", "resource"), pack.provides)
            assertContentEquals(arrayOf("prepared.base", "prepared.other"), backing)
            assertEquals(backing.toList(), dependencies)
        }
        before.restore()
        assertSame(dependencies, pack.depends)
        assertEquals(originalNamespaces, namespaces)
        assertContentEquals(arrayOf("base", "other"), backing)
    }

    @Test fun signatureCollectionDepthPendingAndAnonymousRootsSurviveNestedRestoration() = isolated {
        val anonymous = DataTemplate("Anonymous", "journal")
        Project.collectingTemplateSignatures {
            val before = ProjectStateJournal.checkpoint()
            assertEquals(1, Project.journalState().signatureDepth)
            Project.registerAnonymousTemplate(anonymous)
            val after = before.captureCurrent()
            before.restore()
            assertEquals(1, Project.journalState().signatureDepth)
            assertTrue(Project.journalState().pending.isEmpty())
            assertTrue(Project.journalState().anonymous.isEmpty())
            after.restore()
            assertSame(anonymous, Project.journalState().pending.single())
            assertSame(anonymous, Project.journalState().anonymous.single())
            assertTrue(after.elements.any { it === anonymous })
            before.restore()
        }
        assertEquals(0, Project.journalState().signatureDepth)
    }

    @Test fun declarationReadinessAndHeaderSetReferencesAreRestoredWithoutCompletingAgain() = isolated {
        val before = ProjectStateJournal.checkpoint()
        val oldHeaders = Project.journalState().headers
        Project.completeTemplateDeclarations()
        assertTrue(Project.templateDeclarationsReady)
        val afterHeaders = Project.journalState().headers
        val after = before.captureCurrent()
        before.restore()
        assertFalse(Project.templateDeclarationsReady)
        assertSame(oldHeaders, Project.journalState().headers)
        after.restore()
        assertTrue(Project.templateDeclarationsReady)
        assertSame(afterHeaders, Project.journalState().headers)
        before.restore()
    }

    @Test fun projectEntrySlotsRestoreInitializedOrUninitializedStateAndRetainRemovedFunctions() = isolated {
        val tick = runCatching { Project.projectTick }.getOrNull()
        val load = runCatching { Project.projectLoad }.getOrNull()
        val init = runCatching { Project.projectInit }.getOrNull()
        val before = ProjectStateJournal.checkpoint()
        val replacement = Function("replacement", "journal", null)
        Project.projectTick = replacement
        Project.projectLoad = replacement
        Project.projectInit = replacement
        val after = before.captureCurrent()
        before.restore()
        assertSame(tick, runCatching { Project.projectTick }.getOrNull())
        assertSame(load, runCatching { Project.projectLoad }.getOrNull())
        assertSame(init, runCatching { Project.projectInit }.getOrNull())
        after.restore()
        assertSame(replacement, Project.projectTick)
        assertSame(replacement, Project.projectLoad)
        assertSame(replacement, Project.projectInit)
        before.restore()
        assertTrue(after.elements.any { it === replacement })
    }

    @Test fun actualInitializerPreparationCapturesActiveChainDependenciesAndPublishedBodies() = isolated {
        val outer = ObjectDataTemplate("Outer", "journal")
        val inner = ObjectDataTemplate("Inner", "journal")
        var calls = 0
        inner.constructors.add(ProbeConstructor(inner) { calls++ })
        outer.constructors.add(
            ProbeConstructor(outer) {
                assertSame(outer, Project.journalState().activeObjects.single())
                val before = ProjectStateJournal.checkpoint()
                Project.prepareObjectInitializer(inner)
                val dependencies = assertNotNull(Project.journalState().initializerDependencies[outer])
                val innerBody = assertNotNull(Project.journalState().initializerBodies[inner])
                val after = before.captureCurrent()
                repeat(2) {
                    before.restore()
                    assertFalse(inner in Project.journalState().preparedObjects)
                    assertNull(Project.journalState().initializerDependencies[outer])
                    assertSame(outer, Project.journalState().activeObjects.single())
                    after.restore()
                    assertSame(dependencies, Project.journalState().initializerDependencies[outer])
                    assertTrue(inner in dependencies)
                    assertSame(innerBody, Project.journalState().initializerBodies[inner])
                }
                before.restore()
            }
        )
        Project.prepareObjectInitializer(outer)
        assertEquals(1, calls)
        assertTrue(Project.journalState().activeObjects.isEmpty())
        assertTrue(outer in Project.journalState().preparedObjects)
        assertTrue(Project.journalState().initializerBodies.containsKey(outer))
    }

    @Test fun disconnectedInitializerSetsAndSourceFilesRemainAfterStateRoots() = isolated {
        val state = Project.journalState()
        val owner = ObjectDataTemplate("Owner", "journal")
        val child = ObjectDataTemplate("Child", "journal")
        val deps = Project.InitializerSet().apply { add(child) }
        val file = MCFPPFile()
        state.initializerDependencies[owner] = deps
        state.sourceFiles.add(file)
        val before = ProjectStateJournal.checkpoint()
        state.initializerDependencies.clear()
        deps.clear()
        state.sourceFiles.clear()
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(deps, state.initializerDependencies[owner])
            assertSame(child, deps.single())
            assertSame(file, state.sourceFiles.single())
            after.restore()
            assertTrue(state.initializerDependencies.isEmpty())
            assertTrue(deps.isEmpty())
            assertTrue(state.sourceFiles.isEmpty())
        }
        before.restore()
        assertTrue(after.elements.any { it === owner })
        assertTrue(after.elements.any { it === child })
        assertTrue(after.elements.any { it === file })
    }

    @Test fun failureRestoresNamesDiagnosticsAndStageCallbacksWithoutCallingThem() = isolated {
        val callbacks = Project.stageProcessor[Project.CompileStage.COMPILE.ordinal]
        var callbackCalls = 0
        val callback = {
            callbackCalls++
            Unit
        }
        callbacks.add(callback)
        val names = listOf(
            TempPool.varCount,
            TempPool.functionCount,
            TempPool.functionParam,
            TempPool.anonymousTemplateCount,
            TempPool.namespaceCount
        )
        val before = ProjectStateJournal.checkpoint()
        assertFailsWith<IllegalArgumentException> {
            before.prepare {
                TempPool.getVarIdentify()
                TempPool.getFunctionIdentify("helper")
                TempPool.getFunctionParamIdentify()
                TempPool.getAnonymousTemplateIdentify()
                TempPool.getNamespaceIdentify()
                Project.errorCount = 3
                Project.warningCount = 4
                callbacks.clear()
                Project.stageProcessor[Project.CompileStage.COMPILE.ordinal] = arrayListOf()
                throw IllegalArgumentException("abort")
            }
        }
        assertSame(callbacks, Project.stageProcessor[Project.CompileStage.COMPILE.ordinal])
        assertSame(callback, callbacks.single())
        assertEquals(0, callbackCalls)
        assertEquals(0, Project.errorCount)
        assertEquals(0, Project.warningCount)
        assertEquals(
            names,
            listOf(
                TempPool.varCount,
                TempPool.functionCount,
                TempPool.functionParam,
                TempPool.anonymousTemplateCount,
                TempPool.namespaceCount
            )
        )
    }

    @Test fun successfulPreparationRunsOnceAndAppliesCapturedNamesWithoutReexecution() = isolated {
        var calls = 0
        var name = ""
        val before = ProjectStateJournal.checkpoint()
        val count = TempPool.functionCount
        val prepared = before.prepare {
            calls++
            name = TempPool.getFunctionIdentify("nativeHelper")
            Project.macroFunction[name] = "say prepared"
        }
        assertEquals(1, calls)
        assertEquals(count, TempPool.functionCount)
        assertFalse(Project.macroFunction.containsKey(name))
        repeat(2) {
            prepared.apply()
            assertEquals(count + 1, TempPool.functionCount)
            assertEquals("say prepared", Project.macroFunction[name])
            prepared.rollback()
            assertEquals(count, TempPool.functionCount)
            assertFalse(Project.macroFunction.containsKey(name))
        }
        assertEquals(1, calls)
        assertEquals(name, TempPool.getFunctionIdentify("nativeHelper"))
    }

    @Test fun projectInitCanBeUndoneAndMemberDeepStateIsExplicitlyComposed() = isolated {
        val value = MCInt(3, "old").apply { bindDeclaration() }
        val runtime = Function("afterInit", "journal", null)
        Project.projectTick = runtime
        Project.constants["value"] = value
        val before = ProjectStateJournal.checkpoint()
        val storage = StorageAccess.checkpoint(before.elements.filterIsInstance<Var<*>>())
        val functions = FunctionStateJournal.checkpoint(
            before.elements.filterIsInstance<Function>().filter { it === runtime }
        )
        Project.init()
        Function.currFunction = runtime
        value.identifier = "after"
        assertFalse(StorageAccess.write(value, MCInt(9)).isError)
        assertEquals(0, Project.errorCount, "The preparation must perform a successful mutable declaration write")
        assertEquals(9, (StorageAccess.read(value) as MCInt).value)
        val after = before.captureCurrent()
        val storageAfter = storage.captureCurrent()
        val functionsAfter = functions.captureCurrent()
        before.restore()
        storage.restore()
        functions.restore()
        assertSame(value, Project.constants["value"])
        assertEquals("old", value.identifier)
        assertEquals(3, (StorageAccess.read(value) as MCInt).value)
        assertTrue(runtime.commands.isEmpty())
        after.restore()
        storageAfter.restore()
        functionsAfter.restore()
        assertTrue(Project.constants.isEmpty())
        assertEquals("after", value.identifier)
        assertEquals(9, (StorageAccess.read(value) as MCInt).value)
        Function.currFunction = runtime
        StorageAccess.materialize(value)
        assertEquals(
            9,
            ScoreCommandExecutor(
                listOf("data modify storage mcfpp:system stack_frame prepend value {}") +
                    runtime.commands.analyzeAll()
            ).read(value)
        )
        assertEquals(0, Project.errorCount)
        before.restore()
        storage.restore()
        functions.restore()
        assertSame(value, Project.constants["value"])
        assertEquals(3, (StorageAccess.read(value) as MCInt).value)
        assertTrue(runtime.commands.isEmpty())
    }

    @Test fun unknownContainerIsRejectedBeforeSizeIteratorOrCopyCallbacks() = isolated {
        var calls = 0
        val unknown = object : ArrayList<Module>() {
            override val size: Int get() {
                calls++
                return super.size
            }
            override fun iterator(): MutableIterator<Module> {
                calls++
                return super.iterator()
            }
            override fun toArray(): Array<Any?> {
                calls++
                return super.toArray()
            }
        }
        val before = ProjectStateJournal.checkpoint()
        Project.modules = unknown
        assertFailsWith<IllegalStateException> { before.captureCurrent() }
        assertEquals(0, calls)
        before.restore()
    }

    @Test fun unknownConstantAndErasedHeaderPayloadAreRejectedBeforeHashOrHostCallbacks() = isolated {
        var calls = 0
        val host = object {
            override fun hashCode(): Int {
                calls++
                return 7
            }
            override fun equals(other: Any?): Boolean {
                calls++
                return this === other
            }
            override fun toString(): String {
                calls++
                return "host"
            }
        }
        val before = ProjectStateJournal.checkpoint()
        Project.constants[host] = MCInt(1)
        calls = 0
        assertFailsWith<IllegalStateException> { ProjectStateJournal.checkpoint() }
        assertEquals(0, calls)
        before.restore()

        @Suppress("UNCHECKED_CAST")
        val arguments = listOf(host) as List<TypeId>
        val tainted = TypeId.Applied(TypeId.Builtin("test"), arguments)
        Project.journalState().invalidHeaders.add(tainted)
        calls = 0
        assertFailsWith<IllegalStateException> { before.captureCurrent() }
        assertEquals(0, calls)
        before.restore()
        assertEquals(0, calls)
    }

    @Test fun unknownConfigAndNestedStringContainersAreRejectedBeforeCallbacks() = isolated {
        var calls = 0
        val before = ProjectStateJournal.checkpoint()
        Project.config = object : ProjectConfig() {
            override fun hashCode(): Int {
                calls++
                return 1
            }
            override fun toString(): String {
                calls++
                return "config"
            }
        }
        assertFailsWith<IllegalStateException> { before.captureCurrent() }
        assertEquals(0, calls)
        before.restore()
        Project.config.includes = object : ArrayList<String>() {
            override fun iterator(): MutableIterator<String> {
                calls++
                return super.iterator()
            }
            override fun toArray(): Array<Any?> {
                calls++
                return super.toArray()
            }
        }
        assertFailsWith<IllegalStateException> { before.captureCurrent() }
        assertEquals(0, calls)
        before.restore()
    }

    private class ProbeConstructor(owner: DataTemplate, private val action: () -> Unit) : DataTemplateConstructor(owner, null) {
        override fun prepareBody(target: Function) {
            action()
        }
    }
}
