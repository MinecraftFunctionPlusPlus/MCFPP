package top.mcfpp.test.util

import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import top.mcfpp.Project
import top.mcfpp.io.DatapackCreator
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** Always detach the recorder, including when compilation or a test assertion fails. */
internal fun compilerDiagnostics(action: () -> Unit): List<String> {
    val logger = assertIs<Logger>(LogProcessor.logger)
    val messages = arrayListOf<String>()
    val appender = object : AbstractAppender("inheritance-${UUID.randomUUID()}", null, null, false, Property.EMPTY_ARRAY) {
        override fun append(event: LogEvent) {
            messages.add(event.message.formattedMessage)
        }
    }
    appender.start()
    logger.addAppender(appender)
    try {
        action()
    } finally {
        logger.removeAppender(appender)
        appender.stop()
    }
    return messages
}

/** Read exported functions, including imported library bodies used by fresh-load tests. */
internal fun exportDatapackFunctions(output: Path): Map<String, List<String>> {
    DatapackCreator.createDatapack(output.toString())
    val functions = linkedMapOf<String, List<String>>()
    val dataDirectories = listOf(output.resolve("Imports/data"), output.resolve(Project.config.name).resolve("data"))
    for (data in dataDirectories) {
        if (!Files.exists(data)) continue
        Files.walk(data).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".mcfunction") }.forEach { file ->
                val relative = data.relativize(file)
                if (relative.nameCount >= 3 && relative.getName(1).toString() == "function") {
                    val name = relative.subpath(2, relative.nameCount).joinToString("/").removeSuffix(".mcfunction")
                    functions["${relative.getName(0)}:$name"] = Files.readAllLines(file).map(String::trim)
                }
            }
        }
    }
    return functions
}

internal fun executePublishedFunctions(main: Function): ScoreCommandExecutor {
    val output = Files.createTempDirectory("mcfpp-inheritance-execution-")
    try {
        val functions = exportDatapackFunctions(output)
        return ScoreCommandExecutor(assertNotNull(functions[main.namespaceID.toString()]), functions)
    } finally {
        output.toFile().deleteRecursively()
    }
}
