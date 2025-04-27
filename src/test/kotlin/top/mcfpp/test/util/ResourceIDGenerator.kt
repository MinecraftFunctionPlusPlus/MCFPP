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
        """@From<"top.mcfpp.mni.resource.${id}Data">
data $id: ResourceID;

@From<"top.mcfpp.mni.resource.${id}ObjectData">
object data $id: ResourceID;
"""
    //覆盖写入文件
    val file = File("src/main/mcfpp/minecraft/resource/$id.mcfpp")
    file.writeText(template)

    //生成java文件
    val javaTemplate =
"""package top.mcfpp.mni.resource;

public class ${id}Data {

}        
"""
    val javaFile = File("src/main/java/top/mcfpp/mni/resource/${id}Data.java")
    javaFile.writeText(javaTemplate)

    //javaConcrete
    val javaConcreteTemplate =
"""package top.mcfpp.mni.resource;

public class ${id}ObjectData {

}
"""
    val javaConcreteFile = File("src/main/java/top/mcfpp/mni/resource/${id}ObjectData.java")
    javaConcreteFile.writeText(javaConcreteTemplate)
}