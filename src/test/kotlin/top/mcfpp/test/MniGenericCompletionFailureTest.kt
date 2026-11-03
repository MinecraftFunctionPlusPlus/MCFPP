package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.SpecializationKeys
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.expand.DeclarationStateJournal
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.DataTemplateParam
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.pairCompanion
import top.mcfpp.model.compound.GenericObjectDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPConcreteType
import kotlin.test.*
import kotlin.test.Test

class MniGenericCompletionFailureTest {
    private fun body(source: String) = mcfppParser(
        CommonTokenStream(
            mcfppLexer(CharStreams.fromString(source))
        )
    ).templateBody()

    private fun generic(
        namespace: Namespace,
        file: MCFPPFile,
        name: String = "Box",
        source: String = "{var value:int=3; func unused():int{return 7;}}"
    ) = register(GenericDataTemplate(body(source), name, namespace.identifier), namespace, file)

    private fun <T : GenericDataTemplate> register(template: T, namespace: Namespace, file: MCFPPFile): T = template.apply {
        declarationFile = file
        readOnlyParams.add(DataTemplateParam("type", "T", MCFPPConcreteType.Type))
        namespace.scope.genericTemplate[identifier to listOf(MCFPPConcreteType.Type)] = this
    }

    private fun argument() = MCFPPTypeVar(MCFPPBaseType.Int, "T")

    private fun isolated(action: (Namespace, MCFPPFile) -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val base = DataTemplate.baseDataTemplate.checkpointDeclarationState()
        val caller = Function.currFunction
        val forced = Function.forcedField
        val callerFile = MCFPPFile.currFile
        val callerTemplate = DataTemplate.currTemplate
        val settings = listOf(
            CompileSettings.ignoreStdLib,
            CompileSettings.isDebug,
            CompileSettings.isLib,
            CompileSettings.printAll,
            CompileSettings.foldIRConstants
        )
        val inlineLimit = CompileSettings.maxWhileInline
        try {
            Project.config = ProjectConfig(version = "26.3")
            CompileSettings.ignoreStdLib = false
            MCFPPStringTest.readFromString("func main(){}", version = "26.3")
            assertEquals(0, Project.errorCount)
            val namespace = GlobalScope.getOrCreateNamespace("completion.generic")
            val file = MCFPPFile(namespace)
            MCFPPFile.currFile = file
            Project.currNamespace = namespace.identifier
            action(namespace, file)
        } finally {
            base.restore()
            namespaces.restore()
            project.restore()
            Function.currFunction = caller
            Function.forcedField = forced
            MCFPPFile.currFile = callerFile
            DataTemplate.currTemplate = callerTemplate
            CompileSettings.ignoreStdLib = settings[0]
            CompileSettings.isDebug = settings[1]
            CompileSettings.isLib = settings[2]
            CompileSettings.printAll = settings[3]
            CompileSettings.foldIRConstants = settings[4]
            CompileSettings.maxWhileInline = inlineLimit
        }
    }

    @Test fun missingParentInActualHeaderRemainsFailedAfterTheCounterIsCleared() = isolated { namespace, file ->
        val prototype = generic(namespace, file).apply { parentID.add("DefinitelyMissingParent") }
        lateinit var instance: CompiledGenericDataTemplate
        Project.collectingTemplateSignatures {
            instance = assertNotNull(prototype.compile(listOf(argument())))
            assertTrue(prototype.hasDeclarationCompletionFailure)
            assertFalse(instance.hasCompletedDeclaration)
        }
        assertTrue(Project.errorCount > 0)
        assertFalse(instance.hasCompletedDeclaration)
        Project.errorCount = 0
        Project.registerTemplateContract(instance)
        assertFalse(instance.hasCompletedDeclaration)
        assertTrue(prototype.hasDeclarationCompletionFailure)
        assertEquals(0, Project.errorCount)
    }

    @Test fun finalParentInActualHeaderRemainsFailedAfterTheCounterIsCleared() = isolated { namespace, file ->
        val parent = DataTemplate("FinalParent", namespace.identifier).apply {
            isFinal = true
            declarationFile = file
        }
        namespace.scope.addTemplate(parent.identifier, parent, false)
        val prototype = generic(namespace, file).apply { parentID.add(parent.identifier) }
        val instance = assertNotNull(prototype.compile(listOf(argument())))
        assertTrue(Project.errorCount > 0)
        assertTrue(prototype.hasDeclarationCompletionFailure)
        Project.errorCount = 0
        Project.registerTemplateContract(instance)
        assertFalse(instance.hasCompletedDeclaration)
        assertEquals(0, Project.errorCount)
    }

