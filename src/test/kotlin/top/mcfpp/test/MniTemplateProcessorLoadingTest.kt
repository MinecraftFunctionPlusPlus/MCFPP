package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.DataTemplateAnnotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPPrivateType
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import top.mcfpp.antlr.mcfppParser as P

class MniTemplateProcessorLoadingTest {
    class Processor : TemplateProcessor {
        init {
            constructions++
        }
        override fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits {
            expansions++
            error("Source request collection must not expand processors")
        }
    }

    class Adapter :
        DataTemplateAnnotation("Adapter", "fixture.requests"),
        TemplateProcessor {
        init {
            constructions++
        }
        override fun forDataTemplate(data: DataTemplate) {
            legacyApplications++
        }
        override fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits {
            expansions++
            error("Source request collection must not expand adapters")
        }
    }

    class Legacy : DataTemplateAnnotation("Legacy", "fixture.requests") {
        init {
            legacyConstructions++
        }
        override fun forDataTemplate(data: DataTemplate) {
            legacyApplications++
        }
    }

    class InvalidAdapter private constructor() :
        DataTemplateAnnotation("Invalid", "fixture.requests"),
        TemplateProcessor {
        init {
            constructions++
        }
        override fun forDataTemplate(data: DataTemplate) {
            legacyApplications++
        }
        override fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits = error("Invalid adapter")
    }

    private fun namespace(name: String) = GlobalScope.localNamespaces.getOrPut(name) { Namespace(name) }

    /** Real source files, parser caches, type/field indexing and annotation pass. */
    private fun index(vararg sources: String): List<MCFPPFile> {
        val files = sources.mapIndexed { index, source ->
            val path = Project.config.sourcePath!!.resolve("input-$index.mcfpp")
            Files.writeString(path, source.trimIndent())
            val file = MCFPPFile(path.toString())
            val tokens = CommonTokenStream(mcfppLexer(CharStreams.fromString(source.trimIndent(), path.toString())))
            val parser = P(tokens)
            val tree = parser.compilationUnit()
            assertEquals(0, parser.numberOfSyntaxErrors)
            Project.tokens[file] = tokens
            Project.trees[file] = tree
            Project.journalState().sourceFiles.add(file)
            file
        }
        Project.indexType()
        Project.resolveField()
        assertEquals(0, Project.errorCount)
        return files
    }

    private inline fun fixture(jars: List<String> = emptyList(), action: () -> Unit) {
        val namespaces = NamespaceStateJournal.checkpoint()
        val project = ProjectStateJournal.checkpoint()
        val config = Project.config
        val loader = Project.classLoader
        val current = Function.currFunction
        val forced = Function.forcedField
        val template = DataTemplate.currTemplate
        val sourceFile = MCFPPFile.currFile
        val ignore = CompileSettings.ignoreStdLib
        val directory = Files.createTempDirectory("mcfpp-processor-source-")
        try {
            Project.config = ProjectConfig(version = "26.3", jars = ArrayList(jars))
            CompileSettings.ignoreStdLib = true
            Project.init()
            Project.readProject()
            Project.config.sourcePath = directory
            constructions = 0
            expansions = 0
            legacyApplications = 0
            legacyConstructions = 0
            action()
        } finally {
            if (Project.classLoader !== loader) (Project.classLoader as? URLClassLoader)?.close()
            namespaces.restore()
            project.restore()
            Project.config = config
            Project.classLoader = loader
            Function.currFunction = current
            Function.forcedField = forced
            DataTemplate.currTemplate = template
            MCFPPFile.currFile = sourceFile
            CompileSettings.ignoreStdLib = ignore
            directory.toFile().deleteRecursively()
        }
    }

    private fun noHost() {
        assertEquals(0, constructions)
        assertEquals(0, expansions)
        assertEquals(0, legacyApplications)
    }

