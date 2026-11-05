package top.mcfpp

import com.alibaba.fastjson2.JSONReader
import com.alibaba.fastjson2.annotation.JSONField
import com.alibaba.fastjson2.filter.ExtraProcessor
import com.alibaba.fastjson2.reader.ObjectReader
import top.mcfpp.command.CommentLevel
import top.mcfpp.util.LogProcessor
import java.io.File
import java.lang.reflect.Type
import java.nio.file.Path

/** File representation; runtime paths and validated defaults are resolved after deserialization. */
internal class ProjectFileConfig {
    private val defaults = ProjectConfig()
    var name: String? = null
    var version: String = defaults.version
    @field:JSONField(name = "namespace", alternateNames = ["rootNamespace"])
    var rootNamespace: String = defaults.rootNamespace
    var description: String = defaults.description
    var stdLib: List<String> = defaults.stdLib
    var stdNamespace: List<String> = defaults.stdNamespace
    var includes: ArrayList<String> = arrayListOf()
    var jars: ArrayList<String> = arrayListOf()
    var noDatapack: Boolean = defaults.noDatapack
    var noMcLib: Boolean = defaults.noMcLib
    var copyImport: Boolean = defaults.copyImport
    @field:JSONField(deserializeUsing = ConfigPathReader::class)
    var root: Path? = null
    @field:JSONField(deserializeUsing = ConfigPathReader::class)
    var sourcePath: Path? = null
    @field:JSONField(deserializeUsing = ConfigPathReader::class)
    var targetPath: Path? = null
    @field:JSONField(deserializeUsing = ConfigPathReader::class)
    var dataPath: Path? = null
    @field:JSONField(deserializeUsing = ConfigPathReader::class)
    var assetsPath: Path? = null
    var entrypoints: Map<String, String> = emptyMap()
    var resourceOverrides: List<String> = emptyList()
    @field:JSONField(deserializeUsing = ConfigCommentLevelReader::class)
    var commentLevel: CommentLevel = defaults.commentLevel
    @field:JSONField(deserializeUsing = MacroExpansionConfigReader::class)
    var macroExpansion: MacroExpansionConfig? = defaults.macroExpansion
    @field:JSONField(deserializeUsing = LibraryPublicationConfigReader::class)
    var library: LibraryPublicationConfig? = null
    @field:JSONField(deserializeUsing = OptimizationConfigReader::class)
    var optimization: top.mcfpp.optimization.OptimizationConfig = top.mcfpp.optimization.OptimizationConfig()
    var args: Array<String> = emptyArray()

    fun toProjectConfig(file: File): ProjectConfig {
        val resolvedRoot = file.parentFile.toPath().resolve(root ?: Path.of("")).normalize()
        val projectName = name ?: file.parentFile.name
        require(projectName.isNotBlank() && projectName.lowercase() !in setOf(".", "..", ".mcfpp", "imports") &&
            !projectName.endsWith('.') && !projectName.endsWith(' ') &&
            projectName.none { it in "/\\<>:\"|?*" || it.code < 32 }) { "name must be a valid, non-reserved directory name" }
        return ProjectConfig(
            version = version,
            rootNamespace = rootNamespace,
            name = projectName,
            description = description,
            stdLib = stdLib,
            stdNamespace = stdNamespace,
            root = resolvedRoot,
            sourcePath = resolvedRoot.resolve(sourcePath ?: Path.of("src/main/mcfpp")).normalize(),
            targetPath = resolvedRoot.resolve(targetPath ?: Path.of("build")).normalize(),
            dataPath = (dataPath ?: Path.of("src/main/resources/data").takeIf {
                java.nio.file.Files.isDirectory(resolvedRoot.resolve(it))
            })?.let { resolvedRoot.resolve(it).normalize() },
            assetsPath = resolvedRoot.resolve(assetsPath ?: Path.of("src/main/resources/assets")).normalize(),
            entrypoints = entrypoints,
            resourceOverrides = resourceOverrides.onEach { require(!it.startsWith('/') && '\\' !in it &&
                it.split('/').none { segment -> segment in setOf("", ".", "..") }) { "Invalid resourceOverrides path: $it" } },
            includes = includes.mapTo(arrayListOf()) { resolvedRoot.resolve(it).normalize().toString() },
            jars = jars.mapTo(arrayListOf()) { resolvedRoot.resolve(it).normalize().toString() },
            commentLevel = commentLevel,
            noDatapack = noDatapack,
            noMcLib = noMcLib,
            copyImport = copyImport,
            macroExpansion = macroExpansion ?: MacroExpansionConfig(),
            library = library,
            optimization = optimization,
        )
    }
}

