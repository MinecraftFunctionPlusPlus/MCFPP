package top.mcfpp.testing

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONWriter
import top.mcfpp.CompilerOptions
import top.mcfpp.Project
import top.mcfpp.library.mcfpm.McfpmCliCompiler
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.project.*
import java.io.PrintWriter
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.stream.XMLOutputFactory

data class McfppTestCase(val id: String, val function: String, val file: String, val line: Int,
    val disabled: String?, val before: List<String>, val after: List<String>)

/** Compiles main + test sources, then executes each case in a fresh DSB world. */
class McfppTestRunner(private val out: PrintWriter, private val err: PrintWriter, private val teamcity: Boolean = false) {
    fun run(project: ProjectDefinition, prepared: PreparedProject, jar: Path?, java: String,
            filter: String?, list: Boolean): Int {
        val tests = project.root.resolve("src/test/mcfpp")
        val hasTests = Files.isDirectory(tests) && Files.walk(tests).use { paths ->
            paths.anyMatch { Files.isRegularFile(it) && it.toString().endsWith(".mcfpp") }
        }
        if (!hasTests) {
            require(filter == null) { "No MCFPP tests match '$filter'" }
            out.println("No MCFPP source tests found.")
            return 0
        }
        val work = project.output.resolve("tests/mcfpp")
        deleteTree(work, project.root)
        val source = Files.createDirectories(work.resolve("sources"))
        val origins = linkedMapOf<Path, Path>()
        val configRoot = project.root.resolve(project.values["root"] as? String ?: "").normalize()
        fun copy(root: Path, test: Boolean) {
            if (!Files.isDirectory(root)) return
            Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".mcfpp") }.sorted().forEach { input ->
                val relative = root.relativize(input)
                val output = source.resolve(relative).let { if (test) it.resolveSibling("__test__" + it.fileName) else it }
                require(!Files.exists(output)) { "Test source path collision: $input" }
                Files.createDirectories(output.parent)
                Files.copy(input, output)
                origins[output.toAbsolutePath().normalize()] = input.toAbsolutePath().normalize()
            } }
        }
        copy(configRoot.resolve(project.values["sourcePath"] as? String ?: "src/main/mcfpp"), false)
        copy(tests, true)
        val assertions = source.resolve("__mcfpp_test_api.mcfpp")
        require(!Files.exists(assertions)) { "Reserved test source name: ${assertions.fileName}" }
        val api = requireNotNull(javaClass.getResourceAsStream("/testing/assertions.mcfpp")) { "Missing bundled MCFPP test API" }
        api.use { Files.copy(it, assertions) }
        val config = work.resolve("mcfpp.toml")
        val values = LinkedHashMap(project.values).apply {
            put("root", configRoot.toString()); put("sourcePath", source.toString())
            put("targetPath", work.resolve("compiled").toString())
            put("noMcLib", true); put("noDatapack", false)
            // Test packs are executable consumers, including when the project publishes a library.
            put("args", (project.values["args"] as? List<*>)?.filterIsInstance<String>().orEmpty().filterNot { it == "-isLib" })
        }
        atomicWrite(config, toml(values).toByteArray())
        var cases = emptyList<McfppTestCase>()
        val register: () -> Unit = {
            val namespace = Namespace("mcfpp.test")
            listOf(Test::class.java, BeforeEach::class.java, AfterEach::class.java, Disabled::class.java).forEach {
                namespace.scope.addAnnotation(it.simpleName, it)
            }
            GlobalScope.stdNamespaces["mcfpp.test"] = namespace
        }
        val collect: () -> Unit = { cases = discover(origins) }
        val init = Project.stageProcessor[Project.CompileStage.INIT.ordinal]
        val emitted = Project.stageProcessor[Project.CompileStage.GEN_DATAPACK.ordinal]
        init.add(register); emitted.add(collect)
        val exit = try {
            URLClassLoader(prepared.classpath.map { it.toUri().toURL() }.toTypedArray(), javaClass.classLoader).use { loader ->
                val request = prepared.compilerRequest(loader).toMutableMap().apply { put("configuration", config) }
                McfpmCliCompiler.compile(CompilerOptions().also { it.file = config.toFile() }, request, err)
            }
        } finally { init.remove(register); emitted.remove(collect) }
        if (exit != 0) return exit
        val pattern = filter?.let { glob(it) }
        cases = cases.filter { pattern == null || pattern.matches(it.id) }
        require(cases.isNotEmpty()) { if (filter == null) "No @Test functions found in src/test/mcfpp" else "No MCFPP tests match '$filter'" }
        atomicWrite(work.resolve("discovery.json"), JSON.toJSONBytes(mapOf("schema" to 1, "tests" to cases), JSONWriter.Feature.PrettyFormat))
        if (list) {
            cases.forEach { out.println("${it.id}\t${it.file}:${it.line}" + if (it.disabled != null) "\tDISABLED ${it.disabled}" else "") }
            return 0
        }
        require(cases.all { it.disabled != null } || jar != null && Files.isRegularFile(jar)) {
            "Set --dsb-jar <datapack-sandbox-cli.jar> or DSB_JAR. See https://dbs.afox.moe/workflows/installation.html"
        }
        val pack = work.resolve("compiled").resolve(project.values["name"] as? String ?: configRoot.fileName.toString())
        event("testSuiteStarted", "name" to "MCFPP")
        val results = try { cases.mapIndexed { index, test -> execute(test, index, project, work, pack, jar, java) } }
            finally { event("testSuiteFinished", "name" to "MCFPP") }
        atomicWrite(work.resolve("results.json"), JSON.toJSONBytes(mapOf("schema" to 1, "tests" to results), JSONWriter.Feature.PrettyFormat))
        writeXml(work.resolve("TEST-mcfpp.xml"), results)
        val failed = results.count { it["status"] == "failed" }
        val skipped = results.count { it["status"] == "skipped" }
        out.println("MCFPP tests: ${results.size - failed - skipped} passed, $failed failed, $skipped skipped.")
        out.println("MCFPP test report: ${work.resolve("TEST-mcfpp.xml")}")
        return if (failed == 0) 0 else 1
    }

    private fun discover(origins: Map<Path, Path>): List<McfppTestCase> {
        val functions = mutableListOf<Function>()
        GlobalScope.localNamespaces.values.forEach { it.scope.forEachFunction { f -> functions.add(f) } }
        return functions.filter { f -> f.annotations.any { it is Test } }.map { f ->
            val file = f.declarationFile?.toPath()?.toAbsolutePath()?.normalize()
            val original = origins[file] ?: error("Missing source location for test ${f.identifier}")
            require(original.toString().replace('\\', '/').contains("/src/test/mcfpp/")) { "@Test must be declared in src/test/mcfpp: $original" }
            require(f.annotations.count { it is TestAnnotation && it !is Disabled } == 1) { "Conflicting test lifecycle annotations on ${f.identifier}" }
            fun hooks(before: Boolean) = functions.filter { hook ->
                hook.declarationFile?.toPath()?.toAbsolutePath()?.normalize() == file &&
                    hook.annotations.any { if (before) it is BeforeEach else it is AfterEach }
            }.sortedBy { it.sourceDeclarationContext?.start?.line ?: 0 }.map { it.namespaceID.toString() }
            McfppTestCase("${f.namespace}:${f.identifier}", f.namespaceID.toString(), original.toString(),
                f.sourceDeclarationContext?.start?.line ?: f.ast?.start?.line ?: 1,
                f.annotations.filterIsInstance<Disabled>().firstOrNull()?.reason, hooks(true), hooks(false))
        }.sortedBy { it.id }
    }

    private fun execute(test: McfppTestCase, index: Int, project: ProjectDefinition, work: Path,
                        pack: Path, jar: Path?, java: String): Map<String, Any?> {
        val start = System.nanoTime()
        var status = "skipped"
        var message = test.disabled.orEmpty()
        event("testStarted", "name" to test.id, "locationHint" to "mcfpp://${Path.of(test.file).toUri().rawPath}:${test.line}")
        if (test.disabled == null) {
            try {
                val directory = Files.createDirectories(work.resolve("cases/$index"))
                val wrapper = pack.resolve("data/mcfpp_test/function/case_$index.mcfunction")
                require(!Files.exists(wrapper)) { "Reserved test function already exists: $wrapper" }
                Files.createDirectories(wrapper.parent)
                Files.writeString(wrapper, (test.before + test.function + test.after).joinToString("\n") {
                    "data modify storage mcfpp:system stack_frame prepend value {}\nfunction $it\ndata remove storage mcfpp:system stack_frame[0]"
                } + "\n")
                val manifest = directory.resolve("test.dps.json")
                atomicWrite(manifest, JSON.toJSONBytes(mapOf(
                    "version" to (project.values["version"] ?: "1.21.8"), "packs" to listOf(pack.toString()),
                    "unsupported" to "error", "failOnMissingResources" to true,
                    "world" to mapOf("players" to listOf(mapOf("name" to "MCFPPTest", "position" to listOf(0, 64, 0)))),
                    "steps" to listOf(mapOf("load" to true), mapOf("commands" to listOf(
                        "scoreboard objectives add mcfpp_test dummy", "scoreboard players set #failures mcfpp_test 0")),
                        mapOf("function" to "mcfpp_test:case_$index")),
                    "assertions" to listOf(mapOf("score" to mapOf("target" to "#failures", "objective" to "mcfpp_test", "equals" to 0)))
                ), JSONWriter.Feature.PrettyFormat))
                val report = directory.resolve("dsb-report.json")
                val outputs = directory.resolve("dsb-outputs.jsonl")
                val log = directory.resolve("dsb.log")
                val process = ProcessBuilder(java, "-Dfile.encoding=UTF-8", "-jar", requireNotNull(jar).toAbsolutePath().toString(),
                    "check", manifest.toString(), "--report-file", report.toString(), "--outputs-file", outputs.toString()).directory(project.root.toFile())
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start()
                val code = try { process.waitFor() } catch (interrupted: InterruptedException) {
                    process.destroyForcibly(); Thread.currentThread().interrupt(); throw interrupted
                }
                val result = if (Files.isRegularFile(report)) JSON.parseArray(Files.readString(report)).firstOrNull() as? Map<*, *> else null
                status = if (code == 0 && result?.get("passed") == true) "passed" else "failed"
                if (status == "failed") message = buildString {
                    appendLine("DSB exit code: $code")
                    if (result != null) appendLine(JSON.toJSONString(result))
                    if (Files.isRegularFile(outputs)) appendLine(Files.readString(outputs))
                    append(Files.readString(log))
                }
            } catch (failure: Exception) {
                if (failure is InterruptedException) throw failure
                status = "failed"; message = failure.message ?: failure.toString()
            }
        }
        val seconds = (System.nanoTime() - start) / 1_000_000_000.0
        if (status == "failed") event("testFailed", "name" to test.id, "message" to "MCFPP assertion or DSB execution failed", "details" to message)
        if (status == "skipped") event("testIgnored", "name" to test.id, "message" to message)
        event("testFinished", "name" to test.id, "duration" to (seconds * 1000).toLong().toString())
        out.println("${status.uppercase()} ${test.id} (${test.file}:${test.line})" + if (status == "skipped") " $message" else "")
        if (status == "failed") err.println(message)
        out.flush(); err.flush()
        return linkedMapOf("id" to test.id, "file" to test.file, "line" to test.line, "status" to status, "message" to message, "seconds" to seconds)
    }

    private fun writeXml(path: Path, results: List<Map<String, Any?>>) {
        Files.newBufferedWriter(path).use { output ->
            val xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output)
            xml.writeStartDocument("UTF-8", "1.0"); xml.writeStartElement("testsuite")
            xml.writeAttribute("name", "MCFPP"); xml.writeAttribute("tests", results.size.toString())
            xml.writeAttribute("failures", results.count { it["status"] == "failed" }.toString())
            xml.writeAttribute("errors", "0"); xml.writeAttribute("skipped", results.count { it["status"] == "skipped" }.toString())
            results.forEach { result ->
                val id = result["id"].toString()
                xml.writeStartElement("testcase"); xml.writeAttribute("name", id.substringAfter(':'))
                xml.writeAttribute("classname", id.substringBefore(':')); xml.writeAttribute("file", result["file"].toString())
                xml.writeAttribute("line", result["line"].toString()); xml.writeAttribute("time", result["seconds"].toString())
                if (result["status"] != "passed") {
                    xml.writeStartElement(if (result["status"] == "skipped") "skipped" else "failure")
                    xml.writeCharacters(result["message"].toString().filter { it == '\n' || it == '\r' || it == '\t' || it >= ' ' })
                    xml.writeEndElement()
                }
                xml.writeEndElement()
            }
            xml.writeEndElement(); xml.writeEndDocument(); xml.close()
        }
    }

    private fun glob(value: String): Regex = Regex(value.split('*').joinToString(".*") { Regex.escape(it) })

    private fun event(name: String, vararg attributes: Pair<String, String>) {
        if (!teamcity) return
        fun escape(value: String) = value.replace("|", "||").replace("'", "|'").replace("\n", "|n")
            .replace("\r", "|r").replace("[", "|[").replace("]", "|]")
        out.println("##teamcity[$name " + attributes.joinToString(" ") { "${it.first}='${escape(it.second)}'" } + "]")
        out.flush()
    }
}
