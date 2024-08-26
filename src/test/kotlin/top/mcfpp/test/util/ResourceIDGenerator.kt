package top.mcfpp.test.util

import java.io.File
import java.lang.StringBuilder


/**
 * Use
 * ```powershell
 * kotlinc src/test/kotlin/top/mcfpp/test/util/ResourceIDGenerator.kt -include-runtime -d ResourceIDGenerator.jar;java -jar ResourceIDGenerator.jar
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
            "import top.mcfpp.`var`.lang.Var\n" +
            "import top.mcfpp.`var`.lang.resource.*\n" +
            "import top.mcfpp.model.Class\n\n")
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
        """
package top.mcfpp.`var`.lang.resource
            
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.`var`.lang.Var
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.`var`.lang.MCFPPValue
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import java.util.*
import top.mcfpp.model.function.Function
import top.mcfpp.mni.resource.${id}Data
import top.mcfpp.mni.resource.${id}ConcreteData

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

    override fun doAssign(b: Var<*>): $id {
        return super.assign(b) as $id
    }

    companion object {
        val data = CompoundData("$id","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFunctionFromClass(${id}Data::class.java)
        }
    }
}

class ${id}Concrete: MCFPPValue<String>, ${id}{

    override var value: String

    constructor(
        curr: FieldContainer,
        value: String,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier) {
        this.value = value
    }

    constructor(value: String, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
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
        val parent = parent
        if (parentClass() != null) {
            val cmd = Commands.selectRun(parent!!, "data modify entity @s data.${'$'}{identifier} set value ${'$'}value")
            Function.addCommands(cmd)
        } else {
            val cmd = Command.build("data modify")
                .build(nbtPath.toCommandPart())
                .build("set value ${'$'}value")
            Function.addCommand(cmd)
        }
        val re = $id(this)
        if(replace){
            Function.currFunction.field.putVar(identifier, re, true)
        }
        return re
    }

    override fun toString(): String {
        return "[${'$'}type,value=${'$'}value]"
    }
    
    companion object {
        val data = CompoundData("$id","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFunctionFromClass(${id}ConcreteData::class.java)
        }
    }
    
}        
"""
    //覆盖写入文件
    val file = File("src/main/kotlin/top/mcfpp/var/lang/resource/$id.kt")
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