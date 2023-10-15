package top.mcfpp.lang

import top.mcfpp.lib.Member
import top.mcfpp.lib.Function
import java.util.UUID

/**
 * 代表了一个实体。
 *
 * 利用一个记分板
 */
class Entity : Var{

    /**
     * 实体的id
     */
    val entityID: String

    /**
     * 实体的uuid，用于选择实体
     */
    val uuid: UUID?

    override val type: String
        get() = "Entity"

    constructor(entityID: String){
        this.entityID = entityID
        isConcrete = false
        uuid = null
    }

    constructor(entityID: String, uuid: UUID){
        this.entityID = entityID
        this.uuid = uuid
        isConcrete = true
    }

    /**
     * 根据标识符获取实体的NBT
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var?, Boolean> {
        TODO("Not yet implemented")
    }

    /**
     * 根据标识符，以实体为
     *
     * @param key 成员方法的标识符
     * @param params 成员方法的参数
     * @return
     */
    override fun getMemberFunction(
        key: String,
        params: List<String>,
        accessModifier: Member.AccessModifier
    ): Pair<Function?, Boolean> {
        TODO("Not yet implemented")
    }

    /**
     * 将b中的值赋值给此实体变量
     * @param b 变量的对象
     */
    override fun assign(b: Var?) {

    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun cast(type: String): Var? {
        TODO("Not yet implemented")
    }

    override fun clone(): Any {
        TODO("Not yet implemented")
    }

    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     * @return
     */
    override fun getTempVar(): Var {
        TODO("Not yet implemented")
    }

    override fun toDynamic() {
        TODO("Not yet implemented")
    }


}