    @Test fun anInvalidReadonlyCallDoesNotPermanentlyDamageAHealthyPrototype() = isolated { namespace, file ->
        val prototype = generic(namespace, file)
        assertNull(prototype.compile(listOf(MCInt(7, "wrongKind"))))
        assertTrue(Project.errorCount > 0)
        assertFalse(prototype.hasDeclarationCompletionFailure)
        assertTrue(prototype.compiledTemplates.isEmpty())
        Project.errorCount = 0
        val instance = assertNotNull(prototype.compile(listOf(argument())))
        assertTrue(instance.hasCompletedDeclaration)
        assertFalse(instance.hasDeclarationCompletionFailure)
        assertEquals(0, Project.errorCount)
    }

    @Test fun preexistingErrorsDoNotBecomeAnOwnersStageFailure() = isolated { namespace, file ->
        val prototype = generic(namespace, file)
        Project.errorCount = 3
        prototype.prepareHeader()
        assertEquals(3, Project.errorCount)
        assertFalse(prototype.hasDeclarationCompletionFailure)
        assertFalse(prototype.hasCompletedDeclaration)
        Project.errorCount = 0
        assertTrue(assertNotNull(prototype.compile(listOf(argument()))).hasCompletedDeclaration)
    }

    @Test fun successfulHeaderPreparationNeverGrantsACompletionProof() = isolated { namespace, file ->
        val prototype = generic(namespace, file)
        repeat(2) { prototype.prepareHeader() }
        assertFalse(prototype.hasDeclarationCompletionFailure)
        assertFalse(prototype.hasCompletedDeclaration)
        assertTrue(prototype.compiledTemplates.isEmpty())
        assertEquals(0, prototype.index)
        assertEquals(0, Project.errorCount)
    }

    @Test fun concreteFieldFailureIsRecordedBeforeTheBarrierAndDoesNotPoisonThePrototypeOrSharedParent() = isolated { namespace, file ->
        val prototype = generic(namespace, file, source = "{var value:int=3; var value:int=4;}")
        lateinit var instance: CompiledGenericDataTemplate
        Project.collectingTemplateSignatures {
            instance = assertNotNull(prototype.compile(listOf(argument())))
            assertTrue(instance.hasDeclarationCompletionFailure)
            assertFalse(prototype.hasDeclarationCompletionFailure)
        }
        assertTrue(Project.errorCount > 0)
        assertFalse(instance.hasCompletedDeclaration)
        Project.errorCount = 0
        Project.registerTemplateContract(DataTemplate.baseDataTemplate)
        assertTrue(DataTemplate.baseDataTemplate.hasCompletedDeclaration)
        Project.registerTemplateContract(instance)
        assertFalse(instance.hasCompletedDeclaration)
        val healthy = generic(namespace, file, "Healthy")
        assertTrue(assertNotNull(healthy.compile(listOf(argument()))).hasCompletedDeclaration)
    }

    @Test fun concreteAnnotationResolutionFailureIsRecordedBeforeTheBarrier() = isolated { namespace, file ->
        val prototype = generic(namespace, file, source = "{@DefinitelyMissingAnnotation var value:int=3;}")
        lateinit var instance: CompiledGenericDataTemplate
        Project.collectingTemplateSignatures {
            instance = assertNotNull(prototype.compile(listOf(argument())))
            assertTrue(instance.hasDeclarationCompletionFailure)
            assertFalse(prototype.hasDeclarationCompletionFailure)
        }
        assertTrue(Project.errorCount > 0)
        Project.errorCount = 0
        Project.registerTemplateContract(instance)
        assertFalse(instance.hasCompletedDeclaration)
        assertEquals(0, Project.errorCount)
    }

