package top.mcfpp.cli

import picocli.CommandLine
import picocli.CommandLine.*
import top.mcfpp.CompilerFlags
import top.mcfpp.CompilerOptions
import top.mcfpp.MCFPP
import top.mcfpp.api.library.LibraryCompilerApi
import top.mcfpp.library.api.LibraryCompilerSession
import top.mcfpp.library.mcfpm.McfpmCliCompiler
import top.mcfpp.compilerCommandLine
import top.mcfpp.util.LogLevel
import top.mcfpp.project.*
import java.io.PrintWriter
import java.net.URLClassLoader
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.Callable

object ProjectCli {
    fun run(args: Array<String>, out: PrintWriter, err: PrintWriter, directory: Path = Path.of("")): Int {
        val root = Root(directory.toAbsolutePath().normalize())
        val cli = CommandLine(root).setOut(out).setErr(err)
        cli.addSubcommand("init", Init(root))
        cli.addSubcommand("build", Build(root))
        cli.addSubcommand("test", TestSources(root))
        cli.addSubcommand("debug", Debug(root))
        cli.addSubcommand("ide-model", IdeModel(root))
        val actions = linkedMapOf("install" to "Prepare locked dependencies without compiling sources.",
            "update" to "Update dependencies within their declared version ranges.",
            "clean" to "Remove project build outputs.")
        for ((name, description) in actions) {
            val command = CommandLine(Action(root, name))
            command.commandSpec.usageMessage().description(description)
            cli.addSubcommand(name, command)
        }
        for ((name, remove) in listOf("add" to false, "remove" to true)) {
            val command = CommandLine(Dependency(root, remove))
            command.commandSpec.usageMessage().description(if (remove) "Remove a direct dependency and refresh its lock." else "Add a direct dependency and install it.")
            cli.addSubcommand(name, command)
        }
        cli.setExecutionExceptionHandler { exception, _, _ ->
            var cause: Throwable = exception
            while (cause is java.lang.reflect.InvocationTargetException && cause.cause != null) cause = cause.cause!!
            err.println("MCFPP: ${cause.message ?: cause.javaClass.name}")
            err.flush()
            if (cause is IllegalArgumentException) 2 else 1
        }
        return cli.execute(*args)
    }

    @Command(name = "mcfpp", description = ["MCFPP projects, dependencies, and builds."], mixinStandardHelpOptions = true,
        version = ["MCFPP ${MCFPP.VERSION} (toolchain ${ProjectDefinition.TOOLCHAIN})"])
    private class Root(val directory: Path) : Callable<Int> {
        @Spec lateinit var spec: Model.CommandSpec
        @Option(names = ["--project"], scope = ScopeType.INHERIT, description = ["Project directory or mcfpp.toml."])
        var project: Path? = null
        @Option(names = ["--offline"], scope = ScopeType.INHERIT, description = ["Forbid network access, including tool downloads."])
        var offline = false
        @Option(names = ["--locked"], scope = ScopeType.INHERIT, description = ["Require current lockfiles without modifying them."])
        var locked = false
        val out: PrintWriter get() = spec.commandLine().out
        val err: PrintWriter get() = spec.commandLine().err
        override fun call(): Int { spec.commandLine().usage(out); return 0 }

        fun <T> withProject(action: (ProjectDefinition) -> T): T {
            val file = ProjectDefinition.find(directory, project)
            val generated = file.parent.resolve(".mcfpp")
            Files.createDirectories(generated)
            return FileChannel.open(generated.resolve("command.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
                val lock = channel.tryLock() ?: error("Another MCFPP command is using this project")
                lock.use { action(ProjectDefinition.load(file)) }
            }
        }
    }

    @Command(description = ["Create an MCFPP project."], mixinStandardHelpOptions = true)
    private class Init(val root: Root) : Callable<Int> {
        @Parameters(index = "0", arity = "0..1", defaultValue = ".") lateinit var directory: Path
        @Option(names = ["--mni"], description = ["Include Kotlin MNI sources and tests."]) var mni = false
        override fun call(): Int {
            val project = ProjectTemplates.create(root.directory.resolve(directory), mni)
            root.out.println("Created ${project.file}. Run mcfpp build.")
            return 0
        }
    }

