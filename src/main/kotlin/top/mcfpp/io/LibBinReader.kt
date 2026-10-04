package top.mcfpp.io

import com.esotericsoftware.kryo.io.Input
import top.mcfpp.io.KryoManager.kryo
import top.mcfpp.io.info.GlobalFieldInfo
import top.mcfpp.model.scope.GlobalScope
import java.io.FileInputStream
import java.io.InputStream

object LibBinReader {

    fun read(path: String) {
        FileInputStream(path).use { stream ->
            readFromStream(stream)
        }
    }

    fun readFromStream(stream: InputStream) {
        Input(stream).use { input ->
            val validHeader = try {
                input.readInt() == LibBinFormat.MAGIC && input.readInt() == LibBinFormat.VERSION
            } catch (_: com.esotericsoftware.kryo.KryoException) {
                false
            }
            if (!validHeader) {
                top.mcfpp.util.LogProcessor.error("Unsupported MCFPP library cache format. Recompile the library sources with this compiler.")
                return
            }
            val info = kryo.readObject(input, GlobalFieldInfo::class.java)
            GlobalScope.mergeInfo(info)
            //解析命名空间
            GlobalScope.libNamespaces.values.forEach { it.resolve() }
            GlobalScope.stdNamespaces.values.forEach { it.resolve() }
        }
    }
}
