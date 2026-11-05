package top.mcfpp

import picocli.CommandLine
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import java.io.File

// AI 很不喜欢给代码写详细的注释，即使我要求了
/** Nullable options distinguish an explicit override from an omitted option. */
open class CompilerFlags {
    @CommandLine.Option(names = ["--debug-info"], description = ["Build an unoptimized datapack with source debugger metadata under debug/."])
    var debugInfo: Boolean? = null
    @CommandLine.Option(names = ["-O", "--optimization-level"], description = ["Optimization level: 0, 1 or 2 (default: 2)."])
    var optimizationLevel: Int? = null

    @CommandLine.Option(names = ["--disable-pass"], split = ",", description = ["Disable optional optimization passes by readable ID."])
    var disabledPasses: Set<String>? = null

    /** Enable compiler debugging. */
    @CommandLine.Option(names = ["-debug"], description = ["Enable compiler debugging."])
    var debug: Boolean? = null

    /** Skip the standard library. */
    @CommandLine.Option(names = ["-ignoreStdLib"], description = ["Skip the standard library."])
    var ignoreStdLib: Boolean? = null

    /** Compile a library without an entry point. */
    @CommandLine.Option(names = ["-isLib"], description = ["Compile a library without an entry point."])
    var isLib: Boolean? = null

    /** Maximum loop inline count (default: 32). */
    @CommandLine.Option(names = ["-maxWhileInline"], description = ["Maximum loop inline count (default: 32)."])
    var maxWhileInline: Int? = null

    /** Print all compilation results. */
    @CommandLine.Option(names = ["-printAll"], description = ["Print all compilation results."])
    var printAll: Boolean? = null

    @CommandLine.Option(names = ["--dump-ir", "-dumpIR"], arity = "0..1", fallbackValue = "ir",
        description = ["Write readable typed IR, lowered commands and linking decisions (default: build/ir). Relative directories use the build output root."])
    var dumpIR: String? = null

    /** Log Level (default: DEBUG). */
    @CommandLine.Option(names = ["-level"], description = ["Log level: \${COMPLETION-CANDIDATES} (default: DEBUG)."])
    var level: LogLevel? = null

    /** Maximum macro expansion depth (default: 64). */
    @CommandLine.Option(names = ["-macroMaxDepth"], converter = [PositiveIntConverter::class], description = ["Maximum macro expansion depth (default: 64)."])
    var macroMaxDepth: Int? = null

    /** Maximum generated macro nodes (default: 100000). */
    @CommandLine.Option(names = ["-macroMaxNodes"], converter = [PositiveIntConverter::class], description = ["Maximum generated macro nodes (default: 100000)."])
    var macroMaxNodes: Int? = null

    /** Print expanded macro trees. */
    @CommandLine.Option(names = ["-dumpMacroExpansion"], arity = "0..1", fallbackValue = "true", converter = [StrictBooleanConverter::class], description = ["Print expanded macro trees."])
    var dumpMacroExpansion: Boolean? = null

    // 覆写宏配置
    internal fun applyMacroOverrides(config: MacroExpansionConfig) = MacroExpansionConfig(
        macroMaxDepth ?: config.maxDepth,
        macroMaxNodes ?: config.maxNodes,
        dumpMacroExpansion ?: config.dump,
    )

    // 覆写编译器设置
    internal fun applyCompilerSettings(cli: CompilerFlags) {
        (cli.debug ?: debug)?.let { CompileSettings.isDebug = it }
        (cli.ignoreStdLib ?: ignoreStdLib)?.let { CompileSettings.ignoreStdLib = it }
        (cli.isLib ?: isLib)?.let { CompileSettings.isLib = it }
        (cli.maxWhileInline ?: maxWhileInline)?.let { CompileSettings.maxWhileInline = it }
        (cli.printAll ?: printAll)?.let { CompileSettings.printAll = it }
        (cli.level ?: level)?.let { LogProcessor.level = it }
    }
}

/**
 * Command-line options for the MCFPP compiler.
 */
@CommandLine.Command(
    name = "mcfpp",
    description = ["Compile an MCFPP project."],
    mixinStandardHelpOptions = true,
    version = ["MCFPP ${MCFPP.VERSION}"],
)
class CompilerOptions : CompilerFlags() {
    // Low-level compiler invocation; the public CLI discovers this through --project.
    @CommandLine.Parameters(index = "0", arity = "1", paramLabel = "FILE", description = ["Project TOML file."])
    lateinit var file: File

    internal val hasFile: Boolean get() = ::file.isInitialized

    internal lateinit var commandLine: CommandLine

    internal lateinit var parseResult: CommandLine.ParseResult

    internal val hasParseResult: Boolean get() = ::parseResult.isInitialized
}

internal fun compilerCommandLine(options: CompilerFlags): CommandLine = CommandLine(options)
    .setCaseInsensitiveEnumValuesAllowed(true)
    .setOverwrittenOptionsAllowed(true)
    .setUnmatchedArgumentsAllowed(true)
    .also { it.commandSpec.parser().collectErrors(true) }

/** Parse one invocation without applying options to the previous compilation. */
internal fun parseCompilerArgs(args: Array<String>): CompilerOptions = CompilerOptions().also {
    it.commandLine = compilerCommandLine(it)
    it.parseResult = it.commandLine.parseArgs(*args)
}

internal fun reportArgumentDiagnostics(result: CommandLine.ParseResult) {
    result.errors().forEach { reportInputError(it.message ?: "Invalid compiler argument") }
    result.unmatched().forEach { LogProcessor.warn("Invalid argument: $it") }
}

/** Input validity must not depend on whether the selected log level displays errors. */
internal fun reportInputError(message: String) {
    LogProcessor.recordProjectError()
    LogProcessor.emitDiagnostic(LogLevel.ERROR, message)
}

internal class PositiveIntConverter : CommandLine.ITypeConverter<Int> {
    override fun convert(value: String): Int = value.toIntOrNull()?.takeIf { it > 0 }
        ?: throw CommandLine.TypeConversionException("expected a positive Int")
}

internal class StrictBooleanConverter : CommandLine.ITypeConverter<Boolean> {
    override fun convert(value: String): Boolean = value.toBooleanStrictOrNull()
        ?: throw CommandLine.TypeConversionException("expected true or false")
}