    @Command(description = ["Prepare dependencies and compile the datapack."], mixinStandardHelpOptions = true)
    private class Build(val root: Root) : Callable<Int> {
        @Mixin var flags = CompilerFlags()
        override fun call(): Int = root.withProject { project ->
            val fileFlags = CompilerFlags()
            val fileArgs = (project.values["args"] as? List<*>)?.filterIsInstance<String>().orEmpty()
            compilerCommandLine(fileFlags).parseArgs(*fileArgs.toTypedArray())
            val level = flags.level ?: fileFlags.level
            val progress = if (level != null && level >= LogLevel.WARN) PrintWriter(java.io.Writer.nullWriter()) else root.err
            ProjectPackages(progress).prepare(project, root.offline, root.locked, mode = "build").use { prepared ->
                prepared.commit()
                URLClassLoader(prepared.classpath.map { it.toUri().toURL() }.toTypedArray(), LibraryCompilerApi::class.java.classLoader).use { loader ->
                    val options = CompilerOptions().also {
                        it.file = project.file.toFile()
                        it.debug = flags.debug; it.ignoreStdLib = flags.ignoreStdLib; it.isLib = flags.isLib
                        it.maxWhileInline = flags.maxWhileInline; it.printAll = flags.printAll; it.level = flags.level
                        it.dumpIR = flags.dumpIR
                        it.debugInfo = flags.debugInfo
                        it.optimizationLevel = flags.optimizationLevel; it.disabledPasses = flags.disabledPasses
                        it.macroMaxDepth = flags.macroMaxDepth; it.macroMaxNodes = flags.macroMaxNodes; it.dumpMacroExpansion = flags.dumpMacroExpansion
                    }
                    val exit = McfpmCliCompiler.compile(options, prepared.compilerRequest(loader), root.err)
                    if (exit == 0) {
                        val output = if ((flags.debugInfo ?: fileFlags.debugInfo) == true) project.output.resolve("debug") else project.output
                        root.out.println("Built $output")
                        val name = project.values["name"] as? String ?: project.root.fileName.toString()
                        root.out.println("Data pack: ${output.resolve(name)}")
                        val resourcePack = output.resolve(name + "-resources")
                        if (Files.isDirectory(resourcePack)) root.out.println("Resource pack: $resourcePack")
                        (flags.dumpIR ?: fileFlags.dumpIR)?.let { directory ->
                            val path = Path.of(directory).let { if (it.isAbsolute) it else project.output.resolve(it) }
                            for (name in listOf("01-compiled.ir", "02-optimized.ir", "03-linked.ir"))
                                root.out.println("MCFPP IR: ${path.resolve(name)}")
                        }
                    }
                    exit
                }
            }
        }
    }

    @Command(name = "ide-model", description = ["Prepare dependencies and export the editor project model."], mixinStandardHelpOptions = true)
    private class IdeModel(val root: Root) : Callable<Int> {
        @Option(names = ["--json"], description = ["Emit the versioned JSON model (the default)."])
        var json = false
        override fun call(): Int = root.withProject { project ->
            ProjectPackages(root.err).prepare(project, root.offline, root.locked).use { prepared ->
                val model = IdeProjectModel.create(project, prepared)
                prepared.commit()
                val bytes = com.alibaba.fastjson2.JSON.toJSONBytes(model, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat)
                atomicWrite(project.generated.resolve("ide-model.json"), bytes)
                root.out.println(String(bytes, Charsets.UTF_8))
                root.out.flush()
            }
            0
        }
    }

    @Command(mixinStandardHelpOptions = true)
    private class Debug(val root: Root) : Callable<Int> {
        @Option(names = ["--stdio"], required = true, description = ["Serve the Debug Adapter Protocol on stdin/stdout."])
        var stdio = false
        override fun call(): Int {
            val selected = root.project?.let { root.directory.resolve(it).normalize() } ?: root.directory
            val directory = if (Files.isRegularFile(selected)) selected.parent else selected
            val adapter = top.mcfpp.debug.McfppDebugAdapter(directory)
            val dispatch = java.util.concurrent.Executors.newCachedThreadPool { task -> Thread(task, "mcfpp-dap-io").apply { isDaemon = true } }
            val launcher = org.eclipse.lsp4j.debug.launch.DSPLauncher.createServerLauncher(adapter, System.`in`, System.out, dispatch, { it })
            adapter.connect(launcher.remoteProxy)
            try { launcher.startListening().get() } finally { adapter.close(); dispatch.shutdownNow() }
            return 0
        }
    }

