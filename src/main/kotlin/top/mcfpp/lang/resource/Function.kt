
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

open class Function: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.Function

    /**
     * 创建一个Function类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个Function值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个Function
     * @param b 被复制的Function值
     */
    constructor(b: Function) : super(b)

    override fun assign(b: Var<*>): Function {
        return super.assign(b) as Function
    }

    override fun cast(type: MCFPPType): Var<*> {
        when(type){
            MCFPPResourceType.Function -> return this
            else -> {
                LogProcessor.error("Cannot cast [${this.type}] to [$type]")
                return UnknownVar(this.identifier)
            }
        }
    }

    companion object {
        val data = CompoundData("Function","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
        }
    }
}

class FunctionConcrete: MCFPPValue<String>, Function{

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

    constructor(id: Function, value: String) : super(id){
        this.value = value
    }

    constructor(id: FunctionConcrete) : super(id){
        this.value = id.value
    }

    override fun clone(): FunctionConcrete {
        return FunctionConcrete(this)
    }

    override fun getTempVar(): Var<*> {
        return FunctionConcrete(this.value)
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
                top.mcfpp.model.function.Function.addCommand(cmd[0])
            }
            top.mcfpp.model.function.Function.addCommand(cmd.last().build(
                "data modify entity @s data.${identifier} set value $value")
            )
        } else {
            val cmd = Command.build("data modify")
                .build(nbtPath.toCommandPart())
                .build("set value $value")
            top.mcfpp.model.function.Function.addCommand(cmd)
        }
        val re = Function(this)
        if(replace){
            top.mcfpp.model.function.Function.currFunction.field.putVar(identifier, re, true)
        }
        return re
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }
}        
