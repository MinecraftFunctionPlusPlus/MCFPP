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
        if(line != ""){
            ResourceIDWriter(line)
            println("ResourceID $line generated.")
        }
    }
    //生成MCFPPResourceType.kt
    val text = StringBuilder("")
    text.append("package top.mcfpp.type\n\n")
    text.append("\n" +
            "import top.mcfpp.model.FieldContainer\n" +
            "import top.mcfpp.core.lang.Var\n" +
            "import top.mcfpp.core.lang.resource.*\n" +
            "import top.mcfpp.model.Class\n" +
            "import top.mcfpp.util.TempPool\n\n")
    text.append("class MCFPPResourceType {\n\n")
    text.append("object ResourceID: MCFPPType(parentType = listOf(MCFPPNBTType.NBT)){\n")
    text.append("    override val typeName: String\n")
    text.append("        get() = \"ResourceID\"\n")
    text.append("}\n")
    for (line in lines){
        if(line != ""){
            text.append("    object $line: MCFPPType(parentType = listOf(ResourceID)){\n")
            text.append("        override val typeName: String\n")
            text.append("            get() = \"$line\"\n\n")
            text.append("        init {\n")
            text.append("            registerType()\n")
            text.append("        }\n")
            text.append("        override fun build(identifier: String, container: FieldContainer): Var<*> = ${line}Concrete(container, \"\", identifier)\n")
            text.append("        override fun build(identifier: String): Var<*> = ${line}Concrete(\"\", identifier)\n")
            text.append("        override fun build(identifier: String, clazz: Class): Var<*> = ${line}Concrete(clazz, \"\", identifier)\n")
            text.append("        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = $line(container, identifier)\n")
            text.append("        override fun buildUnConcrete(identifier: String): Var<*> = $line(identifier)\n")
            text.append("        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = $line(clazz, identifier)\n")
            text.append("    }\n")
        }
    }
    text.append("}\n")
    val typeFile = File("src/main/kotlin/top/mcfpp/type/MCFPPResourceType.kt")
    typeFile.writeText(text.toString())
    println("MCFPPResourceType.kt generated.")
}

fun ResourceIDWriter(id: String){

    val template: String =
        """package top.mcfpp.core.lang.resource
            
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.mni.resource.${id}ConcreteData
import top.mcfpp.mni.resource.${id}Data
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class $id: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.$id

    /**
     * 创建一个${id}类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        curr: FieldContainer,
        identifier: String = TempPool.getVarIdentify()
    ) : super(curr, identifier) {
        this.identifier = identifier
    }

    /**
     * 创建一个${id}值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    /**
     * 复制一个${id}
     * @param b 被复制的${id}值
     */
    constructor(b: $id) : super(b)

    companion object {
        val data = CompoundData("$id","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(${id}Data::class.java)
        }
    }
}

class ${id}Concrete: MCFPPValue<String>, ${id}{

    override var value: String

    constructor(
        curr: FieldContainer,
        value: String,
        identifier: String = TempPool.getVarIdentify()
    ) : super(curr, identifier) {
        this.value = value
    }

    constructor(value: String, identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        this.value = value
    }

    constructor(id: $id, value: String) : super(id){
        this.value = value
    }

    constructor(id: ${id}Concrete) : super(id){
        this.value = id.value
    }

    override fun clone(): ${id}Concrete {
        return ${id}Concrete(this)
    }

    override fun getTempVar(): ${id}Concrete {
        return ${id}Concrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, StringTag(value)).toDynamic(replace)
        return ${id}(this)
    }

    override fun toString(): String {
        return "[${'$'}type,value=${'$'}value]"
    }
    
    companion object {
        val data = CompoundData("$id","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(${id}ConcreteData::class.java)
        }
    }
    
}        
"""
    //覆盖写入文件
    val file = File("src/main/kotlin/top/mcfpp/core/lang/resource/$id.kt")
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

public class ${id}ConcreteData {

}
"""
    val javaConcreteFile = File("src/main/java/top/mcfpp/mni/resource/${id}ConcreteData.java")
    javaConcreteFile.writeText(javaConcreteTemplate)
}