    @Test fun actualSourceFilesRecordInOriginalOrderAndRepeatedAstVisitsReuseRequestsWithoutCompilingBodies() = fixture {
        val files = index(
            """
            namespace fixture.requests
            @Mark(1)
            @Mark(2)
            data First {var value:int=1;}
            func firstBody(){missingBodyCall();}
        """,
            """
            namespace fixture.requests
            @Mark(3)
            data Second {}
            func secondBody(){anotherMissingBodyCall();}
        """
        )
        val owner = namespace("fixture.requests")
        assertTrue(owner.registerTemplateProcessor("fixture.requests:Mark", Processor::class.java))
        files.reversed().forEach(MCFPPFile::runAnnotation)
        val requests = owner.templateProcessors.requests()
        assertEquals(listOf("First", "First", "Second"), requests.map { it.target.identifier })
        assertEquals(
            listOf(1L, 2L, 3L),
            requests.map { (it.arguments.single() as CompilerValue.Typed).payload }
                .map { (it as CompilerValue.Integral).value }
        )
        assertEquals(listOf(0, 0, 1), requests.map { it.order.file })
        assertTrue(requests[0].order.annotation < requests[1].order.annotation)
        assertSame(files[0], requests[0].sourceFile)
        assertSame(requests[0].target.declarationFile, requests[0].sourceFile)
        files.forEach(MCFPPFile::runAnnotation)
        owner.templateProcessors.requests().zip(requests).forEach { (again, original) -> assertSame(original, again) }
        assertEquals(3, owner.templateProcessors.checkpoint().requests.size)
        val bodies = owner.scope.functions.values.flatten().filter { it.identifier.endsWith("Body") }
        assertEquals(2, bodies.size)
        assertTrue(bodies.all { !it.bodyCompiled && it.commands.isEmpty() })
        assertEquals(0, Project.errorCount)
        noHost()
    }

