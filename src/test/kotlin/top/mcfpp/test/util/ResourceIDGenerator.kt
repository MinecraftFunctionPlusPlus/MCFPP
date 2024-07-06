package top.mcfpp.test.util

import java.io.File
import java.lang.StringBuilder


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
    text.append("package top.mcfpp.lang.type\n\n")
    text.append("import top.mcfpp.lang.resource.*\n\n")
    text.append("class MCFPPResourceType {\n\n")
    text.append("object ResourceID: MCFPPType(parentType = listOf(MCFPPNBTType.NBT)){\n")
    text.append("    override val typeName: String\n")
    text.append("        get() = \"ResourceID\"\n")
    text.append("}\n")
    for (line in lines){
        if(line != ""){
            text.append("    object $line: MCFPPType(parentType = listOf(ResourceID)){\n")
            text.append("        override val typeName: String\n")
            text.append("            get() = \"$line\"\n")
            text.append("    }\n")
        }
    }
    text.append("}\n")
    val typeFile = File("src/main/kotlin/top/mcfpp/lang/type/MCFPPResourceType.kt")
    typeFile.writeText(text.toString())
    println("MCFPPResourceType.kt generated.")
}

fun ResourceIDWriter(id: String){

    val template: String =
        """
import net.querz.nbt.tag.StringTag
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.lang.*
import top.mcfpp.lang.resource.ResourceID
import top.mcfpp.lang.resource.ResourceIDConcrete
import top.mcfpp.lang.type.MCFPPClassType
import top.mcfpp.lang.type.MCFPPResourceType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import java.util.*

open class $id: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.$id

    /**
     * 创建一个${id}类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier) {
        this.identifier = identifier
    }

    /**
     * 创建一个${id}值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个${id}
     * @param b 被复制的${id}值
     */
    constructor(b: $id) : super(b)

    override fun assign(b: Var<*>): $id {
        return super.assign(b) as $id
    }

    override fun cast(type: MCFPPType): Var<*> {
        when(type){
            MCFPPResourceType.$id -> return this
            else -> {
                LogProcessor.error("Cannot cast [${'$'}{this.type}] to [${'$'}type]")
                return UnknownVar(this.identifier)
            }
        }
    }

    companion object {
        val data = CompoundData("$id","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
        }
    }
}

class ${id}Concrete: MCFPPValue<String>, ResourceIDConcrete{

    constructor(
        curr: FieldContainer,
        value: String,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier, value)

    constructor(value: String, identifier: String = UUID.randomUUID().toString()) : super(value, identifier)

    constructor(id: ${id}, value: String) : super(id, value)

    constructor(id: ${id}Concrete) : super(id)

    override fun clone(): ${id}Concrete {
        return ${id}Concrete(this)
    }

    override fun getTempVar(): Var<*> {
        return ${id}Concrete(this.value)
    }
}        
"""
    //覆盖写入文件
    val file = File("src/main/kotlin/top/mcfpp/lang/resource/$id.kt")
    file.writeText(template)

    //生成java文件
    val javaTemplate =
"""
package top.mcfpp.mni.resource;

import top.mcfpp.annotations.MNIRegister;

public class ${id}Data {

}        
"""
    val javaFile = File("src/main/java/top/mcfpp/mni/resource/${id}Data.java")
    javaFile.writeText(javaTemplate)

    //javaConcrete
    val javaConcreteTemplate =
"""
    
package top.mcfpp.mni.resource;

import top.mcfpp.annotations.MNIRegister;

public class ${id}ConcreteData {

}
"""
    val javaConcreteFile = File("src/main/java/top/mcfpp/mni/resource/${id}ConcreteData.java")
    javaConcreteFile.writeText(javaConcreteTemplate)
}