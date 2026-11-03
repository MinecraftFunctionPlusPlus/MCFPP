package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.DataTemplateAnnotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.TypeId
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

class MniTemplateProcessorRegistryTest {
    companion object {
        var constructions = 0
        var expansions = 0
        var legacyApplications = 0
        var nativeCalls = 0
    }

    class Processor : TemplateProcessor {
        init {
            constructions++
        }
        override fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits {
            expansions++
            error("Discovery executed a processor")
        }
    }
    class OtherProcessor : TemplateProcessor {
        init {
            constructions++
        }
        override fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits {
            expansions++
            error("Discovery executed a processor")
        }
    }
    class CompatibilityProcessor :
        DataTemplateAnnotation("Compat", "registry.owner"),
        TemplateProcessor {
        init {
            constructions++
        }
        override fun forDataTemplate(data: DataTemplate) {
            legacyApplications++
        }
        override fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits {
            expansions++
            error("Discovery executed a compatibility processor")
        }
    }
    abstract class AbstractProcessor : TemplateProcessor
    private class HiddenProcessor : TemplateProcessor {
        override fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits = error("Hidden processor")
    }
    class ArgumentProcessor(val argument: Int) : TemplateProcessor {
        override fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits = error("Argument processor")
    }
    class PrivateConstructorProcessor private constructor() : TemplateProcessor {
        override fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits = error("Private constructor")
    }
    class ExistingNativeProvider {
        companion object {
            @JvmStatic
            @MNIFunction(returnType = "int")
            fun existing(context: NativeCallContext) {
                nativeCalls++
            }
        }
    }

    private fun namespace(): Namespace {
        Project.init()
        constructions = 0
        expansions = 0
        legacyApplications = 0
        nativeCalls = 0
        return Namespace("registry.owner").also { GlobalScope.localNamespaces[it.identifier] = it }
    }

