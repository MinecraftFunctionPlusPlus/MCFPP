package top.mcfpp.library.mcfpm

import top.mcfpp.CompilerOptions
import top.mcfpp.Project
import top.mcfpp.api.library.LibraryCompilerApi
import top.mcfpp.library.api.LibraryCompilerSession
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path

/** Runs the CLI through the compiler session that owns imported resources and plugin loaders. */
internal object McfpmCliCompiler {
    fun compile(options: CompilerOptions, request: Map<String, Any>, err: PrintWriter): Int {
        return try {
            LibraryCompilerSession.openWithOptions(request, options).use { session ->
                // The session restores global settings before returning its result. Remember the
                // merged file/CLI level while it is active, including a file-only NONE setting.
                var diagnosticLevel = options.level ?: LogProcessor.level
                val rememberLevel: () -> Unit = { diagnosticLevel = LogProcessor.level }
                val startupHooks = Project.stageProcessor[Project.CompileStage.PRE_INIT.ordinal]
                startupHooks.add(0, rememberLevel)
                val result = try {
                    LibraryCompilerSession.compileSession(session)
                } finally {
                    startupHooks.remove(rememberLevel)
                }
                if (diagnosticLevel <= LogLevel.ERROR) {
                    // Captured compiler logs have already been published. Session exceptions are
                    // returned only in the DTO, so the CLI must render those separately.
                    (result["diagnostics"] as? List<*>)?.filterIsInstance<Map<*, *>>()
                        ?.filter { it["severity"] == "error" && it["code"] != "MCFPP_COMPILER" }
                        ?.forEach { err.println("${it["code"]}: ${it["message"]}") }
                }
                if (result["success"] == true) 0 else 1
            }
        } catch (failure: McfpmBridgeException) {
            err.println(failure.message)
            1
        } catch (failure: Exception) {
            err.println("MCFPP_COMPILATION_FAILED: ${failure.message ?: failure.javaClass.name}")
            1
        } catch (failure: LinkageError) {
            err.println("MCFPP_COMPILATION_FAILED: ${failure.message ?: failure.javaClass.name}")
            1
        }
    }

}
