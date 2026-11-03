package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import java.nio.file.Files
import kotlin.test.*
import kotlin.test.Test

/**
 * Real multi-file annotation-processor pipeline.
 *
 * Every test drives the ordinary `indexType -> resolveField -> runAnnotation -> compile` stages;
 * processors are discovered/recorded by the real annotation visitor and dispatched by the
 * post-declaration barrier runner. No test calls a processor or a generated declaration compiler
 * directly.
 */
class MniTemplateProcessorTest {
    companion object {
        private val events = arrayListOf<String>()
        private var firstSawField = false
        private var secondSawFirst = false
        private var secondSawMetadata = false
        private var generatedAnnotationRuns = 0

        private fun reset() {
            events.clear()
            firstSawField = false
            secondSawFirst = false
            secondSawMetadata = false
            generatedAnnotationRuns = 0
        }

        private fun generate(
            context: DeclarationContext,
            template: TemplateView,
            name: String,
            value: Int,
            fieldName: String? = null
        ): MethodEdit {
            @Suppress("UNCHECKED_CAST")
            val owner = template.type() as TypeRef<Any>
            val int = context.types().intType()
            val field = fieldName?.let { declared ->
                template.fieldDeclarations().single { it.field().name() == declared }.field()
            }
            return context.declarations().method(
                owner,
                name,
                FunctionSignature.of(emptyList(), int)
            ) { body ->
                val builder = body.builder()
                if (field != null) {
                    @Suppress("UNCHECKED_CAST")
                    val writable = field as FieldRef<IntT>
                    builder.assign(builder.fieldRef(body.self(), writable), builder.intLiteral(value))
                }
                builder.yield(builder.intLiteral(value))
            }
        }
    }

    /** Publishes one method (writing the private self field) and one whitelist metadata fact. */
    class FirstProcessor : TemplateProcessor {
        override fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits {
            events += "first"
            val declaration = template.fieldDeclarations().single { it.field().name() == "value" }
            firstSawField = declaration.access() == DeclarationAccess.PRIVATE
            val method = generate(context, template, "first", 1, fieldName = "value")
            val metadata = context.declarations().metadata(template.type(), "processor:Generated", emptyMap())
            return context.declarations().edits(listOf(method, metadata))
        }
    }

    /** Runs after First and must observe both its callable member and its metadata. */
    class SecondProcessor : TemplateProcessor {
        override fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits {
            events += "second"
            secondSawFirst = template.methods().any { it.name() == "first" }
            secondSawMetadata = template.annotations().any { it.name() == "processor:Generated" }
            return context.declarations().edits(listOf(generate(context, template, "second", 2)))
        }
    }

    /** Registered for the generated metadata name; the generated fact must never invoke it. */
    class GeneratedAnnotationProcessor : TemplateProcessor {
        override fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits {
            generatedAnnotationRuns++
            return context.declarations().edits(emptyList())
        }
    }

    /** Constructs a member and metadata, then fails before returning the edit list. */
    class BrokenProcessor : TemplateProcessor {
        override fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits {
            events += "broken"
            generate(context, template, "partial", 3)
            context.declarations().metadata(template.type(), "processor:Generated", emptyMap())
            error("intentional processor failure")
        }
    }

