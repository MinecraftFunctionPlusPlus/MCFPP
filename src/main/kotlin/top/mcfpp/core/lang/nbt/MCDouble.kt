package top.mcfpp.core.lang.nbt

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.primitive.DoubleTag
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class MCDouble: NBTBasedData {

    override var type: MCFPPType = MCFPPNBTType.Double

    /**
     * 创建一个double值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: MCDouble) : super(b)

    @InsertCommand
    override fun assignCommand(a: NBTBasedData) : MCDouble {
        nbtType = a.nbtType
        return if(a is MCDoubleConcrete){
            MCDoubleConcrete(this, a.value)
        }else {
            Function.addCommand(Commands.dataSetFrom(nbtPath, a.nbtPath))
            MCDouble(this)
        }
    }

    companion object {

        val data by lazy {
            CompoundData("double","mcfpp")
        }
    }
}


class MCDoubleConcrete: MCDouble, MCFPPValue<DoubleTag> {

    override var value: DoubleTag

    /**
     * 创建一个固定的string
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        curr: FieldContainer,
        value: DoubleTag,
        identifier: String = TempPool.getVarIdentify()
    ) : super(curr.prefix + identifier) {
        this.value = value
    }

    /**
     * 创建一个固定的string。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: DoubleTag, identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        this.value = value
    }

    constructor(v: MCDouble, value: DoubleTag): super(v){
        this.value = value
    }

    constructor(v: MCDoubleConcrete) : super(v){
        this.value = v.value
    }

    override fun clone(): MCDoubleConcrete {
        return MCDoubleConcrete(this)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, value).toDynamic(replace)
        return MCDouble(this)
    }
}