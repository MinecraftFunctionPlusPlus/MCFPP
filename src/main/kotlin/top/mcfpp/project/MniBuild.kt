package top.mcfpp.project

import com.alibaba.fastjson2.JSON
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.io.PrintWriter
import java.util.zip.ZipInputStream

/** Gradle owns JVM resolution and compilation; the CLI owns the MCFPP compilation. */
class MniBuild(private val log: PrintWriter) {
    fun execute(project: ProjectDefinition, lock: Path, mode: String, offline: Boolean, locked: Boolean, update: Boolean = false): List<Path> {
        val jdk = jdkHome()
        val home = gradleHome(offline, log)
        val generated = project.generated.resolve("gradle")
        Files.createDirectories(generated)
        val scripts = (project.mni["extensions"] as? List<*>)?.map {
            require(it is String) { "mni.extensions must contain paths" }
            project.root.resolve(it).normalize().also { path -> require(Files.isRegularFile(path)) { "Missing Gradle extension: $path" } }
        }.orEmpty()
        val fingerprint = sha(("java21-v1:1\n" + JSON.toJSONString(project.packages.mni.map { it.json() }) +
            JSON.toJSONString(project.mni) + scripts.joinToString { Files.readString(it) }).toByteArray())
        val marker = "# mcfpp-input-sha256=$fingerprint"
        val valid = Files.isRegularFile(lock) && Files.readAllLines(lock).contains(marker)
        require(!locked || valid) { "mcfpp-mni.lock is missing or out of date; run mcfpp install" }
        val writeLock = !locked && (!valid || update)
        if (writeLock) Files.deleteIfExists(lock)
        val result = generated.resolve("classpath.json")
        val request = linkedMapOf<String, Any>("root" to project.root.toString(), "build" to project.output.resolve(".mcfpp/mni").toString(),
            "lock" to lock.toString(), "classpath" to compilerClasspath().map { it.toString() },
            "dependencies" to project.packages.mni.map { mapOf("id" to it.id, "configuration" to it.configuration) },
            "repositories" to (project.mni["repositories"] ?: listOf("https://repo.maven.apache.org/maven2/")),
            "extensions" to scripts.map { it.toString() }, "result" to result.toString(),
            "ideModel" to generated.resolve("ide-model.json").toString())
        val plugins = table(project.mni["plugins"], "mni.plugins")
        require(plugins.values.all { it is String && it.isNotBlank() }) { "mni.plugins must map plugin IDs to versions" }
        val pluginText = plugins.entries.joinToString("\n") { "    id(${quote(it.key)}) version ${quote(it.value as String)}" }
        atomicWrite(generated.resolve("settings.gradle.kts"), "rootProject.name = \"mcfpp-mni\"\n".toByteArray())
        atomicWrite(generated.resolve("request.json"), JSON.toJSONBytes(request))
        atomicWrite(generated.resolve("build.gradle.kts"), script.replace("// EXTRA_PLUGINS", pluginText).toByteArray())
        Files.deleteIfExists(result)
        val command = mutableListOf(jdk.resolve("bin/${executable("java")}").toString(), "-classpath", home.resolve("lib").toString() + java.io.File.separator + "*",
            "org.gradle.launcher.GradleMain", "--project-dir", generated.toString(), "--console=plain",
            "-Dorg.gradle.java.installations.auto-download=false", "-Dorg.gradle.java.installations.paths=$jdk")
        if (offline) command += "--offline"
        if (update && !offline) command += "--refresh-dependencies"
        if (writeLock) command += "--write-locks"
        command += when (mode) { "build" -> "mcfppCompileMni"; "test" -> "mcfppTestMni"; else -> "mcfppResolveMni" }
        log.println("Preparing Java MNI ($mode)...")
        log.flush()
        val process = ProcessBuilder(command).directory(project.root.toFile()).redirectErrorStream(true).apply {
            environment()["JAVA_HOME"] = jdk.toString()
        }.start()
        process.inputStream.bufferedReader().useLines { lines -> lines.forEach { log.println(it); log.flush() } }
        check(process.waitFor() == 0) { "Java MNI $mode failed; see Gradle diagnostics above" }
        if (writeLock) {
            val native = if (Files.exists(lock)) Files.readAllLines(lock).filterNot { it.startsWith("# mcfpp-input-sha256=") } else emptyList()
            atomicWrite(lock, (native.joinToString("\n") + "\n$marker\n").toByteArray())
        }
        require(Files.isRegularFile(result)) { "Gradle did not export its MNI classpath" }
        return JSON.parseArray(Files.readString(result)).map { Path.of(it as String) }
    }

