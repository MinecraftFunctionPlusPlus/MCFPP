
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

open class Particle: ResourceID {

    override var type: MCFPPType = MCFPPResourceType.Particle

    /**
     * 创建一个Particle类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个Particle值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier){
        isTemp = true
    }

    /**
     * 复制一个Particle
     * @param b 被复制的Particle值
     */
    constructor(b: Particle) : super(b)

    override fun assign(b: Var<*>): Particle {
        return super.assign(b) as Particle
    }

    override fun cast(type: MCFPPType): Var<*> {
        when(type){
            MCFPPResourceType.Particle -> return this
            else -> {
                LogProcessor.error("Cannot cast [${this.type}] to [$type]")
                return UnknownVar(this.identifier)
            }
        }
    }

    companion object {
        val data = CompoundData("Particle","mcfpp.lang.resource")

        init {
            data.initialize()
            data.extends(ResourceID.data)
        }
    }
}

class ParticleConcrete: MCFPPValue<String>, ResourceIDConcrete{

    constructor(
        curr: FieldContainer,
        value: String,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier, value)

    constructor(value: String, identifier: String = UUID.randomUUID().toString()) : super(value, identifier)

    constructor(id: Particle, value: String) : super(id, value)

    constructor(id: ParticleConcrete) : super(id)

    override fun clone(): ParticleConcrete {
        return ParticleConcrete(this)
    }

    override fun getTempVar(): Var<*> {
        return ParticleConcrete(this.value)
    }
}        