internal class ConfigDiagnostics : ExtraProcessor {
    val errors = arrayListOf<String>()
    val unknownKeys = arrayListOf<String>()
    override fun processExtra(target: Any?, key: String, value: Any?) {
        unknownKeys += key
    }
}

private fun JSONReader.configError(message: String) {
    (context.extraProcessor as ConfigDiagnostics).errors += message
}


internal fun readProjectConfig(options: CompilerOptions): ProjectConfig {
    // 读取配置文件并初始化工程配置
    val file = options.file.absoluteFile
    val input = ProjectFileConfig()
    var config = ProjectConfig(root = file.parentFile.toPath(), name = file.nameWithoutExtension)

    val diagnostics = ConfigDiagnostics()
    var readFailure: Exception? = null
    try {
        // 解析工程配置文件
        val values = top.mcfpp.project.ProjectDefinition.readToml(file.toPath()).filterKeys {
            it !in setOf("schema", "mni", "toolchain", "mcfpm")
        }
        JSONReader.of(com.alibaba.fastjson2.JSON.toJSONString(values)).use { reader ->
            require(reader.isObject) { "Project configuration must be an object" }
            // 未知字段处理
            reader.context.extraProcessor = diagnostics
            // 解析JSON对象
            reader.readObject(input)
            require(reader.isEnd) { "Unexpected content after project configuration" }
        }

        if (input.macroExpansion == null) diagnostics.errors += "macroExpansion: expected an object"

        // 将解析后的配置转换为ProjectConfig对象
        config = input.toProjectConfig(file)
    } catch (failure: Exception) {
        readFailure = failure
    }

    // 编译器参数解析。编译器的参数会覆盖配置文件中的相同参数。
    val fileFlags = CompilerFlags()
    val fileArgs = compilerCommandLine(fileFlags).parseArgs(*input.args)
    fileFlags.applyCompilerSettings(options)    // 覆写
    LogProcessor.debug("Reading project from file \"$file\"")

    if (options.hasParseResult) reportArgumentDiagnostics(options.parseResult)
    reportArgumentDiagnostics(fileArgs)
    diagnostics.errors.forEach(::reportInputError)
    diagnostics.unknownKeys.forEach { LogProcessor.warn("Unsupported config item: $it") }
    readFailure?.let { reportInputError("Error while reading project from file \"$file\": ${it.message}") }

    // Preserve the established ordering of compiler switches and macro overrides.
    config.macroExpansion = options.applyMacroOverrides(fileFlags.applyMacroOverrides(config.macroExpansion))
    try {
        config.optimization = config.optimization.copy(
            level = options.optimizationLevel ?: fileFlags.optimizationLevel ?: config.optimization.level,
            disabledPasses = options.disabledPasses ?: fileFlags.disabledPasses ?: config.optimization.disabledPasses)
    } catch (failure: IllegalArgumentException) { reportInputError(failure.message ?: "Invalid optimization configuration") }
    config.irOutputPath = (options.dumpIR ?: fileFlags.dumpIR)?.let { directory ->
        val path = Path.of(directory)
        (if (path.isAbsolute) path else config.targetPath!!.resolve(path)).normalize()
    }
    config.debugInfo = options.debugInfo ?: fileFlags.debugInfo ?: false
    if (config.debugInfo) {
        config.optimization = config.optimization.copy(level = 0)
        config.targetPath = config.targetPath!!.resolve("debug")
    }
    return config
}

