package top.mcfpp.library.compiler

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.command.TargetCapabilities
import top.mcfpp.compile
import top.mcfpp.io.LibBinWriter
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Rebuild source declarations and their owned resources together for each supported target. */
object BuiltinLibraryGenerator {
    @JvmStatic
    fun main(args: Array<String>) {
        val root = Path.of(args[0]).toAbsolutePath().normalize()
        val bundle = root.resolve("src/main/resources/datapack")
        val module = Files.readAllBytes(bundle.resolve("module.json"))
        val targets = args.drop(1).ifEmpty { TargetCapabilities.supportedVersions.toList() }
        require(targets.all { it in TargetCapabilities.supportedVersions })
        LogProcessor.level = LogLevel.INFO
        for (target in targets) {
            CompileSettings.ignoreStdLib = true
            CompileSettings.isLib = true
            val output = bundle.resolve("targets/$target")
            val work = Files.createDirectories(root.resolve("build/stdlib-rebuild"))
            val staging = work.resolve(target)
            compile(ProjectConfig(version = target, rootNamespace = "mcfpp", root = root, name = "stdlib",
                sourcePath = root.resolve("src/main/mcfpp"), dataPath = bundle.resolve("stdlib/data"),
                targetPath = staging, copyImport = false, noDatapack = true, noMcLib = true))
            check(Project.errorCount == 0) { "Standard library compilation failed for $target" }
            Project.compileStage = Project.CompileStage.GEN_INDEX
            Project.libraryImportSession?.prepareInitializers()
            LibBinWriter.withStagedExport(staging.toString()) { complete ->
                // Preserve package switches. Copy from the export staging area directly:
                // Windows scanners can hold directory handles even for fresh temp folders.
                Files.write(complete.resolve("module.json"), module)
                copyChanged(complete, output)
            }
            Project.stageProcessor[Project.CompileStage.GEN_INDEX.ordinal].forEach { it() }
            if (target == "1.21.8") {
                Files.copy(output.resolve("bin.mclib"), bundle.resolve("bin.mclib"), REPLACE_EXISTING)
                copyChanged(output.resolve("datapack/stdlib/data"), bundle.resolve("datapack/stdlib/data"))
            }
            println("Rebuilt stdlib for Minecraft $target")
        }
    }

    private fun copyChanged(source: Path, destination: Path) {
        Files.walk(source).use { paths -> paths.filter(Files::isRegularFile).forEach { file ->
            val target = destination.resolve(source.relativize(file))
            Files.createDirectories(target.parent)
            if (!Files.isRegularFile(target) || Files.mismatch(file, target) != -1L)
                Files.copy(file, target, REPLACE_EXISTING)
        } }
    }
}
