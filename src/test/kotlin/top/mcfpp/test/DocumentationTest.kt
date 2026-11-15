package top.mcfpp.test

import com.alibaba.fastjson2.JSON
import top.mcfpp.Project
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.annotations.MNIMacro
import top.mcfpp.core.lang.MCInt
import top.mcfpp.doc.*
import top.mcfpp.library.api.LibraryCompilerSession
import top.mcfpp.library.api.LibraryImportSession
import top.mcfpp.library.compiler.LibraryIdentity
import top.mcfpp.library.syntax.DeclarationId
import top.mcfpp.library.format.CompoundCodec
import top.mcfpp.library.format.LibraryModelCodec
import top.mcfpp.library.format.NativeCodec
import top.mcfpp.library.model.LibraryModel
import top.mcfpp.mni.api.*
import top.mcfpp.project.ProjectTemplates
import top.mcfpp.project.ProjectDefinition
import top.mcfpp.project.toml
import top.mcfpp.cli.ProjectCli
import top.mcfpp.util.ValueWrapper
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class DocumentationTest {
    @Test fun parsesMarkdownTagsAndCodeWithoutLosingContent() {
        val input = "/// # 中文\r\n///\r\n///     indented\r\n/// ```mcfpp\r\n/// @param example is code\r\n/// ```\r\n/// @param value 第一行\r\n///   第二行\r\n/// @return result\r\n/// @custom retained\r\n/// @deprecated"
        val document = Document.parse(input)
        assertEquals("# 中文\n\n    indented\n```mcfpp\n@param example is code\n```", document.markdown)
        assertEquals(4, document.tags.size)
        assertEquals(ParamTag("value", "第一行\n  第二行"), document.tags[0])
        assertIs<UnknownTag>(document.tags[2])
        assertEquals(1, document.problems.size)
        assertEquals(11, document.problems.single().line)
        val restored = Document.parse(document.toSource())
        assertEquals(document.markdown, restored.markdown)
        assertEquals(document.tags, restored.tags)
        for (tag in DocumentTag.knownTags) DocumentTag.parse(tag)
        assertEquals("/// literal", Document.parseText(Document.parse("/// /// literal").toSource()).markdown)
    }

    @Test fun collectsDeclarationsWithoutCompilingBodiesAndResolvesOverloads() = project { root ->
        source(root, """
            /// Namespace docs.
            namespace docs
            /// An integer alias.
            typealias int as Count
            /// A documented base.
            data Base {
                /// Current **count**.
                protected var value: int
                /// Private implementation.
                private var secret: int
                /// Read the count.
                func read(): int { return notImplemented; }
            }
            /// See [Base] and [read(int)].
            /// @see Base.read()
            data Child: Base {}
            /// Generic box.
            data Box<T> {
                /// Stored value.
                var value: T
                /// Returns the value.
                func readValue(): T { return unfinished; }
            }
            /// A state.
            enum State {
                /// Ready to use.
                ready,
                /// Finished.
                done
            }
            /// Reads an integer.
            /// @param value the value
            /// @return the result
            func read(value: int): int { return unfinished; }
            /// Reads a boolean.
            func read(value: bool): bool { return unfinished; }
            /// See [generic<type>(T)].
            func generic<T>(value: T): T { return unfinished; }
            data Root {
                /// Root method.
                func method(): int { return unfinished; }
            }
            data Left: Root {}
            data Right: Root {
                /// Right method.
                func method(): int { return unfinished; }
            }
            data Diamond: Left, Right {}
            data Reversed: Right, Left {}
        """.trimIndent())
        val model = DocumentationExport.generate(request(root))
        assertEquals(0, Project.errorCount)
        assertEquals(2, model.symbols.count { it.name == "read" && it.owner == null })
        assertEquals(2, model.symbols.filter { it.name == "read" && it.owner == null }.map { it.id }.distinct().size)
        assertFalse(model.symbols.any { it.name == "secret" })
        assertEquals("Read the count.", model.symbols.single { it.owner == "Base" && it.name == "read" }.markdown)
        assertFalse(model.symbols.any { it.owner == "Child" && it.name == "read" })
        assertEquals("Current **count**.", model.symbols.single { it.owner == "Child" && it.name == "value" }.markdown)
        assertNotNull(model.symbols.single { it.owner == "Child" && it.name == "value" }.inheritedFrom)
        assertEquals("Stored value.", model.symbols.single { it.owner == "Box" && it.name == "value" }.markdown)
        assertEquals("Ready to use.", model.symbols.single { it.name == "ready" }.markdown)
        assertEquals("An integer alias.", model.symbols.single { it.name == "Count" }.markdown)
        val references = model.symbols.single { it.name == "Child" && it.owner == null }.references
        assertEquals(3, references.size)
        assertTrue(references.all { it.included })
        assertTrue(model.warnings.isEmpty(), model.warnings.joinToString())
        assertEquals(1, model.symbols.single { it.name == "generic" }.references.size)
        assertEquals("Root method.", model.symbols.single { it.owner == "Root" && it.name == "method" }.markdown)
        assertEquals("Right method.", model.symbols.single { it.owner == "Right" && it.name == "method" }.markdown)
        for (owner in listOf("Diamond", "Reversed")) assertFalse(model.symbols.any { it.owner == owner && it.name == "method" })
        assertFalse(Files.exists(root.resolve("build/bin.mclib")))
        val again = DocumentationExport.generate(request(root), DocumentationOptions(includePrivate = true))
        assertTrue(again.symbols.any { it.name == "secret" })
        assertEquals(model.symbols.map { it.id }, again.symbols.filterNot { it.name == "secret" }.map { it.id })
    }

    @Test fun explicitNativeAndMacroDocumentsSurviveLibraryRoundTrip() = project { root ->
        DocumentationProvider.calls = 0
        val definition = ProjectDefinition.load(root.resolve("mcfpp.toml"))
        Files.writeString(definition.file, toml(definition.values + ("library" to mapOf("coordinate" to "docs:fixture", "version" to "1.0.0"))))
        source(root, """
            /// Library docs.
            namespace docs
            /// Integer alias.
            typealias int as Count
            /// Global counter.
            var globalCount: int = 1
            /// State docs.
            enum Status {
                /// Ready state.
                ready
            }
            /// The native function. See [macroAnswer()].
            /// @return native result
            func nativeAnswer(): int = top.mcfpp.test.DocumentationProvider.nativeAnswer
            /// The macro. See [nativeAnswer()].
            /// @return macro result
            func macroAnswer(): int = top.mcfpp.test.DocumentationProvider.macroAnswer
            /// Container docs.
            data Box<T> {
                /// A stored value.
                var value: T
            }
            data Parent {
                /// Inherited value.
                var inheritedValue: int
                /// Creates a parent value.
                new(inheritedValue: int) { return { inheritedValue } }
                /// Parent method.
                func method(): int { return 1; }
            }
            data Child: Parent {
                /// Child method.
                func method(): int { return 2; }
            }
            data GrandChild: Child {}
            func main() {}
        """.trimIndent())
        val sourceModel = DocumentationExport.generate(request(root), DocumentationOptions(includePrivate = true))
        assertEquals("macro", sourceModel.symbols.single { it.name == "macroAnswer" }.kind)
        val sourceFactory = sourceModel.symbols.single { it.owner == "Parent" && it.kind == "factory" }
        assertEquals("new", sourceFactory.name)
        assertEquals("new(inheritedValue: int)", sourceFactory.signature)
        assertEquals(0, DocumentationProvider.calls, "Documentation must not execute native or macro bodies")
        LibraryCompilerSession.openSession(request(root)).use {
            val result = LibraryCompilerSession.compileSession(it)
            assertEquals(true, result["success"], result["diagnostics"].toString())
        }
        val codec = LibraryModelCodec(listOf(CompoundCodec, NativeCodec))
        val library = codec.decode(Files.readAllBytes(root.resolve("build/bin.mclib")))
        val restored = DocumentationExport.fromLibraries("fixture", listOf(library), DocumentationOptions(includePrivate = true, includeDependencies = true))
        for (name in listOf("nativeAnswer", "macroAnswer", "Count", "Box", "value", "globalCount", "Status", "ready")) {
            val symbol = assertNotNull(restored.symbols.singleOrNull { it.name == name }, "$name missing from ${restored.symbols.map { it.name }}")
            assertEquals(sourceModel.symbols.single { it.name == name }.markdown, symbol.markdown, name)
        }
        assertEquals(sourceModel.symbols.single { it.name == "macroAnswer" }.tags, restored.symbols.single { it.name == "macroAnswer" }.tags)
        assertTrue(restored.symbols.single { it.name == "macroAnswer" }.references.single().included)
        assertEquals("Inherited value.", restored.symbols.single { it.owner == "GrandChild" && it.name == "inheritedValue" }.markdown)
        assertNotNull(restored.symbols.single { it.owner == "GrandChild" && it.name == "inheritedValue" }.inheritedFrom)
        assertEquals("Child method.", restored.symbols.single { it.owner == "Child" && it.name == "method" }.markdown)
        assertFalse(restored.symbols.any { it.owner == "GrandChild" && it.name == "method" })
        val restoredFactory = restored.symbols.single { it.owner == "Parent" && it.kind == "factory" }
        assertEquals(sourceFactory.markdown, restoredFactory.markdown)
        assertEquals(sourceFactory.signature, restoredFactory.signature)
        assertEquals(sourceFactory.parameters, restoredFactory.parameters)
        assertFalse(restored.symbols.any { it.owner in setOf("Child", "GrandChild") && it.kind == "factory" })
        val oldLibrary = LibraryModel(library.manifest, library.declarations, library.tables, library.syntax, library.annotations,
            library.stateCells, library.runtime, library.resources)
        val old = DocumentationExport.fromLibraries("old", listOf(codec.decode(codec.encode(oldLibrary))))
        assertTrue(old.symbols.all { it.markdown.isEmpty() && it.tags.isEmpty() })
        Project.init()
        val previousLoader = Project.classLoader
        try {
            Project.classLoader = javaClass.classLoader
            LibraryImportSession().use { imported ->
                imported.stage(codec.encode(library)); imported.bindAll()
                val identity = LibraryIdentity(library.manifest.library, library.manifest.classifier)
                val alias = library.declarations.declarations.indexOfFirst { it.common.name == "Count" }
                assertEquals("Integer alias.", imported.declarationDocument(identity, DeclarationId(alias + 1L)).markdown)
                assertEquals("The native function. See [macroAnswer()].", imported.functions().single { it.identifier == "nativeAnswer" }.document.markdown)
                assertEquals(1, imported.documentationModels().size)
            }
        } finally { Project.classLoader = previousLoader }
        project { consumer ->
            source(consumer, "namespace consumer\nimport docs:*\n/// See [macroAnswer()].\nfunc useApi() { unfinished(); }")
            val consumerRequest = request(consumer) + mapOf(
                "lockedPackages" to listOf(mapOf("coordinate" to "docs:fixture", "version" to "1.0.0", "dependencies" to emptyList<Any>())),
                "libraries" to listOf(mapOf("coordinate" to "docs:fixture", "version" to "1.0.0", "classifier" to "mcfpp",
                    "sha256" to java.security.MessageDigest.getInstance("SHA-256").digest(codec.encode(library)), "binary" to codec.encode(library),
                    "moduleJson" to Files.readAllBytes(root.resolve("build/module.json")), "resources" to emptyMap<String, ByteArray>())))
            val own = DocumentationExport.generate(consumerRequest)
            assertFalse(own.symbols.any { it.name == "macroAnswer" })
            assertEquals(false, own.symbols.single { it.name == "useApi" }.references.single().included)
            val withDependencies = DocumentationExport.generate(consumerRequest, DocumentationOptions(includeDependencies = true))
            assertEquals(true, withDependencies.symbols.single { it.name == "useApi" }.references.single().included)
            assertEquals("The macro. See [nativeAnswer()].", withDependencies.symbols.single { it.name == "macroAnswer" }.markdown)
        }
    }

    @Test fun bindsCommentsBeforeAnnotationsToTheFollowingDeclaration() = project { root ->
        val definition = ProjectDefinition.load(root.resolve("mcfpp.toml"))
        Files.writeString(definition.file, toml(definition.values - "args"))
        source(root, "namespace docs\n/// Runs each tick.\n@Tick\nfunc onTick() { unfinished(); }\ndata Annotated {\n/// Member docs.\n@Tick\nfunc read(): int { return unfinished; }\n}")
        val model = DocumentationExport.generate(request(root))
        assertEquals("Runs each tick.", model.symbols.single { it.name == "onTick" }.markdown)
        assertEquals("Member docs.", model.symbols.single { it.name == "read" }.markdown)
    }

    @Test fun newDocumentTagsSurviveCompilerTransactionsAndCopies() {
        Project.init()
        val document = Document.parse("Description.\n@receiver value\n@property field value\n@constructor value\n@sample sample\n@custom value")
        val function = top.mcfpp.model.function.Function("documented", "docs", null).apply { this.document = document }
        val copy = top.mcfpp.model.function.Function(function)
        assertNotSame(document, copy.document)
        val before = top.mcfpp.mni.expand.FunctionStateJournal.checkpoint(listOf(function))
        document.tags.clear()
        function.document = Document("replacement")
        before.restore()
        assertSame(document, function.document)
        assertEquals(5, function.document.tags.size)
        assertEquals(5, copy.document.tags.size)
        val template = top.mcfpp.model.compound.DataTemplate("Documented", "docs").apply { this.document = document.copy() }
        val declaration = top.mcfpp.mni.expand.DeclarationStateJournal.checkpoint(arrayListOf(template))
        template.document.tags.clear()
        template.document = Document("replacement")
        declaration.restore()
        assertEquals(document.toSource(), template.document.toSource())
    }

    @Test fun rendersJsonHtmlAndLeavesPreviousOutputWhenDeclarationsFail() = project { root ->
        source(root, """
            namespace docs
            /// # Read me
            /// **Bold** and `code`, [read(int)] and [read].
            /// ```mcfpp
            /// [NotASymbol]
            /// ```
            /// @see read(int)
            func main() { unfinished(); }
            func read(value: int): int { return missing; }
            func read(value: bool): bool { return missing; }
        """.trimIndent())
        val model = DocumentationExport.generate(request(root))
        assertEquals(1, model.warnings.size, model.warnings.joinToString())
        assertTrue(model.warnings.single().contains("[read]"))
        val directory = root.resolve("export")
        DocumentationExport.write(model, directory)
        val json = JSON.parseObject(Files.readString(directory.resolve("docs.json")))
        assertEquals(1, json.getIntValue("schemaVersion"))
        val html = Files.readString(directory.resolve("index.html"))
        assertContains(html, "<strong>Bold</strong>")
        assertContains(html, "[NotASymbol]")
        assertContains(html, "[read]")
        val target = model.symbols.single { it.name == "read" && it.parameters.single().type == "int" }.id
        assertContains(html, "href=\"#$target\"")
        val previous = Files.readAllBytes(directory.resolve("index.html"))
        source(root, "func broken(value: NoSuchType) {}")
        assertFails { DocumentationExport.generate(request(root)) }
        for (invalid in listOf("data Broken<T> { var field: NoSuchType; }", "data Broken<T> { func read(value: NoSuchType) {} }")) {
            source(root, invalid)
            assertFails(invalid) { DocumentationExport.generate(request(root)) }
        }
        assertContentEquals(previous, Files.readAllBytes(directory.resolve("index.html")))
        DocumentationExport.write(model, directory, DocumentationFormat.json)
        assertFalse(Files.exists(directory.resolve("index.html")))
        assertTrue(Files.exists(directory.resolve("docs.json")))
    }

    @Test fun cliHonorsProjectLockedOfflineFormatAndOutput() = project { root ->
        source(root, "/// Public docs.\nfunc sample() { unfinished(); }\ndata Hidden { private var secret: int; }")
        fun run(vararg args: String): Pair<Int, String> {
            val output = StringWriter()
            val writer = PrintWriter(output, true)
            return ProjectCli.run(arrayOf(*args), writer, writer, root) to output.toString()
        }
        val first = run("doc")
        assertEquals(0, first.first, first.second)
        assertTrue(Files.exists(root.resolve("build/docs/index.html")))
        assertTrue(Files.exists(root.resolve("build/docs/docs.json")))
        val second = run("--project", root.toString(), "--locked", "--offline", "doc", "--format", "json", "--output", "api", "--include-private")
        assertEquals(0, second.first, second.second)
        assertFalse(Files.exists(root.resolve("api/index.html")))
        assertContains(Files.readString(root.resolve("api/docs.json")), "secret")
        assertFalse(Files.exists(root.resolve("build/bin.mclib")))
    }

    private fun project(action: (Path) -> Unit) {
        val root = Files.createTempDirectory("mcfpp-documentation-")
        try {
            val definition = ProjectTemplates.create(root, false)
            Files.writeString(definition.file, toml(definition.values + ("args" to listOf("-ignoreStdLib"))))
            action(root)
        } finally { root.toFile().deleteRecursively() }
    }
    private fun source(root: Path, source: String) { Files.writeString(root.resolve("src/main/mcfpp/main.mcfpp"), source) }
    private fun request(root: Path): Map<String, Any> = mapOf("apiVersion" to 1, "configuration" to root.resolve("mcfpp.toml"),
        "lockedPackages" to emptyList<Any>(), "libraries" to emptyList<Any>(), "plugins" to emptyList<Any>(),
        "resourcePacks" to emptyList<Any>(), "datapacks" to emptyList<Any>(), "hostClassLoader" to javaClass.classLoader)
}

object DocumentationProvider {
    var calls = 0
    @JvmStatic @MNIFunction(returnType = "int")
    fun nativeAnswer(result: ValueWrapper<MCInt>) { calls++; result.value = MCInt(1) }
    @JvmStatic @MNIMacro(returnType = "int")
    fun macroAnswer(context: MacroContext): Expr<IntT> { calls++; return context.quote.expression(context.types().intType()) { yield(literal(1)) } }
}