internal class OptimizationConfigReader : ObjectReader<top.mcfpp.optimization.OptimizationConfig> {
    override fun readObject(reader: JSONReader, type: Type?, name: Any?, features: Long): top.mcfpp.optimization.OptimizationConfig {
        val defaults = top.mcfpp.optimization.OptimizationConfig()
        return try {
            val input = reader.readAny() as? Map<*, *> ?: error("optimization: expected a table")
            require(input.keys.all { it in setOf("level", "disabledPasses", "costs") }) { "optimization: unknown configuration item" }
            val level = if ("level" in input) input["level"] as? Int ?: error("optimization.level: expected an integer") else 2
            val disabled = if ("disabledPasses" in input) {
                val items = input["disabledPasses"] as? List<*> ?: error("optimization.disabledPasses: expected an array")
                require(items.all { it is String }) { "optimization.disabledPasses: expected strings" }
                items.filterIsInstance<String>().toSet()
            } else emptySet()
            val costs = if ("costs" in input) input["costs"] as? Map<*, *> ?: error("optimization.costs: expected a table") else emptyMap<Any, Any>()
            val keys = setOf("command", "storage", "entityNbt", "playerNbt", "macroFunctionCommand", "macroBase", "macroCharacter", "macroArgument")
            require(costs.keys.all { it in keys }) { "optimization.costs: unknown weight" }
            fun weight(key: String, fallback: Double) = if (key in costs)
                (costs[key] as? Number)?.toDouble() ?: error("optimization.costs.$key: expected a number") else fallback
            val weights = top.mcfpp.optimization.CommandCostModel(
                weight("command", 1.0), weight("storage", 10.0), weight("entityNbt", 60.0), weight("playerNbt", 150.0),
                weight("macroFunctionCommand", 1.0), weight("macroBase", 1.0), weight("macroCharacter", 0.01), weight("macroArgument", 1.0))
            top.mcfpp.optimization.OptimizationConfig(level, disabled, weights)
        } catch (failure: Exception) {
            reader.configError(failure.message ?: "Invalid optimization configuration")
            defaults
        }
    }
}

internal class ConfigPathReader : ObjectReader<Path> {
    override fun readObject(reader: JSONReader, type: Type?, name: Any?, features: Long): Path {
        val value = reader.readAny()
        require(value is String) { "$name: expected a path string" }
        return Path.of(value)
    }
}

/** Avoid coercing numbers or booleans into package coordinates and versions. */
internal class LibraryPublicationConfigReader : ObjectReader<LibraryPublicationConfig> {
    override fun readObject(reader: JSONReader, type: Type?, name: Any?, features: Long): LibraryPublicationConfig? {
        val values = reader.readAny()
        if (values !is Map<*, *>) {
            reader.configError("library: expected an object")
            return null
        }
        values.keys.filter { it !in setOf("coordinate", "version", "classifier") }.forEach {
            reader.configError("library.$it: unsupported publication item")
        }
        fun string(key: String, default: String? = null): String? {
            if (key !in values && default != null) return default
            return values[key] as? String ?: run {
                reader.configError("library.$key: expected a string")
                null
            }
        }
        val coordinate = string("coordinate")
        val version = string("version")
        val classifier = string("classifier", "mcfpp")
        if (coordinate == null || version == null || classifier == null) return null
        return try {
            LibraryPublicationConfig(coordinate, version, classifier)
        } catch (failure: IllegalArgumentException) {
            reader.configError(requireNotNull(failure.message))
            null
        }
    }
}

internal class ConfigCommentLevelReader : ObjectReader<CommentLevel> {
    override fun readObject(reader: JSONReader, type: Type?, name: Any?, features: Long): CommentLevel {
        val value = reader.readString()
        return CommentLevel.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: run {
            reader.configError("Unsupported comment level: $value, using default value \"DEBUG\"")
            CommentLevel.DEBUG
        }
    }
}

/** Validate macro limits strictly instead of letting the JSON library coerce strings or decimals. */
internal class MacroExpansionConfigReader : ObjectReader<MacroExpansionConfig> {
    override fun readObject(reader: JSONReader, type: Type?, name: Any?, features: Long): MacroExpansionConfig {
        val values = reader.readAny()
        if (values !is Map<*, *>) {
            reader.configError("macroExpansion: expected an object")
            return MacroExpansionConfig()
        }
        fun positiveInt(key: String, default: Int): Int {
            if (key !in values) return default
            val value = values[key]
            if (value is Int && value > 0) return value
            reader.configError("macroExpansion.$key: expected a positive Int")
            return default
        }
        val dump = if ("dump" !in values) false else values["dump"] as? Boolean ?: run {
            reader.configError("macroExpansion.dump: expected a boolean")
            false
        }
        return MacroExpansionConfig(
            positiveInt("maxDepth", MacroExpansionConfig.DEFAULT_MAX_DEPTH),
            positiveInt("maxNodes", MacroExpansionConfig.DEFAULT_MAX_NODES),
            dump,
        )
    }
}
