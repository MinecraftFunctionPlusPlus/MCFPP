package top.mcfpp.library.mcfpm

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.nio.file.Path
import java.util.Collections

/** The request is the JDK-only LibraryCompilerApi v1 map, ready for openSession. */
data class PreparedMcfpmProject(
    val request: Map<String, Any>,
    val diagnostics: List<Map<String, Any>>,
)

class McfpmBridgeException(
    val diagnostics: List<Map<String, Any>>,
    cause: Throwable? = null,
) : IllegalStateException(diagnostics.joinToString("\n") { diagnostic ->
    "${diagnostic["code"]}: ${diagnostic["message"]}" +
        (diagnostic["context"] as? Map<*, *>)?.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()
}, cause)

/** Consumes the SDK's installed lock/cache facade without linking its Kotlin implementation ABI. */
class McfpmInstalledProjectBridge(
    private val sdkClassLoader: ClassLoader = McfpmInstalledProjectBridge::class.java.classLoader,
) {
    fun prepare(
        configuration: Path,
        compilerApiParent: ClassLoader,
        cacheDirectory: Path? = null,
        trustStore: Path? = null,
        manifest: Path? = null,
        lockfile: Path? = null,
    ): PreparedMcfpmProject {
        val input = linkedMapOf<String, Any>(
            "configuration" to configuration.toAbsolutePath().normalize(),
            "compilerApiParent" to compilerApiParent,
        )
        cacheDirectory?.let { input["cacheDirectory"] = it }
        trustStore?.let { input["trustStore"] = it }
        manifest?.let { input["manifest"] = it }
        lockfile?.let { input["lockfile"] = it }
        try {
            val facade = Class.forName(FACADE, true, sdkClassLoader)
            val method = facade.getMethod("prepare", Map::class.java)
            require(Modifier.isPublic(facade.modifiers) && Modifier.isPublic(method.modifiers) &&
                Modifier.isStatic(method.modifiers) && method.returnType == Map::class.java) {
                "$FACADE.prepare must be public static Map prepare(Map)"
            }
            val result = snapshotMap(method.invoke(null, Collections.unmodifiableMap(input)), "result")
            val diagnostics = maps(result["diagnostics"], "diagnostics")
            diagnostics.forEach { diagnostic ->
                require(diagnostic["code"] is String && diagnostic["message"] is String &&
                    diagnostic["severity"] in listOf("error", "warning", "info")) { "Invalid SDK diagnostic" }
            }
            when (result["success"]) {
                false -> throw McfpmBridgeException(diagnostics.ifEmpty {
                    listOf(integrationDiagnostic("Installed project preparation failed; run mcfpm install"))
                })
                true -> Unit
                else -> error("SDK result.success must be a Boolean")
            }
            require(diagnostics.none { it["severity"] == "error" }) { "SDK returned success with error diagnostics" }
            val request = readMap(result["request"], "request")
            require(request["apiVersion"] == 1) { "Unsupported installed-project request ABI: ${request["apiVersion"]}" }
            require(request["configuration"] == input["configuration"]) { "SDK changed the consumer configuration" }
            require(request["hostClassLoader"] === compilerApiParent) { "SDK did not preserve the compiler API class loader" }
            listOf("libraries", "lockedPackages", "plugins", "resourcePacks", "datapacks").forEach {
                maps(request[it], "request.$it")
            }
            return PreparedMcfpmProject(request, diagnostics)
        } catch (exception: McfpmBridgeException) {
            throw exception
        } catch (exception: InvocationTargetException) {
            throw integrationFailure(exception.targetException)
        } catch (exception: ReflectiveOperationException) {
            throw integrationFailure(exception)
        } catch (exception: LinkageError) {
            throw integrationFailure(exception)
        } catch (exception: IllegalArgumentException) {
            throw integrationFailure(exception)
        } catch (exception: IllegalStateException) {
            throw integrationFailure(exception)
        }
    }

    private fun integrationFailure(cause: Throwable) = McfpmBridgeException(listOf(integrationDiagnostic(
        "Unable to prepare installed dependencies through $FACADE: ${cause.message ?: cause.javaClass.name}. " +
            "Use an MCFPP distribution with a compatible Mcfpm SDK; run mcfpm install if the lock or cache is missing.",
    )), cause)

    private fun integrationDiagnostic(message: String): Map<String, Any> = Collections.unmodifiableMap(mapOf(
        "code" to "MCFPM-MCFPP-001", "severity" to "error", "message" to message,
    ))

    private fun maps(value: Any?, location: String): List<Map<String, Any>> {
        require(value is List<*>) { "$location must be a List" }
        return Collections.unmodifiableList(value.mapIndexed { index, entry -> readMap(entry, "$location[$index]") })
    }

    @Suppress("UNCHECKED_CAST")
    private fun readMap(value: Any?, location: String): Map<String, Any> {
        // All nested values and string keys were checked while copying the facade result.
        require(value is Map<*, *>) { "$location must be a Map" }
        return value as Map<String, Any>
    }

    private fun snapshotMap(value: Any?, location: String): Map<String, Any> {
        require(value is Map<*, *>) { "$location must be a Map" }
        val result = linkedMapOf<String, Any>()
        value.forEach { (key, entry) ->
            require(key is String) { "$location contains a non-string key" }
            result[key] = snapshot(entry, "$location.$key")
        }
        return Collections.unmodifiableMap(result)
    }

    private fun snapshot(value: Any?, location: String): Any = when (value) {
        is String, is Int, is Long, is Boolean, is Path, is ClassLoader -> value
        is ByteArray -> value.copyOf()
        is Map<*, *> -> snapshotMap(value, location)
        is List<*> -> Collections.unmodifiableList(value.mapIndexed { index, entry -> snapshot(entry, "$location[$index]") })
        else -> throw IllegalArgumentException("$location contains a non-JDK ABI value: ${value?.javaClass?.name ?: "null"}")
    }

    private companion object {
        const val FACADE = "moe.afox.mcfpm.mcfpp.McfppInstalledProjectApi"
    }
}
