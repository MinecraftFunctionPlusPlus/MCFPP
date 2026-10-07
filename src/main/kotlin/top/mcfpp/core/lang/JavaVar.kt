package top.mcfpp.core.lang

import top.mcfpp.command.Command
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.core.lang.nbt.*
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/**
 * Internal host payload adapter. It does not expose reflective language members.
 *
 * @constructor Create empty Java var
 */
@Suppress("unchecked_cast")
class JavaVar : ConcreteVar<JavaVar, Any?> {

    override var type: MCFPPType = MCFPPConcreteType.JavaVar

    /**
     * 创建一个固定的JavaVar。它的标识符和mc名一致
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    @JvmOverloads
    constructor(value: Any?, identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        this.value = value
    }


    /**
     * 复制一个JavaVar
     * @param b 被复制的JavaVar值
     */
    constructor(b: JavaVar) : super(b)

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    override fun doAssignedBy(b: Var<*>): JavaVar {
        when (b) {
            is JavaVar -> {
                this.value = b.value
            }

            else -> {
                this.value = b
            }
        }
        return this
    }

    override fun clone(): JavaVar {
        return JavaVar(this)
    }

    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     * @return
     */
    override fun getTempVar(): JavaVar = this

    override fun storeToStack() {}

    override fun getFromStack() {}

    /**
     * 根据标识符获取一个成员。
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return null to true
    }

    /**
     * 根据方法标识符和方法的参数列表获取一个方法。如果没有这个方法，则返回null
     *
     * @param key 成员方法的标识符
     * @param normalArgs 成员方法的参数
     * @return
     */
    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return UnknownFunction(key) to true
    }

    override fun toString(): String {
        return "JavaVar[$value]"
    }

    override fun toCommandPart(): Command {
        return when(value){
            null -> Command("null")
            is Var<*> -> (value as Var<*>).toCommandPart()
            is Command -> value as Command
            else -> Command(value.toString())
        }
    }

    companion object{

        fun javaToMC(v : Any) : Var<*>{
            return when(v){
                is Int -> MCIntConcrete(v)
                is Float -> MCFloatConcrete(v)
                is Boolean -> ScoreBoolConcrete(v)
                is String -> MCStringConcrete(StringTag(v))
                is CompoundTag -> NBTBasedDataConcrete(v)
                //is ArrayList<*> -> NBTListConcrete()
                //is HashMap<*,*> -> NBTDictionaryConcrete(v.map { javaToMC(it.key!!) to javaToMC(it.value!!) }.toMap())
                else -> JavaVar(v)
            }
        }

        fun javaToMC(v: ArrayList<Any>): ArrayList<Var<*>>{
            val re = ArrayList<Var<*>>()
            for (i in v){
                re.add(javaToMC(i))
            }
            return re
        }
    }

}
