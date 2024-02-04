package top.mcfpp.lang

import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lib.CompoundData
import top.mcfpp.lib.FieldContainer
import top.mcfpp.lib.Function
import top.mcfpp.lib.Member
import top.mcfpp.lib.NativeFunction
import java.util.*

/**
 * any是所有类型的基类。在mcfpp中，any的作用更像是将一个变量包装起来，将不同变量统一为一种类型。如果将一种类型转换为any类，它只能被转换回原变量能转换的类型。
 *如果因为分支语句，导致any不能被编译器获知它的类型，那么编译器就会阻止转换的进行。
 *
 * 但是你不能访问到any包装的变量中的成员，除非你将它转换为原变量的类型。
 *
 * ```java
 * any i = 5;
 * i = i + 1; // 错误
 * i = (int) i + 1; // 正确
 * i = "string";    // 正确，可以被赋值为任意类型
 * ```
 *
 * `any`与`var`的区别在于，`any`是一个变量的类型，而`var`是一个变量的声明关键字。用`any`声明的变量的类型为any，而用`var`声明的对象的类
 *型为这个变量当时值的类型。例如，`any i = 5`中`i`的类型为`any`，而`var i = 5`中`i`的类型为`int`。
 * @constructor Create empty M c any
 */
class MCAny : Var {
    override val type: String
        get() = "any"

    var value : Var? = null

    /**
     * 创建一个int类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个int值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier)

    /**
     * 创建一个固定的int
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        curr: FieldContainer,
        value: Var,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier) {
        isConcrete = true
        this.value = value
    }

    /**
     * 创建一个固定的int。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: Var, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        isConcrete = true
        this.value = value
    }

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: MCAny) : super(b)

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    override fun assign(b: Var?) {
        hasAssigned = true
        isConcrete = true
        when (b) {
            is MCAny -> {
                this.value = b.value
            }
            else -> {
                this.value = b
            }
        }
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun cast(type: String): Var {
        return if(isConcrete){
            when(type){
                "MCAny" -> this
                else -> value!!.cast(type)
            }
        }else{
            when(type){
                "MCAny" -> this
                else -> throw VariableConverseException()
            }
        }
    }

    override fun getVarValue(): Any? {
        return value
    }

    override fun clone(): Any {
        return MCAny(this)
    }

    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     * @return
     */
    override fun getTempVar(): Var {
        return this
    }

    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun toDynamic() {}

    /**
     * 根据标识符获取一个成员。
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var?, Boolean> {
        return data.field.getVar(key) to true
    }

    /**
     * 根据方法标识符和方法的参数列表获取一个方法。如果没有这个方法，则返回null
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
        return data.field.getFunction(key, params) to true
    }

    companion object{
        val data = CompoundData("any","mcfpp")

        init {
            data.initialize()
            data.field.addFunction(NativeFunction("toString",MCAnyData::class.java,"string","mcfpp"),false)
        }
    }
}