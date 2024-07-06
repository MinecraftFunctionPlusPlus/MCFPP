
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

open class VillagerType: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.VillagerType

    /**
     * 创建一个VillagerType类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个VillagerType值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个VillagerType
     * @param b 被复制的VillagerType值
     */
    constructor(b: VillagerType) : super(b)

    override fun assign(b: Var<*>): VillagerType {
        return super.assign(b) as VillagerType
    }

    override fun cast(type: MCFPPType): Var<*> {
        when(type){
            MCFPPResourceType.VillagerType -> return this
            else -> {
                LogProcessor.error("Cannot cast [${this.type}] to [$type]")
                return UnknownVar(this.identifier)
            }
        }
    }

    companion object {
        val data = CompoundData("VillagerType","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
        }
    }
}

class VillagerTypeConcrete: MCFPPValue<String>, ResourceIDConcrete{

    constructor(
        curr: FieldContainer,
        value: String,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier, value)

    constructor(value: String, identifier: String = UUID.randomUUID().toString()) : super(value, identifier)

    constructor(id: VillagerType, value: String) : super(id, value)

    constructor(id: VillagerTypeConcrete) : super(id)

    override fun clone(): VillagerTypeConcrete {
        return VillagerTypeConcrete(this)
    }

    override fun getTempVar(): Var<*> {
        return VillagerTypeConcrete(this.value)
    }
}        
