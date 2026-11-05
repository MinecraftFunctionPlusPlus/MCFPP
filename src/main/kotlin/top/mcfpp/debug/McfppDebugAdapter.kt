package top.mcfpp.debug

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import org.eclipse.lsp4j.debug.*
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import top.mcfpp.project.ProjectDefinition
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Source language semantics live here; DSB only knows command boundaries and physical frames. */
class McfppDebugAdapter(private val directory: Path) : IDebugProtocolServer, AutoCloseable {
    private lateinit var client: IDebugProtocolClient
    private val owner = Executors.newSingleThreadExecutor { task -> java.lang.Thread(task, "mcfpp-debug-adapter").apply { isDaemon = true } }
    @Volatile private var closed = false
    @Volatile private var buildProcess: Process? = null
    private var sandbox: SandboxDebugClient? = null
    private var metadata = JSONObject()
    private var functions = JSONObject()
    private var snapshot = JSONObject()
    private var rawStop = JSONObject()
    private var frames = emptyList<Frame>()
    private val requestedBreakpoints = linkedMapOf<String, List<Int>>()
    private val references = linkedMapOf<Int, Any?>()
    private val nextReference = AtomicInteger()
    private var paused = false
    private var prepared = false
    private var started = false
    private var terminated = false
    private var stopOnEntry = false
    private var entryFunctions = emptyList<String>()
    private var test = false
    private var step: Step? = null
    private var outputCursor = 0
    private var linesStartAt1 = true
    private var columnsStartAt1 = true

    private data class Frame(val activation: Long, val function: String, var point: JSONObject?, var raw: JSONObject)
    private data class Step(val mode: String, val depth: Int, val statement: String?, val function: String?, val line: Int?)

    fun connect(client: IDebugProtocolClient) { this.client = client }
    private fun <T> guarded(block: () -> T): T = try { check(!closed) { "Debug session closed" }; block() }
        catch (error: Exception) { throw ResponseErrorException(ResponseError(-32602, error.message ?: error.javaClass.simpleName, null)) }
    private fun action(block: () -> Unit): CompletableFuture<Void> = CompletableFuture.runAsync({ guarded(block) }, owner)
    private fun <T> query(block: () -> T): CompletableFuture<T> = CompletableFuture.supplyAsync({ guarded(block) }, owner)
    private fun output(text: String, category: String = "console") {
        if (::client.isInitialized) client.output(OutputEventArguments().apply { this.output = text; this.category = category })
    }

    override fun initialize(args: InitializeRequestArguments): CompletableFuture<Capabilities> {
        linesStartAt1 = args.linesStartAt1 != false
        columnsStartAt1 = args.columnsStartAt1 != false
        return CompletableFuture.completedFuture(Capabilities().apply {
            supportsConfigurationDoneRequest = true
            supportsEvaluateForHovers = true
            supportsTerminateRequest = true
            supportsSetVariable = false
            supportsConditionalBreakpoints = false
        })
    }