    companion object {
        const val GRADLE = "8.14"
        private const val GRADLE_SHA = "61ad310d3c7d3e5da131b76bbf22b5a4c0786e9d892dae8c1658d4b484de3caa"
        private fun executable(name: String) = if (System.getProperty("os.name").startsWith("Windows")) "$name.exe" else name

        private fun jdkHome(): Path {
            val candidates = listOfNotNull(System.getenv("MCFPP_JAVA_HOME"), System.getenv("JDK21_HOME"), System.getenv("JAVA_HOME"), System.getProperty("java.home"))
            return candidates.map(Path::of).firstOrNull { home ->
                Files.isRegularFile(home.resolve("bin/${executable("javac")}")) &&
                    Files.isRegularFile(home.resolve("release")) && Regex("JAVA_VERSION=\"21[.\"]").containsMatchIn(Files.readString(home.resolve("release")))
            } ?: error("Java MNI requires JDK 21. Set MCFPP_JAVA_HOME or JAVA_HOME to a JDK 21 installation.")
        }

        private fun gradleHome(offline: Boolean, log: PrintWriter): Path {
            val user = Path.of(System.getenv("GRADLE_USER_HOME") ?: Path.of(System.getProperty("user.home"), ".gradle").toString())
            val wrapper = user.resolve("wrapper/dists/gradle-$GRADLE-bin")
            if (Files.isDirectory(wrapper)) Files.walk(wrapper, 3).use { paths ->
                paths.filter { it.fileName.toString() == "gradle-$GRADLE" && Files.isRegularFile(it.resolve("lib/gradle-launcher-$GRADLE.jar")) }
                    .findFirst().orElse(null)?.let { return it }
            }
            val cache = user.resolve("mcfpp-toolchains")
            val home = cache.resolve("gradle-$GRADLE")
            if (Files.isRegularFile(home.resolve("lib/gradle-launcher-$GRADLE.jar"))) return home
            check(!offline) { "Gradle $GRADLE is not cached; run mcfpp install online" }
            log.println("Downloading Gradle $GRADLE...")
            log.flush()
            Files.createDirectories(cache)
            val zip = Files.createTempFile(cache, "gradle-", ".zip")
            val unpack = Files.createTempDirectory(cache, "gradle-unpack-")
            try {
                val response = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build().send(
                    HttpRequest.newBuilder(URI.create("https://services.gradle.org/distributions/gradle-$GRADLE-bin.zip")).build(),
                    HttpResponse.BodyHandlers.ofFile(zip))
                check(response.statusCode() == 200) { "Unable to download Gradle: HTTP ${response.statusCode()}" }
                check(sha(Files.readAllBytes(zip)) == GRADLE_SHA) { "Gradle distribution checksum mismatch" }
                ZipInputStream(Files.newInputStream(zip)).use { input ->
                    while (true) {
                        val entry = input.nextEntry ?: break
                        val target = unpack.resolve(entry.name).normalize()
                        require(target.startsWith(unpack)) { "Invalid Gradle distribution path" }
                        if (entry.isDirectory) Files.createDirectories(target) else {
                            Files.createDirectories(target.parent)
                            Files.newOutputStream(target).use { input.copyTo(it) }
                        }
                    }
                }
                Files.move(unpack.resolve("gradle-$GRADLE"), home)
            } finally { Files.deleteIfExists(zip); deleteTree(unpack, cache) }
            return home
        }

        internal fun compilerClasspath(): List<Path> {
            val override = System.getProperty("mcfpp.compiler.classpath")
            return (override ?: System.getProperty("java.class.path")).split(java.io.File.pathSeparator)
                .filter { it.isNotBlank() }.map { Path.of(it).toAbsolutePath().normalize() }.distinct()
        }

        internal fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        private val script = """
            import groovy.json.JsonSlurper
            import groovy.json.JsonOutput
            import org.gradle.api.artifacts.dsl.LockMode

            plugins {
                java
                // EXTRA_PLUGINS
            }
            val request = JsonSlurper().parse(file("request.json")) as Map<*, *>
            val sourceRoot = file(request["root"] as String)
            layout.buildDirectory.set(file(request["build"] as String))
            java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
            sourceSets {
                main { java.setSrcDirs(listOf(sourceRoot.resolve("src/main/java"))); resources.setSrcDirs(listOf(sourceRoot.resolve("src/main/resources"))) }
                test { java.setSrcDirs(listOf(sourceRoot.resolve("src/test/java"))); resources.setSrcDirs(listOf(sourceRoot.resolve("src/test/resources"))) }
            }
            repositories {
                (request["repositories"] as List<*>).forEach { url -> maven { setUrl(url as String) } }
            }
            dependencyLocking {
                lockAllConfigurations()
                lockMode.set(LockMode.STRICT)
                lockFile.set(file(request["lock"] as String))
            }
            dependencies {
                add("compileOnly", files(request["classpath"] as List<*>))
                add("testImplementation", files(request["classpath"] as List<*>))
            }
            (request["extensions"] as List<*>).forEach { apply(from = it as String) }
            (request["dependencies"] as List<*>).forEach {
                val dep = it as Map<*, *>
                dependencies.add(dep["configuration"] as String, dep["id"] as String)
            }
            tasks.withType<Test>().configureEach { useJUnitPlatform() }
            val resolveMni = tasks.register("mcfppResolveMni") {
                doLast {
                    configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
                    val paths = sourceSets.main.get().runtimeClasspath.files.map { it.absolutePath }
                    file(request["result"] as String).writeText(JsonOutput.toJson(paths))
                    val model = mapOf(
                        "sourceSets" to sourceSets.map { source -> mapOf(
                            "name" to source.name,
                            "sources" to source.allJava.srcDirs.map { it.absolutePath },
                            "resources" to source.resources.srcDirs.map { it.absolutePath },
                            "compileClasspath" to source.compileClasspath.files.map { it.absolutePath },
                            "runtimeClasspath" to source.runtimeClasspath.files.map { it.absolutePath },
                            "outputs" to source.output.files.map { it.absolutePath }
                        ) }
                    )
                    file(request["ideModel"] as String).writeText(JsonOutput.toJson(model))
                }
            }
            tasks.register("mcfppCompileMni") { dependsOn(tasks.named("classes"), resolveMni) }
            tasks.register("mcfppTestMni") { dependsOn(tasks.named("test"), resolveMni) }
        """.trimIndent() + "\n"
    }
}
