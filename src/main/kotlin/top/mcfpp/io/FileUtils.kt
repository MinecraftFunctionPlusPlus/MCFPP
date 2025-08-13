package top.mcfpp.io

import org.apache.tools.zip.ZipFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.jar.JarEntry
import java.util.jar.JarFile

object FileUtils {


    /**
     * 删除原有的数据包中的全部内容。
     * @param directory 文件或文件夹
     */
    fun delAllFile(directory: File) {
        if (!directory.isDirectory) {
            directory.delete()
        } else {
            val files: Array<out File>? = directory.listFiles()
            // 空文件夹
            if (files!!.isEmpty()) {
                directory.delete()
                return
            }
            // 删除子文件夹和子文件
            for (file in files) {
                if (file.isDirectory) {
                    delAllFile(file)
                } else {
                    file.delete()
                }
            }

            // 删除文件夹本身
            directory.delete()
        }
    }

    fun copyAllFiles(sourcePath: String, targetPath: String) {
        val source = File(sourcePath)
        val target = File(targetPath)
        if (!source.exists() || !source.isDirectory) return
        if (!target.exists()) target.mkdirs()
        source.listFiles()?.forEach { file ->
            val targetFile = File(target, file.name)
            if (file.isDirectory) {
                copyAllFiles(file.absolutePath, targetFile.absolutePath)
            } else {
                file.copyTo(targetFile, overwrite = true)
            }
        }
    }


    fun extractFolderFromInner(folderInJar: String, outputDir: String) {
        val f = File(DatapackCreator::class.java.getProtectionDomain().codeSource.location.toURI())
        if(!f.isFile){
            copyAllFiles("src/main/resources/$folderInJar", outputDir)
            return
        }
        JarFile(f).use { jarFile ->
            jarFile.stream()
                .filter { entry: JarEntry ->
                    entry.name.startsWith(folderInJar) && !entry.isDirectory
                }
                .forEach { entry: JarEntry ->
                    try {
                        jarFile.getInputStream(entry).use { `is` ->
                            val outputPath =
                                Paths.get(outputDir, entry.name.substring(folderInJar.length))
                            Files.createDirectories(outputPath)
                            Files.copy(`is`, outputPath, StandardCopyOption.REPLACE_EXISTING)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
        }
    }

    fun extractFileFromInner(fileInJar: String, outputFile: String) {
        // 尝试从 ClassLoader 获取资源
        val inputStream: InputStream? = DatapackCreator::class.java.classLoader.getResourceAsStream(fileInJar)
            ?: run {
                // 如果资源不存在，尝试从文件系统加载
                val file = File(fileInJar)
                if (file.exists()) file.inputStream() else null
            }

        // 如果资源仍未找到，抛出异常
        inputStream ?: throw FileNotFoundException("File $fileInJar not found in JAR or file system.")

        // 确保目标目录存在，并将文件写入目标路径
        inputStream.use { input ->
            val outputPath = Paths.get(outputFile)
            Files.createDirectories(outputPath.parent) // 确保目标目录存在
            Files.copy(input, outputPath, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun ZipFile.extractTo(sourceDir: String, targetDir: String) {
        val outputDir = File(targetDir)
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        entries.asSequence()
            .filter { it.name.startsWith(sourceDir) }
            .forEach { entry ->
                val outputFile = File(outputDir, entry.name.removePrefix(sourceDir))
                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    getInputStream(entry).use { input ->
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
    }


}