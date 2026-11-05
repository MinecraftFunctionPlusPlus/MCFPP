package top.mcfpp.io

import top.mcfpp.library.compiler.BuiltinImportPlan
import top.mcfpp.library.compiler.LibraryIdentity
import top.mcfpp.library.format.LibraryModelCodec
import top.mcfpp.library.format.ManifestCodec
import top.mcfpp.library.model.LibraryCompatibility
import top.mcfpp.library.model.LibraryModel

/** Reads only the bundled Playground library through streams; no URLs, archives or plugins. */
object LibBinReader {
    internal class BuiltinSnapshot(binary: ByteArray, plan: ByteArray? = null) {
        private val binary = binary.copyOf()
        private val plan = plan?.copyOf()
        private var decoded: LibraryModel? = null

        @Synchronized
        fun decode(bytes: ByteArray, codec: LibraryModelCodec, compatibility: LibraryCompatibility): LibraryModel {
            if (!bytes.contentEquals(binary)) return codec.decode(bytes, compatibility)
            val model = decoded ?: codec.decode(binary, compatibility).also { value ->
                plan?.let { value.importPlan = BuiltinImportPlan.decode(it, binary, value) }
                decoded = value
            }
            ManifestCodec.validate(model.manifest, compatibility)
            return model.sessionCopy()
        }
    }

    class SourceException(val payload: Payload, cause: Throwable) : IllegalStateException(
        "Unable to import ${payload.identity ?: payload.source}: ${cause.message}", cause,
    )

    data class Payload(
        val binary: ByteArray,
        val moduleJson: ByteArray,
        val resources: Map<String, ByteArray>,
        val identity: LibraryIdentity? = null,
        val source: String,
    ) {
        internal var builtinSnapshot: BuiltinSnapshot? = null
        internal var builtinPlan: ByteArray? = null
    }

    fun builtin(loader: ClassLoader, target: String): Payload {
        require(target == "1.21.8") { "The Playground bundles Minecraft 1.21.8" }
        fun read(name: String): ByteArray = requireNotNull(loader.getResourceAsStream(name)) {
            "Missing embedded Playground resource: $name"
        }.use { it.readBytes() }

        // Both Wasm and the JVM benchmark consume this generated, deterministic inventory.
        val prefix = "datapack/targets/$target/"
        val names = read("browser-resources.txt").toString(Charsets.UTF_8).lineSequence().filter { it.isNotEmpty() }
        val resources = linkedMapOf<String, ByteArray>()
        names.filter { it.startsWith(prefix + "datapack/") }.forEach { name ->
            resources[name.removePrefix(prefix)] = read(name)
        }
        val binary = read(prefix + "bin.mclib")
        val plan = read(prefix + BuiltinImportPlan.RESOURCE)
        return Payload(binary, read(prefix + "module.json"), resources,
            source = "Embedded Minecraft $target standard library").also {
            it.builtinPlan = plan
            it.builtinSnapshot = BuiltinSnapshot(binary, plan)
        }
    }
}