    override fun launch(args: MutableMap<String, Any>): CompletableFuture<Void> = action {
        check(!prepared) { "This adapter owns one debug launch" }
        try {
            val root = Path.of(args["projectPath"]?.toString() ?: directory.toString()).toAbsolutePath().normalize()
            val project = ProjectDefinition.load(ProjectDefinition.find(root, null))
            val function = args["function"]?.toString()
            val testId = args["test"]?.toString()
            require((function != null) != (testId != null)) { "Select exactly one function or MCFPP test" }
            test = testId != null
            stopOnEntry = args["stopOnEntry"] == true
            val commands = mutableListOf(if (test) "test" else "build", "--project", project.root.toString(), "--debug-info")
            if (test) commands.addAll(listOf("--list", "--tests", testId!!))
            if (args["offline"] == true) commands.add("--offline")
            if (args["locked"] == true) commands.add("--locked")
            compile(project.root, commands)
            val sidecar = project.output.resolve(if (test) "tests/mcfpp/compiled/debug/mcfpp.debug.json" else "debug/mcfpp.debug.json")
            metadata = JSON.parseObject(Files.readString(sidecar))
            require(metadata.getIntValue("schema") == 1) { "Unsupported MCFPP debug metadata schema" }
            functions = metadata.getJSONObject("functions")
            validateMetadata()
            val sequence = if (test) {
                val discovery = JSON.parseObject(Files.readString(project.output.resolve("tests/mcfpp/discovery.json")))
                val selected = discovery.getJSONArray("tests").map { it as JSONObject }.singleOrNull { it.getString("id") == testId }
                    ?: error("Select one existing MCFPP test: $testId")
                require(selected["disabled"] == null) { "Test is disabled: ${selected["disabled"]}" }
                selected.getJSONArray("before").map { it.toString() } + selected.getString("function") + selected.getJSONArray("after").map { it.toString() }
            } else {
                val selected = if (functions.containsKey(function!!)) function else functions.entries.singleOrNull { (id, info) ->
                    id.substringBefore(':') + ":" + (info as JSONObject).getString("name") == function
                }?.key
                require(selected != null) { "Function '$function' is not in this debug build" }
                require(functions.getJSONObject(selected).getBooleanValue("entry")) { "Select a top-level function without parameters" }
                listOf(selected)
            }
            val jar = Path.of(args["dsbJar"]?.toString() ?: System.getenv("DSB_JAR") ?: error("Set dsbJar or DSB_JAR"))
                .toAbsolutePath().normalize()
            require(Files.isRegularFile(jar)) { "Datapack Sandbox JAR does not exist: $jar" }
            val java = args["dsbJava"]?.toString() ?: System.getenv("DSB_JAVA")
                ?: Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java").toString()
            sandbox = SandboxDebugClient(java, jar, project.root, { name, body ->
                if (!closed) owner.execute { if (!closed) handleEvent(name, body) }
            }, { output(it, "stderr") })
            box().awaitReady(metadata.getString("version"))
            val wrapper = buildString {
                if (test) append("scoreboard objectives add mcfpp_test dummy\nscoreboard players set #failures mcfpp_test 0\n")
                sequence.forEach {
                    append("data modify storage mcfpp:system stack_frame prepend value {}\n")
                    append("function $it\n")
                    append("data remove storage mcfpp:system stack_frame[0]\n")
                }
            }
            box().request("createSandbox", mapOf("version" to metadata.getString("version"), "packs" to listOf(metadata.getString("pack")),
                "defaultPlayerName" to if (test) "MCFPPTest" else "MCFPPDebug", "unsupported" to "error",
                "functionSources" to listOf(mapOf("id" to "mcfpp_debug:entry", "text" to wrapper))))
            if (test) box().request("applyWorldFixture", mapOf("world" to mapOf("players" to listOf(
                mapOf("name" to "MCFPPTest", "position" to listOf(0, 64, 0))))))
            entryFunctions = listOf("mcfpp_debug:entry")
            installBreakpoints()
            prepared = true
            client.initialized()
        } catch (failure: Exception) {
            output("Unable to launch MCFPP debugger: ${failure.message}\n", "stderr")
            sandbox?.close()
            finish(1)
            throw failure
        }
    }

