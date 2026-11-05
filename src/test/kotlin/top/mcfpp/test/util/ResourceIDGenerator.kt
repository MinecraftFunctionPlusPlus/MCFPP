package top.mcfpp.test.util

import java.io.File


/**
 * Use
 * ```powershell
kotlinc src/test/kotlin/top/mcfpp/test/util/ResourceIDGenerator.kt -include-runtime -d ResourceIDGenerator.jar;java -jar ResourceIDGenerator.jar
 * ```
 */
fun main(){
    //读取type.txt
    val file = File("src/main/resources/type.txt")
    val lines = file.readLines()
    for (line in lines){
        if(line != "" && !line.startsWith("#")){
            ResourceIDWriter(line)
            println("ResourceID $line generated.")
        }
    }
}

fun ResourceIDWriter(id: String){

    val template =
        """@From("top.mcfpp.mni.resource.${id}Data")
data $id: ResourceID

@From("top.mcfpp.mni.resource.${id}ObjectData")
object data $id: ResourceID
"""
    //覆盖写入文件
    val file = File("src/main/mcfpp/minecraft/resource/$id.mcfpp")
    file.writeText(template)

    // Keep hand-written provider methods when regenerating resource declarations.
    for (suffix in listOf("Data", "ObjectData")) {
        val provider = File("src/main/kotlin/top/mcfpp/mni/resource/$id$suffix.kt")
        if (!provider.exists()) {
            provider.writeText("package top.mcfpp.mni.resource\n\nobject $id$suffix {}\n")
        }
    }
}
