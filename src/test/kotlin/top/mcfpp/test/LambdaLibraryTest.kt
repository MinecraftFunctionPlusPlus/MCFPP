package top.mcfpp.test

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONWriter
import top.mcfpp.Project
import top.mcfpp.core.lang.FunctionValue
import top.mcfpp.io.LibBinReader
import top.mcfpp.io.LibBinWriter
import top.mcfpp.io.DatapackCreator
import top.mcfpp.library.format.LibraryBinaryWriter
import top.mcfpp.library.format.LibraryFormatCode
import top.mcfpp.library.format.LibraryFormatException
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class LambdaLibraryTest {
    @Test
    fun freshSessionLibraryClosuresMatchSourceExecution() {
        val originalIncludes = Project.config.includes
        try {
            Project.config.includes = arrayListOf()
            val declarations = """
                func make(base:int): (int) -> int { return { value -> base + value }; }
                func other(base:int): (int) -> int { return { value -> base * 10 + value }; }
                func choose(route:int): (int) -> int {
                    var selected: (int) -> int = make(10);
                    if(route != 0) { selected = other(10); }
                    return selected;
                }
                func reference(value:int):int { return value + 2; }
                func referenceFactory(): (int) -> int { return reference; }
                func nested(base:int): () -> () -> int { return { return { base + 1 }; }; }
            """.trimIndent()
            fun calls(prefix: String) = """
                func main() {
                    var first = ${prefix}make(3);
                    var second = ${prefix}make(8);
                    var copied: (int) -> int = first;
                    var firstResult = first(4);
                    var secondResult = second(4);
                    var copyResult = copied(5);
                    import var route:int;
                    /data modify storage mcfpp:system stack_frame[0].route set value 0
                    var selected = ${prefix}choose(route);
                    var branchZero = selected(5);
                    /data modify storage mcfpp:system stack_frame[0].route set value 1
                    var selectedAgain = ${prefix}choose(route);
                    var branchOne = selectedAgain(5);
                    var outer = ${prefix}nested(20);
                    var inner = outer();
                    var nestedResult = inner();
                    var referenced = ${prefix}referenceFactory();
                    var referenceResult = referenced(7);
                    var firstAgain = first(6);
                    /data modify storage lambda:results first set from storage mcfpp:system stack_frame[0].firstResult
                    /data modify storage lambda:results second set from storage mcfpp:system stack_frame[0].secondResult
                    /data modify storage lambda:results copied set from storage mcfpp:system stack_frame[0].copyResult
                    /data modify storage lambda:results zero set from storage mcfpp:system stack_frame[0].branchZero
                    /data modify storage lambda:results one set from storage mcfpp:system stack_frame[0].branchOne
                    /data modify storage lambda:results nested set from storage mcfpp:system stack_frame[0].nestedResult
                    /data modify storage lambda:results referenced set from storage mcfpp:system stack_frame[0].referenceResult
                    /data modify storage lambda:results again set from storage mcfpp:system stack_frame[0].firstAgain
                }
            """.trimIndent()
            MCFPPStringTest.readFromString(declarations + "\n" + calls(""), version = "26.3")
            assertEquals(0, Project.errorCount, "Producer must compile actual closure bodies")
            val producerNamespace = GlobalScope.localNamespaces.getValue("default.test")
            val originalFactory = producerNamespace.scope.functions.getValue("make").single()
            export("source")
            LibBinWriter.write("build/lambda/library/producer")

            Project.config.includes = arrayListOf("build/lambda/library/producer")
            MCFPPStringTest.readFromString(calls("default.test:"), version = "26.3", afterFileCreated = {
                assertTrue(GlobalScope.localNamespaces.values.none { namespace -> namespace.scope.functions.values.flatten().any { it === originalFactory } })
                assertTrue(GlobalScope.libNamespaces.values.none { namespace -> namespace.scope.functions.values.flatten().any { it === originalFactory } })
                val restored = GlobalScope.libNamespaces.getValue("default.test").scope.functions.getValue("make").single()
                assertNotSame(originalFactory, restored, "Import must recreate declarations after Project.init")
                assertFalse(restored.bodyCompiled)
                assertEquals(originalFactory.returnType.typeId, restored.returnType.typeId)
                val original = originalFactory.ast!!
                val handle = assertNotNull(restored.bodyHandle, "Imported closure factories retain their structured library body")
                val projection = handle.project()
                val body = projection.context
                assertEquals(original.statement().size, body.statement().size)
                assertSame(projection.bound.root, assertNotNull(projection.origin(body)).node,
                    "Projected syntax must retain the immutable author semantic root")
                val another = handle.project()
                assertSame(projection.bound.root, another.bound.root, "Repeated projections share only immutable semantic input")
                assertSame(projection.bound.root, assertNotNull(another.origin(another.context)).node)
                assertNotSame(body, another.context, "Each use owns a fresh mutable context")
                assertNotSame(body.start, another.context.start)
                assertNotSame(body.start.inputStream, another.context.start.inputStream)
                assertNotSame(original.start.inputStream, body.start.inputStream)
                assertSame(body.start.inputStream, body.stop.inputStream, "Token source text must remain shared")
            })
            assertEquals(0, Project.errorCount, "Consumer must compile imported factory bodies and nested closures")
            val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
            assertEquals(2, assertIs<FunctionValue>(main.scope.getVar("selected")).knownTargets().size)
            export("imported")
        } finally {
            Project.config.includes = originalIncludes
        }
    }

    @Test
    fun previousLibraryFormatIsRejectedWithRecompileDiagnostic() {
        MCFPPStringTest.readFromString("func main(){}", version = "26.3")
        assertEquals(0, Project.errorCount)
        Project.libraryImportSession?.close()
        Project.libraryImportSession = null
        val header = LibraryBinaryWriter().apply {
            rawBytes(byteArrayOf(0x4d, 0x43, 0x46, 0x4c))
            u32(87)
        }.toByteArray()
        val failure = assertFailsWith<LibraryFormatException> {
            LibBinReader.readFromStream(java.io.ByteArrayInputStream(header))
        }
        assertEquals(LibraryFormatCode.DEVELOPMENT_CACHE, failure.code)
        assertEquals(0L, failure.byteOffset)
        assertTrue(failure.detail.contains("re-export"), "Unpublished development caches must require recompilation")
    }

    private fun export(name: String) {
        val root = Path.of("build/lambda/library/$name")
        val pack = root.resolve("pack")
        if (Files.exists(pack)) {
            val actual = pack.toRealPath()
            require(actual.startsWith(Path.of("build/lambda/library").toRealPath()) && actual.fileName.toString() == "pack")
            Files.walk(actual).use { paths -> paths.sorted(java.util.Comparator.reverseOrder()).forEach { Files.delete(it) } }
        }
        // Use the production consumer linker; only the DSB entry and observations are fixtures.
        val previousName = Project.config.name
        val previousCopyImport = Project.config.copyImport
        try {
            Project.config.name = "pack"
            Project.config.copyImport = false
            DatapackCreator.createDatapack(root.toString())
        } finally {
            Project.config.name = previousName
            Project.config.copyImport = previousCopyImport
        }
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val entry = pack.resolve("data/lambda/function/run.mcfunction")
        Files.createDirectories(entry.parent)
        Files.writeString(entry, (GlobalScope.scoreboards.values.map { "scoreboard objectives add ${it.name} ${it.criterion}" } +
            listOf("data modify storage mcfpp:system stack_frame set value [{}]", "function ${main.namespaceID}",
                "data remove storage mcfpp:system stack_frame[0]")).joinToString("\n") + "\n")
        val expected = mapOf("first" to 7, "second" to 12, "copied" to 8, "zero" to 15,
            "one" to 105, "nested" to 21, "referenced" to 9, "again" to 9)
        val manifest = mapOf("version" to "26.3", "unsupported" to "error", "failOnMissingResources" to true,
            "packs" to listOf("./pack"), "steps" to listOf(mapOf("commands" to listOf("function lambda:run"))),
            "assertions" to expected.map { (field, value) ->
                mapOf("storage" to mapOf("id" to "lambda:results", "path" to field, "equals" to value))
            } + mapOf("storage" to mapOf("id" to "mcfpp:system", "path" to "stack_frame", "equals" to emptyList<Any>())))
        Files.writeString(root.resolve("$name.dps.json"), JSON.toJSONString(manifest, JSONWriter.Feature.PrettyFormat))
    }
}
