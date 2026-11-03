package top.mcfpp.library.format

import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*

object ManifestCodec {
    fun encode(value: LibraryManifest): ByteArray = LibraryBinaryWriter().apply { record {
        TypeTableCodec.writePackage(this, value.library); string(value.classifier)
        string(value.semantics); string(value.runtimeAbi); writeTarget(this, value.target)
        list(value.dependencies) { TypeTableCodec.writePackage(this, it.library); string(it.classifier) }
        list(value.entryEnvironments) { u32(it.value) }; list(value.rootDeclarations) { u32(it.value) }
    } }.toByteArray()

    fun decode(bytes: ByteArray): LibraryManifest {
        val reader = LibraryBinaryReader(bytes, sectionId = LibrarySectionId.MANIFEST)
        return try { reader.record {
            val library = TypeTableCodec.readPackage(this); val classifier = string()
            val semantics = string(); val abi = string(); val target = readTarget(this)
            LibraryManifest(library, classifier, target,
                list { LibraryDependency(TypeTableCodec.readPackage(this), string()) },
                list { EnvironmentId(u32()) }, list { DeclarationId(u32()) }, semantics, abi)
        }.also { reader.requireEnd() } } catch (failure: IllegalArgumentException) {
            reader.fail(failure.message ?: "Invalid manifest")
        }
    }

    fun writeTarget(writer: LibraryBinaryWriter, value: LibraryTarget) = with(writer) {
        string(value.minecraftVersion); u8(value.edition); list(value.requiredCapabilities) { string(it) }
    }
    fun readTarget(reader: LibraryBinaryReader): LibraryTarget = with(reader) { LibraryTarget(string(), u8(), list { string() }) }

    fun validate(value: LibraryManifest, compatibility: LibraryCompatibility) {
        if (value.semantics !in compatibility.semantics)
            problem("LIB_SEMANTICS_UNSUPPORTED", "Unsupported language semantics ${value.semantics}")
        if (value.runtimeAbi !in compatibility.runtimeAbis)
            problem("LIB_RUNTIME_ABI", "Unsupported runtime ABI ${value.runtimeAbi}")
        val target = value.target
        if (target.edition != 1 || target.edition != compatibility.edition ||
            compatibility.minecraftVersion?.let { it != target.minecraftVersion } == true ||
            compatibility.availableCapabilities?.let { !it.containsAll(target.requiredCapabilities) } == true)
            problem("LIB_TARGET", "Library target/capabilities are incompatible: $target")
        require(value.library.coordinate.isNotEmpty() && value.library.version.isNotEmpty())
        require(target.minecraftVersion.isNotEmpty())
        require(value.dependencies.distinct().size == value.dependencies.size) { "Duplicate library dependency" }
        require(value.dependencies.none { it.library == value.library && it.classifier == value.classifier }) { "Self dependency" }
        require(value.entryEnvironments.distinct().size == value.entryEnvironments.size)
        require(value.rootDeclarations.distinct().size == value.rootDeclarations.size)
    }

    private fun problem(code: String, message: String): Nothing =
        throw LibraryFormatException(code, message, 0, LibrarySectionId.MANIFEST)
}