    @Test fun realAnnotationArgumentsKeepTheirTypedScalarAndCompositeCompilerFacts() = fixture {
        val files = index(
            """
            namespace fixture.requests
            @Mark(1,1.25f,true,"saved",[1,2],{entry:3},null)
            data Facts {}
        """
        )
        val owner = namespace("fixture.requests")
        assertTrue(owner.registerTemplateProcessor("fixture.requests:Mark", Processor::class.java))
        files.forEach(MCFPPFile::runAnnotation)
        val arguments = owner.templateProcessors.requests().single().arguments.map { assertIs<CompilerValue.Typed>(it) }
        assertEquals(MCFPPBaseType.Int.typeId, arguments[0].type)
        assertEquals(CompilerValue.Integral(1), arguments[0].payload)
        assertEquals(MCFPPBaseType.Float.typeId, arguments[1].type)
        assertEquals(CompilerValue.FloatBits(1.25f.toRawBits()), arguments[1].payload)
        assertEquals(CompilerValue.Bool(true), arguments[2].payload)
        assertEquals(CompilerValue.Text("saved"), arguments[3].payload)
        assertEquals(
            listOf(1L, 2L),
            assertIs<CompilerValue.Sequence>(arguments[4].payload).elements.map {
                assertIs<CompilerValue.Integral>(assertIs<CompilerValue.Typed>(it).payload).value
            }
        )
        assertEquals(
            CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(3)),
            assertIs<CompilerValue.Record>(arguments[5].payload).fields.getValue("entry")
        )
        assertEquals(MCFPPPrivateType.Null.typeId, arguments[6].type)
        assertSame(CompilerValue.NullValue, arguments[6].payload)
        assertFailsWith<UnsupportedOperationException> {
            (arguments[4].payload as CompilerValue.Sequence).elements.let {
                @Suppress("UNCHECKED_CAST")
                (it as MutableList<CompilerValue>).clear()
            }
        }
        assertEquals(0, Project.errorCount)
        noHost()
    }

    @Test fun wildcardImportResolvesExplicitMappingsAndLocalMappingsKeepNamespacePriority() = fixture {
        val imported = namespace("fixture.provider")
        assertTrue(imported.registerTemplateProcessor("fixture.provider:Imported", Processor::class.java))
        assertTrue(imported.registerTemplateProcessor("fixture.provider:Mark", Processor::class.java))
        val owner = namespace("fixture.requests")
        assertTrue(owner.registerTemplateProcessor("fixture.requests:Mark", Processor::class.java))
        val files = index(
            """
            namespace fixture.requests
            import fixture.provider:*
            @Imported(4)
            @Mark(5)
            data Target {}
        """
        )
        files.forEach(MCFPPFile::runAnnotation)
        val importedRequest = imported.templateProcessors.requests().single()
        val localRequest = owner.templateProcessors.requests().single()
        assertEquals("fixture.provider:Imported", importedRequest.registration.annotationName)
        assertEquals("fixture.requests:Mark", localRequest.registration.annotationName)
        assertSame(importedRequest.target, localRequest.target)
        assertSame(files.single(), importedRequest.sourceFile)
        assertTrue(importedRequest.order.annotation < localRequest.order.annotation)
        assertEquals(0, Project.errorCount)
        noHost()
    }

    @Test fun invisibleRegistrationsAreNotFoundBySearchingEveryProjectNamespace() = fixture {
        val hidden = namespace("fixture.hidden")
        assertTrue(hidden.registerTemplateProcessor("fixture.hidden:Hidden", Processor::class.java))
        val files = index("namespace fixture.requests\n@Hidden\ndata Target {}")
        files.forEach(MCFPPFile::runAnnotation)
        assertEquals(1, Project.errorCount)
        assertTrue(hidden.templateProcessors.requests().isEmpty())
        assertTrue(namespace("fixture.requests").templateProcessors.requests().isEmpty())
        noHost()
    }

    @Test fun invalidLegacyAdaptersAreRejectedWithoutFallingBackToTheirLegacyConstructor() = fixture {
        val files = index("namespace fixture.requests\n@Invalid\ndata Target {}")
        val owner = namespace("fixture.requests")
        assertTrue(owner.scope.addAnnotation("Invalid", InvalidAdapter::class.java))
        files.forEach(MCFPPFile::runAnnotation)
        assertEquals(1, Project.errorCount)
        assertTrue(owner.templateProcessors.registrations().isEmpty())
        assertTrue(owner.templateProcessors.requests().isEmpty())
        noHost()
    }

    @Test fun aNonClosedCompositeArgumentRecordsNoPartialRequestOrHostExecution() = fixture {
        val files = index("namespace fixture.requests\n@Mark(1,[unavailable])\ndata Target {}")
        val owner = namespace("fixture.requests")
        assertTrue(owner.registerTemplateProcessor("fixture.requests:Mark", Processor::class.java))
        files.forEach(MCFPPFile::runAnnotation)
        assertEquals(1, Project.errorCount)
        assertTrue(owner.templateProcessors.requests().isEmpty())
        noHost()
    }

    @Test fun processorsOnFunctionsFieldsAndDanglingInputsAreRejectedWithoutLeakingToLaterTemplates() = fixture {
        val files = index(
            """
            namespace fixture.requests
            @Mark
            func ordinary(){neverCompileThis();}
            data Fields {
                @Mark
                var value:int=1;
            }
            data Later {}
            @Mark
        """
        )
        val owner = namespace("fixture.requests")
        assertTrue(owner.registerTemplateProcessor("fixture.requests:Mark", Processor::class.java))
        files.forEach(MCFPPFile::runAnnotation)
        assertEquals(3, Project.errorCount)
        assertTrue(owner.templateProcessors.requests().isEmpty())
        assertFalse(owner.scope.functions.getValue("ordinary").single().bodyCompiled)
        noHost()
    }

    @Test fun ordinaryLegacyAnnotationsStillConstructAndApplyAtTheirExistingCompletionBoundary() = fixture {
        val files = index("namespace fixture.requests\n@Legacy\n@Mark\ndata Target {}")
        val owner = namespace("fixture.requests")
        assertTrue(owner.scope.addAnnotation("Legacy", Legacy::class.java))
        assertTrue(owner.registerTemplateProcessor("fixture.requests:Mark", Processor::class.java))
        files.forEach(MCFPPFile::runAnnotation)
        val target = owner.templateProcessors.requests().single().target
        assertEquals(1, legacyConstructions)
        assertEquals(0, legacyApplications)
        assertIs<Legacy>(target.pendingDeclarationAnnotations.single())
        assertSame(target.annotations.single(), target.pendingDeclarationAnnotations.single())
        Project.completeTemplateDeclarations()
        assertEquals(1, legacyApplications)
        assertTrue(target.pendingDeclarationAnnotations.isEmpty())
        assertEquals(0, constructions)
        assertEquals(0, expansions)
        assertEquals(0, Project.errorCount)
    }

    @Test fun annotationMetadataDoesNotAutomaticallyBecomeAnotherSourceProcessorRequest() = fixture {
        val files = index("namespace fixture.requests\ndata Target {}")
        val owner = namespace("fixture.requests")
        assertTrue(owner.registerTemplateProcessor("fixture.requests:Adapter", Adapter::class.java))
        val target = owner.scope.getTemplate("Target")!!
        val metadata = Adapter()
        target.annotations.add(metadata)
        constructions = 0
        files.forEach(MCFPPFile::runAnnotation)
        assertSame(metadata, target.annotations.single())
        assertTrue(owner.templateProcessors.requests().isEmpty())
        assertEquals(0, Project.errorCount)
        noHost()
    }

    @Test fun actualConfiguredJarLoaderFindsAndRecordsImportedProcessorsWithoutClassInitialization() {
        val directory = Files.createTempDirectory("mcfpp-loading-processor-jar-")
        val marker = "mcfpp.loading.processor.${java.util.UUID.randomUUID()}"
        try {
            val source = directory.resolve("LoadingJarProcessor.java")
            Files.writeString(
                source,
                """
                import top.mcfpp.mni.api.*;
                public class LoadingJarProcessor implements TemplateProcessor {
                    static { System.setProperty("$marker", "initialized"); }
                    public LoadingJarProcessor() { System.setProperty("$marker", "constructed"); }
                    public DeclarationEdits expand(DeclarationContext context, TemplateView template) {
                        System.setProperty("$marker", "expanded");
                        throw new AssertionError("source collection invoked expand");
                    }
                }
                """.trimIndent()
            )
            val classpath = listOf(TemplateProcessor::class.java, kotlin.Unit::class.java)
                .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }.distinct()
                .joinToString(java.io.File.pathSeparator)
            assertEquals(
                0,
                ToolProvider.getSystemJavaCompiler().run(
                    null, null, null,
                    "-proc:none", "-classpath", classpath, "-d", directory.toString(), source.toString()
                )
            )
            val jar = directory.resolve("processor.jar")
            JarOutputStream(Files.newOutputStream(jar)).use { output ->
                output.putNextEntry(JarEntry("LoadingJarProcessor.class"))
                Files.copy(directory.resolve("LoadingJarProcessor.class"), output)
                output.closeEntry()
            }
            fixture(listOf(jar.toString())) {
                val owner = namespace("fixture.jar")
                assertTrue(owner.registerTemplateProcessor("fixture.jar:Jar", "LoadingJarProcessor"))
                val files = index("namespace fixture.requests\nimport fixture.jar:*\n@Jar(7)\ndata Target {}")
                files.forEach(MCFPPFile::runAnnotation)
                val request = owner.templateProcessors.requests().single()
                assertSame(Project.classLoader, request.registration.processorClass.classLoader)
                assertEquals("LoadingJarProcessor", request.registration.processorClass.name)
                assertEquals(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(7)), request.arguments.single())
                assertNull(System.getProperty(marker), "Recording must not initialize, construct, or expand a configured processor")
                assertEquals(0, Project.errorCount)
                noHost()
            }
        } finally {
            System.clearProperty(marker)
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun legacyAdapterUsesOnlyTheNewProtocolForRealNamedDeclarationKinds() = fixture {
        val files = index(
            """
            namespace fixture.requests
            @Adapter
            data Plain {}
            @Adapter
            data Generic<T> {}
            @Adapter
            object data Singleton {}
            @Adapter
            abstract data Contract {}
        """
        )
        val owner = namespace("fixture.requests")
        assertTrue(owner.scope.addAnnotation("Adapter", Adapter::class.java))
        files.forEach(MCFPPFile::runAnnotation)
        assertEquals(
            listOf("Plain", "Generic", "Singleton", "Contract"),
            owner.templateProcessors.requests().map { it.target.identifier }
        )
        owner.templateProcessors.requests().forEach { request ->
            assertSame(Adapter::class.java, request.registration.processorClass)
            assertTrue(request.target.annotations.isEmpty())
            assertTrue(request.target.pendingDeclarationAnnotations.isEmpty())
        }
        assertFalse(owner.templateProcessors.registrations().isEmpty())
        assertEquals(0, Project.errorCount)
        noHost()
    }

    @Test fun deferredGenericBodiesRejectProcessorPlacementWithoutEvaluatingLegacyAnnotationsOrLeakingInputs() = fixture {
        val files = index(
            """
            namespace fixture.requests
            data Box<T> {
                @Mark
                var value:int=1;
                @Legacy
                var deferred:int=2;
            }
            object data Single<T> {
                @Mark
                var value:int=1;
            }
            abstract data Contract<T> {
                @Adapter
                abstract func read():int;
            }
            data Later {}
        """
        )
        val owner = namespace("fixture.requests")
        assertTrue(owner.registerTemplateProcessor("fixture.requests:Mark", Processor::class.java))
        assertTrue(owner.scope.addAnnotation("Adapter", Adapter::class.java))
        assertTrue(owner.scope.addAnnotation("Legacy", Legacy::class.java))
        files.forEach(MCFPPFile::runAnnotation)
        assertEquals(3, Project.errorCount)
        assertTrue(owner.templateProcessors.requests().isEmpty())
        assertNull(owner.templateProcessors.find("fixture.requests:Adapter"))
        val later = owner.scope.getTemplate("Later")!!
        assertTrue(later.annotations.isEmpty())
        assertTrue(later.pendingDeclarationAnnotations.isEmpty())
        assertEquals(0, legacyConstructions)
        noHost()
    }

    @Test fun aLocalLegacyAnnotationTakesPriorityOverALibraryProcessorWithTheSameNamespaceAndName() = fixture {
        val files = index("namespace fixture.requests\n@Mark\ndata Target {}")
        val local = namespace("fixture.requests")
        assertTrue(local.scope.addAnnotation("Mark", Legacy::class.java))
        val library = Namespace("fixture.requests")
        GlobalScope.libNamespaces[library.identifier] = library
        assertTrue(library.registerTemplateProcessor("fixture.requests:Mark", Processor::class.java))
        files.forEach(MCFPPFile::runAnnotation)
        val target = local.scope.getTemplate("Target")!!
        assertEquals(1, legacyConstructions)
        assertEquals(0, legacyApplications)
        assertIs<Legacy>(target.pendingDeclarationAnnotations.single())
        assertSame(target.annotations.single(), target.pendingDeclarationAnnotations.single())
        assertTrue(local.templateProcessors.requests().isEmpty())
        assertTrue(library.templateProcessors.requests().isEmpty())
        Project.completeTemplateDeclarations()
        assertEquals(1, legacyApplications)
        assertTrue(target.pendingDeclarationAnnotations.isEmpty())
        assertTrue(library.templateProcessors.requests().isEmpty())
        assertEquals(0, constructions)
        assertEquals(0, expansions)
        assertEquals(0, Project.errorCount)
    }

    companion object {
        var constructions = 0
        var expansions = 0
        var legacyApplications = 0
        var legacyConstructions = 0
    }
}
