
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

open class EntityTypeTag: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.EntityTypeTag

    /**
     * 创建一个EntityTypeTag类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个EntityTypeTag值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个EntityTypeTag
     * @param b 被复制的EntityTypeTag值
     */
    constructor(b: EntityTypeTag) : super(b)

    override fun assign(b: Var<*>): EntityTypeTag {
        return super.assign(b) as EntityTypeTag
    }

    override fun cast(type: MCFPPType): Var<*> {
        when(type){
            MCFPPResourceType.EntityTypeTag -> return this
            else -> {
                LogProcessor.error("Cannot cast [${this.type}] to [$type]")
                return UnknownVar(this.identifier)
            }
        }
    }

    companion object {
        val data = CompoundData("EntityTypeTag","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
        }
    }
}

class EntityTypeTagConcrete: MCFPPValue<String>, ResourceIDConcrete{

    constructor(
        curr: FieldContainer,
        value: String,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier, value)

    constructor(value: String, identifier: String = UUID.randomUUID().toString()) : super(value, identifier)

    constructor(id: EntityTypeTag, value: String) : super(id, value)

    constructor(id: EntityTypeTagConcrete) : super(id)

    override fun clone(): EntityTypeTagConcrete {
        return EntityTypeTagConcrete(this)
    }

    override fun getTempVar(): Var<*> {
        return EntityTypeTagConcrete(this.value)
    }
}        