    @Command(mixinStandardHelpOptions = true)
    private class TestSources(val root: Root) : Callable<Int> {
        @Option(names = ["--debug-info"], description = ["Compile source tests with debugger metadata."])
        var debugInfo = false
        @Option(names = ["--dsb-jar"], description = ["Datapack Sandbox CLI JAR (default: DSB_JAR)."])
        var jar: Path? = System.getenv("DSB_JAR")?.takeIf { it.isNotBlank() }?.let(Path::of)
        @Option(names = ["--dsb-java"], description = ["Java executable for DSB (default: DSB_JAVA or the current JDK)."])
        var java: String = System.getenv("DSB_JAVA")?.takeIf { it.isNotBlank() }
            ?: Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java").toString()
        @Option(names = ["--tests"], description = ["Select MCFPP namespace:function IDs; '*' is a wildcard. Runs MCFPP tests only."])
        var filter: String? = null
        @Option(names = ["--list"], description = ["Compile and list MCFPP tests without executing them or Java tests."])
        var list = false
        @Option(names = ["--teamcity"], description = ["Emit IDE test service messages."])
        var teamcity = false
        override fun call(): Int = root.withProject { project ->
            val mode = if (list || filter != null) "build" else "test"
            ProjectPackages(root.err).prepare(project, root.offline, root.locked, mode = mode).use { prepared ->
                prepared.commit()
                if (project.mniEnabled && mode == "test") root.out.println("Java tests completed.")
                top.mcfpp.testing.McfppTestRunner(root.out, root.err, teamcity).run(project, prepared,
                    jar?.let { root.directory.resolve(it).normalize() }, java, filter, list, debugInfo)
            }
        }
    }

    @Command(mixinStandardHelpOptions = true)
    private class Action(val root: Root, val action: String) : Callable<Int> {
        override fun call(): Int = root.withProject { project ->
            if (action == "clean") {
                deleteTree(project.output, project.root)
                root.out.println("Cleaned ${project.output}")
            } else {
                require(action != "update" || !root.locked) { "update cannot be used with --locked" }
                ProjectPackages(root.err).prepare(project, root.offline, root.locked, action == "update", action).use { prepared -> prepared.commit() }
                root.out.println("Dependencies ready.")
            }
            0
        }
    }

    @Command(mixinStandardHelpOptions = true)
    private class Dependency(val root: Root, val remove: Boolean) : Callable<Int> {
        @Parameters(index = "0", paramLabel = "COORDINATE") lateinit var coordinate: String
        @Option(names = ["--mni"], description = ["Manage a Java/Maven dependency."]) var mni = false
        @Option(names = ["--configuration"], description = ["Gradle dependency configuration (default: implementation)."])
        var configuration: String? = null
        override fun call(): Int = root.withProject { project ->
            require(!root.locked) { "Dependency changes cannot be used with --locked" }
            require(mni || configuration == null) { "--configuration requires --mni" }
            var next = project.packages
            if (mni) {
                val old = next.mni
                val entries = if (remove) {
                    require(coordinate.count { it == ':' } == 1) { "MNI removal requires group:artifact" }
                    old.filterNot { it.module == coordinate && (configuration == null || it.configuration == configuration) }.also {
                        require(it.size != old.size) { "No matching direct MNI dependency: $coordinate" }
                    }
                } else {
                    val dependency = MniDependency(coordinate, configuration ?: "implementation")
                    old.filterNot { it.module == dependency.module && it.configuration == dependency.configuration } + dependency
                }
                next = next.copy(mni = entries)
            } else {
                val entries = LinkedHashMap(next.mcfpp)
                if (remove) require(entries.remove(coordinate) != null) { "No direct MCFPP dependency: $coordinate" }
                else {
                    val separator = coordinate.indexOf('@')
                    require(separator > 0 && separator < coordinate.lastIndex) { "Use group:name@VERSION_REQUIREMENT" }
                    entries[coordinate.substring(0, separator)] = coordinate.substring(separator + 1)
                }
                next = next.copy(mcfpp = entries)
            }
            val enable = mni && !remove && !project.mniEnabled
            val values = if (enable) LinkedHashMap(project.values).apply {
                put("mni", LinkedHashMap(project.mni).apply { put("enabled", true) })
            } else project.values
            val candidate = project.copy(values = values, packages = next)
            ProjectPackages(root.err).prepare(candidate, root.offline, false).use { prepared ->
                val writes = linkedMapOf(project.root.resolve("mcfpp-packages.json") to next.bytes())
                if (enable) writes[project.file] = project.enableMni().toByteArray()
                prepared.commit(writes)
            }
            if (enable) Files.createDirectories(project.root.resolve("src/main/java"))
            root.out.println(if (remove) "Removed $coordinate" else "Added $coordinate")
            0
        }
    }
}
