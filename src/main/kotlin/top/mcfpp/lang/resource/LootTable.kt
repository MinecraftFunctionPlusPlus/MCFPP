
package top.mcfpp.lang.resource
            
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.lang.ClassPointer
import top.mcfpp.lang.UnknownVar
import top.mcfpp.lang.Var
import top.mcfpp.lang.type.MCFPPClassType
import top.mcfpp.lang.type.MCFPPResourceType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.util.LogProcessor
import java.util.*
import top.mcfpp.model.function.Function

open class LootTable: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.LootTable

    /**
     * 创建一个LootTable类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个LootTable值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个LootTable
     * @param b 被复制的LootTable值
     */
    constructor(b: LootTable) : super(b)

    override fun assign(b: Var<*>): LootTable {
        return super.assign(b) as LootTable
    }

    override fun cast(type: MCFPPType): Var<*> {
        when(type){
            MCFPPResourceType.LootTable -> return this
            else -> {
                LogProcessor.error("Cannot cast [${this.type}] to [$type]")
                return UnknownVar(this.identifier)
            }
        }
    }

    companion object {
        val data = CompoundData("LootTable","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
        }
    }
}

class LootTableConcrete: MCFPPValue<String>, LootTable{

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

    constructor(id: LootTable, value: String) : super(id){
        this.value = value
    }

    constructor(id: LootTableConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): LootTableConcrete {
        return LootTableConcrete(this)
    }

    override fun getTempVar(): Var<*> {
        return LootTableConcrete(this.value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        val parent = parent
        if (parentClass() != null) {
            val cmd = when(parent){
                is ClassPointer -> {
                    Commands.selectRun(parent)
                }
                is MCFPPClassType -> {
                    arrayOf(Command.build("execute as ${parentClass()!!.uuid} run "))
                }
                else -> TODO()
            }
            if(cmd.size == 2){
                Function.addCommand(cmd[0])
            }
            Function.addCommand(cmd.last().build(
                "data modify entity @s data.${identifier} set value $value")
            )
        } else {
            val cmd = Command.build("data modify")
                .build(nbtPath.toCommandPart())
                .build("set value $value")
            Function.addCommand(cmd)
        }
        val re = LootTable(this)
        if(replace){
            Function.currFunction.field.putVar(identifier, re, true)
        }
        return re
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }
}        
