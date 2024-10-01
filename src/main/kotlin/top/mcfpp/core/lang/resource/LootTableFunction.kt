
package top.mcfpp.core.lang.resource
            
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPResourceType
import top.mcfpp.type.MCFPPType
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import java.util.*
import top.mcfpp.model.function.Function
import top.mcfpp.mni.resource.LootTableFunctionData
import top.mcfpp.mni.resource.LootTableFunctionConcreteData

open class LootTableFunction: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.LootTableFunction

    /**
     * 创建一个LootTableFunction类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个LootTableFunction值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个LootTableFunction
     * @param b 被复制的LootTableFunction值
     */
    constructor(b: LootTableFunction) : super(b)

    override fun doAssignedBy(b: Var<*>): LootTableFunction {
        return super.assignedBy(b) as LootTableFunction
    }

    companion object {
        val data = CompoundData("LootTableFunction","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(LootTableFunctionData::class.java)
        }
    }
}

class LootTableFunctionConcrete: MCFPPValue<String>, LootTableFunction{

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

    constructor(id: LootTableFunction, value: String) : super(id){
        this.value = value
    }

    constructor(id: LootTableFunctionConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): LootTableFunctionConcrete {
        return LootTableFunctionConcrete(this)
    }

    override fun getTempVar(): LootTableFunctionConcrete {
        return LootTableFunctionConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        val parent = parent
        if (parentClass() != null) {
            val cmd = Commands.selectRun(parent!!, "data modify entity @s data.${identifier} set value $value")
            Function.addCommands(cmd)
        } else {
            val cmd = Command.build("data modify")
                .build(nbtPath.toCommandPart())
                .build("set value $value")
            Function.addCommand(cmd)
        }
        val re = LootTableFunction(this)
        if(replace){
            Function.currFunction.field.putVar(identifier, re, true)
        }
        return re
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }
    
    companion object {
        val data = CompoundData("LootTableFunction","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
            data.getNativeFromClass(LootTableFunctionConcreteData::class.java)
        }
    }
    
}        