    @Test fun failedCompanionBindingAlsoMarksTheActualOwningInstance() = isolated { namespace, file ->
        val companion = GenericObjectDataTemplate(body("{var value:int; var value:int;}"), "Box", namespace.identifier).apply {
            declarationFile = file
            readOnlyParams.add(DataTemplateParam("type", "T", MCFPPConcreteType.Type))
        }
        namespace.scope.objects.add(companion)
        val prototype = generic(namespace, file)
        assertTrue(pairCompanion(prototype, companion))
        val instance = assertNotNull(prototype.compile(listOf(argument())))
        val boundCompanion = assertNotNull(
            companion.materializedTemplate(
                SpecializationKeys.forArguments(companion.declarationId, listOf(argument()))
            )
        )
        assertSame(companion, boundCompanion.originTemplate)
        assertSame(boundCompanion, instance.companionObject)
        assertTrue(companion.compiledTemplates.isEmpty())
        assertTrue(prototype.compiledTemplates.isEmpty())
        assertTrue(Project.errorCount > 0)
        assertTrue(instance.hasDeclarationCompletionFailure)
        assertTrue(boundCompanion.hasDeclarationCompletionFailure)
        assertFalse(prototype.hasDeclarationCompletionFailure)
        Project.errorCount = 0
        Project.registerTemplateContract(instance)
        Project.registerTemplateContract(boundCompanion)
        assertFalse(instance.hasCompletedDeclaration)
        assertFalse(boundCompanion.hasCompletedDeclaration)
        assertTrue(instance.hasDeclarationCompletionFailure)
        assertTrue(boundCompanion.hasDeclarationCompletionFailure)
        assertTrue(companion.compiledTemplates.isEmpty())
        assertTrue(prototype.compiledTemplates.isEmpty())
        assertEquals(0, Project.errorCount)
    }

    @Test fun thrownConcreteInitializationMarksOnlyTheActualInstanceAndCannotBeErasedByRequeueing() = isolated { namespace, file ->
        val prototype = register(ThrowingGeneric(body("{}"), namespace.identifier, false), namespace, file)
        assertFailsWith<IllegalStateException> { prototype.compile(listOf(argument())) }
        val instance = assertNotNull(prototype.created)
        assertTrue(instance.hasDeclarationCompletionFailure)
        assertFalse(prototype.hasDeclarationCompletionFailure)
        assertEquals(0, Project.errorCount)
        Project.registerTemplateContract(instance)
        assertFalse(instance.hasCompletedDeclaration)
        assertTrue(prototype.compiledTemplates.isEmpty())
    }

    @Test fun thrownFactoryMarksItsPrototypeInsteadOfAnyHealthySharedParent() = isolated { namespace, file ->
        val prototype = register(ThrowingGeneric(body("{}"), namespace.identifier, true), namespace, file)
        assertFailsWith<IllegalStateException> { prototype.compile(listOf(argument())) }
        assertTrue(prototype.hasDeclarationCompletionFailure)
        assertNull(prototype.created)
        assertFalse(DataTemplate.baseDataTemplate.hasDeclarationCompletionFailure)
        assertTrue(prototype.compiledTemplates.isEmpty())
    }

    @Test fun realHeaderFailureAndRetryStateRestoreInBothDirectionsThroughTheDeepCheckpoint() = isolated { namespace, file ->
        val prototype = generic(namespace, file).apply { parentID.add("DefinitelyMissingParent") }
        val before = DeclarationStateJournal.checkpoint(listOf(prototype))
        prototype.prepareHeader()
        assertTrue(Project.errorCount > 0)
        assertTrue(prototype.hasDeclarationCompletionFailure)
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertFalse(prototype.hasDeclarationCompletionFailure)
            after.restore()
            assertTrue(prototype.hasDeclarationCompletionFailure)
        }
        before.restore()
        prototype.parentID.clear()
        Project.errorCount = 0
        val instance = assertNotNull(prototype.compile(listOf(argument())))
        assertTrue(instance.hasCompletedDeclaration)
        assertFalse(prototype.hasDeclarationCompletionFailure)
        assertEquals(0, Project.errorCount)
    }

    private class ThrowingGeneric(
        context: mcfppParser.TemplateBodyContext,
        namespace: String,
        private val throwInFactory: Boolean
    ) : GenericDataTemplate(context, "Throwing", namespace) {
        var created: CompiledGenericDataTemplate? = null
        override fun createCompiledTemplate(
            identifier: String,
            args: List<Var<*>>,
            argumentValues: List<CompilerValue>
        ): CompiledGenericDataTemplate {
            if (throwInFactory) error("Actual factory failed")
            return object : CompiledGenericDataTemplate(identifier, namespace, this@ThrowingGeneric, args, argumentValues) {
                override fun initialize() {
                    error("Actual initialization failed")
                }
            }.also { created = it }
        }
    }
}
