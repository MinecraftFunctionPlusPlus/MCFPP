package top.mcfpp.test

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONWriter
import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Commands
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.StorageSource
import top.mcfpp.util.LogProcessor
import top.mcfpp.annotations.MNIMacro
import top.mcfpp.annotations.MacroParam
import top.mcfpp.core.lang.MCInt
import top.mcfpp.mni.api.*
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.ToolProvider
import kotlin.test.*
import kotlin.test.Test

class MniLambdaMigrationTest {
    class Entries {
        companion object {
            @JvmStatic
            @MNIMacro(identifier = "observe", params = [
                MacroParam(name = "body", type = "()->int", mode = ParamMode.INLINE),
                MacroParam(name = "observed", type = "int", mode = ParamMode.VALUE)
            ], returnType = "int")
            fun observe(context: MacroContext, body: InlineFn<IntT>, observed: Value<IntT>): Expr<IntT> =
                context.quote().expression(context.types().intType()) { builder ->
                    builder.yield(builder.inline<IntT>(body, emptyList()))
                }
        }
    }

    private fun providers(): URLClassLoader {
        val classes = Files.createDirectories(Path.of("build/lambda/mni-provider-classes"))
        val classpath = listOf(MNIMacro::class.java, MacroContext::class.java, kotlin.Unit::class.java)
            .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }.distinct().joinToString(File.pathSeparator)
        val compiler = assertNotNull(ToolProvider.getSystemJavaCompiler())
        assertEquals(0, compiler.run(null, null, null, "-proc:none", "-classpath", classpath, "-d", classes.toString(),
            "examples/mni/repeat/provider/java/example/mnirepeat/JavaRepeatMacro.java",
            "examples/mni/raycast/provider/java/example/mniraycast/JavaRaycastMacro.java"))
        return URLClassLoader(arrayOf(classes.toUri().toURL()), javaClass.classLoader)
    }

    private fun compile(source: String, loader: ClassLoader): List<String> {
        val publication = LogProcessor.beginDiagnosticCapture().use { capture ->
            MCFPPStringTest.readFromString(source, version = "26.3", afterFileCreated = { file ->
                Project.classLoader = loader
                file.namespace.injectedBy(loader.loadClass("example.mnirepeat.JavaRepeatMacro"))
                file.namespace.injectedBy(loader.loadClass("example.mniraycast.JavaRaycastMacro"))
                file.namespace.injectedBy(Entries::class.java)
            })
            capture.seal()
        }
        return publication.diagnostics.map { it.message }
    }

    private fun export(name: String, expected: Map<String, Int>, ray: Boolean = false) {
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        // Observe each real storage binding before main releases its frame. External int
        // variables use the compiler's current NBT representation, not an invented score slot.
        var beforePop = main.commands.indexOfLast { it.analyze() == "data remove storage mcfpp:system stack_frame[0]" }
        assertTrue(beforePop >= 0)
        expected.keys.forEach { key ->
            val value = main.scope.getVar(key) as MCInt
            main.commands.add(beforePop++, Commands.dataSetFrom(NBTPath(StorageSource("lambda:results")).memberIndex(key), StorageAccess.ensure(value).path))
        }
        LambdaCallTest().export(name, expected)
        val root = Path.of("build/lambda/sync-$name")
        Project.macroFunction.forEach { (key, body) ->
            val path = root.resolve("pack/data/mcfpp/function/dynamic/$key.mcfunction")
            Files.createDirectories(path.parent)
            Files.writeString(path, body)
        }
        if (ray) {
            val manifestFile = root.resolve("$name.dps.json")
            val manifest = JSON.parseObject(Files.readString(manifestFile))
            manifest["steps"] = listOf(mapOf("commands" to listOf(
                "setblock 0 0 0 minecraft:stone", "setblock 0 0 1 minecraft:stone", "setblock 0 0 2 minecraft:stone", "function lambda:run")))
            Files.writeString(manifestFile, JSON.toJSONString(manifest, JSONWriter.Feature.PrettyFormat))
        }
    }

    @Test fun realMigratedRepeatExampleExportsOriginalResults() = providers().use { loader ->
        compile(Files.readString(Path.of("examples/mni/repeat/project/src/main.mcfpp")), loader)
        assertEquals(0, Project.errorCount)
        export("mni-repeat", linkedMapOf("total" to 10, "guarded" to 8, "tail" to 9, "nested" to 22))
    }

    @Test fun realMigratedRayExampleExportsContinueStopAndCallerTails() = providers().use { loader ->
        compile(Files.readString(Path.of("examples/mni/raycast/project/src/main.mcfpp")), loader)
        assertEquals(0, Project.errorCount)
        export("mni-ray", linkedMapOf("continueHits" to 5, "continueLast" to 4, "afterContinue" to 6,
            "stopHits" to 1, "stopLast" to 0, "afterStop" to 2), ray = true)
    }

    @Test fun inlineCapturesSnapshotOnceAndGlobalsStayLive() = providers().use { loader ->
        val diagnostics = compile("""
            var sharedValue: int = 7;
            var visits: int = 0;
            var sum: int = 0;
            var rayVisits: int = 0;
            func main() {
                external local: int = 7;
                var savedLocal: ()->int = { local };
                local = 99;
                external snapshot: int = savedLocal();
                var savedGlobal: ()->int = { sharedValue };
                sharedValue = 99;
                external live: int = savedGlobal();
                external nested: int = repeat(2, 0, { accumulator, i ->
                    var inner: int = i + 3;
                    var saved: int = observe({ inner }, inner);
                    inner = 99;
                    accumulator + saved
                });
                repeat(5) {
                    visits += 1;
                    if (it == 2) { return; }
                    sum += it;
                    999
                };
                repeat(1, {});
                raycast(5, { i -> rayVisits += 1; if (i == 1) { return true; }; true });
                external rayResult: int = rayVisits;
                external visitResult: int = visits;
                external sumResult: int = sum;
                external after: int = sum + 1;
            }
        """.trimIndent(), loader)
        assertEquals(0, Project.errorCount, diagnostics.joinToString("\n"))
        export("mni-values", linkedMapOf("local" to 99, "snapshot" to 7, "live" to 99, "nested" to 7,
            "visitResult" to 5, "sumResult" to 8, "after" to 9, "rayResult" to 5), ray = true)
    }

    @Test fun oldArrowCapturedWritesAndDynamicInlineArgumentsAreRejected() = providers().use { loader ->
        val cases = listOf(
            "func main(){ repeat(1, (i) => {}); }" to "Legacy lambda",
            "func main(){ var outer=1; repeat(1, { i -> outer = i; }); }" to "Captured local",
            "func main(){ var outer=1; repeat(1, { i -> outer += i; }); }" to "Captured local",
            "func main(){ var outer=1; repeat(1, { i -> observe({ 1 }, ref outer); }); }" to "Legacy 'ref expression'",
            "func main(){ var callback: (int)->void = { i -> i; }; repeat(1, callback); }" to "dynamic function values"
        )
        for ((source, diagnostic) in cases) {
            val diagnostics = compile(source, loader)
            assertTrue(Project.errorCount > 0, source)
            assertTrue(diagnostics.any { it.contains(diagnostic) }, diagnostics.joinToString("\n"))
        }
    }

    @Test fun ordinaryLambdaLegacyReferenceReportsThePublicMigrationDiagnostic() = providers().use { loader ->
        val diagnostics = compile("""
            func main() {
                external outside: int = 7;
                var action: ()->int = {
                    var seen = outside;
                    var result: int = observe({ 1 }, ref outside);
                    result
                };
                var result = action();
            }
        """.trimIndent(), loader)
        assertTrue(Project.errorCount > 0)
        assertTrue(diagnostics.any { it.contains("Legacy 'ref expression'") }, diagnostics.joinToString("\n"))
    }

    @Test fun actualExampleCallbackTreesAreEquivalentUnderSllAndLl() {
        val sources = listOf("examples/mni/repeat/project/src/main.mcfpp", "examples/mni/raycast/project/src/main.mcfpp")
        for (source in sources) {
            val text = Files.readString(Path.of(source))
            val parser = top.mcfpp.antlr.mcfppParser(org.antlr.v4.runtime.CommonTokenStream(
                top.mcfpp.antlr.mcfppLexer(org.antlr.v4.runtime.CharStreams.fromString(text))))
            val unit = parser.compilationUnit()
            assertEquals(0, parser.numberOfSyntaxErrors)
            fun scan(node: org.antlr.v4.runtime.tree.ParseTree) {
                if (node is top.mcfpp.antlr.mcfppParser.InlineLambdaContext) {
                    val body = text.substring(node.start.startIndex, node.stop.stopIndex + 1)
                    val restored = top.mcfpp.mni.expand.FrozenCallbackSyntax.capture(node, body)
                        .restore(body, node.start.startIndex, node.stop.stopIndex + 1)
                    assertNotSame(node, restored)
                    assertEquals(node.toStringTree(parser), restored.toStringTree(parser))
                    fun tree(mode: org.antlr.v4.runtime.atn.PredictionMode): String {
                        val candidate = top.mcfpp.antlr.mcfppParser(org.antlr.v4.runtime.CommonTokenStream(
                            top.mcfpp.antlr.mcfppLexer(org.antlr.v4.runtime.CharStreams.fromString(body))))
                        candidate.interpreter.predictionMode = mode
                        candidate.errorHandler = org.antlr.v4.runtime.BailErrorStrategy()
                        val result = try { candidate.inlineLambda() } catch (failure: org.antlr.v4.runtime.misc.ParseCancellationException) {
                            if (mode != org.antlr.v4.runtime.atn.PredictionMode.SLL) throw failure
                            candidate.reset()
                            candidate.interpreter.predictionMode = org.antlr.v4.runtime.atn.PredictionMode.LL
                            candidate.inlineLambda()
                        }
                        return result.toStringTree(candidate).also {
                            assertEquals(org.antlr.v4.runtime.Token.EOF, candidate.currentToken.type)
                        }
                    }
                    assertEquals(tree(org.antlr.v4.runtime.atn.PredictionMode.LL), tree(org.antlr.v4.runtime.atn.PredictionMode.SLL), body)
                }
                for (index in 0 until node.childCount) scan(node.getChild(index))
            }
            scan(unit)
        }
    }
}
