package top.mcfpp.lang

import net.querz.nbt.tag.IntArrayTag
import net.querz.nbt.tag.Tag
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.type.MCFPPBaseType.BaseEntity
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPNBTType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import java.util.UUID


/**
 * 代表了一个实体。一个实体类型的变量通常是一个UUID数组，可以通过Thrower法来选择实体，从而实现对实体的操作。
 *
 */
class Entity : NBTAny<IntArrayTag>{

    //TODO: 这里可以根据实体类型的不同用不同的type,比如zombie
    override var type: MCFPPType = BaseEntity

    /**
     * 创建一个list类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : this(curr.prefix + identifier) {
        this.identifier = identifier

    }

    /**
     * 创建一个list值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier)

    /**
     * 创建一个固定的list
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        curr: FieldContainer,
        value: IntArrayTag,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier) {
        isConcrete = true
        if(value.value.size != 4) throw VariableConverseException()
    }

    /**
     * 创建一个固定的list。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: IntArrayTag, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        isConcrete = true
        if(value.value.size != 4) throw VariableConverseException()
    }

    /**
     * 复制一个list
     * @param b 被复制的list值
     */
    constructor(b: NBTAny<IntArrayTag>) : super(b)

    /**
     * 根据标识符获取实体的NBT
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        TODO("Not yet implemented")
    }

    /**
     * 根据标识符，以实体为
     *
     * @param key 成员方法的标识符
     * @param normalParams 成员方法的参数
     * @return
     */
    override fun getMemberFunction(
        key: String,
        readOnlyParams:List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        TODO("Not yet implemented")
    }


    /**
     * 将b中的值赋值给此实体变量
     * @param b 变量的对象
     */
    override fun assign(b: Var<*>?) {
        hasAssigned = true
        when (b) {
            is Entity -> {
                assignCommand(b)
            }
            else -> {
                throw VariableConverseException()
            }
        }
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun cast(type: MCFPPType): Var<*> {
        return when(type){
            BaseEntity -> this
            //MCFPPNBTType.NBT -> NBTBasedData(javaValue!!)
            MCFPPBaseType.Any -> MCAny(this)
            else -> throw VariableConverseException()
        }
    }

    override fun createTempVar(): Var<*> = Entity()

    override fun createTempVar(value: Tag<*>): Var<*> = Entity(value as IntArrayTag)

    companion object {
        val data = CompoundData("entity","mcfpp")
    }

}