    private fun compile(root: Path, args: List<String>) {
        val java = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java").toString()
        val process = ProcessBuilder(listOf(java, "-Dfile.encoding=UTF-8", "-cp", System.getProperty("java.class.path"), "top.mcfpp.MCFPPKt") + args)
            .directory(root.toFile()).redirectErrorStream(true).start()
        buildProcess = process
        try {
            process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.forEach { output(it + "\n") } }
            check(process.waitFor() == 0) { "Debug build failed; see compiler output" }
        } finally { buildProcess = null }
    }

    private fun validateMetadata() {
        metadata.getJSONObject("sources").forEach { (file, hash) ->
            require(Files.isRegularFile(Path.of(file)) && DebugMetadata.hash(Path.of(file)) == hash) { "Source changed since debug build: $file" }
        }
        val pack = Path.of(metadata.getString("pack"))
        metadata.getJSONObject("artifacts")?.forEach { (relative, hash) ->
            val file = pack.resolve(relative).normalize()
            require(file.startsWith(pack) && Files.isRegularFile(file) && DebugMetadata.hash(file) == hash) { "Datapack changed since debug build: $file" }
        }
        functions.values.forEach { value ->
            val info = value as JSONObject
            val file = pack.resolve(info.getString("file")).normalize()
            require(file.startsWith(pack) && Files.isRegularFile(file) && DebugMetadata.hash(file) == info.getString("hash")) { "Datapack changed since debug build: $file" }
        }
    }

    override fun configurationDone(args: ConfigurationDoneArguments): CompletableFuture<Void> = action {
        check(prepared && !started) { "The debug build is not ready or execution already started" }
        validateMetadata()
        started = true
        if (stopOnEntry) step = Step("entry", 0, null, null, null)
        box().request("debug.start", mapOf("load" to true, "functions" to entryFunctions, "stopOnEntry" to stopOnEntry))
    }

    override fun setBreakpoints(args: SetBreakpointsArguments): CompletableFuture<SetBreakpointsResponse> = query {
        val file = Path.of(requireNotNull(args.source.path) { "Breakpoint source has no path" }).toAbsolutePath().normalize().toString()
        val lines = args.breakpoints?.map { sourceLine(it.line) }.orEmpty()
        requestedBreakpoints[file] = lines
        if (prepared) installBreakpoints()
        SetBreakpointsResponse().apply {
            breakpoints = lines.map { line -> Breakpoint().apply {
                this.line = clientLine(line)
                isVerified = locations(file, line).isNotEmpty()
                if (!isVerified) message = "This line has no executable runtime sequence point"
                source = args.source
            } }.toTypedArray()
        }
    }

    private fun locations(file: String, line: Int): List<Map<String, Any>> = functions.entries.flatMap { (id, value) ->
        (value as JSONObject).getJSONArray("locations").mapNotNull { entry ->
            val location = entry as JSONObject
            val point = location.getJSONObject("point")
            if (location.getBooleanValue("entry") && sameFile(point.getString("file"), file) && point.getIntValue("line") == line)
                mapOf("function" to id, "line" to location.getIntValue("line")) else null
        }
    }

    private fun installBreakpoints() {
        val points = requestedBreakpoints.flatMap { (file, lines) -> lines.flatMap { locations(file, it) } }.distinct()
        box().request("debug.setBreakpoints", mapOf("breakpoints" to points))
    }

    private fun point(raw: JSONObject): JSONObject? = functions.getJSONObject(raw.getString("id"))?.getJSONArray("locations")
        ?.map { it as JSONObject }?.firstOrNull { it.getIntValue("line") == raw.getIntValue("line") }

    private fun logicalFrames(raw: JSONArray): List<Frame> {
        val result = mutableListOf<Frame>()
        raw.forEach { value ->
            val frame = value as JSONObject
            val info = functions.getJSONObject(frame.getString("id"))
            val source = point(frame)?.getJSONObject("point")
            if (info != null && !info.getBooleanValue("helper")) result.add(Frame(frame.getLongValue("activation"), frame.getString("id"), source, frame))
            else if (source != null && result.isNotEmpty()) result.last().apply { this.point = source; this.raw = frame }
        }
        return result
    }

    private fun handleEvent(name: String, body: JSONObject) {
        when (name) {
            "debug.stopped" -> {
                rawStop = body
                snapshot = body.getJSONObject("snapshot")
                flushOutputs()
                val raw = body.getJSONArray("frames")
                frames = logicalFrames(raw)
                val current = frames.lastOrNull()
                val location = raw.lastOrNull()?.let { point(it as JSONObject) }
                val position = location?.getJSONObject("point")
                val stepping = step
                val reached = if (stepping == null) position != null else location?.getBooleanValue("entry") == true && when (stepping.mode) {
                    "entry" -> true
                    "out" -> frames.size < stepping.depth
                    else -> (stepping.mode == "into" || frames.size <= stepping.depth) &&
                        (position?.getString("statement") != stepping.statement || frames.size != stepping.depth ||
                            (raw.lastOrNull() as? JSONObject)?.let { it.getString("id") == stepping.function && it.getIntValue("line") == stepping.line } == true)
                }
                if (position == null || body.getString("reason") != "breakpoint" && stepping != null && !reached) {
                    box().request("debug.step", mapOf("mode" to "into"))
                    return
                }
                paused = true
                references.clear()
                step = null
                client.stopped(StoppedEventArguments().apply {
                    reason = if (body.getString("reason") == "breakpoint") "breakpoint" else if (stepping?.mode == "entry") "entry" else if (stepping != null) "step" else "pause"
                    threadId = 1; allThreadsStopped = true
                    description = current?.function
                })
            }
            "debug.error", "session.lost" -> {
                body.getJSONObject("snapshot")?.let { snapshot = it; flushOutputs() }
                val location = body.getJSONObject("location")
                val original = location?.getString("file")?.let { file -> functions.values.asSequence().map { it as JSONObject }
                    .firstOrNull { Path.of(metadata.getString("pack")).resolve(it.getString("file")).normalize().toString().replace('\\', '/') == file.replace('\\', '/') }
                    ?.getJSONArray("locations")?.map { it as JSONObject }?.firstOrNull { it.getIntValue("line") == location.getIntValue("line") }?.getJSONObject("point") }
                val prefix = original?.let { "${it.getString("file")}:${it.getIntValue("line")}:${it.getIntValue("column")}: " }.orEmpty()
                output("$prefix${body.getString("code") ?: "DEBUG_ERROR"}: ${body.getString("message")}\n", "stderr")
                finish(1)
            }
            "debug.terminated" -> {
                body.getJSONObject("snapshot")?.let { snapshot = it; flushOutputs() }
                val failures = snapshot.getJSONObject("scores")?.getJSONObject("mcfpp_test")?.getIntValue("#failures") ?: 0
                if (test && failures > 0) output("MCFPP test failed: $failures assertion(s).\n", "stderr")
                finish(if (test && failures > 0) 1 else 0)
            }
        }
    }

    private fun flushOutputs() {
        val events = snapshot.getJSONArray("outputs") ?: return
        for (index in outputCursor until events.size) {
            val event = events.getJSONObject(index)
            val command = event.getJSONObject("source")?.getString("command").orEmpty()
            val explicitQuery = listOf("data get ", "scoreboard players get ", "compute ").any { command.startsWith(it) }
            if (event.getString("channel") != "data" || explicitQuery) output(event.getString("text") + "\n", "stdout")
        }
        outputCursor = events.size
    }

    override fun threads(): CompletableFuture<ThreadsResponse> = query { ThreadsResponse().apply {
        threads = arrayOf(org.eclipse.lsp4j.debug.Thread().apply { id = 1; name = "Datapack Sandbox" })
    } }

    override fun stackTrace(args: StackTraceArguments): CompletableFuture<StackTraceResponse> = query {
        requirePaused()
        StackTraceResponse().apply {
            val all = frames.asReversed().mapIndexed { index, frame -> StackFrame().apply {
                id = index + 1; name = frame.function
                val point = frame.point
                source = point?.let { Source().apply { path = it.getString("file"); name = Path.of(path).fileName.toString() } }
                line = clientLine(point?.getIntValue("line") ?: 1)
                column = clientColumn(point?.getIntValue("column") ?: 1)
            } }
            totalFrames = all.size
            val from = args.startFrame ?: 0
            stackFrames = all.drop(from).let { if ((args.levels ?: 0) > 0) it.take(args.levels) else it }.toTypedArray()
        }
    }

    override fun scopes(args: ScopesArguments): CompletableFuture<ScopesResponse> = query {
        requirePaused()
        val frame = frames.asReversed().getOrNull(args.frameId - 1) ?: error("Unknown stack frame")
        ScopesResponse().apply {
            scopes = arrayOf(
                Scope().apply { name = "Locals"; variablesReference = reference(locals(frame, args.frameId - 1)); isExpensive = false },
                Scope().apply { name = "World"; variablesReference = reference(snapshot); isExpensive = true },
            )
        }
    }

    private fun locals(frame: Frame, depth: Int): Map<String, Any?> = frame.point?.getJSONArray("variables")?.associate { item ->
        val variable = item as JSONObject
        variable.getString("name") to runCatching { readVariable(variable, depth, frame.raw) }.getOrElse { Unavailable(it.message ?: "Unavailable") }
    }.orEmpty()

    private data class Unavailable(val reason: String)

    private fun readVariable(variable: JSONObject, depth: Int, context: JSONObject): Any? {
        val saved = variable.getString("savedPath")
        if (depth > 0 && saved != null) return address(saved, depth, context = context)
        return when (variable.getString("kind")) {
            "score" -> {
                val player = variable.getString("player")
                val objective = variable.getString("objective")
                val value = (if (player.startsWith('@')) inspectRead("scoreboard players get $player $objective", context)
                    else snapshot.getJSONObject("scores")?.getJSONObject(objective)?.get(player))
                    ?: error("Value is not initialized at this position")
                if (variable.getString("type") == "bool") (value as Number).toInt() != 0 else value
            }
            "nbt" -> address(variable.getString("path"), depth, variable.getString("source"), context)
            "constant" -> variable.getString("value")?.let { value -> runCatching { JSON.parse(value) }.getOrElse { value } }
            else -> error(variable.getString("value") ?: "No readable runtime location")
        }
    }

    private fun inspectRead(command: String, context: JSONObject): Any? = box().request("debug.inspect",
        mapOf("frame" to context.getLongValue("activation"), "command" to command))["value"]

    private fun address(path: String, depth: Int, source: String? = null, context: JSONObject): Any? {
        val full = if (path.startsWith("storage ") || path.startsWith("entity ") || path.startsWith("block ")) path else "$source $path"
        if (full.startsWith("entity ") || full.startsWith("block ")) return inspectRead("data get $full", context)
        val parts = full.split(' ', limit = 3)
        require(parts.size == 3 && parts[0] == "storage") { "This value's storage address is unavailable" }
        val data = snapshot.getJSONObject("storage")?.get(parts[1]) ?: error("Storage ${parts[1]} is not initialized")
        val adjusted = parts[2].replace(Regex("^stack_frame\\[(\\d+)]")) { "stack_frame[${it.groupValues[1].toInt() + depth}]" }
        return runCatching { DebugValuePaths.read(data, adjusted) }.getOrElse { inspectRead("data get ${parts[0]} ${parts[1]} $adjusted", context) }
    }

    override fun variables(args: VariablesArguments): CompletableFuture<VariablesResponse> = query {
        requirePaused()
        require(references.containsKey(args.variablesReference)) { "Variable handle is no longer valid" }
        val value = references[args.variablesReference]
        val children = when (value) {
            is Map<*, *> -> value.entries.map { it.key.toString() to it.value }
            is List<*> -> value.mapIndexed { index, item -> "[$index]" to item }
            else -> emptyList()
        }
        VariablesResponse().apply {
            variables = children.drop(args.start ?: 0).let { if ((args.count ?: 0) > 0) it.take(args.count) else it }
                .map { (key, child) -> Variable().apply { name = key; this.value = display(child); variablesReference = childReference(child) } }.toTypedArray()
        }
    }

    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluateResponse> = query {
        requirePaused()
        val depth = (args.frameId ?: 1) - 1
        val frame = frames.asReversed().getOrNull(depth) ?: error("Unknown stack frame")
        val value = DebugValuePaths.read(locals(frame, depth), args.expression)
        EvaluateResponse().apply { result = display(value); variablesReference = childReference(value) }
    }

    private fun display(value: Any?): String = when (value) {
        is Unavailable -> "<unavailable: ${value.reason}>"
        null -> "null"
        is String -> value
        else -> JSON.toJSONString(value)
    }
    private fun reference(value: Any?): Int = nextReference.incrementAndGet().also { references[it] = value }
    private fun childReference(value: Any?): Int = if (value is Map<*, *> || value is List<*>) reference(value) else 0

    override fun continue_(args: ContinueArguments): CompletableFuture<ContinueResponse> = query {
        requirePaused(); validateMetadata(); paused = false; references.clear(); step = null
        box().request("debug.continue")
        ContinueResponse().apply { allThreadsContinued = true }
    }
    private fun step(mode: String): CompletableFuture<Void> = action {
        requirePaused(); validateMetadata()
        val raw = rawStop.getJSONArray("frames").lastOrNull() as? JSONObject
        step = Step(mode, frames.size, frames.lastOrNull()?.point?.getString("statement"), raw?.getString("id"), raw?.getIntValue("line"))
        paused = false; references.clear()
        box().request("debug.step", mapOf("mode" to "into"))
    }
    override fun next(args: NextArguments): CompletableFuture<Void> = step("over")
    override fun stepIn(args: StepInArguments): CompletableFuture<Void> = step("into")
    override fun stepOut(args: StepOutArguments): CompletableFuture<Void> = step("out")
    override fun pause(args: PauseArguments): CompletableFuture<Void> = action { box().request("debug.pause") }
    override fun disconnect(args: DisconnectArguments): CompletableFuture<Void> { close(); return CompletableFuture.completedFuture(null) }
    override fun terminate(args: TerminateArguments): CompletableFuture<Void> { close(); return CompletableFuture.completedFuture(null) }

    private fun finish(code: Int) {
        if (terminated) return
        terminated = true; paused = false; references.clear()
        client.exited(ExitedEventArguments().apply { exitCode = code })
        client.terminated(TerminatedEventArguments())
    }
    private fun box(): SandboxDebugClient = sandbox ?: error("Datapack Sandbox is not ready")
    private fun requirePaused() { check(paused) { "Execution is not paused" } }
    private fun sourceLine(line: Int) = if (linesStartAt1) line else line + 1
    private fun clientLine(line: Int) = if (linesStartAt1) line else line - 1
    private fun clientColumn(column: Int) = if (columnsStartAt1) column else column - 1
    private fun sameFile(a: String, b: String) = Path.of(a).toAbsolutePath().normalize() == Path.of(b).toAbsolutePath().normalize()

    override fun close() {
        if (closed) return
        closed = true
        buildProcess?.destroyForcibly()
        sandbox?.close()
        owner.shutdownNow()
    }
}