    private fun compileProject(
        sources: Map<String, String>,
        configure: (Namespace) -> Unit,
        verify: (Namespace) -> Unit
    ) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val config = Project.config
        val callerFile = MCFPPFile.currFile
        val callerFunction = Function.currFunction
        val callerTemplate = DataTemplate.currTemplate
        val ignoreStdLib = CompileSettings.ignoreStdLib
        val directory = Files.createTempDirectory("mcfpp-template-processor-")
        try {
            sources.forEach { (name, text) -> Files.writeString(directory.resolve(name), text) }
            CompileSettings.ignoreStdLib = true
            Project.config = ProjectConfig(version = "26.3").apply {
                root = directory
                sourcePath = directory
                rootNamespace = "processor"
                targetPath = null
                noDatapack = true
            }
            Project.init()
            Project.readProject()
            Project.indexType()
            Project.resolveField()
            val namespace = GlobalScope.localNamespaces.getValue("processor")
            configure(namespace)
            Project.runAnnotation()
            Project.compile()
            verify(namespace)
        } finally {
            CompileSettings.ignoreStdLib = ignoreStdLib
            namespaces.restore()
            project.restore()
            Project.config = config
            MCFPPFile.currFile = callerFile
            Function.currFunction = callerFunction
            DataTemplate.currTemplate = callerTemplate
            directory.toFile().deleteRecursively()
        }
    }

    private fun box(namespace: Namespace): DataTemplate = namespace.scope.template.values.single { it.identifier == "Box" }

    @Test
    fun realMultiFileSourceUsesOneOrderedBarrierAndPublishesCallableMembersAndMetadata() {
        reset()
        compileProject(
            linkedMapOf(
                "a_box.mcfpp" to """
                    @First
                    @Second
                    data Box {
                        private var value: int
                    }
                """.trimIndent(),
                "z_use.mcfpp" to """
                    func use(box: Box): int {
                        return box.first() + box.second()
                    }
                """.trimIndent()
            ),
            configure = { namespace ->
                assertTrue(namespace.registerTemplateProcessor("processor:First", FirstProcessor::class.java))
                assertTrue(namespace.registerTemplateProcessor("processor:Second", SecondProcessor::class.java))
                // A generated annotation name is a metadata fact, never a new input request.
                assertTrue(
                    namespace.registerTemplateProcessor(
                        "processor:Generated",
                        GeneratedAnnotationProcessor::class.java
                    )
                )
                TemplateProcessorRunner.registerMetadata("processor:Generated", DeclarationMetadataRule(emptyMap()))
            }
        ) { namespace ->
            val box = box(namespace)
            assertEquals(listOf("first", "second"), events)
            assertTrue(firstSawField)
            assertTrue(secondSawFirst, "The second processor must see the first committed member")
            assertTrue(secondSawMetadata, "Generated metadata is visible to a later processor")
            assertEquals(0, generatedAnnotationRuns, "Generated metadata must not schedule a processor")
            assertTrue(box.scope.getFunctionCandidates("first").isNotEmpty())
            assertTrue(box.scope.getFunctionCandidates("second").isNotEmpty())
            assertTrue(
                box.scope.getFunctionCandidates("first").single().commands.isNotEmpty(),
                "The private self field write must lower to real commands"
            )
            assertEquals(listOf("processor:Generated"), box.declarationMetadata().map { it.name() })
            assertTrue(namespace.scope.functions.getValue("use").single().bodyCompiled)
            assertEquals(0, Project.errorCount)

            // The same original input is never executed twice.
            assertEquals(0, TemplateProcessorRunner.run())
            assertEquals(listOf("first", "second"), events)
        }
    }

    @Test
    fun failedProcessorPublishesNoPartialMemberOrMetadataAndEarlierCommitSurvives() {
        reset()
        compileProject(
            linkedMapOf(
                "a_box.mcfpp" to """
                    @First
                    @Broken
                    data Box {
                        private var value: int
                    }
                """.trimIndent(),
                "z_use.mcfpp" to """
                    func use(box: Box): int {
                        return box.first()
                    }
                """.trimIndent()
            ),
            configure = { namespace ->
                assertTrue(namespace.registerTemplateProcessor("processor:First", FirstProcessor::class.java))
                assertTrue(namespace.registerTemplateProcessor("processor:Broken", BrokenProcessor::class.java))
                TemplateProcessorRunner.registerMetadata("processor:Generated", DeclarationMetadataRule(emptyMap()))
            }
        ) { namespace ->
            val box = box(namespace)
            assertEquals(listOf("first", "broken"), events)
            assertTrue(
                box.scope.getFunctionCandidates("first").isNotEmpty(),
                "A previous atomic commit remains published"
            )
            assertTrue(
                box.scope.getFunctionCandidates("partial").isEmpty(),
                "A failed processor leaves no half member"
            )
            assertEquals(
                listOf("processor:Generated"),
                box.declarationMetadata().map { it.name() },
                "Only the successful processor's metadata batch is published"
            )
            assertTrue(Project.errorCount > 0)
        }
    }
}
