package top.mcfpp.library.api

import top.mcfpp.library.compiler.LibraryExporter
import top.mcfpp.library.compiler.OwnedLibrarySelection
import top.mcfpp.library.format.LibraryModelCodec
import top.mcfpp.library.format.CompoundCodec
import top.mcfpp.library.format.NativeCodec
import top.mcfpp.library.model.LibraryModel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * A completed library export.
 * @property path Absolute normalized destination path.
 * @property bytesWritten Size of the encoded library file in bytes.
 * @property model Declarations and resources captured for this export.
 */
data class LibraryExportResult(val path: Path, val bytesWritten: Long, val model: LibraryModel)

/** Writes version 1 library files from declarations selected by the compiler. */
object LibraryExport {
    /**
     * Captures [selection], encodes it, and atomically replaces [path] with the completed file.
     * Creates parent directories as needed. Returns the destination, encoded size, and captured model.
     */
    fun write(selection: OwnedLibrarySelection, path: Path): LibraryExportResult {
        val model = LibraryExporter.capture(selection)
        val bytes = LibraryModelCodec(listOf(CompoundCodec, NativeCodec)).encode(model)
        val destination = path.toAbsolutePath().normalize()
        val parent = requireNotNull(destination.parent)
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".mcfpp-library-", ".tmp")
        try {
            Files.newOutputStream(temporary).use { it.write(bytes); it.flush() }
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
        return LibraryExportResult(destination, bytes.size.toLong(), model)
    }
}