    private data class Input(
        val file: MCFPPFile,
        val declaration: P.TemplateDeclarationContext,
        val annotations: List<P.AnnotationContext>,
        val target: DataTemplate
    )
    private fun nodes(tree: ParseTree): List<ParseTree> = listOf(tree) +
        (0 until tree.childCount).flatMap { nodes(tree.getChild(it)) }
    private fun input(namespace: Namespace, name: String = "Target", repeated: Boolean = false): Input {
        val text = "namespace registry.owner\n@Mark\n" + (if (repeated) "@Mark\n" else "") + "data $name{}"
        val parser = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, "$name.mcfpp"))))
        val tree = parser.compilationUnit()
        assertEquals(0, parser.numberOfSyntaxErrors)
        val file = MCFPPFile(namespace)
        val nodes = nodes(tree)
        return Input(
            file,
            nodes.filterIsInstance<P.TemplateDeclarationContext>().single(),
            nodes.filterIsInstance<P.AnnotationContext>(),
            DataTemplate(name, namespace.identifier).apply { declarationFile = file }
        )
    }
    private val argument get() = CompilerValue.Typed(TypeId.Builtin("int"), CompilerValue.Integral(3))
    private fun record(
        namespace: Namespace,
        input: Input,
        file: Int = 0,
        annotation: Int = 0,
        arguments: List<CompilerValue> = listOf(argument)
    ): TemplateProcessorRequest = assertNotNull(
        namespace.templateProcessors.record(
            namespace.templateProcessors.find("registry.owner:Mark")!!,
            input.target,
            input.file,
            input.declaration,
            input.annotations[annotation],
            arguments,
            TemplateProcessorSourceOrder(file, input.declaration.start.tokenIndex, annotation)
        )
    )
    private fun noHost() {
        assertEquals(0, constructions)
        assertEquals(0, expansions)
        assertEquals(0, legacyApplications)
        assertEquals(0, nativeCalls)
    }

    @Test fun explicitRegistrationIsIdempotentAndDoesNotDisturbExistingNativeDiscovery() {
        val namespace = namespace()
        namespace.injectedBy(ExistingNativeProvider::class.java)
        val native = namespace.scope.functions.getValue("existing").single()
        assertTrue(namespace.registerTemplateProcessor("registry.owner:Mark", Processor::class.java))
        val registration = namespace.templateProcessors.registrations().single()
        assertTrue(namespace.registerTemplateProcessor("registry.owner:Mark", Processor::class.java.name))
        assertSame(registration, namespace.templateProcessors.find("registry.owner:Mark"))
        assertFalse(namespace.registerTemplateProcessor("registry.owner:Mark", OtherProcessor::class.java))
        assertSame(Processor::class.java, namespace.templateProcessors.registrations().single().processorClass)
        assertSame(native, namespace.scope.functions.getValue("existing").single())
        assertEquals(1, Project.errorCount)
        noHost()
    }

    @Test fun annotationNamesUseTheRealNamespaceAndIdentifierGrammar() {
        val namespace = namespace()
        for (name in listOf("Mark", "méthode", "_mark2"))
            assertTrue(namespace.registerTemplateProcessor("registry.owner:$name", Processor::class.java), name)
        for (name in listOf(
            "Mark", "other.owner:Mark", "registry.owner:if", "registry.owner:this",
            "registry.owner:ref", "registry.owner:vec3", "registry.owner:Mark.more",
            "registry.owner:Mark/*comment*/", "registry.owner: Mark", "registry.owner:Mark!"
        ))
            assertFalse(namespace.registerTemplateProcessor(name, Processor::class.java), name)
        assertFalse(Namespace("bad space").registerTemplateProcessor("bad space:Mark", Processor::class.java))
        assertTrue(Namespace("registry.entity").registerTemplateProcessor("registry.entity:Mark", Processor::class.java))
        noHost()
    }

    @Test fun nonProcessorsInaccessibleAbstractAndNonDefaultConstructibleClassesAreRejected() {
        val namespace = namespace()
        val invalid = listOf(
            String::class.java,
            TemplateProcessor::class.java,
            AbstractProcessor::class.java,
            HiddenProcessor::class.java,
            ArgumentProcessor::class.java,
            PrivateConstructorProcessor::class.java
        )
        invalid.forEachIndexed { index, type ->
            assertFalse(namespace.registerTemplateProcessor("registry.owner:Invalid$index", type))
        }
        assertFalse(namespace.registerTemplateProcessor("registry.owner:Missing", "missing.registry.Processor"))
        assertTrue(namespace.templateProcessors.registrations().isEmpty())
        assertEquals(invalid.size + 1, Project.errorCount)
        noHost()
    }

    @Test fun compatibilityAdaptersAreOnlyRecordedAndNeverConstructedOrApplied() {
        val namespace = namespace()
        assertTrue(namespace.registerTemplateProcessor("registry.owner:Mark", CompatibilityProcessor::class.java))
        val input = input(namespace)
        val request = record(namespace, input)
        assertSame(CompatibilityProcessor::class.java, request.registration.processorClass)
        assertSame(input.target, request.target)
        noHost()
        assertEquals(0, Project.errorCount)
    }

    @Test fun requestsAreOrderedBySourceAndDeduplicateOnlyTheSameActualInputAndTarget() {
        val namespace = namespace()
        assertTrue(namespace.registerTemplateProcessor("registry.owner:Mark", Processor::class.java))
        val early = input(namespace, "Early", repeated = true)
        val late = input(namespace, "Late")
        val last = record(namespace, late, file = 1)
        val second = record(namespace, early, annotation = 1)
        val first = record(namespace, early)
        assertSame(first, record(namespace, early))
        assertEquals(listOf(first, second, last), namespace.templateProcessors.requests())
        val specializedTarget = DataTemplate("AnotherBinding", namespace.identifier)
        val registration = namespace.templateProcessors.registrations().single()
        assertNotNull(
            namespace.templateProcessors.record(
                registration,
                specializedTarget,
                early.file,
                early.declaration,
                early.annotations[0],
                listOf(argument),
                first.order
            )
        )
        assertEquals(4, namespace.templateProcessors.requests().size)
        assertNull(
            namespace.templateProcessors.record(
                registration,
                early.target,
                early.file,
                early.declaration,
                early.annotations[0],
                listOf(CompilerValue.Typed(TypeId.Builtin("int"), CompilerValue.Integral(9))),
                first.order
            )
        )
        assertEquals(4, namespace.templateProcessors.requests().size)
        noHost()
    }

    @Test fun completeTypedArgumentGraphsAndPublishedListsAreImmutableSnapshots() {
        val namespace = namespace()
        assertTrue(namespace.registerTemplateProcessor("registry.owner:Mark", Processor::class.java))
        val specialized = TypeId.Specialized(TypeId.Declaration("template", "registry.owner", "Box"), listOf(argument))
        val fields = linkedMapOf<String, CompilerValue>(
            "type" to CompilerValue.TypeValue(specialized),
            "values" to CompilerValue.Sequence(listOf(argument))
        )
        val arguments = arrayListOf<CompilerValue>(CompilerValue.Typed(TypeId.Builtin("dict"), CompilerValue.Record(fields)))
        val request = record(namespace, input(namespace), arguments = arguments)
        arguments.clear()
        fields.clear()
        val payload = assertIs<CompilerValue.Record>(assertIs<CompilerValue.Typed>(request.arguments.single()).payload)
        assertEquals(specialized, assertIs<CompilerValue.TypeValue>(payload.fields.getValue("type")).id)
        assertEquals(listOf(argument), assertIs<CompilerValue.Sequence>(payload.fields.getValue("values")).elements)
        assertFailsWith<UnsupportedOperationException> { (request.arguments as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> { (namespace.templateProcessors.registrations() as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> { (namespace.templateProcessors.requests() as MutableList<*>).clear() }
        noHost()
        assertEquals(0, Project.errorCount)
    }

    @Test fun rawCheckpointsRestoreBothDirectionsAndRetainLostDeclarationsFilesAndSyntax() {
        val namespace = namespace()
        assertTrue(namespace.registerTemplateProcessor("registry.owner:Mark", Processor::class.java))
        val first = record(namespace, input(namespace, "First"))
        val before = namespace.templateProcessors.checkpoint()
        assertTrue(namespace.registerTemplateProcessor("registry.owner:Other", OtherProcessor::class.java))
        val second = record(namespace, input(namespace, "Second"), file = 1)
        val after = before.captureCurrent()
        before.restore()
        assertEquals(listOf(first), namespace.templateProcessors.requests())
        assertNull(namespace.templateProcessors.find("registry.owner:Other"))
        val lost = after.captureCurrent()
        assertEquals(listOf(first, second), lost.retainedRequests)
        assertEquals(listOf(first.target, second.target), lost.targets)
        assertEquals(listOf(first.sourceFile, second.sourceFile).size, lost.sourceFiles.size)
        assertTrue(lost.syntax.any { it === second.annotationSyntax })
        assertTrue(lost.syntax.any { it === second.declarationSyntax })
        assertTrue(lost.sourceFiles.any { it === second.sourceFile })
        after.restore()
        assertEquals(listOf(first, second), namespace.templateProcessors.requests())
        assertSame(OtherProcessor::class.java, namespace.templateProcessors.find("registry.owner:Other")!!.processorClass)
        lost.restore()
        assertEquals(listOf(first), namespace.templateProcessors.requests())
        assertFailsWith<UnsupportedOperationException> { (lost.retainedRequests as MutableList<*>).clear() }
        noHost()
        assertEquals(0, Project.errorCount)
    }

    @Test fun namespaceMergeAndProjectResetDoNotShareAStaticRegistry() {
        val namespace = namespace()
        val other = Namespace(namespace.identifier)
        assertTrue(other.registerTemplateProcessor("registry.owner:Mark", Processor::class.java))
        val request = record(other, input(other))
        namespace.merge(other)
        namespace.merge(other)
        assertEquals(1, namespace.templateProcessors.registrations().size)
        assertEquals(1, namespace.templateProcessors.requests().size)
        assertSame(request.target, namespace.templateProcessors.requests().single().target)
        assertTrue(Namespace("registry.unrelated").templateProcessors.registrations().isEmpty())
        Project.init()
        val fresh = GlobalScope.getOrCreateNamespace(namespace.identifier)
        assertNotSame(namespace, fresh)
        assertTrue(fresh.templateProcessors.registrations().isEmpty())
        assertTrue(fresh.templateProcessors.requests().isEmpty())
        noHost()
    }

    @Test fun realJarDiscoveryUsesTheConfiguredProjectLoaderWithoutClassInitialization() {
        val namespace = namespace()
        val output = Files.createTempDirectory("mcfpp-processor-registry-")
        val marker = "mcfpp.test.registry.initialization"
        val previousMarker = System.getProperty(marker)
        val previousLoader = Project.classLoader
        System.clearProperty(marker)
        try {
            val source = output.resolve("JarProcessor.java")
            Files.writeString(
                source,
                """
                import top.mcfpp.mni.api.*;
                public class JarProcessor implements TemplateProcessor {
                    static { System.setProperty("$marker", "initialized"); }
                    public JarProcessor() { System.setProperty("$marker", "constructed"); }
                    public DeclarationEdits expand(DeclarationContext context, TemplateView template) {
                        System.setProperty("$marker", "expanded");
                        throw new AssertionError("Registry executed JarProcessor");
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
                    "-proc:none", "-classpath", classpath, "-d", output.toString(), source.toString()
                )
            )
            val jar = output.resolve("processor.jar")
            JarOutputStream(Files.newOutputStream(jar)).use { stream ->
                stream.putNextEntry(JarEntry("JarProcessor.class"))
                Files.copy(output.resolve("JarProcessor.class"), stream)
                stream.closeEntry()
            }
            URLClassLoader(arrayOf(jar.toUri().toURL()), previousLoader).use { loader ->
                Project.classLoader = loader
                assertTrue(namespace.registerTemplateProcessor("registry.owner:Jar", "JarProcessor"))
                val registered = namespace.templateProcessors.find("registry.owner:Jar")!!
                assertSame(loader, registered.processorClass.classLoader)
                assertSame(Class.forName("JarProcessor", false, loader), registered.processorClass)
                assertNull(System.getProperty(marker), "Class discovery must not initialize, construct, or execute the processor")
            }
        } finally {
            Project.classLoader = previousLoader
            if (previousMarker == null) System.clearProperty(marker) else System.setProperty(marker, previousMarker)
            output.toFile().deleteRecursively()
        }
        noHost()
        assertEquals(0, Project.errorCount)
    }
}
