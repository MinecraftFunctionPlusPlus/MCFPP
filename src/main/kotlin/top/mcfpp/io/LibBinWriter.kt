package top.mcfpp.io

import com.esotericsoftware.kryo.io.Output
import top.mcfpp.io.KryoManager.kryo
import top.mcfpp.model.scope.GlobalScope
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths

object LibBinWriter {

    fun write(path: String) {
        if (path == "null") return

        val libPath = Paths.get("$path/bin.mclib")
        Files.createDirectories(libPath.parent)
        if (Files.notExists(libPath)) Files.createFile(libPath)

        FileOutputStream(libPath.toFile()).use { fileOutputStream ->
            Output(fileOutputStream).use { output ->
                kryo.writeObject(output, GlobalScope.getInfo())
                output.flush()
            }
        }
    }
}