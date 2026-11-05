package top.mcfpp.debug

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/** Owns one DSB process. Response dispatch never blocks the protocol reader. */
internal class SandboxDebugClient(java: String, jar: Path, directory: Path,
    private val event: (String, JSONObject) -> Unit, private val output: (String) -> Unit) : AutoCloseable {
    private val process = ProcessBuilder(java, "-Dfile.encoding=UTF-8", "-jar", jar.toString(), "serve", "--protocol", "jsonl")
        .directory(directory.toFile()).start()
    private val writer = process.outputStream.bufferedWriter(Charsets.UTF_8)
    private val pending = ConcurrentHashMap<String, CompletableFuture<JSONObject>>()
    private val ids = AtomicLong()
    private val hello = CompletableFuture<JSONObject>()
    @Volatile private var closed = false

    init {
        thread(name = "mcfpp-dsb-stderr", isDaemon = true) {
            process.errorStream.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.forEach { output(it + "\n") } }
        }
        thread(name = "mcfpp-dsb-jsonl", isDaemon = true) {
            try {
                process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.forEach { line ->
                    val response = JSON.parseObject(line)
                    val name = response.getString("event")
                    if (name != null) event(name, response.getJSONObject("body") ?: JSONObject())
                    else if (response["id"] == null) hello.complete(response.getJSONObject("result"))
                    else pending.remove(response.getString("id"))?.let { future ->
                        if (response.getBooleanValue("ok")) future.complete(response.getJSONObject("result") ?: JSONObject())
                        else future.completeExceptionally(IllegalStateException(response.getJSONObject("error").toJSONString()))
                    }
                } }
                if (!closed) throw IllegalStateException("Datapack Sandbox closed its protocol stream")
            } catch (error: Exception) {
                hello.completeExceptionally(error)
                pending.values.forEach { it.completeExceptionally(error) }
                pending.clear()
                if (!closed) event("session.lost", JSONObject.of("message", error.message))
            }
        }
    }

    fun awaitReady(version: String) {
        val greeting = hello.get(30, TimeUnit.SECONDS)
        require(greeting.getString("protocol") == "dps-jsonl") { "Unsupported DSB protocol" }
        val capabilities = greeting.getJSONObject("capabilities")
        require(capabilities?.getBooleanValue("liveDebug") == true && capabilities.getIntValue("debugProtocolVersion") == 1) {
            "This Datapack Sandbox JAR does not support live debugging protocol 1"
        }
        require(greeting.getJSONArray("versions").contains(version)) { "Datapack Sandbox does not support Minecraft $version" }
    }

    fun request(method: String, params: Map<String, Any?> = emptyMap()): JSONObject {
        check(!closed) { "Datapack Sandbox session is closed" }
        val id = ids.incrementAndGet().toString()
        val result = CompletableFuture<JSONObject>()
        pending[id] = result
        try {
            synchronized(writer) {
                writer.write(JSON.toJSONString(mapOf("id" to id, "method" to method, "params" to params)))
                writer.newLine(); writer.flush()
            }
            return result.get(30, TimeUnit.SECONDS)
        } finally { pending.remove(id) }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { writer.close() }
        if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
        pending.values.forEach { it.completeExceptionally(IllegalStateException("Debug session closed")) }
        pending.clear()
    }
}
